package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Le plus etroit du jeu, et l'un des deux plus durs avec le desert : neuf cent
 * trente metres accroches au cratere, cinq tours, une piste de terre qui se
 * resserre a dix metres et une epingle d'une quinzaine de metres de rayon.
 * C'est aussi l'un des deux sauts qui envoient le plus haut, avec la dune du
 * desert : six a sept metres au-dessus de la piste au saut du cratere, pour une
 * quarantaine de metres de vol.
 *
 * <p>Douze pieces posees : le volcan, a cent cinq metres du ruban, et onze reperes
 * de bord de piste. Sur une coulee rien ne pousse, et c'est le semis qui porte
 * le vide — mais il le portait en sept nuances du meme gris-noir, si bien que
 * le circuit ne se distinguait plus d'un terrain vague. Ce qui fait le decor
 * ici, ce n'est pas la densite mais le <b>contraste</b> : basalte noir, cendre
 * claire, soufre ocre, lave orange.
 */
public final class PetitVolcan {

    private PetitVolcan() {
    }

    public static final TrackDef DEF = new TrackDef(
            "volcan", "Petit Volcan", "Cinq tours au bord du cratère",
            Theme.VOLCANO, Surface.TERRE, Barrier.PNEUS,
            5.5, 4.5, 5, 5,
            // le trace
            new double[][]{
                    {-49.9, -130.2, 6},
                    {-13.8, -132.3, 7.4},
                    {22.2, -134.4, 10.8},
                    {58.3, -136.4, 14.3},
                    {94.4, -138.3, 16},
                    {106.3, -136.3, 16},
                    {117.3, -131.3, 16.2},
                    {126.5, -123.5, 16.5},
                    {133.3, -113.5, 17},
                    {149.8, -80.5, 18.9},
                    {166.2, -47.1, 20.8},
                    {169.1, -34.5, 21.4},
                    {168.2, -21.5, 21.7},
                    {163.6, -9.3, 22},
                    {146, 22.7, 21.9},
                    {128.4, 54.7, 21.7},
                    {120.9, 64.4, 21.6},
                    {110.8, 71.4, 21.5},
                    {99.1, 75.2, 21.3},
                    {63.4, 84.2, 21.1},
                    {27.1, 92.9, 20.9},
                    {13.4, 91.8, 20.4},
                    {-21.8, 76.7, 16.8},
                    {-44.5, 65.2, 13.6},
                    {-51.2, 54.2, 12.1},
                    {-51.6, 41.3, 10.8},
                    {-47, 29.8, 9.8},
                    {-44, 18.1, 9.2},
                    {-48.7, 6.9, 9},
                    {-59.3, 1.2, 8.9},
                    {-71.7, 3.2, 8.4},
                    {-100.8, 10.4, 6.4},
                    {-112.7, 8.6, 5.3},
                    {-123.6, 3.3, 4.2},
                    {-132.4, -5, 3},
                    {-138.3, -15.5, 2},
                    {-151.3, -50.1, 0.1},
                    {-153.5, -62.2, 0},
                    {-151.6, -74.5, 0.2},
                    {-141.9, -107.6, 1.3},
                    {-133.6, -117.4, 1.7},
                    {-122.6, -124, 2},
                    {-110.1, -126.7, 2.1},
                    {-74, -128.8, 4.8},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{45, 2.6, 9}, {350, 0.8, 6}, {620, 0.9, 7}},
            false,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 6}, {250, 5.2}, {600, 6.2}, {820, 5.4}},
            // les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            //
            // Le premier n'est pas un objet de bord de piste mais le volcan
            // lui-meme.
            //
            // Il a d'abord ete pose au centre de la boucle — le point interieur
            // le plus eloigne de l'axe, a 106 m — et c'etait une erreur qu'un
            // calcul a corrigee : sur un circuit en anneau, le centre reste a
            // soixante ou quatre-vingt-dix degres du cap presque tout le tour,
            // donc hors du champ de la camera. Balayage fait sur tout le plan,
            // a pas de 25 m, avec pour critere la fraction du tour ou le point
            // tombe a moins de 28 degres du cap : le centre donnait zero, et
            // cet emplacement-ci donne 28 % du tour, dont un plan continu de
            // 190 m. Il reste a 105 m de l'axe — le circuit longe donc bien le
            // pied du cone, comme le titre le promet.
            new double[][]{
                    {273.5, 104.9, 0, 1.0},
                    {200, 16, 0, 1.3},
                    {500, -17, 0, 1.5},
                    {760, 18, 0, 1.2},
                    {300, 14, 0, 1.6},
                    {650, -15, 0, 1.4},
                    {120, 15, 0, 1.5},
                    {560, -16, 0, 1.3},
                    {420, 16, 0, 1.4},
                    {830, -14, 0, 1.5},
                    // deux coulees le long du ruban : la lueur au sol que le
                    // semis ne donne qu'au hasard, posee la ou on la verra
                    {255, 15, 12, 1.6},
                    {700, -16, -9, 1.5},
            },
            new String[]{
                    "cratere",
                    "orgues",
                    "orgues",
                    "orgues",
                    "obsidienne",
                    "obsidienne",
                    "fumerolle",
                    "fumerolle",
                    "braise",
                    "braise",
                    "coulee",
                    "coulee",
            });
}
