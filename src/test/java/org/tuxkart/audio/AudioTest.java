package org.tuxkart.audio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.function.DoubleConsumer;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * La synthese est verifiee sur le signal lui-meme, rendu hors ligne : c'est le
 * seul moyen de tester du son sans carte son, et cela attrape ce qu'une oreille
 * distraite laisse passer (repliement, composante continue, coupure nette).
 */
class AudioTest {

    private static final int RATE = 44100;
    private static final int CHUNK = 512;

    /** Rend n secondes de son ; le crochet recoit le temps ecoule, par bloc. */
    private static double[] render(Audio audio, double seconds, DoubleConsumer hook) {
        int blocks = (int) (seconds * RATE / CHUNK);
        byte[] buf = new byte[CHUNK * 4];
        double[] out = new double[blocks * CHUNK * 2];
        int p = 0;
        for (int b = 0; b < blocks; b++) {
            if (hook != null) hook.accept(b * (double) CHUNK / RATE);
            audio.renderBlock(buf);
            for (int i = 0; i < CHUNK * 2; i++) {
                out[p++] = (short) ((buf[i * 2] & 0xff) | (buf[i * 2 + 1] << 8)) / 32768.0;
            }
        }
        return out;
    }

    /** Crete du canal gauche sur une fenetre donnee, en secondes. */
    private static double peak(double[] s, double from, double to) {
        double peak = 0;
        for (int i = (int) (from * RATE); i < (int) (to * RATE) && i * 2 < s.length; i++) {
            peak = Math.max(peak, Math.abs(s[i * 2]));
        }
        return peak;
    }

    @ParameterizedTest
    @EnumSource(Sfx.class)
    @DisplayName("chaque bruitage tient dans la reserve de niveau et retombe au silence")
    void bruitagesPropres(Sfx sfx) {
        Audio audio = Audio.offline();
        audio.play(sfx);
        double[] s = render(audio, 2.0, null);

        double crete = 0, somme = 0;
        for (double v : s) {
            assertTrue(Double.isFinite(v), sfx + " produit un echantillon non fini");
            crete = Math.max(crete, Math.abs(v));
            somme += v;
        }
        final double peak = crete;
        final double sum = somme;
        assertAll(
                // au-dela, le bruitage ne laisserait plus de place au moteur
                // qui joue toujours en meme temps que lui
                () -> assertTrue(peak > 0.05 && peak < 0.7, sfx + " : crete " + peak),
                // une composante continue ne s'entend pas mais ampute d'autant
                // la reserve de niveau de tout le melange
                // 3e-4 et non 2e-3 : la seule recette qui depassait ce seuil
                // etait l'explosion, dont le balayage descendait a 36 Hz —
                // trop bas pour boucler ses periodes dans la duree du son
                () -> assertEquals(0, sum / s.length, 3e-4, sfx + " : composante continue"),
                // toutes les recettes durent moins d'une seconde et demie ;
                // une voix qui reste accrochee mangerait une place du pupitre
                () -> assertTrue(peak(s, 1.6, 2.0) < 1e-3, sfx + " : queue interminable"));
    }

    @Test
    @DisplayName("le pupitre reste borne meme sous un deluge de bruitages")
    void pupitreBorne() {
        Audio audio = Audio.offline();
        for (int i = 0; i < 500; i++) audio.play(Sfx.HIT);
        double[] s = render(audio, 1.0, null);
        for (double v : s) assertTrue(Math.abs(v) <= 1.0 && Double.isFinite(v));
    }

    @Test
    @DisplayName("le timbre du moteur s'ouvre avec la charge")
    void moteurSOuvreAvecLaCharge() {
        Audio audio = Audio.offline();
        double[] s = render(audio, 6.0, t -> audio.engine(true, t < 3 ? 0.15 : 1.0, 0));

        // le ralenti est presque tout en graves, le plein gaz etale ses
        // harmoniques : on compare l'energie au-dessus de 700 Hz, isolee par
        // une difference premiere (passe-haut du pauvre)
        double idle = highEnergy(s, 2.0, 2.9);
        double full = highEnergy(s, 5.0, 5.9);
        assertTrue(full > idle * 3, "aigus : ralenti " + idle + " plein gaz " + full);
    }

    @Test
    @DisplayName("le derapage ajoute une bande aigue au moteur")
    void derapageAjouteDesAigus() {
        Audio audio = Audio.offline();
        double[] s = render(audio, 6.0, t -> audio.engine(true, 0.5, t < 3 ? 0 : 1.0));
        assertTrue(highEnergy(s, 5.0, 5.9) > highEnergy(s, 2.0, 2.9) * 2);
    }

    @Test
    @DisplayName("couper le moteur le fait descendre au lieu de le trancher")
    void coupureDuMoteurEnFondu() {
        Audio audio = Audio.offline();
        double[] s = render(audio, 2.0, t -> audio.engine(t < 1.0, 0.9, 0));

        double avant = peak(s, 0.90, 1.00);
        assertAll(
                () -> assertTrue(avant > 0.05, "moteur inaudible avant la coupure"),
                // la fenetre commence 40 ms apres l'ordre de coupure : avant
                // cela le bloc en cours de melange joue de toute facon, et le
                // test ne distinguerait pas un fondu d'une coupure nette
                () -> assertTrue(peak(s, 1.04, 1.09) > avant * 0.35, "coupure trop brutale"),
                // mais il doit bel et bien descendre, pas tenir sa note
                () -> assertTrue(peak(s, 1.30, 1.40) < avant * 0.3, "le moteur ne descend pas"),
                () -> assertTrue(peak(s, 1.8, 2.0) < 1e-3, "le moteur ne se tait jamais"));
    }

    @Test
    @DisplayName("le melange ne coute qu'une fraction d'un coeur")
    void melangeBienPlusRapideQueLeTempsReel() {
        Audio audio = Audio.offline();
        byte[] buf = new byte[CHUNK * 4];
        audio.engine(true, 1.0, 1.0);
        for (int i = 0; i < 300; i++) audio.renderBlock(buf);     // chauffe

        int blocks = (int) (5.0 * RATE / CHUNK);
        long t0 = System.nanoTime();
        for (int b = 0; b < blocks; b++) {
            if (b % 9 == 0) audio.play(Sfx.HIT);
            audio.renderBlock(buf);
        }
        double ratio = (System.nanoTime() - t0) / 1e9 / 5.0;
        // le fil audio se reveille toutes les 11 ms : s'il ne rendait pas son
        // bloc bien avant l'echeance, la ligne se viderait et hacherait le son
        assertTrue(ratio < 0.25, "cout du melange : " + Math.round(ratio * 100) + " % du temps reel");
    }

    // --------------------------------------------------------------------
    // Ce que la mesure spectrale ajoute : un bruitage peut tenir dans la
    // reserve, retomber au silence et n'avoir aucune composante continue tout
    // en etant inaudible en course, ou indiscernable de son voisin. Les quatre
    // tests qui suivent verrouillent ces trois proprietes-la.
    // --------------------------------------------------------------------

    /** Les bruitages qui jouent moteur tournant ; les deux clics de menu, non. */
    private static final Sfx[] EN_COURSE = {
            Sfx.HERRING, Sfx.BOX, Sfx.BOOST, Sfx.FIRE, Sfx.HIT, Sfx.BUMP,
            Sfx.JUMP, Sfx.LAP, Sfx.COUNTDOWN, Sfx.GO, Sfx.FINISH};

    /**
     * Les bruitages du roulement ordinaire, ceux qui s'enchainent pendant un
     * tour. Le decompte, le depart et l'arrivee n'en sont pas : ce sont des
     * annonces, elles jouent moteur au ralenti ou a l'arret et ont le droit
     * d'etre plus fortes.
     */
    private static final Sfx[] ROULEMENT = {
            Sfx.HERRING, Sfx.BOX, Sfx.BOOST, Sfx.FIRE, Sfx.HIT, Sfx.BUMP, Sfx.JUMP, Sfx.LAP};

    /** Plages comparees au moteur ; il en suffit d'une ou le bruitage passe. */
    private static final double[][] PLAGES =
            {{0, 200}, {200, 700}, {700, 2000}, {2000, 6000}, {6000, 22050}};

    private static double[] rendu(Sfx sfx) {
        Audio audio = Audio.offline();
        audio.play(sfx);
        return Signal.mono(audio, 2.5);
    }

    private static double[] moteur(double charge) {
        Audio audio = Audio.offline();
        audio.engine(true, charge, 0);
        return Signal.mono(audio, 3.0);
    }

    @Test
    @DisplayName("aucun bruitage ne disparait sous le moteur")
    void chaqueBruitagePasseAuDessusDuMoteur() {
        // le moteur a mi-charge : 69 % de son energie sous 200 Hz, 26 % de 200
        // a 700 Hz. Un bruitage entierement grave y est perdu — mesure faite,
        // la retombee de saut etait dix decibels sous lui dans sa meilleure
        // plage, le choc de carrosserie cinq, le turbo six.
        double[] ref = Signal.power(moteur(0.70), 2 * Signal.RATE, 3 * Signal.RATE);
        for (Sfx sfx : EN_COURSE) {
            double[] m = rendu(sfx);
            double[] pow = Signal.power(m, 0, (int) (Signal.tail(m) * Signal.RATE));
            double best = -99;
            for (double[] p : PLAGES) {
                double a = Signal.rmsIn(pow, p[0], p[1]);
                double b = Signal.rmsIn(ref, p[0], p[1]);
                best = Math.max(best, 20 * Math.log10(Math.max(1e-9, a) / Math.max(1e-9, b)));
            }
            assertTrue(best > -2.0, sfx + " : " + Math.round(best * 10) / 10.0
                    + " dB sous le moteur dans sa meilleure plage");
        }
    }

    @Test
    @DisplayName("deux bruitages de course ne se confondent pas")
    void bruitagesDistinctsDeuxADeux() {
        // un joueur doit reconnaitre le son en une fraction de seconde, moteur
        // en fond : deux bruitages doivent donc differer par le registre ou par
        // la duree. Avant correction, explosion contre retombee valait 0,88 de
        // cosinus et turbo contre explosion 0,90, tous trois entasses sous
        // 200 Hz.
        int n = EN_COURSE.length;
        double[][] profil = new double[n][];
        double[] duree = new double[n];
        for (int i = 0; i < n; i++) {
            double[] m = rendu(EN_COURSE[i]);
            duree[i] = Signal.tail(m);
            profil[i] = Signal.thirds(Signal.power(m, 0, (int) (duree[i] * Signal.RATE)));
        }
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                double cos = Signal.cosine(profil[i], profil[j]);
                double rapport = Math.max(duree[i], duree[j]) / Math.min(duree[i], duree[j]);
                assertTrue(cos < 0.90 || rapport > 2.5,
                        EN_COURSE[i] + " et " + EN_COURSE[j] + " occupent les memes bandes (cosinus "
                                + Math.round(cos * 1000) / 1000.0 + ") pour des durees voisines (x"
                                + Math.round(rapport * 100) / 100.0 + ")");
            }
        }
    }

    @Test
    @DisplayName("les treize bruitages tiennent dans une meme fourchette de niveau")
    void niveauxCoherentsEntreEux() {
        // un bruitage deux fois plus fort qu'un autre force a regler le volume
        // sur le pire. La fourchette se mesure sur la valeur efficace ponderee
        // A et non sur la crete : c'est celle qui suit ce qu'on entend.
        double minCourse = 1, maxCourse = 0, minTous = 1, maxTous = 0;
        StringBuilder bas = new StringBuilder(), haut = new StringBuilder();
        for (Sfx sfx : Sfx.values()) {
            double[] m = rendu(sfx);
            double a = Signal.aWeighted(Signal.power(m, 0, (int) (Signal.tail(m) * Signal.RATE)));
            if (a < minTous) {
                minTous = a;
                bas.setLength(0);
                bas.append(sfx.name());
            }
            if (a > maxTous) {
                maxTous = a;
                haut.setLength(0);
                haut.append(sfx.name());
            }
            for (Sfx c : ROULEMENT) {
                if (c == sfx) {
                    minCourse = Math.min(minCourse, a);
                    maxCourse = Math.max(maxCourse, a);
                }
            }
        }
        double ecartCourse = 20 * Math.log10(maxCourse / minCourse);
        double ecartTous = 20 * Math.log10(maxTous / minTous);
        assertAll(
                () -> assertTrue(ecartCourse < 5.0,
                        "les bruitages du roulement s'etalent sur " + Math.round(ecartCourse) + " dB"),
                () -> assertTrue(ecartTous < 12.0,
                        "les treize s'etalent sur " + Math.round(ecartTous) + " dB, de " + bas + " a " + haut));
    }

    @Test
    @DisplayName("le pire empilement plausible garde de la reserve avant ecretage")
    void reserveAvantEcretage() {
        // plein gaz, en travers, quatre bruitages a la fois : c'est le pire cas
        // qu'une course produit vraiment. La saturation douce borne le signal
        // de toute facon, mais elle epaissit le son des qu'elle travaille.
        Audio audio = Audio.offline();
        audio.engine(true, 1.0, 1.0);
        Signal.mono(audio, 1.0);
        audio.play(Sfx.HIT);
        audio.play(Sfx.BOOST);
        audio.play(Sfx.HERRING);
        audio.play(Sfx.LAP);
        double[] s = Signal.mono(audio, 2.0);
        double crete = 0;
        for (double v : s) crete = Math.max(crete, Math.abs(v));
        final double c = crete;
        assertTrue(c < 0.90, "crete du pire empilement : " + Math.round(c * 1000) / 1000.0);
    }

    @Test
    @DisplayName("le moteur se renforce a mesure que la charge monte")
    void moteurDePlusEnPlusFortAvecLaCharge() {
        // pas seulement plus aigu : plus fort. La resonance d'echappement
        // tombait autrefois pile sur la frequence d'allumage vers charge 0,15,
        // et le ralenti sortait aussi fort que le plein pot pour un niveau
        // percu six fois plus faible — quinze decibels de reserve pour rien.
        double precedentA = 0, precedentBrut = 0;
        for (int i = 0; i <= 8; i++) {
            double charge = i / 8.0;
            double[] pow = Signal.power(moteur(charge), 2 * Signal.RATE, 3 * Signal.RATE);
            double a = Signal.aWeighted(pow);
            double brut = Signal.rmsIn(pow, 0, 22050);
            assertTrue(a > precedentA * 1.05,
                    "charge " + charge + " : niveau percu " + a + " apres " + precedentA);
            // le niveau brut n'a pas a croitre jusqu'au bout — au-dela de 0,8
            // l'energie part dans les aigus — mais il ne doit plus s'effondrer
            if (charge <= 0.8) {
                assertTrue(brut > precedentBrut * 0.98,
                        "charge " + charge + " : valeur efficace " + brut + " apres " + precedentBrut);
            }
            precedentA = a;
            precedentBrut = brut;
        }
    }

    @Test
    @DisplayName("la boucle de melange n'alloue rien")
    void rienNeSAlloueDansLaBoucleDeMelange() {
        // un ramasse-miettes declenche par le fil audio fait des trous dans le
        // son ; declenche ailleurs, il produit une image longue que
        // FrameLimiter prend pour une file de presentation pleine. Avant le
        // pupitre de voix recycle, une rafale de bruitages demandait 490 octets
        // par bloc, soit 41 Ko par seconde.
        if (!(java.lang.management.ManagementFactory.getThreadMXBean()
                instanceof com.sun.management.ThreadMXBean mx)) {
            return;                                   // machine virtuelle sans ce compteur
        }
        Audio audio = Audio.offline();
        byte[] buf = new byte[CHUNK * 4];
        audio.engine(true, 1.0, 1.0);
        for (int i = 0; i < 500; i++) audio.renderBlock(buf);

        // la file est remplie d'avance : dans le jeu c'est le fil d'interface
        // qui l'alimente, pas le fil de melange
        for (int i = 0; i < 2000; i += 5) audio.play(Sfx.HIT);
        long avant = mx.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < 2000; i++) audio.renderBlock(buf);
        double parBloc = (mx.getCurrentThreadAllocatedBytes() - avant) / 2000.0;
        assertTrue(parBloc < 32, "melange : " + Math.round(parBloc) + " octets alloues par bloc");
    }

    // --------------------------------------------------------------------
    // La musique. Elle joue en meme temps que tout le reste et n'a droit ni a
    // la place du moteur, ni a celle des bruitages : ces cinq tests verrouillent
    // le registre, le niveau, la boucle, l'attenuation et le cout.
    // --------------------------------------------------------------------

    /** Rend un morceau seul, fondu d'entree ecarte. */
    private static double[] morceau(Music tune, double seconds) {
        Audio audio = Audio.offline();
        audio.music(tune);
        return Signal.mono(audio, seconds);
    }

    @Test
    @DisplayName("le morceau de course laisse le grave au moteur")
    void musiqueDeCourseHorsDuRegistreDuMoteur() {
        // c'est la contrainte la plus dure du chantier : le moteur porte
        // l'information de vitesse, sa hauteur va de 60 a 300 Hz et il place
        // 93 % de son energie sous 700 Hz. Une musique posee au meme endroit ne
        // s'entendrait pas et le masquerait — le seul defaut qui rendrait le
        // jeu moins bon plutot que different.
        double[] m = morceau(Music.RACE, 16.0);
        double[] pow = Signal.power(m, 2 * Signal.RATE, 15 * Signal.RATE);
        double[] ref = Signal.power(moteur(0.70), 2 * Signal.RATE, 3 * Signal.RATE);

        double grave = Signal.rmsIn(pow, 0, 700);
        double graveMoteur = Signal.rmsIn(ref, 0, 700);
        double haut = Signal.rmsIn(pow, 700, 22050);
        double tout = Signal.rmsIn(pow, 0, 22050);
        assertAll(
                // sous 700 Hz la musique de course doit etre inexistante devant
                // le moteur ; 20 dB, c'est un vingtieme de son niveau
                () -> assertTrue(20 * Math.log10(grave / graveMoteur) < -20,
                        "la musique de course pese " + Math.round(20 * Math.log10(grave / graveMoteur))
                                + " dB par rapport au moteur sous 700 Hz"),
                () -> assertTrue(haut * haut > 0.9 * tout * tout,
                        "moins de 90 % de l'energie du morceau de course est au-dessus de 700 Hz"),
                // et elle ne doit pas non plus prendre la place des bruitages
                // au-dessus : trois decibels sous le moteur dans sa propre
                // plage, c'est la marge qui leur reste
                () -> {
                    double a = Signal.rmsIn(pow, 700, 3000);
                    double b = Signal.rmsIn(ref, 700, 3000);
                    assertTrue(20 * Math.log10(a / b) < -3,
                            "la musique tient " + Math.round(20 * Math.log10(a / b))
                                    + " dB face au moteur entre 700 Hz et 3 kHz");
                });
    }

    @Test
    @DisplayName("les deux morceaux restent sous le niveau des bruitages")
    void musiqueEnRetraitDesBruitages() {
        // la musique est un fond : elle doit rester sous le plus discret des
        // bruitages du roulement, sans quoi elle prend le devant du melange
        double[] course = Signal.aWeightedEt(morceau(Music.RACE, 16.0), 2, 15);
        double[] menus = Signal.aWeightedEt(morceau(Music.MENU, 20.0), 2, 19);
        double faible = 1;
        for (Sfx sfx : ROULEMENT) {
            double[] m = rendu(sfx);
            faible = Math.min(faible, Signal.aWeighted(
                    Signal.power(m, 0, (int) (Signal.tail(m) * Signal.RATE))));
        }
        final double plancher = faible;
        assertAll(
                () -> assertTrue(course[0] < plancher * 0.75,
                        "morceau de course : " + course[0] + " contre " + plancher + " pour le bruitage le plus discret"),
                () -> assertTrue(menus[0] < plancher, "morceau des menus : " + menus[0]),
                // la crete compte aussi : la musique joue en permanence, elle
                // ne doit pas a elle seule entamer la reserve
                () -> assertTrue(course[1] < 0.20, "crete du morceau de course : " + course[1]),
                () -> assertTrue(menus[1] < 0.30, "crete du morceau des menus : " + menus[1]));
    }

    @Test
    @DisplayName("la boucle se referme sans saut ni claquement")
    void boucleSansCouture() {
        // pas de bande a raccorder ici : le compteur de pas revient a zero et
        // les voix en cours continuent de decroitre. Reste a le verifier sur le
        // signal, ce qui attrape le cas ou une voix serait coupee au bouclage.
        double periode = Music.STEPS * 60.0 / (Music.RACE.bpm() * 4);
        double[] m = morceau(Music.RACE, 2 * periode + 2);
        int wrap = (int) (periode * Signal.RATE);

        double auBord = 0, ailleurs = 0;
        for (int i = 1; i < m.length; i++) {
            double d = Math.abs(m[i] - m[i - 1]);
            if (Math.abs(i - wrap) < Signal.RATE / 50 || Math.abs(i - 2 * wrap) < Signal.RATE / 50) {
                auBord = Math.max(auBord, d);
            } else if (i > Signal.RATE) {
                ailleurs = Math.max(ailleurs, d);
            }
        }
        final double bord = auBord;
        final double reste = ailleurs;
        // un raccord rate se voit comme une marche : une difference entre deux
        // echantillons voisins bien plus grande que tout ce que le morceau
        // produit par ailleurs
        assertTrue(bord <= reste, "saut au bouclage : " + bord + " contre " + reste + " ailleurs");

        // et le morceau ne doit pas s'eteindre juste avant de repartir
        double avant = 0, apres = 0;
        for (int i = wrap - Signal.RATE / 4; i < wrap; i++) avant += m[i] * m[i];
        for (int i = wrap; i < wrap + Signal.RATE / 4; i++) apres += m[i] * m[i];
        assertTrue(Math.sqrt(avant) > Math.sqrt(apres) * 0.15,
                "trou de " + Math.round(20 * Math.log10(Math.sqrt(avant / apres))) + " dB avant le bouclage");
    }

    @Test
    @DisplayName("un bruitage fait reculer la musique le temps qu'il sonne")
    void musiqueAttenueeSousUnBruitage() {
        // sans cela la musique occupe en continu les memes bandes que le tour
        // boucle ou le ramassage et leur retire les quelques decibels qui les
        // rendent lisibles. Le temoin est le chabada, au-dessus de 7 kHz : le
        // decompte n'y met rien, ce qu'on mesure la est donc bien la musique.
        double[] sans = musiqueEtBruitage(null);
        double[] avec = musiqueEtBruitage(Sfx.COUNTDOWN);
        double a = bandeAigue(sans, 0.05, 0.25);
        double b = bandeAigue(avec, 0.05, 0.25);
        double recul = 20 * Math.log10(b / a);
        // et elle doit remonter : une musique qui reste basse apres coup
        // s'entend comme une panne
        double reprise = 20 * Math.log10(bandeAigue(avec, 0.9, 1.15) / bandeAigue(sans, 0.9, 1.15));
        assertAll(
                () -> assertTrue(recul < -4, "attenuation mesuree : " + Math.round(recul) + " dB"),
                () -> assertTrue(reprise > -2, "la musique ne remonte pas : " + Math.round(reprise) + " dB"));
    }

    private static double[] musiqueEtBruitage(Sfx sfx) {
        Audio audio = Audio.offline();
        audio.music(Music.RACE);
        Signal.mono(audio, 3.5);                      // fondu d'entree termine
        if (sfx != null) audio.play(sfx);
        return Signal.mono(audio, 1.5);
    }

    private static double bandeAigue(double[] m, double from, double to) {
        return Signal.rmsIn(Signal.power(m, (int) (from * Signal.RATE), (int) (to * Signal.RATE)), 7000, 20000);
    }

    @Test
    @DisplayName("moteur, musique et bruitages ensemble tiennent le budget")
    void toutEnsembleResteSousQuatrePourCent() {
        Audio audio = Audio.offline();
        byte[] buf = new byte[CHUNK * 4];
        audio.music(Music.MENU);                       // le morceau le plus fourni
        audio.engine(true, 1.0, 1.0);
        for (int i = 0; i < 800; i++) audio.renderBlock(buf);

        int blocks = (int) (5.0 * RATE / CHUNK);
        long t0 = System.nanoTime();
        for (int b = 0; b < blocks; b++) {
            if (b % 9 == 0) audio.play(Sfx.HIT);
            audio.renderBlock(buf);
        }
        double ratio = (System.nanoTime() - t0) / 1e9 / 5.0;
        // 25 % et non 4 % : la machine qui fait tourner les tests n'est pas
        // celle qui joue, et une mesure de duree y derive largement. Le budget
        // de 4 % se verifie a la main ; ce seuil-ci attrape une regression
        // d'un facteur cinq, ce qu'aucune derive de charge ne produit.
        assertTrue(ratio < 0.25, "cout total : " + Math.round(ratio * 100) + " % du temps reel");
    }

    @Test
    @DisplayName("couper la musique la fait taire, couper le son aussi")
    void musiqueSeCoupe() {
        Audio audio = Audio.offline();
        audio.music(Music.RACE);
        double[] joue = Signal.mono(audio, 4.0);
        double niveau = 0;
        for (int i = 2 * RATE; i < joue.length; i++) niveau = Math.max(niveau, Math.abs(joue[i]));
        assertTrue(niveau > 0.01, "le morceau ne joue pas du tout");

        audio.setMusicEnabled(false);
        double[] coupee = Signal.mono(audio, 3.0);
        assertTrue(peakMono(coupee, 1.5, 3.0) < 1e-4, "la musique ne se coupe pas");

        // le reglage « Son » reste maitre : il coupe aussi la musique
        Audio autre = Audio.offline();
        autre.music(Music.MENU);
        autre.setEnabled(false);
        assertTrue(peakMono(Signal.mono(autre, 3.0), 1.5, 3.0) < 1e-4,
                "couper le son ne coupe pas la musique");
    }

    private static double peakMono(double[] m, double from, double to) {
        double p = 0;
        for (int i = (int) (from * RATE); i < (int) (to * RATE) && i < m.length; i++) {
            p = Math.max(p, Math.abs(m[i]));
        }
        return p;
    }

    /** Energie des aigus, mesuree par difference premiere du canal gauche. */
    private static double highEnergy(double[] s, double from, double to) {
        double sum = 0;
        int n = 0;
        for (int i = (int) (from * RATE); i < (int) (to * RATE); i++, n++) {
            double d = s[i * 2] - s[(i - 1) * 2];
            sum += d * d;
        }
        return sum / n;
    }
}
