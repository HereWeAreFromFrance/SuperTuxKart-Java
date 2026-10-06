package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Neuf cents metres sur un tapis : le plus court des neuf, donc quatre tours,
 * et le plus accrocheur apres les deux circuits de bitume — le feutre retient. Deux bandes prises a plein regime, une
 * boucle serree autour de la poche de coin — et la poche elle-meme, dont on
 * traverse le cuir a mi-parcours, la ou la bande se resserre.
 */
public final class LeBillard {

    private LeBillard() {
    }

    public static final TrackDef DEF = new TrackDef(
            "billard", "Table de billard", "Deux bandes, quatre tours, une poche à traverser",
            Theme.BILLARD, Surface.FEUTRE, Barrier.BOIS,
            7.5, 6.0, 4, 3,
            // le trace
            new double[][]{
                    {0, -124, 0},
                    {75, -122, 0},
                    {131, -102, 0},
                    {156, -58, 0},
                    {141, -13, 0},
                    {95, 4, 0},
                    {60, 37, 0},
                    {85, 81, 0},
                    {53, 115, 0},
                    {-12, 125, 0},
                    {-78, 114, 0},
                    {-128, 81, 0},
                    {-150, 32, 0},
                    {-143, -22, 0},
                    {-112, -69, 0},
                    {-63, -109, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{115, 1.35, 7}, {446, 1.4, 6.5}, {30, 2, 9}},
            true,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 12.5}, {115, 7.5}, {230, 14}, {338, 7}, {446, 12}, {547, 6.5}, {648, 13.5}, {749, 8}, {842, 11}},
            // Les objets poses : {abscisse, ecart lateral, cap relatif, echelle}.
            //
            // Le cap relatif compte pour tout ce qui a une face. A cap nul, la
            // longueur d'une piece court perpendiculairement au ruban et sa
            // facade regarde en arriere : le tableau de score tournait donc son
            // ardoise vers le mur et le ratelier ses queues. Un quart de tour
            // les remet face a la piste — moins quatre-vingt-dix du cote des
            // ecarts negatifs, plus quatre-vingt-dix de l'autre.
            //
            // La poche garde le cap nul, et pour la meme raison retournee : ce
            // qu'on doit voir d'elle n'est pas son flanc mais sa gueule, et une
            // facade qui regarde en arriere regarde le kart qui arrive. A plus
            // ou moins quatre-vingt-dix on ne croisait qu'un billot de bois
            // coiffe de feutre — on ne voit jamais une piece de bord de piste
            // autrement que de trois quarts arriere. C'est aussi ce qui l'a
            // deplacee du huit cent quarante-deuxieme metre au sept cent
            // soixantieme : elle y tenait a vingt-six metres de l'axe, seule
            // distance qui laissait la machoire hors du couloir dans un secteur
            // large de dix-sept — et a vingt-six metres, un objet de un metre
            // vingt sort du champ avant d'etre lisible. Le couloir se resserre
            // a quatorze au sept cent soixantieme : vingt-trois metres y
            // suffisent, machoire comprise.
            //
            // Trois pieces etaient posees sur l'axe de la chaussee et une
            // quatrieme a huit metres de cet axe pour une demi-chaussee de neuf
            // : le decor n'ayant pas de collision, les karts les traversaient
            // tour apres tour. Les trois craies ont cede la place a des billes,
            // qui se voient : a l'etalon d'interieur une craie fait quatre
            // centimetres et demi, ce qui la reserve a la litiere du semis.
            new double[][]{
                    {43, -19, 0, 1.4},
                    {50, -15, 0, 1},
                    {60, 13, 0, 1},
                    {69, -14, 0, 1},
                    {89, -16, 0, 1},
                    {115, -18, 8, 1},
                    {115, 19, -8, 1},
                    {230, -78, -90, 1},
                    {253, -48, 0, 1},
                    {265, 44, 90, 1},
                    {245, 30, 0, 1},
                    {338, -22, 0, 1.2},
                    {360, -16, 0, 1},
                    {446, 60, 90, 1},
                    {461, 72, 90, 1},
                    {487, -40, 0, 1},
                    // Les deux tas de billes de la gorge de la poche. Ils
                    // etaient a seize et dix-sept metres de l'axe : le nez de
                    // bande se tient maintenant a 16,5 m, et le tas de droite
                    // etait enfonce d'un demi-metre dans le drap.
                    {547, -15, 0, 1},
                    {547, 15, 0, 1},
                    {648, -84, -90, 1},
                    {670, 76, 90, 1},
                    {657, 40, 0, 1},
                    {749, -30, 0, 1},
                    {760, -23, 0, 1},
                    {871, 20, 0, 1},
                    {550, 0, 0, 1},
            },
            new String[]{
                    "rack",
                    "boules",
                    "boule",
                    "boule",
                    "boules",
                    "queue",
                    "queue",
                    "autretable",
                    "tabouret",
                    "score",
                    "boules",
                    "poche",
                    "boules",
                    "portequeues",
                    "score",
                    "tabouret",
                    "boules",
                    "boules",
                    "autretable",
                    "portequeues",
                    "rack",
                    "queue",
                    "poche",
                    "boule",
                    "pochegeante",
            });
}
