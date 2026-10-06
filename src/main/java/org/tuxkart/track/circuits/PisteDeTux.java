package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * La piste d'entrainement du manchot : large, plate, sans piege, et la plus
 * large des deux pistes de largeur constante — avec la Colline du Manchot,
 * mais en quinze metres au lieu de douze. C'est a elle qu'on mesure les
 * autres. Une bosse a mi-parcours, la grange de la ferme a traverser dans la
 * ligne droite, et rien d'autre pour distraire d'apprendre a freiner.
 */
public final class PisteDeTux {

    private PisteDeTux() {
    }

    public static final TrackDef DEF = new TrackDef(
            "tux", "Piste de Tux", "Le circuit d'entraînement du manchot",
            Theme.GRASS, Surface.BITUME, Barrier.RAIL,
            7.5, 7.0, 3, 1,
            // le trace
            new double[][]{
                    {0, -195, 0},
                    {85, -186, 0},
                    {146, -128, 0},
                    {160, -42, 0},
                    {134, 38, 0},
                    {78, 96, 0},
                    {8, 126, 0},
                    {-64, 134, 0},
                    {-118, 108, 0},
                    {-140, 52, 0},
                    {-172, 6, 0},
                    {-150, -60, 0},
                    {-160, -122, 0},
                    {-104, -166, 0},
                    {-46, -192, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{300, 1.5, 8}},
            false,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[0][],
            // les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            new double[][]{
                    {495, 0, 0, 1},  // la grange : on la traverse de part en part
                    // La ferme et son silo faisaient corps avec la grange, mais
                    // trente metres apres elle et a trente et un metres de cote :
                    // mesure faite, la ferme n'etait cadree que sur trente-quatre
                    // metres de piste et le silo sur douze, et ces metres-la sont
                    // tous compris entre 463 et 527 — c'est-a-dire dans la grange,
                    // dont les murs les bouchaient. Ils passent donc en amont : on
                    // voit la cour de ferme, puis on plonge dans sa grange. Cadres
                    // de s=314 a s=384 et de s=344 a s=408, au plus pres a 61 et
                    // 68 m. Le cap et l'ecart sont tenus par l'encombrement :
                    // le corps de logis fait jusqu'a vingt metres de long, et a
                    // trente metres d'ecart sous un cap de trente degres son
                    // angle le plus proche tombait a quinze metres sept — dans
                    // le couloir, donc contre la rambarde. Tourne a cinquante-
                    // cinq degres il n'encombre plus que douze metres cinq de
                    // travers, et sa facade se presente encore de trois quarts.
                    {424, 31, 55, 1.3},
                    {452, 34, 0, 1.2},
                    // Le moulin etait a gauche, du cote interieur de la boucle :
                    // il ne sortait jamais de 42 degres de l'axe, donc zero metre
                    // de piste sur mille soixante-dix. Passe a droite, il est
                    // cadre de s=80 a s=158, au plus pres a 55 m.
                    {190, 28, 0, 1.25},
                    {60, 22, 0, 1},
                    {66, 27, 30, 1.2},
                    {73, 21, -20, 1},
                    {250, 26, 0, 1.4},
                    {300, -25, 0, 1.3},
                    {700, 25, 0, 1.2},
                    {760, -27, 0, 1},
                    {880, 20, 0, 1},
                    {930, -22, 0, 1},
            },
            new String[]{
                    "grange",
                    "ferme",
                    "silo",
                    "moulin",
                    "foin",
                    "foin",
                    "foin",
                    "feuillufruit",
                    "feuillufruit",
                    "bouleau",
                    "bouleau",
                    "fleurs",
                    "buisson",
            });
}
