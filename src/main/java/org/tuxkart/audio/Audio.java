package org.tuxkart.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.SourceDataLine;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Moteur audio entierement synthetique : aucune ressource sonore n'est
 * necessaire. Un thread dedie melange le bruit de moteur (dont la hauteur suit
 * le regime), les crissements de pneus et les effets ponctuels, puis pousse le
 * tout dans une ligne javax.sound. Si la machine n'a pas de peripherique audio,
 * tout est silencieusement desactive.
 *
 * <p>La synthese ne se contente pas d'oscillateurs nus : chaque son passe par
 * un filtre resonant dont la coupure bouge pendant la note, ce qui donne la
 * matiere qu'une simple enveloppe d'amplitude ne produit jamais. Le moteur est
 * un train d'impulsions additif (borne sous Nyquist, donc sans repliement)
 * filtre par un passe-bas resonant et double d'une resonance d'echappement ; les
 * bruitages sont empiles en plusieurs couches (corps grave, transitoire, bruit
 * filtre) et partagent une petite reverberation qui les sort du son « sec » des
 * synthetiseurs de test.
 */
public final class Audio {

    private static final int RATE = 44100;
    private static final int CHUNK = 512;              // 11,6 ms de son par bloc
    private static final int CHANNELS = 2;
    private static final double DT = 1.0 / RATE;

    /**
     * Les coefficients de filtre et le regime ne sont recalcules qu'une fois
     * tous les CONTROL echantillons : a 44,1 kHz cela reste 689 mises a jour
     * par seconde, bien au-dela de ce que l'oreille distingue, pour un cout de
     * calcul divise d'autant.
     */
    private static final int CONTROL = 64;

    /** Nombre maximal d'harmoniques du moteur (les suivantes sont inaudibles). */
    private static final int HARMONICS = 18;

    /** Coupure appliquee au bruit avant toute mise en forme. */
    private static final double COLOUR_HZ = 7000;

    /** Coupure de l'infra-grave du moteur. */
    private static final double RUMBLE_HZ = 40;

    /** Voix simultanees, et partiels par voix : les deux bornent le pupitre. */
    private static final int MAX_VOICES = 32;
    private static final int MAX_PARTIALS = 8;

    /**
     * Voix reservees a la musique. Mesure faite sur les deux morceaux, neuf
     * suffisent au plus charge (nappe de trois notes, basse, chant, contrechant,
     * chabada, frappe) ; douze laissent de quoi enchainer sans voler de note.
     */
    private static final int MUSIC_VOICES = 12;

    // ------------------------------------------------------------- outillage

    private static final int SIN_BITS = 12;
    private static final int SIN_SIZE = 1 << SIN_BITS;
    private static final double[] SIN = new double[SIN_SIZE + 1];

    static {
        for (int i = 0; i <= SIN_SIZE; i++) SIN[i] = Math.sin(2 * Math.PI * i / SIN_SIZE);
    }

    /** sin(2*pi*x), x exprime en tours, par table interpolee lineairement. */
    private static double osc(double x) {
        double p = x - Math.floor(x);
        double f = p * SIN_SIZE;
        int i = (int) f;
        return SIN[i] + (SIN[i + 1] - SIN[i]) * (f - i);
    }

    /**
     * Ecretage doux. Un clipping net donnerait le grain metallique typique des
     * saturations numeriques ; cette cubique arrondit le sommet et se contente
     * d'epaissir le son quand le melange sature.
     */
    private static double soft(double x) {
        double y = Math.clamp(x * (1 / 1.5), -1, 1);
        return 1.5 * (y - y * y * y / 3);
    }

    /** Coefficient de coupure du filtre pour une frequence en hertz. */
    private static double cutoffCoef(double hz) {
        return Math.tan(Math.PI * Math.clamp(hz, 20, RATE * 0.45) / RATE);
    }

    /**
     * Filtre a variable d'etat, forme trapezoidale : contrairement au filtre de
     * Chamberlin il reste stable quand la coupure balaie tout le spectre en
     * quelques millisecondes, ce que font la plupart des bruitages ici.
     */
    private static final class Svf {
        private double s1, s2;
        double lp, bp, hp;

        void reset() {
            s1 = s2 = 0;
        }

        void process(double in, double g, double k) {
            double a1 = 1 / (1 + g * (g + k));
            double a2 = g * a1;
            double a3 = g * a2;
            double v3 = in - s2;
            double v1 = a1 * s1 + a2 * v3;
            double v2 = s2 + a2 * s1 + a3 * v3;
            s1 = 2 * v1 - s1;
            s2 = 2 * v2 - s2;
            lp = v2;
            bp = v1;
            hp = in - k * v1 - v2;
        }
    }

    private static final class Comb {
        private final double[] buf;
        private final double fb, damp;
        private int idx;
        private double store;

        Comb(int n, double fb, double damp) {
            buf = new double[n];
            this.fb = fb;
            this.damp = damp;
        }

        double process(double in) {
            double out = buf[idx];
            store = out * (1 - damp) + store * damp;
            buf[idx] = in + store * fb;
            if (++idx == buf.length) idx = 0;
            return out;
        }
    }

    private static final class Allpass {
        private final double[] buf;
        private int idx;

        Allpass(int n) {
            buf = new double[n];
        }

        double process(double in) {
            double out = buf[idx];
            buf[idx] = in + out * 0.5;
            if (++idx == buf.length) idx = 0;
            return out - in;
        }
    }

    /**
     * Reverberation de Schroeder minimale (quatre peignes en parallele, deux
     * passe-tout en serie) appliquee au seul bus des bruitages. Une explosion ou
     * un carillon sans queue sonne comme un test de synthetiseur ; 200 ms de
     * decroissance suffisent a les poser dans un espace. Les longueurs de
     * retard des deux canaux sont decalees, ce qui ouvre l'image stereo.
     */
    private static final class Reverb {
        private static final int[] LEN = {1116, 1188, 1277, 1356};
        private static final int[] AP = {556, 441};
        private static final int SPREAD = 23;

        private final Comb[] combL = new Comb[LEN.length];
        private final Comb[] combR = new Comb[LEN.length];
        private final Allpass[] apL = new Allpass[AP.length];
        private final Allpass[] apR = new Allpass[AP.length];

        double left, right;

        Reverb() {
            for (int i = 0; i < LEN.length; i++) {
                combL[i] = new Comb(LEN[i], 0.76, 0.35);
                combR[i] = new Comb(LEN[i] + SPREAD, 0.76, 0.35);
            }
            for (int i = 0; i < AP.length; i++) {
                apL[i] = new Allpass(AP[i]);
                apR[i] = new Allpass(AP[i] + SPREAD);
            }
        }

        void process(double in) {
            double l = 0, r = 0;
            for (int i = 0; i < combL.length; i++) {
                l += combL[i].process(in);
                r += combR[i].process(in);
            }
            l *= 0.25;
            r *= 0.25;
            for (int i = 0; i < apL.length; i++) {
                l = apL[i].process(l);
                r = apR[i].process(r);
            }
            left = l;
            right = r;
        }
    }

    // ----------------------------------------------------------------- voix

    /**
     * Une voix additive : quelques partiels, un bruit optionnel, un filtre dont
     * la coupure glisse, le tout sous une enveloppe exponentielle. Les modes de
     * l'ancienne version (bip, bruit, balayage, carillon) sont tous des cas
     * particuliers de ce modele.
     */
    /**
     * Une voix additive : quelques partiels, un bruit optionnel, un filtre dont
     * la coupure glisse, le tout sous une enveloppe exponentielle. Les modes de
     * l'ancienne version (bip, bruit, balayage, carillon) sont tous des cas
     * particuliers de ce modele.
     *
     * <p>Les voix sont creees une fois pour toutes et recyclees : declencher un
     * bruitage allouait 490 octets par bloc sous une rafale, soit 41 Ko/s
     * demandes au ramasse-miettes depuis le fil audio lui-meme. Mesure faite,
     * c'est zero aujourd'hui.
     */
    private static final class Voice {
        boolean active;
        double delay;                       // retard avant declenchement, en s
        double t;
        double dur;
        double gain;
        double attack;
        double decay;                       // taux de decroissance, en 1/s
        double f0, f1, fCurve;              // glissando exponentiel f0 -> f1
        int np;                             // nombre de partiels utilises
        final double[] ratio = new double[MAX_PARTIALS];
        final double[] amp = new double[MAX_PARTIALS];
        final double[] pdec = new double[MAX_PARTIALS];
        final double[] phase = new double[MAX_PARTIALS];
        double noise;
        int mode;                           // -1 aucun, 0 passe-bas, 1 bande, 2 haut
        double cut0, cut1, q;
        final Svf svf = new Svf();
        double drive;
        double vibDepth, vibRate, vibPhase;
        double send;                        // dose envoyee a la reverberation
        double gl, gr;                      // panoramique a puissance constante
        double duck;                        // dose d'attenuation demandee a la musique

        // etat de rendu : les decroissances exponentielles sont tenues par
        // recurrence multiplicative (un produit par echantillon au lieu d'un
        // Math.exp), et la frequence comme la coupure ne sont recalculees
        // qu'au taux de controle
        int ctl;
        double env, envStep;
        final double[] pAmp = new double[MAX_PARTIALS];
        final double[] pStep = new double[MAX_PARTIALS];
        double curF, curG, curK;

        void reset(double d, double g) {
            active = false;
            delay = 0;
            t = 0;
            dur = d;
            gain = g;
            attack = 0.002;
            decay = 8;
            f0 = 0;
            f1 = -1;
            fCurve = 1;
            np = 1;
            ratio[0] = 1;
            amp[0] = 1;
            pdec[0] = 1;
            phase[0] = 0;
            noise = 0;
            mode = -1;
            cut0 = cut1 = 0;
            q = 0.8;
            drive = 0;
            vibDepth = vibRate = vibPhase = 0;
            send = 0;
            gl = gr = 0.707;
            duck = 0;
            ctl = 0;
            env = 1;
            envStep = 1;
            curF = 0;
            curG = 0;
            curK = 1;
            svf.reset();
        }
    }

    // partiels inharmoniques d'une cloche : c'est ce rapport-la, et pas des
    // harmoniques entieres, qui fait entendre du metal plutot qu'un orgue
    private static final double[] BELL_RATIO = {1, 2.02, 2.99, 4.21, 5.43};
    private static final double[] BELL_AMP = {1, 0.48, 0.30, 0.17, 0.09};
    private static final double[] BELL_DEC = {1, 1.5, 2.2, 3.1, 4.2};

    // plaque frappee : partiels serres et inharmoniques, decroissance rapide.
    // C'est ce qui distingue une tole d'une cloche, dont les partiels sont
    // largement espaces et tiennent longtemps.
    private static final double[] PLATE_RATIO = {1, 1.41, 1.93, 2.61, 3.37};
    private static final double[] PLATE_AMP = {1, 0.72, 0.55, 0.40, 0.26};
    private static final double[] PLATE_DEC = {1, 1.3, 1.7, 2.2, 2.8};

    private static final double COLOUR_G = cutoffCoef(COLOUR_HZ);
    private static final double RUMBLE_G = cutoffCoef(RUMBLE_HZ);

    // --------------------------------------------------------------- moteur

    private final ConcurrentLinkedQueue<Sfx> pending = new ConcurrentLinkedQueue<>();

    /**
     * Reserve au fil de mixage : les evenements arrivent par {@link #pending},
     * les voix elles-memes ne sont lues et liberees que la, donc aucune
     * synchronisation n'est necessaire (l'ancienne version prenait un verrou
     * par echantillon, soit 44 100 fois par seconde pour rien).
     */
    private final Voice[] voices = new Voice[MAX_VOICES];

    /** Recoit les voix refusees quand le pupitre est plein : evite un test par appel. */
    private final Voice overflow = new Voice();

    /**
     * Pupitre separe pour la musique. Separe et non partage : sans cela un
     * morceau a huit voix affamerait le pupitre des bruitages, et c'est
     * l'explosion ou le tour boucle qui sauterait — l'inverse de ce qu'on veut.
     */
    private final Voice[] musicVoices = new Voice[MUSIC_VOICES];

    private SourceDataLine line;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean available;
    private volatile boolean enabled = true;

    private volatile double engineLoad;
    private volatile boolean engineOn;
    private volatile double skid;

    private long rng = 0x9E3779B97F4A7C15L;

    private final Reverb reverb = new Reverb();

    /**
     * Attenuation demandee a la musique par le bruitage en cours. La demande
     * retombe en 300 ms et la reponse la suit en 12 ms : sans ce lissage, un
     * saut de gain de dix decibels en un echantillon s'entend comme un clic sur
     * le lit musical.
     */
    private double duckTarget, duckEnv;
    private static final double DUCK_RELEASE = Math.exp(-CONTROL * DT / 0.30);
    private static final double DUCK_SLEW = 1 - Math.exp(-CONTROL * DT / 0.012);

    // ------------------------------------------------------- etat du morceau
    private volatile boolean musicEnabled = true;
    private volatile Music tuneWanted = Music.NONE;

    /** Reserve au fil de melange. */
    private Music tunePlaying = Music.NONE;
    private double musicGate;
    private int musicStep;
    private double musicClock;

    /** Fondus d'entree et de sortie du morceau : un changement d'ecran ne claque pas. */
    private static final double MUSIC_IN = 1 - Math.exp(-CONTROL * DT / 0.35);
    private static final double MUSIC_OUT = 1 - Math.exp(-CONTROL * DT / 0.20);

    /** Hauteurs MIDI 24 a 108, en hertz : evite un Math.pow par note. */
    private static final double[] PITCH = new double[109];

    static {
        for (int n = 24; n < PITCH.length; n++) PITCH[n] = 440 * Math.pow(2, (n - 69) / 12.0);
    }

    // etat du moteur thermique, reserve au fil de mixage
    private int control;
    private double enginePhase, subPhase;
    private double smoothedLoad = 0.12, smoothedSkid, rpmJitter, engineGate;
    private double engineFreq = 48;
    private final double[] harmAmp = new double[HARMONICS + 1];
    private int harmCount = 1;
    private double bodyG, bodyK, exhaustG, exhaustK, intakeG, intakeK;
    private double windG, squealG, squealK, scrubG;
    private double engineAmp, intakeAmp, windAmp, squealAmp, scrubAmp, squealFreq;
    private double squealPhase, wobblePhase;

    private final Svf bodyLp = new Svf();
    private final Svf exhaustBp = new Svf();
    private final Svf rumbleHp = new Svf();
    private final Svf colourL = new Svf();
    private final Svf colourR = new Svf();
    private final Svf intakeL = new Svf();
    private final Svf intakeR = new Svf();
    private final Svf windL = new Svf();
    private final Svf windR = new Svf();
    private final Svf squealL = new Svf();
    private final Svf squealR = new Svf();
    private final Svf scrubL = new Svf();
    private final Svf scrubR = new Svf();

    private void allocateVoices() {
        for (int i = 0; i < MAX_VOICES; i++) {
            voices[i] = new Voice();
            voices[i].reset(1, 0);
        }
        for (int i = 0; i < MUSIC_VOICES; i++) {
            musicVoices[i] = new Voice();
            musicVoices[i].reset(1, 0);
        }
        overflow.reset(1, 0);
    }

    public Audio() {
        allocateVoices();
        try {
            AudioFormat fmt = new AudioFormat(RATE, 16, CHANNELS, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, fmt);
            if (!AudioSystem.isLineSupported(info)) {
                available = false;
                return;
            }
            line = (SourceDataLine) AudioSystem.getLine(info);
            // quatre blocs de reserve, soit environ 46 ms : assez pour encaisser
            // une saccade du fil de mixage sans que le decompte de depart parte
            // en retard sur l'image
            line.open(fmt, CHUNK * CHANNELS * 2 * 4);
            line.start();
            available = true;
            running = true;
            // un fil *de plateforme*, explicitement : la boucle de melange se
            // bloque sur line.write() et reclame un ordonnancement regulier,
            // ce qu'un fil virtuel ne donnerait pas
            thread = Thread.ofPlatform().name("tuxkart-audio").daemon().start(this::mixLoop);
        } catch (Throwable t) {
            available = false;
        }
    }

    /**
     * Instance sans peripherique ni fil de melange : la synthese n'avance que
     * sur appel de {@link #renderBlock(byte[])}. Reservee aux tests, qui
     * doivent pouvoir examiner le son produit sur une machine sans carte son
     * et sans dependre de l'ordonnancement d'un fil de fond.
     */
    static Audio offline() {
        return new Audio(true);
    }

    private Audio(boolean offline) {
        allocateVoices();
        available = true;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setEnabled(boolean on) {
        enabled = on;
        if (!on) {
            engineOn = false;
            pending.clear();
        }
    }

    public void play(Sfx sfx) {
        if (!available || !enabled) return;
        if (pending.size() < 24) pending.add(sfx);
    }

    /**
     * Regle le moteur : charge dans [0, 1].
     *
     * Le reglage de sourdine s'applique ici aussi. L'ecran de course rappelle
     * cette methode a chaque image : sans ce test, couper le son ne faisait
     * taire que les bruitages, le moteur repartait des l'image suivante.
     */
    public void engine(boolean on, double load, double skidAmount) {
        engineOn = on && enabled;
        engineLoad = load;
        skid = skidAmount;
    }

    /**
     * Choisit le morceau. Le changement passe par un fondu : couper une note en
     * cours au moment ou l'ecran change claque dans les enceintes.
     */
    public void music(Music tune) {
        tuneWanted = tune == null ? Music.NONE : tune;
    }

    /**
     * Reglage « Musique », independant du reglage « Son ». Les deux existent
     * parce qu'ils ne repondent pas a la meme demande : couper le son coupe
     * aussi le moteur, qui porte l'information de vitesse, alors que couper la
     * seule musique laisse le jeu jouable. Le reglage « Son » reste maitre.
     */
    public void setMusicEnabled(boolean on) {
        musicEnabled = on;
    }

    public void shutdown() {
        running = false;
        if (thread != null) thread.interrupt();
        try {
            if (line != null) {
                line.stop();
                line.close();
            }
        } catch (Throwable _) {
        }
    }

    // ------------------------------------------------------------------ mix

    /** Bruit blanc par xorshift : quelques nanosecondes, contre un Random synchronise. */
    private double noise() {
        long x = rng;
        x ^= x << 13;
        x ^= x >>> 7;
        x ^= x << 17;
        rng = x;
        return (x >> 40) * (1.0 / (1L << 23));
    }

    private void mixLoop() {
        byte[] buf = new byte[CHUNK * CHANNELS * 2];
        while (running) {
            try {
                renderBlock(buf);
                line.write(buf, 0, buf.length);
            } catch (Throwable t) {
                running = false;
            }
        }
    }

    /**
     * Remplit un bloc de CHUNK trames entrelacees. Separe de {@link #mixLoop()}
     * pour pouvoir rendre la synthese hors ligne, sans peripherique audio.
     */
    void renderBlock(byte[] buf) {
        Sfx s;
        while ((s = pending.poll()) != null) spawn(s);

        for (int i = 0; i < CHUNK; i++) {
            if (control-- <= 0) {
                control = CONTROL - 1;
                updateEngine();
                updateMusic();
            }

            double left = 0, right = 0;
            // bruit borne a 7 kHz avant tout usage : le bruit blanc pur porte
            // la moitie de son energie au-dessus de 11 kHz, ce qui s'entend
            // comme un souffle de bande magnetique et jamais comme un moteur
            colourL.process(noise(), COLOUR_G, 1.1);
            colourR.process(noise(), COLOUR_G, 1.1);
            double nl = colourL.lp, nr = colourR.lp;

            if (engineGate > 0.0005) {
                enginePhase += engineFreq * DT;
                subPhase += engineFreq * 0.5 * DT;
                if (enginePhase >= 1) enginePhase -= 1;
                if (subPhase >= 1) subPhase -= 1;

                // train d'impulsions : la somme des harmoniques en phase
                // reconstitue le coup de combustion, borne sous Nyquist
                double body = 0;
                for (int n = 1; n <= harmCount; n++) body += osc(enginePhase * n) * harmAmp[n];
                // demi-ordres : un quatre-temps n'allume qu'un tour sur
                // deux, d'ou le battement grave caracteristique
                body += osc(subPhase) * 0.20 + osc(subPhase * 3) * 0.12;

                bodyLp.process(body, bodyG, bodyK);
                exhaustBp.process(body, exhaustG, exhaustK);
                // coupure de l'infra-grave : sous 40 Hz aucune enceinte de
                // portable ne restitue quoi que ce soit, alors que le
                // demi-ordre du ralenti y mangeait la reserve de niveau. Le
                // battement grave n'apparait donc qu'en montant en regime,
                // ce qui est exactement le comportement d'un echappement.
                rumbleHp.process(bodyLp.lp * 0.85 + exhaustBp.bp * 0.45, RUMBLE_G, 1.2);
                double tonal = rumbleHp.hp * engineAmp;

                intakeL.process(nl, intakeG, intakeK);
                intakeR.process(nr, intakeG, intakeK);
                windL.process(nl, windG, 1.4);
                windR.process(nr, windG, 1.4);

                left += tonal + intakeL.bp * intakeAmp + windL.hp * windAmp;
                right += tonal + intakeR.bp * intakeAmp + windR.hp * windAmp;
            }

            if (smoothedSkid > 0.01) {
                // le crissement n'est pas du bruit blanc : c'est une
                // bande etroite qui module, posee sur le frottement
                // sourd de la gomme sur le bitume
                squealPhase += squealFreq * DT;
                wobblePhase += 6.3 * DT;
                if (squealPhase >= 1) squealPhase -= 1;
                if (wobblePhase >= 1) wobblePhase -= 1;
                double wobble = 0.72 + 0.28 * osc(wobblePhase);
                double tone = osc(squealPhase) * squealAmp * wobble;

                squealL.process(nl, squealG, squealK);
                squealR.process(nr, squealG, squealK);
                scrubL.process(nl, scrubG, 1.2);
                scrubR.process(nr, scrubG, 1.2);

                left += tone + squealL.bp * squealAmp * 1.6 + scrubL.lp * scrubAmp;
                right += tone + squealR.bp * squealAmp * 1.6 + scrubR.lp * scrubAmp;
            }

            double send = 0;
            for (int v = 0; v < MAX_VOICES; v++) {
                Voice voice = voices[v];
                if (!voice.active) continue;
                double out = renderVoice(voice);
                left += out * voice.gl;
                right += out * voice.gr;
                send += out * voice.send;
                if (voice.t >= voice.dur) voice.active = false;
            }
            // bus de musique, sorti a part : c'est le seul moyen de lui
            // appliquer son propre fondu et l'attenuation demandee par les
            // bruitages sans toucher au reste du melange
            if (musicGate > 0.0005) {
                double ml = 0, mr = 0;
                for (int v = 0; v < MUSIC_VOICES; v++) {
                    Voice voice = musicVoices[v];
                    if (!voice.active) continue;
                    double out = renderVoice(voice);
                    ml += out * voice.gl;
                    mr += out * voice.gr;
                    send += out * voice.send * musicGain;
                    if (voice.t >= voice.dur) voice.active = false;
                }
                left += ml * musicGain;
                right += mr * musicGain;
            }

            reverb.process(send);
            left += reverb.left;
            right += reverb.right;

            int l = (int) (soft(left * 0.92) * 30000);
            int r = (int) (soft(right * 0.92) * 30000);
            int k = i * 4;
            buf[k] = (byte) (l & 0xff);
            buf[k + 1] = (byte) ((l >> 8) & 0xff);
            buf[k + 2] = (byte) (r & 0xff);
            buf[k + 3] = (byte) ((r >> 8) & 0xff);
        }
    }

    /**
     * Recalcule regime, timbre et coefficients de filtre. Tout ce qui suit la
     * charge est lisse ici : un saut de regime d'une image a l'autre s'entend
     * comme un craquement.
     */
    private void updateEngine() {
        double step = CONTROL * DT;
        // fondu d'entree et de sortie du moteur : couper net un signal encore
        // a pleine amplitude, ce que faisait le seuil precedent, claque dans
        // les enceintes a chaque pause et a chaque fin de course
        engineGate += ((engineOn ? 1 : 0) - engineGate) * (1 - Math.exp(-step / 0.10));
        double target = engineOn ? Math.clamp(engineLoad, 0, 1) : 0;
        smoothedLoad += (target - smoothedLoad) * (1 - Math.exp(-step / 0.13));
        smoothedSkid += (Math.clamp(skid, 0, 1) - smoothedSkid) * (1 - Math.exp(-step / 0.05));

        double l = smoothedLoad;

        // le ralenti d'un thermique n'est jamais parfaitement regulier : ce
        // flottement de quelques pour mille suffit a effacer l'impression de
        // sirene d'un oscillateur pur
        rpmJitter += (noise() - rpmJitter) * 0.06;
        engineFreq = (60 + l * 240) * (1 + rpmJitter * (0.016 - l * 0.011));

        // les harmoniques s'etalent d'autant plus haut que la charge monte :
        // c'est ce qui fait entendre l'effort, pas seulement la hauteur du son
        double roll = 0.64 - l * 0.38;
        harmCount = (int) Math.clamp((RATE * 0.45) / engineFreq, 1, HARMONICS);
        double sum = 0;
        for (int n = 1; n <= harmCount; n++) {
            harmAmp[n] = Math.exp(-roll * (n - 1));
            sum += harmAmp[n];
        }
        for (int n = 1; n <= harmCount; n++) harmAmp[n] /= sum;

        bodyG = cutoffCoef(340 + l * l * 3600);
        bodyK = 1 / 1.3;
        // La resonance du silencieux tombait a 88-134 Hz alors que la
        // frequence d'allumage passe par la meme valeur vers charge 0,14 :
        // les deux coincidaient, et le ralenti sortait a 0,107 de valeur
        // efficace contre 0,109 a plein pot pour un niveau percu six fois
        // plus faible. Quinze decibels de reserve depenses en pure perte.
        // Placee au-dessus de la frequence d'allumage de ralenti, elle ne
        // croise plus le fondamental qu'a mi-charge, ou elle sert.
        exhaustG = cutoffCoef(155 + l * 120);
        exhaustK = 1 / 2.4;
        intakeG = cutoffCoef(720 + l * 1500);        // sifflement d'admission
        intakeK = 1 / 0.9;
        windG = cutoffCoef(1250);                    // souffle de l'air a vitesse

        // le gain global reprend le decibel gagne par la resonance
        // deplacee : sans quoi le moteur remontait d'autant, et rognait la
        // marge que les bruitages viennent tout juste de gagner sur lui
        engineAmp = (0.107 + l * 0.169) * engineGate;
        intakeAmp = (0.030 + l * 0.085) * engineGate;
        windAmp = (0.016 + l * l * 0.055) * engineGate;

        double sk = smoothedSkid;
        squealFreq = 1080 + sk * 560 + l * 320;
        squealG = cutoffCoef(squealFreq * 1.25);
        squealK = 1 / 7.0;                           // bande tres etroite
        scrubG = cutoffCoef(380);
        squealAmp = sk * sk * 0.055;
        scrubAmp = sk * 0.16;
    }

    /**
     * Frequence et coupure d'une voix, au taux de controle. Les puissances et
     * la tangente qui les calculent coutent bien plus qu'un echantillon de
     * synthese : les evaluer 689 fois par seconde plutot que 44 100 divise le
     * cout du melange par trois, sans difference audible sur des balayages qui
     * durent au minimum cinquante millisecondes.
     */
    private void voiceControl(Voice v) {
        double p = Math.min(1, v.t / v.dur);
        if (v.f0 > 0) {
            double f = v.f1 > 0 && v.f1 != v.f0
                    ? v.f0 * Math.pow(v.f1 / v.f0, Math.pow(p, v.fCurve))
                    : v.f0;
            if (v.vibDepth > 0) {
                v.vibPhase += v.vibRate * CONTROL * DT;
                f *= 1 + v.vibDepth * osc(v.vibPhase);
            }
            v.curF = f;
        }
        if (v.mode >= 0) {
            v.curG = cutoffCoef(v.cut0 * Math.pow(v.cut1 / v.cut0, p));
            v.curK = 1 / v.q;
        }
    }

    private double renderVoice(Voice v) {
        if (v.delay > 0) {
            v.delay -= DT;
            return 0;
        }
        if (v.ctl-- <= 0) {
            v.ctl = CONTROL - 1;
            voiceControl(v);
        }
        v.t += DT;

        v.env *= v.envStep;
        double env = v.env;
        if (v.t < v.attack) env *= v.t / v.attack;
        // fondu terminal : retirer une voix encore audible produit un clic
        double p = v.t / v.dur;
        if (p > 0.85) env *= Math.max(0, (1 - p) / 0.15);

        double s = 0;
        for (int i = 0; i < v.np; i++) {
            // les partiels aigus s'eteignent avant le fondamental, sans quoi
            // une cloche vire au bourdon d'orgue
            v.pAmp[i] *= v.pStep[i];
            double fi = v.curF * v.ratio[i];
            if (fi <= 0 || fi >= RATE * 0.47) continue;   // au-dela : repliement garanti
            v.phase[i] += fi * DT;
            s += osc(v.phase[i]) * v.pAmp[i];
        }
        if (v.noise > 0) s += noise() * v.noise;
        if (v.drive > 0) s = soft(s * (1 + v.drive));

        if (v.mode >= 0) {
            v.svf.process(s, v.curG, v.curK);
            s = switch (v.mode) {
                case 0 -> v.svf.lp;
                case 1 -> v.svf.bp;
                default -> v.svf.hp;
            };
        }
        return s * env * v.gain;
    }

    // ------------------------------------------------------------- musique

    /** Gain effectif du bus musical : fondu d'ecran fois attenuation. */
    private double musicGain;

    /**
     * Avance le morceau d'un pas de controle. Le sequenceur ne tourne qu'ici,
     * soit 689 fois par seconde : une double-croche a 144 a la noire dure
     * 104 ms, la grille est donc placee a un millier de fois mieux que ce que
     * l'oreille distingue, pour le prix d'un decompte entier.
     */
    private void updateMusic() {
        Music want = enabled && musicEnabled ? tuneWanted : Music.NONE;
        if (want != tunePlaying) {
            // on descend d'abord, on change ensuite : basculer de morceau au
            // milieu d'une note tranche le signal a pleine amplitude
            musicGate -= musicGate * MUSIC_OUT;
            if (musicGate < 0.02) {
                musicGate = 0;
                tunePlaying = want;
                musicStep = 0;
                musicClock = 0;
                for (int i = 0; i < MUSIC_VOICES; i++) musicVoices[i].active = false;
            }
        } else if (tunePlaying != Music.NONE) {
            musicGate += (1 - musicGate) * MUSIC_IN;
        }

        duckTarget *= DUCK_RELEASE;
        duckEnv += (duckTarget - duckEnv) * DUCK_SLEW;
        // onze decibels au plus : de quoi rendre au tour boucle et a
        // l'explosion la marge qu'ils ont sur le moteur, sans que la musique
        // paraisse s'eteindre a chaque hareng
        musicGain = musicGate * tunePlaying.gain() * (1 - 0.72 * Math.min(1, duckEnv));

        if (tunePlaying == Music.NONE) return;
        musicClock -= CONTROL;
        if (musicClock <= 0) {
            musicClock += RATE * 60.0 / (tunePlaying.bpm() * 4);
            fireStep(tunePlaying, musicStep);
            musicStep++;
            if (musicStep >= Music.STEPS) musicStep = 0;
        }
    }

    /** Prend une voix libre au pupitre musical. */
    private B musicVoice(double dur, double gain) {
        for (int i = 0; i < MUSIC_VOICES; i++) {
            if (!musicVoices[i].active) return builder.on(musicVoices[i], dur, gain);
        }
        return builder.on(overflow, dur, gain);
    }

    /**
     * Declenche les notes qui tombent sur ce pas.
     *
     * <p>Tous les timbres sont pinces et courts. Ce n'est pas un gout : une
     * voix tenue occupe sa bande en permanence, et la seule chose qui joue en
     * permanence dans ce jeu doit rester le moteur.
     */
    private void fireStep(Music tune, int step) {
        int[] p = tune.pattern(Music.LEAD);
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] != step) continue;
            // chant : quatre harmoniques et un passe-bas qui se referme, soit
            // un timbre franc de boite a musique plutot qu'une sinusoide
            musicVoice(0.34, 0.075).freq(PITCH[p[i + 1]]).harmonics(4, 1.15).env(0.004, 7.5)
                    .lowpass(3600, 1100, 1.1).pan(-0.22).send(0.10).add();
        }
        p = tune.pattern(Music.ARP);
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] != step) continue;
            musicVoice(0.20, 0.040).freq(PITCH[p[i + 1]]).harmonics(2, 1.7).env(0.003, 15)
                    .pan(0.26).send(0.12).add();
        }
        p = tune.pattern(Music.BASS);
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] != step) continue;
            musicVoice(0.26, 0.095).freq(PITCH[p[i + 1]]).harmonics(5, 0.95).env(0.003, 9)
                    .lowpass(1100, 320, 1.3).drive(0.7).add();
        }
        p = tune.pattern(Music.PAD);
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] != step) continue;
            musicVoice(2.10, 0.030).freq(PITCH[p[i + 1]]).harmonics(3, 1.6).env(0.18, 1.4)
                    .lowpass(2200, 900, 0.9).send(0.22).add();
        }
        p = tune.pattern(Music.PERC);
        for (int i = 0; i < p.length; i += 2) {
            if (p[i] != step) continue;
            switch (p[i + 1]) {
                // chabada au-dessus de 7 kHz : la seule plage que ni le moteur
                // ni aucun des treize bruitages n'occupe vraiment
                case Music.HAT -> musicVoice(0.05, 0.035).noise(1).env(0.0004, 90)
                        .highpass(7500, 0.8).pan(0.35).add();
                case Music.HAT_FORT -> musicVoice(0.07, 0.060).noise(1).env(0.0004, 70)
                        .highpass(7000, 0.8).pan(0.35).add();
                default -> musicVoice(0.10, 0.050).noise(1).env(0.001, 32)
                        .bandpass(4200, 3000, 1.8).pan(-0.30).send(0.15).add();
            }
        }
    }

    // ------------------------------------------------------------ recettes

    /**
     * Assemblage d'une voix ; chaque bruitage en empile deux ou trois. Un seul
     * exemplaire, reutilise : {@code spawn} ne tourne que sur le fil de melange,
     * et un assembleur par voix rallouerait ce que le pupitre fixe economise.
     */
    private final class B {
        private Voice v = overflow;

        B on(Voice voice, double dur, double gain) {
            v = voice;
            v.reset(dur, gain);
            return this;
        }

        B freq(double f) {
            v.f0 = f;
            v.f1 = f;
            return this;
        }

        B sweep(double from, double to, double curve) {
            v.f0 = from;
            v.f1 = to;
            v.fCurve = curve;
            return this;
        }

        /** Empilement harmonique : rolloff eleve = son doux, faible = son mordant. */
        B harmonics(int n, double rolloff) {
            v.np = Math.min(n, MAX_PARTIALS);
            for (int i = 0; i < v.np; i++) {
                v.ratio[i] = i + 1;
                v.amp[i] = Math.pow(i + 1, -rolloff);
                v.pdec[i] = 1 + i * 0.22;
            }
            return this;
        }

        B bell() {
            return partials(BELL_RATIO, BELL_AMP, BELL_DEC);
        }

        /** Partiels inharmoniques d'une plaque frappee : tole, caisse, carrosserie. */
        B plate() {
            return partials(PLATE_RATIO, PLATE_AMP, PLATE_DEC);
        }

        B partials(double[] ratio, double[] amp, double[] dec) {
            v.np = Math.min(ratio.length, MAX_PARTIALS);
            System.arraycopy(ratio, 0, v.ratio, 0, v.np);
            System.arraycopy(amp, 0, v.amp, 0, v.np);
            System.arraycopy(dec, 0, v.pdec, 0, v.np);
            return this;
        }

        B noise(double amount) {
            v.noise = amount;
            return this;
        }

        B env(double attack, double decay) {
            v.attack = attack;
            v.decay = decay;
            return this;
        }

        B lowpass(double from, double to, double q) {
            return filter(0, from, to, q);
        }

        B bandpass(double from, double to, double q) {
            return filter(1, from, to, q);
        }

        B highpass(double hz, double q) {
            return filter(2, hz, hz, q);
        }

        private B filter(int mode, double from, double to, double q) {
            v.mode = mode;
            v.cut0 = from;
            v.cut1 = to;
            v.q = q;
            return this;
        }

        B drive(double d) {
            v.drive = d;
            return this;
        }

        B vib(double depth, double rate) {
            v.vibDepth = depth;
            v.vibRate = rate;
            return this;
        }

        B send(double amount) {
            v.send = amount;
            return this;
        }

        /** Retarde le declenchement ; la duree reste celle du son lui-meme. */
        B after(double seconds) {
            v.delay = seconds;
            return this;
        }

        /** Panoramique a puissance constante, -1 a gauche, +1 a droite. */
        B pan(double p) {
            double a = (Math.clamp(p, -1, 1) + 1) * Math.PI / 4;
            v.gl = Math.cos(a);
            v.gr = Math.sin(a);
            return this;
        }

        /**
         * Dose d'attenuation demandee a la musique pendant la voix. Sans elle,
         * la musique occupe en continu les memes bandes que les bruitages qui
         * portent une information (tour boucle, depart, impact) et rogne les
         * quelques decibels qui les rendent lisibles par-dessus le moteur.
         */
        B duck(double amount) {
            v.duck = amount;
            return this;
        }

        void add() {
            if (v == overflow) return;
            v.envStep = Math.exp(-v.decay * DT);
            for (int i = 0; i < v.np; i++) {
                v.pAmp[i] = v.amp[i];
                v.phase[i] = 0;
                v.pStep[i] = v.pdec[i] == 1 ? 1 : Math.exp(-v.decay * (v.pdec[i] - 1) * DT);
            }
            v.curF = v.f0;
            if (v.duck > duckTarget) duckTarget = v.duck;
            v.active = true;
        }
    }

    private final B builder = new B();

    /** Prend une voix libre au pupitre, ou la corbeille s'il est plein. */
    private B voice(double dur, double gain) {
        for (int i = 0; i < MAX_VOICES; i++) {
            if (!voices[i].active) return builder.on(voices[i], dur, gain);
        }
        return builder.on(overflow, dur, gain);
    }

    /** Transitoire d'attaque : le claquement bref qui rend un son « frappe ». */
    private B tick(double gain, double bright) {
        return voice(0.05, gain).noise(1).env(0.0004, 90).highpass(bright, 0.8);
    }

    /**
     * Recettes des treize bruitages.
     *
     * <p>Trois mesures gouvernent ces reglages, toutes prises sur le signal
     * rendu hors ligne et comparees au moteur a mi-charge :
     *
     * <ul>
     * <li><b>le moteur occupe le grave.</b> A charge 0,7 il place 58 % de son
     * energie sous 200 Hz et 36 % de 200 a 700 Hz ; au-dessus de 700 Hz il est
     * 14 a 25 dB sous son propre maximum. Un bruitage dont toute l'energie est
     * dans le grave ne s'entend donc pas en course : le choc de carrosserie
     * etait 5 dB sous le moteur dans sa meilleure bande, la retombee de saut
     * 10 dB, le turbo 6 dB. Chacun porte desormais une signature au-dessus de
     * 700 Hz, et chacun la sienne ;</li>
     * <li><b>le niveau utile n'est pas le niveau brut.</b> Le turbo etait le
     * deuxieme bruitage le plus fort en valeur efficace et l'avant-dernier une
     * fois pondere A : il depensait sa reserve dans un infra-grave inaudible.
     * Les gains sont regles sur la valeur efficace ponderee A, qui suit ce que
     * l'oreille entend, pas sur la crete ;</li>
     * <li><b>deux bruitages qui s'enchainent doivent differer.</b> Le cosinus
     * des profils par tiers d'octave chiffre la confusion : explosion contre
     * retombee valait 0,88 avant, turbo contre explosion 0,90.</li>
     * </ul>
     */
    private void spawn(Sfx sfx) {
        switch (sfx) {
            // hareng ramasse : le plus frequent des bruitages de course, donc
            // le plus bref. Registre volontairement haut, ou le moteur est
            // quinze decibels sous son maximum : il passe plein gaz.
            case HERRING -> {
                voice(0.34, 0.19).freq(1318.5).bell().env(0.001, 12).send(0.22).duck(0.55).add();
                voice(0.28, 0.12).freq(1975.5).bell().env(0.001, 15).after(0.038).pan(0.20).send(0.22).add();
                tick(0.05, 4200).add();
            }
            // caisse d'objet : une caisse qu'on defonce, pas un carillon. Le
            // bruit large et le corps de bois la separent du hareng, avec
            // lequel elle s'enchaine souvent.
            case BOX -> {
                voice(0.20, 0.50).noise(1).env(0.002, 22).bandpass(1900, 1050, 1.6).send(0.15).duck(0.55).add();
                voice(0.24, 0.13).freq(260).plate().env(0.002, 17).lowpass(2000, 800, 1.2).drive(0.8).add();
                voice(0.16, 0.14).freq(932).harmonics(3, 1.5).env(0.002, 22).after(0.045).pan(0.15).send(0.2).add();
            }
            // turbo : souffle montant. L'ancien grondement mettait 73 % de son
            // energie sous 200 Hz, ou le moteur est six decibels plus fort ;
            // le turbo ne s'entendait tout simplement pas.
            case BOOST -> {
                voice(0.55, 0.50).noise(1).env(0.03, 5.2).bandpass(900, 3000, 1.6).send(0.2).duck(0.75).add();
                voice(0.50, 0.16).sweep(230, 800, 0.8).harmonics(6, 1.0).env(0.025, 5.0)
                        .lowpass(700, 3000, 1.6).drive(1.2).add();
                // le coup de pied dans le dos reste, mais bref : c'est une
                // ponctuation, plus le corps du son
                voice(0.18, 0.16).sweep(130, 72, 1.2).harmonics(2, 1.6).env(0.002, 18).add();
            }
            // tir : expiration breve qui degringole, avec un corps pitche
            case FIRE -> {
                voice(0.30, 0.28).noise(1).env(0.002, 11).bandpass(3400, 800, 2.0).send(0.15).duck(0.65).add();
                voice(0.24, 0.14).sweep(1200, 260, 1.5).harmonics(4, 1.2).env(0.002, 14)
                        .lowpass(3800, 900, 1.2).drive(1.2).add();
            }
            // explosion : le plus gros evenement de la course, et le seul a qui
            // le grave est laisse. Le balayage s'arrete a 58 Hz et non 36 :
            // sous 50 Hz une enceinte de portable ne rend rien, et la
            // composante continue mesuree y valait dix fois celle des autres.
            case HIT -> {
                voice(0.62, 0.36).sweep(190, 58, 1.5).harmonics(3, 1.6).env(0.002, 6.0)
                        .drive(1.6).send(0.25).duck(1.0).add();
                voice(0.55, 0.30).noise(1).env(0.003, 7.5).lowpass(6000, 260, 1.3).send(0.4).add();
                // l'eclat : c'est lui qui fait passer l'explosion au-dessus du
                // moteur, le grave seul ne suffisait pas
                voice(0.22, 0.16).noise(1).env(0.001, 22).bandpass(3800, 2200, 2.2).send(0.2).add();
                tick(0.14, 2600).add();
            }
            // choc de carrosserie : masse sourde et clac de tole, sans queue
            case BUMP -> {
                voice(0.14, 0.14).noise(1).env(0.002, 30).lowpass(900, 180, 1.6).add();
                voice(0.13, 0.11).sweep(150, 88, 1.2).harmonics(3, 1.5).env(0.002, 32).drive(1.3).add();
                // le clac : sans lui le choc restait cinq decibels sous le
                // moteur dans toutes les bandes, donc muet en course
                voice(0.16, 0.14).freq(1150).plate().env(0.001, 26).bandpass(1900, 1200, 1.5).duck(0.6).add();
            }
            // retombee de saut : ecrasement de suspension et pneus qui mordent.
            // La morsure est une bande de bruit vers 2,5 kHz ; le clac du choc
            // de carrosserie, lui, est une plaque a 1,2 kHz. Meme famille de
            // percussion, mais ni le meme registre ni le meme grain, sans quoi
            // taper un muret et retomber d'un saut sonnaient pareil.
            case JUMP -> {
                voice(0.15, 0.24).noise(1).env(0.002, 26).lowpass(700, 200, 1.5).add();
                voice(0.14, 0.13).sweep(210, 110, 1.3).harmonics(2, 1.4).env(0.002, 28).add();
                voice(0.15, 0.18).noise(1).env(0.002, 22).bandpass(3200, 2300, 3.0).duck(0.5).add();
            }
            // tour boucle : deux notes montantes qui resonnent
            case LAP -> {
                voice(0.60, 0.17).freq(784).bell().env(0.002, 6.5).send(0.35).duck(1.0).add();
                voice(0.62, 0.15).freq(1174).bell().env(0.002, 6).after(0.11).pan(0.12).send(0.35).add();
            }
            // decompte : bip de sonorisation, avec juste assez d'harmoniques
            // pour avoir un corps plutot qu'une sinusoide de test
            case COUNTDOWN -> {
                voice(0.26, 0.25).freq(700).harmonics(3, 1.8).env(0.006, 11).lowpass(3200, 2200, 1)
                        .send(0.12).duck(1.0).add();
            }
            // depart : meme timbre a l'octave, tenu et plus ouvert
            case GO -> {
                voice(0.62, 0.24).freq(1046.5).harmonics(4, 1.6).env(0.005, 5).lowpass(4200, 3000, 1)
                        .send(0.25).duck(1.0).add();
                voice(0.62, 0.08).freq(1568).harmonics(2, 1.8).env(0.006, 5.5).send(0.25).add();
            }
            // arrivee : arpege majeur, chaque note posee sur la precedente
            case FINISH -> {
                voice(1.10, 0.15).freq(523.25).bell().env(0.002, 3.4).send(0.4).pan(-0.15).duck(1.0).add();
                voice(1.00, 0.14).freq(659.25).bell().env(0.002, 3.4).after(0.13).send(0.4).add();
                voice(0.95, 0.14).freq(783.99).bell().env(0.002, 3.2).after(0.26).send(0.4).pan(0.15).add();
                voice(1.05, 0.12).freq(1046.5).bell().env(0.002, 2.8).after(0.39).send(0.45).add();
            }
            // deplacement dans un menu : un clic, pas une note
            case MENU_MOVE -> {
                voice(0.06, 0.09).noise(1).env(0.0005, 65).bandpass(2400, 2000, 4).add();
                voice(0.06, 0.05).freq(1250).harmonics(2, 2).env(0.001, 50).add();
            }
            // validation : deux notes breves qui montent
            case MENU_SELECT -> {
                voice(0.20, 0.13).freq(660).harmonics(3, 1.7).env(0.003, 13).send(0.18).add();
                voice(0.22, 0.12).freq(990).harmonics(3, 1.7).env(0.003, 11).after(0.06).send(0.18).add();
            }
        }
    }
}
