package org.tuxkart.audio;

/**
 * Les morceaux, sous forme de motifs : une grille de double-croches et, pour
 * chaque pupitre, la liste des notes qui y tombent. Aucun fichier, aucune piste
 * enregistree — {@link Audio} lit cette grille au taux de controle et declenche
 * des voix de synthese, exactement comme il le fait pour les bruitages.
 *
 * <p><b>Pourquoi deux morceaux et pas un.</b> Mesure faite sur le signal, le
 * moteur place 69 % de son energie sous 200 Hz et 26 % de 200 a 700 Hz a
 * mi-charge, et c'est lui qui porte l'information de vitesse : sa hauteur va de
 * 60 a 300 Hz selon le regime. Une basse tenue dans cette plage le couvrirait,
 * et une musique qui couvre le moteur rend le jeu moins jouable, pas seulement
 * different. Le morceau de course n'a donc <em>aucune</em> voix grave et
 * n'entretient rien : tout y est pince et court, la ou le moteur est quinze
 * decibels sous son maximum. Le morceau des menus, lui, joue moteur eteint et
 * dispose de tout le spectre — c'est le seul des deux a avoir une basse.
 *
 * <p><b>La boucle est sans couture par construction.</b> Il n'y a pas de bande
 * a raccorder : le compteur de pas revient a zero, les voix en cours continuent
 * de decroitre par-dessus, et aucun echantillon n'est interrompu. Le test le
 * verifie tout de meme sur le signal, sur la derivee au point de bouclage.
 *
 * <p>Les hauteurs sont des numeros MIDI : 69 = la 440. Une paire par note,
 * {pas, note} ; pour la percussion, {pas, genre}.
 */
public enum Music {

    /** Silence : aucun morceau. */
    NONE,

    /**
     * Menus, selection, aide, resultats. Re majeur, 108 a la noire, huit
     * mesures. Enchainement Re, Sim, Sol, La, deux fois.
     */
    MENU,

    /**
     * Course. Meme tonalite, 144 a la noire, huit mesures. Pas de basse, pas de
     * nappe : rien qui tienne sous 500 Hz pendant que le moteur travaille.
     */
    RACE;

    /** Pupitres. Le grave et la nappe restent vides pendant la course. */
    static final int LEAD = 0, ARP = 1, BASS = 2, PAD = 3, PERC = 4;

    /** Genres de percussion : chabada discret, chabada marque, frappe. */
    static final int HAT = 0, HAT_FORT = 1, CLAP = 2;

    /** Longueur de la boucle, en double-croches. */
    static final int STEPS = 128;

    private static final int[] VIDE = {};

    /** Tempo a la noire. */
    double bpm() {
        return this == RACE ? 144 : 108;
    }

    /**
     * Gain du bus, par morceau. Le morceau de course est volontairement trois
     * decibels sous celui des menus : mesure faite, a niveau egal il arrivait a
     * deux decibels du moteur dans la plage 700-2000 Hz, la ou le ramassage, la
     * caisse, le choc et le tour boucle placent tous leur signature. Trois
     * decibels de moins lui rendent leur marge, l'attenuation automatique
     * faisant le reste au moment ou ils sonnent.
     */
    double gain() {
        return this == RACE ? 0.70 : 1.30;
    }

    int[] pattern(int part) {
        return switch (this) {
            case MENU -> switch (part) {
                case LEAD -> M.MENU_LEAD;
                case ARP -> M.MENU_ARP;
                case BASS -> M.MENU_BASS;
                case PAD -> M.MENU_PAD;
                default -> M.MENU_PERC;
            };
            case RACE -> switch (part) {
                case LEAD -> M.RACE_LEAD;
                case ARP -> M.RACE_ARP;
                case PERC -> M.RACE_PERC;
                default -> VIDE;                 // ni basse ni nappe : voir l'en-tete
            };
            default -> VIDE;
        };
    }

    /**
     * Les motifs eux-memes, dans une classe a part : une constante d'enumeration
     * ne peut pas lire un champ statique de sa propre enumeration au moment ou
     * elle se construit.
     */
    private static final class M {

        // ------------------------------------------------------------ course
        // Tete de chant, re5 a fa#6 (587 a 1480 Hz). Registre choisi pour
        // passer au-dessus du moteur sans aller chercher les aigus, ou les
        // bruitages de ramassage et d'impact ont deja leur signature.
        static final int[] RACE_LEAD = {
                // Re
                0, 74, 2, 78, 4, 81, 7, 78, 8, 81, 11, 86, 14, 83,
                // La
                16, 85, 18, 83, 20, 81, 23, 76, 24, 78, 26, 81,
                // Sim
                32, 83, 35, 86, 37, 83, 39, 81, 40, 78, 43, 83,
                // Sol
                48, 79, 50, 83, 52, 86, 54, 83, 56, 81, 59, 79,
                // Re, une octave plus haut : la reprise se reconnait
                64, 86, 67, 81, 69, 78, 71, 81, 72, 86, 76, 90, 78, 86,
                // La
                80, 88, 82, 85, 84, 81, 87, 85, 88, 88,
                // Sol
                96, 86, 98, 83, 100, 79, 102, 83, 104, 86, 107, 83,
                // La, puis une montee vers le Re de la mesure 1 : sans elle les
                // huit cents dernieres millisecondes etaient presque vides et la
                // reprise s'entendait comme un demarrage plutot qu'une boucle
                112, 85, 114, 88, 116, 81, 120, 81, 122, 83, 124, 85, 126, 86,
        };

        // Contrechant pince, une octave au-dessus, sur les contretemps.
        static final int[] RACE_ARP = {
                2, 90, 6, 93, 10, 90, 14, 86,
                18, 88, 22, 93, 26, 88, 30, 85,
                34, 90, 38, 86, 42, 90, 46, 83,
                50, 91, 54, 86, 58, 91, 62, 83,
                66, 90, 70, 93, 74, 90, 78, 86,
                82, 88, 86, 93, 90, 88, 94, 85,
                98, 90, 102, 86, 106, 90, 110, 83,
                114, 91, 118, 86, 122, 91, 126, 88,
        };

        /**
         * Percussion. Le chabada est place au-dessus de 7 kHz : c'est la seule
         * plage vraiment libre du melange, le moteur y est quarante decibels
         * sous son maximum et aucun bruitage n'y porte sa signature. La frappe
         * du contretemps, elle, est plus basse et donc plus exposee — d'ou son
         * niveau discret.
         */
        static final int[] RACE_PERC = perc(true);

        // ------------------------------------------------------------- menus
        // Moteur eteint : tout le spectre est disponible, le morceau a donc une
        // basse et une nappe, ce que celui de la course s'interdit.
        static final int[] MENU_LEAD = {
                0, 74, 4, 78, 8, 81, 12, 78,
                16, 83, 20, 78, 24, 74, 28, 78,
                32, 79, 36, 83, 40, 86, 44, 83,
                48, 85, 52, 81, 56, 76, 60, 81,
                64, 86, 68, 81, 72, 78, 76, 74,
                80, 78, 84, 83, 88, 90, 92, 86,
                96, 83, 100, 79, 104, 86, 108, 83,
                112, 81, 116, 85, 120, 88, 124, 81,
        };

        static final int[] MENU_ARP = {
                2, 90, 10, 86, 18, 90, 26, 83,
                34, 91, 42, 86, 50, 93, 58, 88,
                66, 90, 74, 86, 82, 90, 90, 83,
                98, 91, 106, 86, 114, 93, 122, 88,
        };

        // Basse : 98 a 147 Hz. Une enceinte de portable commence a rendre
        // quelque chose vers 120 Hz, aller plus bas serait de la reserve
        // depensee pour rien.
        static final int[] MENU_BASS = {
                0, 50, 6, 50, 8, 57, 14, 50,
                16, 47, 22, 47, 24, 54, 30, 47,
                32, 43, 38, 43, 40, 50, 46, 43,
                48, 45, 54, 45, 56, 52, 62, 45,
                64, 50, 70, 50, 72, 57, 78, 50,
                80, 47, 86, 47, 88, 54, 94, 47,
                96, 43, 102, 43, 104, 50, 110, 43,
                112, 45, 118, 45, 120, 52, 126, 49,
        };

        // Nappe : la triade de la mesure, posee sur son premier temps.
        static final int[] MENU_PAD = {
                0, 62, 0, 66, 0, 69,             // Re
                16, 59, 16, 62, 16, 66,          // Sim
                32, 55, 32, 59, 32, 62,          // Sol
                48, 57, 48, 61, 48, 64,          // La
                64, 62, 64, 66, 64, 69,
                80, 59, 80, 62, 80, 66,
                96, 55, 96, 59, 96, 62,
                112, 57, 112, 61, 112, 64,
        };

        static final int[] MENU_PERC = perc(false);

        /** Chabada sur les croches, frappe sur les deuxieme et quatrieme temps. */
        private static int[] perc(boolean course) {
            int[] out = new int[2 * STEPS];      // large : on tronque a la fin
            int n = 0;
            for (int s = 0; s < STEPS; s++) {
                int m = s % 16;
                if (m == 4 || m == 12) {
                    out[n++] = s;
                    out[n++] = CLAP;
                } else if (s % 2 == 0 && (course || m % 4 == 2)) {
                    out[n++] = s;
                    out[n++] = m % 4 == 0 ? HAT_FORT : HAT;
                }
            }
            int[] exact = new int[n];
            System.arraycopy(out, 0, exact, 0, n);
            return exact;
        }
    }
}
