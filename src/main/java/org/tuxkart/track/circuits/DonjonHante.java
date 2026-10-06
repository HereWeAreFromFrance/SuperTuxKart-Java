package org.tuxkart.track.circuits;

import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.TrackDef;

/**
 * Le donjon : dalles descellees, deux epingles, et la nef voutee qu'on prend
 * plein sud des la sortie de la grille — quatre-vingt-dix metres entre deux
 * files de piliers, le seul ouvrage du jeu ou l'on roule assez longtemps pour
 * oublier qu'on est dehors.
 */
public final class DonjonHante {

    private DonjonHante() {
    }

    public static final TrackDef DEF = new TrackDef(
            "donjon", "Donjon hanté", "Deux épingles et une longue nef",
            Theme.DONJON, Surface.DALLE, Barrier.TALUS,
            8.5, 7.0, 3, 4,
            // le trace
            new double[][]{
                    {0, -190, 0},
                    {96, -186, 0},
                    {168, -160, 0},
                    {212, -104, 2},
                    {206, -40, 3},
                    {150, -14, 3},
                    {96, 24, 1},
                    {112, 92, 0},
                    {60, 146, 0},
                    {-20, 168, 0},
                    {-104, 152, 1},
                    {-166, 108, 2},
                    {-180, 44, 2},
                    {-146, -16, 1},
                    {-176, -80, 0},
                    {-140, -146, 0},
                    {-72, -180, 0},
            },
            // les bosses : {abscisse, hauteur, demi-longueur}
            new double[][]{{60, 1.15, 7.5}, {330, 1, 6.5}, {600, 1.3, 8}, {740, 2.2, 9}},
            true,
            // le profil de largeur : {abscisse, demi-largeur}
            new double[][]{{0, 12}, {120, 6}, {260, 11}, {390, 5.8}, {520, 13}, {660, 7}, {800, 10.5}, {930, 6.2}, {1060, 12.5}, {1190, 7.5}},
            // Les objets poses : {abscisse, ecart lateral, cap relatif, echelle}
            //
            // Le donjon est bati a l'echelle du monde — une unite, un metre —
            // et son mobilier vient d'etre ramene a sa taille reelle : une
            // armoire de trois metres, un sarcophage de deux metres huit. Les
            // ecarts lateraux, eux, dataient d'un mobilier quatre fois plus
            // grand : un trone pose a vingt-quatre metres au-dela du bord du
            // couloir ne se voyait plus du tout, le champ de la camera l'ayant
            // quitte bien avant qu'on arrive dessus. Chaque piece est donc
            // ramenee au bas-cote : mesure faite sur {@code halfRoadAtS}, les
            // quarante-cinq pieces posees se tiennent entre 2,45 m et 4,50 m
            // au-dela du bord de la chaussee, et aucune n'est sur le bitume.
            //
            // Trois metres et non huit, et c'est une affaire de champ : une
            // piece posee a {@code R} metres de l'axe ne rentre dans un champ
            // de soixante-cinq degres qu'a {@code 1,4 x R} metres devant soi,
            // quand la camera vise a quatorze. A vingt-quatre metres de cote,
            // un trone n'est vu que de loin et sort du cadre avant qu'on
            // arrive dessus — verifie a la capture, decor du semis coupe : il
            // ne restait rien a l'ecran. La chaussee, elle, reste libre : le
            // repere est {@code halfRoadAtS}, pas le couloir, et le bas-cote
            // est fait pour etre meuble.
            //
            // Le cap suit la meme regle que le Comptoir : une facade qui doit
            // se presenter au bord de piste se tourne vers l'axe, donc +90 a
            // droite et -90 a gauche ; cap 0 pour ce qui doit regarder le kart
            // qui arrive — l'arcade qu'on prend de face, la gargouille qui
            // fixe, la croix d'une tombe — et pour les sarcophages, dont la
            // longueur court alors le long de la piste et montre son flanc.
            new double[][]{
                    // Dans la nef : les sarcophages contre un bas-cote, les
                    // torches contre l'autre. Ils etaient a trente metres de
                    // l'axe, soit derriere une paroi batie a dix-huit : on
                    // roulait quatre-vingt-dix metres dans une nef vide.
                    //
                    // Rentres a vingt metres ils restaient invisibles, pour une
                    // raison de champ et non de mur : une piece a vingt metres
                    // de cote ne rentre dans un champ de soixante-cinq degres
                    // qu'a vingt-huit metres devant soi, et la camera vise a
                    // quatorze. Ils sont donc poses trois metres au-dela du
                    // bitume, largeur locale par largeur locale.
                    {50, -13, 0, 1},
                    {90, -10, 0, 1},
                    {130, -9.5, 0, 1},
                    {50, 13, 0, 1},
                    {90, 10, 0, 1},
                    {130, 9.5, 0, 1},
                    {120, -9, 0, 1},
                    {120, 10, 0, 1},
                    // le crane etait pose sur l'axe, au milieu du bitume : les
                    // karts le traversaient. Il passe au bas-cote, et grossit —
                    // a quarante centimetres, un crane pose a la main ne se
                    // distinguait pas d'un gravat du semis.
                    {140, -10, 0, 2.5},
                    {260, 14, 90, 1},
                    {260, -14, -90, 1},
                    {285, 13, 90, 1},
                    {300, -13, -90, 1},
                    {275, 14, 0, 1},
                    // L'arcade est haute de quatre a six metres : sur l'axe,
                    // ses deux piedroits tombaient dans une chaussee de 5,8 m
                    // de demi-largeur — le point le plus etroit du tour — et
                    // l'IA traversait la pierre. Elle se dresse donc a cote,
                    // hors du couloir, qui fait ici 10,6 m.
                    {390, -15, 0, 1.3},
                    {410, -10, 0, 1},
                    {410, 10, 0, 1},
                    {520, -17, 0, 1},
                    {545, -16, 0, 1},
                    {570, -15, 0, 1},
                    {532, 16, 0, 1},
                    {560, 15, 0, 1},
                    {585, 14, 0, 1},
                    {660, -10, 0, 1},
                    {660, 10, 0, 1},
                    // Les gravats etaient a l'echelle 3,5, heritee du
                    // {@code rocher} qu'ils ont remplace : deux dalles de dix
                    // metres de large et sept de haut, dressees a trois metres
                    // six du bitume, qui barraient tout le champ au passage —
                    // un mur, pas un eboulis, et sans collision on le
                    // traversait. A 1,6 le plus gros bloc fait deux metres
                    // trente, soit la longueur d'un kart : de quoi se voir sans
                    // fermer la salle.
                    {690, -11, 0, 1.6},
                    {800, 14, 90, 1},
                    {820, -14, 0, 1},
                    {845, 13, 90, 1},
                    {860, -12, 0, 1},
                    {930, -10, 0, 1},
                    {930, 10, 0, 1},
                    {1060, -16, -90, 1},
                    {1090, 15, 0, 1},
                    {1190, -11, 0, 1},
                    {1190, 12, 0, 1},
                    {1230, 13, 0, 2.5},
                    {60, 0, 0, 1},
                    {200, 12, 0, 1},
                    {240, -14, 0, 1},
                    {420, 10, 0, 1},
                    {700, -11, -90, 1},
                    {740, 12, 0, 1},
                    {980, -11, -90, 1},
                    {1100, 15, 0, 1},
                    {1240, -14, 0, 1},
            },
            new String[]{
                    "sarcophage",
                    "sarcophage",
                    "sarcophage",
                    "torche",
                    "torche",
                    "torche",
                    "colonne",
                    "colonne",
                    "crane",
                    "trone",
                    "armoire",
                    "coffre",
                    "coffre",
                    "chandelier",
                    "arcade",
                    "gargouille",
                    "gargouille",
                    "tombe",
                    "tombe",
                    "tombe",
                    "chaudron",
                    "champignon",
                    "champignon",
                    "torche",
                    "torche",
                    "gravats",
                    "armoire",
                    "sarcophage",
                    "coffre",
                    "chandelier",
                    "colonne",
                    "colonne",
                    "trone",
                    "gargouille",
                    "torche",
                    "tombe",
                    "crane",
                    "nef",
                    "gargouille",
                    "colonne",
                    "chaudron",
                    "armoire",
                    "tombe",
                    "trone",
                    "sarcophage",
                    "torche",
            });
}
