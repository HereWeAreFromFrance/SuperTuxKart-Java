package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Le plus long des neuf, et l'un des deux plus rapides avec la Piste de
 * Tux, a cinq pour cent pres : deux kilometres et demi de
 * courbes tendues sur une neige qui ne retient rien. Deux tours suffisent.
 *
 * <p>Le tremplin de glace du debut de tour ne doit pas sa longueur a sa
 * hauteur : la rampe est suivie d'une ravine, si bien que le sol se derobe
 * deux fois — une a la crete, une a la reception. Une cinquantaine de metres de
 * vol, le plus long du jeu — le saut du cratere, au volcan, envoie plus haut
 * mais moins loin. L'iglou geant, lui, se traverse a pleine
 * vitesse au nord du circuit.
 */
public final class Banquise {

    private Banquise() {
    }

    public static final TrackDef DEF = new TrackDef(
            "banquise", "Banquise", "Longues courbes, très peu d'adhérence",
            Theme.SNOW, Surface.NEIGE, Barrier.CONGERE,
            10.0, 8.0, 2, 3,
            // le trace
            new double[][]{
                    {-111, -339.7, 0},
                    {-62.9, -342.9, 0},
                    {-14.9, -346, 0},
                    {33.2, -349.1, 0},
                    {81.2, -352.3, 0},
                    {129.3, -355.4, 0},
                    {177.8, -356.2, 0},
                    {205.5, -349.7, 0},
                    {231.1, -337.2, 0},
                    {270.9, -308.8, 0},
                    {310.5, -280.2, 0},
                    {341, -253.7, 0},
                    {354.2, -233.4, 0},
                    {362.7, -210.6, 0},
                    {365.9, -186.6, 0},
                    {363.8, -162.4, 0},
                    {352.6, -114.8, 0},
                    {341.3, -67.3, 0},
                    {331.3, -40.3, 0},
                    {319.3, -23.5, 0},
                    {303.7, -10, 0},
                    {258.9, 11.6, 0},
                    {214.6, 33.3, 0},
                    {203.1, 44.3, 0},
                    {194.3, 57.7, 0},
                    {188.8, 72.7, 0},
                    {186.8, 88.5, 0},
                    {188.5, 104.4, 0},
                    {193.8, 119.5, 0},
                    {221, 160.7, 0},
                    {228.6, 179.2, 0},
                    {232.1, 198.9, 0},
                    {231.4, 218.9, 0},
                    {226.6, 238.3, 0},
                    {204.5, 279.5, 0},
                    {181.1, 313.8, 0},
                    {159.4, 331.9, 0},
                    {134, 344.5, 0},
                    {86, 354.6, 0},
                    {37.3, 363.1, 0},
                    {-11.4, 371.6, 0},
                    {-53.1, 374.7, 0},
                    {-82, 369.6, 0},
                    {-127.7, 351.5, 0},
                    {-172.5, 332.6, 0},
                    {-217.4, 313.4, 0},
                    {-238, 299.6, 0},
                    {-255.1, 281.6, 0},
                    {-267.7, 260.2, 0},
                    {-287.4, 215.9, 0},
                    {-307, 171.7, 0},
                    {-313.2, 152.2, 0},
                    {-313.4, 135.9, 0},
                    {-309, 120.1, 0},
                    {-300.6, 106, 0},
                    {-288.7, 94.8, 0},
                    {-266.9, 74.5, 0},
                    {-256.4, 57.5, 0},
                    {-249.9, 38.5, 0},
                    {-247.8, 18.6, 0},
                    {-250.2, -1.3, 0},
                    {-256.9, -20.1, 0},
                    {-267.5, -37, 0},
                    {-300.4, -74.7, 0},
                    {-313.9, -94.6, 0},
                    {-322.3, -117.1, 0},
                    {-325.1, -140.9, 0},
                    {-322.2, -164.7, 0},
                    {-305.2, -211.2, 0},
                    {-287.4, -258, 0},
                    {-274.4, -282.9, 0},
                    {-255.9, -304.2, 0},
                    {-233, -320.5, 0},
                    {-206.9, -331, 0},
                    {-179.1, -335.3, 0},
                    {-131, -338.4, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{90, 3, 10}, {170, -2.2, 22}, {1520, 1.2, 9}},
            false,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 11}, {400, 12.5}, {900, 9.5}, {1400, 10.5}, {1900, 12}, {2200, 10}},
            // les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            new double[][]{
                    {1310, 0, 0, 1},  // le dome de glace, sur la longue courbe du nord
                    {640, 46, 0, 1},
                    // Les deux epaves etaient a 42 et 48 m du ruban, derriere
                    // la ligne de sapins : le champ de la camera va de 60 a 69
                    // degres, et un objet pose a R metres de cote n'entre dans
                    // le cadre qu'a 1,4 x R devant — il fallait donc les
                    // apercevoir a soixante metres, la ou la foret les cachait
                    // deja. On n'en voyait qu'un bout de mat. Trente metres les
                    // font entrer dans le cadre a quarante-deux, au premier rang
                    // des sapins, et le couloir n'y fait que vingt metres de
                    // demi-largeur : la coque reste six metres au large.
                    {300, -30, 15, 1.2},
                    {1900, 32, -30, 1},
                    {120, 28, 0, 1},
                    {1200, -30, 0, 1.2},
                    {2300, 32, 0, 1},
                    {400, 26, 0, 1},
                    {800, -28, 0, 1},
                    {1600, 27, 0, 1},
                    {2100, -26, 0, 1},
                    {200, 32, 0, 1.4},
                    {520, -34, 0, 1.6},
                    {1450, 36, 0, 1.3},
                    {2000, -32, 0, 1.5},
                    {700, 38, 0, 1.5},
                    {1700, -40, 0, 1.3},
                    {2250, 34, 0, 1.4},
                    {950, 42, 0, 1.2},
                    {1050, -44, 0, 1.1},
            },
            new String[]{
                    "iglou",
                    "phare",
                    "epave",
                    "epave",
                    "bonhomme",
                    "bonhomme",
                    "bonhomme",
                    "balise",
                    "balise",
                    "balise",
                    "balise",
                    "blocdeglace",
                    "blocdeglace",
                    "blocdeglace",
                    "blocdeglace",
                    "picdeglace",
                    "picdeglace",
                    "picdeglace",
                    "sapinneige",
                    "sapinneige",
            });
}
