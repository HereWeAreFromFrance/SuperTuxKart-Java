package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * La salle la plus meublee du jeu apres le salon : cinquante-sept pieces
 * posees une a une, du comptoir aux sous-verres. Chicane entre deux tables
 * rondes, epingle au fond de la salle, et le tonneau geant a traverser a
 * mi-parcours.
 */
public final class LeComptoir {

    private LeComptoir() {
    }

    public static final TrackDef DEF = new TrackDef(
            "bar", "Comptoir", "Chicane entre les tables, tonneau au fond",
            Theme.BAR, Surface.PARQUET, Barrier.BOIS,
            8.5, 7.0, 3, 4,
            // le trace
            new double[][]{
                    {0, -189, 0},
                    {97, -187, 0},
                    {181, -158, 0},
                    {216, -97, 0},
                    {178, -38, 0},
                    {109, -21, 0},
                    {69, 32, 0},
                    {113, 88, 0},
                    {65, 145, 0},
                    {-23, 176, 1},
                    {-116, 164, 1},
                    {-181, 111, 1},
                    {-195, 40, 0},
                    {-168, -34, 0},
                    {-193, -107, 0},
                    {-139, -166, 0},
                    {-65, -193, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{116, 1.25, 7}, {410, 0.9, 6}, {735, 1.15, 7.5}, {25, 2.2, 9.5}},
            true,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 10.5}, {147, 6.5}, {294, 12.5}, {420, 7}, {567, 11.5}, {714, 6.2}, {861, 13}, {1008, 7.5}, {1155, 11}, {1281, 6.8}},
            // Les objets poses : {abscisse, ecart lateral, cap relatif, echelle}.
            //
            // Le cap relatif compte pour tout ce qui a une face. A cap nul, la
            // longueur d'une piece court perpendiculairement au ruban et sa
            // facade regarde en arriere : le comptoir se presentait donc de
            // bout, ses pompes et son repose-pied caches, et l'etagere tournait
            // son miroir vers le mur. Un quart de tour les remet face a la
            // piste — moins quatre-vingt-dix du cote des ecarts negatifs, plus
            // quatre-vingt-dix de l'autre.
            new double[][]{
                    {42, -40, -90, 1},
                    {42, -49, -90, 1},
                    {24, -33, 0, 1},
                    {36, -33, 0, 1},
                    {48, -33, 0, 1},
                    {60, -33, 0, 1},
                    {32, -22, 0, 1},
                    {61, -23, 0, 1},
                    {147, -19, 0, 1},
                    {147, 20, 0, 1},
                    // la pile de sous-verres etait posee sur l'axe de la
                    // chaussee : le decor n'ayant pas de collision, les karts
                    // la traversaient tour apres tour
                    {163, -17, 0, 0.9},
                    {294, 46, 0, 1},
                    {315, 68, 0, 1},
                    {299, -52, -90, 1},
                    {328, -40, -90, 1},
                    {346, 34, 0, 1},
                    {361, -28, 0, 1},
                    {420, -18, 25, 1},
                    {443, 19, -25, 1},
                    {567, 44, 90, 1},
                    {567, 53, 90, 1},
                    {552, 37, 0, 1},
                    {582, 37, 0, 1},
                    {714, -17, 0, 1.2},
                    {714, 18, 0, 1},
                    {730, -13, 0, 1},
                    {861, -74, 0, 1},
                    {892, -58, -90, 1},
                    {882, 70, 0, 1},
                    {920, 52, 90, 1},
                    {1008, 22, 0, 1},
                    {1034, -20, 0, 1},
                    {1155, -50, -90, 1},
                    {1186, 44, 0, 1},
                    {1281, -17, 0, 1},
                    {1281, 18, 0, 1},
                    // le bol de cacahuetes aussi
                    {1312, 19, 0, 0.9},
                    {669, 0, 0, 1},
                    {120, 34, 0, 1},
                    {126, 41, 0, 1},
                    {115, 29, 0, 1},
                    {230, -44, 0, 1},
                    {245, -56, 0, 1},
                    {258, -46, 0, 1},
                    {470, 40, 90, 1},
                    {500, 52, 90, 1},
                    {600, -38, 0, 1},
                    // chope, verre et sous-verres etaient a trente et
                    // quarante-huit metres de l'axe : a cette distance une
                    // piece de un metre cinquante ne se voit plus. Dedans, le
                    // decor se longe — on les ramene au ras du couloir.
                    {615, -19, 0, 1},
                    {628, -22, 0, 1},
                    {760, 36, 0, 1},
                    {775, 48, 0, 1},
                    {790, 42, 0, 1.2},
                    {900, -40, -90, 1},
                    {1000, 44, 90, 1},
                    {1150, -36, 0, 1},
                    {1165, -20, 0, 1},
                    {1300, 19, 0, 1},
            },
            new String[]{
                    "comptoir",
                    "etagere",
                    "tabouret",
                    "tabouret",
                    "tabouret",
                    "tabouret",
                    "chope",
                    "verre",
                    "tonneau",
                    "tonneau",
                    "sousverres",
                    "tableronde",
                    "tableronde",
                    "jukebox",
                    "cible",
                    "bouteille",
                    "caisse",
                    "caisse",
                    "caisse",
                    "comptoir",
                    "etagere",
                    "tabouret",
                    "tabouret",
                    "tonneau",
                    "tonneau",
                    "bouteille",
                    "tableronde",
                    "cible",
                    "tableronde",
                    "jukebox",
                    "caisse",
                    "tonneau",
                    "etagere",
                    "tableronde",
                    "tonneau",
                    "caisse",
                    "cacahuetes",
                    "tonneaugeant",
                    "tableronde",
                    "tabouret",
                    "tabouret",
                    "caisse",
                    "caisse",
                    "tonneau",
                    "jukebox",
                    "cible",
                    "tableronde",
                    "chope",
                    "verre",
                    "tonneau",
                    "tonneau",
                    "caisse",
                    "etagere",
                    "comptoir",
                    "tabouret",
                    "sousverres",
                    "cacahuetes",
            });
}
