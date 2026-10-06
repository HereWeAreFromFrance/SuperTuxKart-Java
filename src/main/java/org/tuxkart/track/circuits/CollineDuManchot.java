package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Trente-quatre metres de denivele par tour, une piste etroite et bordee de bois,
 * et trois bosses : le petit tremplin apres la ligne, le grand dans la descente
 * du bucheron, et un simple dos d'ane au dernier tiers. Dans le premier tiers du
 * tour, le tunnel perce sous la crete.
 */
public final class CollineDuManchot {

    private CollineDuManchot() {
    }

    public static final TrackDef DEF = new TrackDef(
            "colline", "Colline du Manchot", "Trente-quatre mètres de dénivelé par tour",
            Theme.FOREST, Surface.BITUME, Barrier.BOIS,
            6.0, 5.0, 3, 3,
            // le trace
            new double[][]{
                    {-58.4, -236.3, 2},
                    {-18.2, -239.1, 2.6},
                    {22.1, -241.9, 4.2},
                    {62.3, -244.6, 6.1},
                    {102.5, -247.4, 7.5},
                    {127.2, -246, 8},
                    {142.9, -240.3, 8},
                    {156.8, -231.1, 8.2},
                    {185.1, -201.1, 9.8},
                    {213, -170.9, 12.6},
                    {241, -140.6, 16},
                    {250.5, -126.3, 17.4},
                    {254.5, -114, 18.5},
                    {255.5, -101.1, 19.5},
                    {248.2, -61, 22.2},
                    {240.3, -21.6, 23.8},
                    {232.1, 18.3, 24.1},
                    {226.5, 30.7, 24.4},
                    {217.8, 41.1, 24.8},
                    {187.7, 68.5, 26.9},
                    {157.6, 95.8, 29.7},
                    {126.4, 121.7, 32.5},
                    {114.7, 125.1, 33.3},
                    {102.5, 124.8, 34},
                    {63.6, 113.9, 35.6},
                    {24.7, 102.8, 36},
                    {-6.9, 95, 35.5},
                    {-18.9, 98, 35.2},
                    {-28.5, 105.9, 34.8},
                    {-34.8, 116.7, 34.4},
                    {-55.1, 153.2, 32.7},
                    {-66.6, 170.4, 31.9},
                    {-76.5, 177.9, 31.4},
                    {-88, 182.4, 31},
                    {-128.3, 188.3, 30.1},
                    {-168.6, 193.9, 29.7},
                    {-185, 192.9, 29},
                    {-200.5, 187.4, 28},
                    {-214, 177.9, 26.6},
                    {-224.3, 165.1, 25},
                    {-240.8, 128.1, 20.7},
                    {-256.7, 91.1, 16.7},
                    {-262.4, 71.2, 15.3},
                    {-262.2, 58.6, 14.6},
                    {-259.2, 46.4, 14.2},
                    {-253.5, 35.2, 14},
                    {-230.8, 1.2, 13.4},
                    {-208.2, -32.8, 11.4},
                    {-197.6, -51.1, 10},
                    {-196.7, -64.4, 9.1},
                    {-201.8, -76.8, 8.2},
                    {-225.3, -110.5, 5.7},
                    {-237, -131.7, 4.6},
                    {-239, -143.6, 4.3},
                    {-238.1, -155.6, 4.1},
                    {-234.3, -167.1, 4},
                    {-227.9, -177.4, 4},
                    {-219.2, -185.7, 3.9},
                    {-184.1, -208.3, 3.5},
                    {-147.4, -228.8, 2.9},
                    {-134.8, -231, 2.7},
                    {-94.6, -233.8, 2.2},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{45, 2.4, 9}, {590, 3.6, 9.5}, {1120, 1.1, 7}},
            false,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[0][],
            // les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            new double[][]{
                    {283, 0, 0, 1},  // le tunnel perce sous la crete
                    // La ferme etait du mauvais cote : a gauche au sommet, elle
                    // n'etait cadree que sur trente metres de piste et jamais a
                    // moins de cent deux metres. Le meme point a droite la donne
                    // sur quatre-vingt-six metres, de s=588 — la reception du
                    // grand tremplin — a s=674, au plus pres a 47 m.
                    // Meme cap qu'a la Piste de Tux, et pour la meme raison :
                    // a vingt degres le pignon mordait sur le couloir.
                    {700, 27, 55, 1.3},
                    // Le moulin restait a trente et un metres de cote dans une
                    // courbe : cinquante metres de piste. Recule a l'entree du
                    // dernier virage, il est cadre de s=1342 a s=1426.
                    {1450, 26, 0, 1.3},
                    {60, 18, 0, 1.3},
                    {100, -20, 0, 1.1},
                    {350, 22, 0, 1.2},
                    {420, -18, 0, 1.4},
                    {800, 20, 0, 1.2},
                    {900, -22, 0, 1.3},
                    {1200, 19, 0, 1.1},
                    {1300, -21, 0, 1.35},
                    {150, 16, 0, 1},
                    {500, -17, 0, 1.2},
                    {1000, 18, 0, 1},
                    {1550, -16, 0, 1.1},
                    {200, 15, 0, 1},
                    {450, -15, 0, 1},
                    {750, 16, 0, 1},
                    {1100, -14, 0, 1},
                    {1400, 15, 0, 1},
                    {250, -18, 40, 1},
                    {600, 17, -30, 1},
                    {1050, -19, 20, 1},
                    {1600, 18, 60, 1},
                    {320, 14, 0, 1},
                    {860, -14, 0, 1},
                    {1150, 15, 0, 1},
                    {1500, -15, 0, 1},
                    {380, 20, 0, 1.4},
                    {950, -21, 0, 1.5},
                    {1250, 22, 0, 1.3},
                    {1580, -20, 0, 1.6},
            },
            new String[]{
                    "tunnel",
                    "ferme",
                    "moulin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "sapin",
                    "arbremort",
                    "arbremort",
                    "arbremort",
                    "arbremort",
                    "souche",
                    "souche",
                    "souche",
                    "souche",
                    "souche",
                    "tronccouche",
                    "tronccouche",
                    "tronccouche",
                    "tronccouche",
                    "buisson",
                    "buisson",
                    "buisson",
                    "buisson",
                    "rocher",
                    "rocher",
                    "rocher",
                    "rocher",
            });
}
