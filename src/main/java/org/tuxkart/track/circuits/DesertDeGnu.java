package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Le plus changeant des circuits de plein air : de treize metres de sable entre
 * deux dunes a vingt-deux au sortir du grand droit, et deux epingles seches ou
 * la piste se referme — seules les quatre pieces d'interieur s'ouvrent
 * davantage. Le tremplin de la dune envoie a pres de quarante metres, presque
 * aussi haut que le saut du cratere, et le dernier droit passe sous la salle
 * hypostyle du temple.
 */
public final class DesertDeGnu {

    private DesertDeGnu() {
    }

    public static final TrackDef DEF = new TrackDef(
            "desert", "Désert de GNU", "Épingles sèches et dunes tremplins",
            Theme.DESERT, Surface.SABLE, Barrier.TALUS,
            8.0, 6.0, 2, 5,
            // le trace
            new double[][]{
                    {19.7, -233.7, 0},
                    {63.9, -236.1, 0.2},
                    {108.6, -238.1, 0.8},
                    {129.5, -234.5, 1.2},
                    {148.8, -225.8, 1.6},
                    {165.5, -212.8, 2},
                    {196.5, -184.2, 3},
                    {219.2, -160.1, 3.7},
                    {225.8, -145.2, 4},
                    {228.2, -129, 4.3},
                    {226, -112.8, 4.5},
                    {208.9, -71.4, 4.9},
                    {194.4, -42.3, 5},
                    {185, -34.3, 5},
                    {173.8, -29.6, 4.9},
                    {128.2, -22.9, 4.6},
                    {86.3, -15.3, 4.1},
                    {76.5, -6.1, 3.9},
                    {71.4, 5.6, 3.8},
                    {55.7, 48.1, 3.3},
                    {57.2, 60.8, 3.2},
                    {63.9, 71.7, 3.1},
                    {74.2, 79, 3.1},
                    {112.7, 101, 3},
                    {125.8, 110.9, 3.2},
                    {136.1, 123.8, 3.4},
                    {142.7, 138.8, 3.7},
                    {145.4, 155, 4.1},
                    {144, 171.4, 4.6},
                    {133, 202.4, 5.5},
                    {124.4, 216.1, 6},
                    {113, 227.7, 6.5},
                    {99.4, 236.5, 6.9},
                    {84.3, 242.3, 7.3},
                    {39.7, 247.3, 7.9},
                    {-5.1, 251.4, 7.9},
                    {-46.3, 252.9, 7.6},
                    {-62.3, 248, 7.4},
                    {-76.6, 239.2, 7.2},
                    {-112.1, 210.1, 6.5},
                    {-130.7, 193, 6},
                    {-134.5, 180.6, 5.8},
                    {-131.8, 167.9, 5.5},
                    {-124.5, 157.2, 5.3},
                    {-99.4, 122.1, 4.7},
                    {-96.3, 109.7, 4.5},
                    {-99, 97.2, 4.3},
                    {-107, 87.3, 4.2},
                    {-118.4, 81.6, 4.1},
                    {-157.8, 66.8, 4},
                    {-192.4, 51.3, 3.8},
                    {-201.8, 42.6, 3.7},
                    {-209.1, 32.1, 3.6},
                    {-214, 20.4, 3.4},
                    {-224, -23.9, 2.6},
                    {-233.8, -68, 1.8},
                    {-235.2, -88.8, 1.5},
                    {-231.9, -105.2, 1.3},
                    {-224.8, -120.3, 1.1},
                    {-200.5, -159.2, 1},
                    {-175.8, -197.9, 0.9},
                    {-163.9, -209.8, 0.8},
                    {-149.6, -218.8, 0.7},
                    {-133.6, -224.4, 0.6},
                    {-88.9, -227.8, 0.4},
                    {-44.7, -230.2, 0.1},
                    {-0.4, -232.6, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{865, 3.8, 9.5}, {300, 0.7, 5}, {1200, 0.8, 6}},
            false,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 9.5}, {400, 6.5}, {860, 11}, {1300, 6.8}, {1650, 9.5}},
            // les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            new double[][]{
                    {1660, 0, 0, 1},  // la salle hypostyle, juste avant la ligne
                    {600, 25, 0, 1.2},
                    {1200, -27, 0, 1.1},
                    {100, 22, 0, 1.4},
                    {250, -23, 0, 1.2},
                    {450, 24, 0, 1.5},
                    {750, -23, 0, 1.3},
                    {1000, 25, 0, 1.4},
                    {1400, -24, 0, 1.2},
                    {180, 21, 0, 1.3},
                    {520, -21, 0, 1.5},
                    {900, 23, 0, 1.2},
                    {1300, -20, 0, 1.4},
                    {320, 27, 0, 1.6},
                    {680, -29, 0, 1.4},
                    {1100, 28, 0, 1.5},
                    {1500, -27, 0, 1.3},
                    {380, 35, 0, 1.8},
                    {960, -37, 0, 1.6},
                    {1560, 33, 0, 1.7},
                    {150, 19, 0, 1.2},
                    {560, -20, 0, 1.3},
                    {1050, 21, 0, 1.1},
                    {1450, -19, 0, 1.2},
                    {820, 31, 20, 1.6},
                    {1350, -33, -15, 1.5},
            },
            new String[]{
                    "temple",
                    "totem",
                    "totem",
                    "cactus",
                    "cactus",
                    "cactus",
                    "cactus",
                    "cactus",
                    "cactus",
                    "agave",
                    "agave",
                    "agave",
                    "agave",
                    "pierredressee",
                    "pierredressee",
                    "pierredressee",
                    "pierredressee",
                    "butte",
                    "butte",
                    "butte",
                    "herbeseche",
                    "herbeseche",
                    "herbeseche",
                    "herbeseche",
                    "arche",
                    "arche",
            });
}
