package org.tuxkart.render;

import javafx.scene.paint.Color;
import org.tuxkart.track.Ouvrage;
import org.tuxkart.track.Theme;

import java.util.Random;

/**
 * La geometrie des grands volumes traverses par la piste.
 *
 * <p>Le decor ordinaire se contourne : un arbre, un tonneau, une pile de
 * livres. Ces ouvrages-la se <b>traversent</b> — la piste passe dessous, entre
 * leurs murs, et le joueur roule quelques secondes dans une penombre dont il ne
 * voit plus les bords. C'est le seul endroit du jeu ou le decor cesse d'etre un
 * paysage pour devenir un volume.
 *
 * <p>Quatre regles les gouvernent, et elles decoulent toutes du moteur :
 *
 * <ul>
 *   <li><b>Rien ne barre le passage.</b> Aucun ouvrage n'a de collision — la
 *       physique ne connait que le ruban et ses bords. La largeur libre est
 *       donc une affaire de mise en scene : elle suit le couloir de piste
 *       ({@link Ouvrage#profil}), et un test verifie que la piste passe
 *       bien dedans.</li>
 *   <li><b>Le mur suit le couloir, travee par travee.</b> Une demi-largeur
 *       constante prise sur le point le plus large de l'emprise donnait des
 *       volumes hors d'echelle : la nef du donjon, batie a la largeur de la
 *       grille de depart qu'elle attrapait par un bout, faisait soixante-cinq
 *       metres de large pour quatre-vingt-douze de long, et sa voute culminait
 *       a quarante-sept metres — au-dessus du champ de la camera sur toute sa
 *       longueur. Chaque batisseur interroge donc son profil a chaque travee,
 *       et les longues parois sont debitees en panneaux devies.</li>
 *   <li><b>Chaque paroi est une plaque, jamais un bloc.</b> Le decor fusionne
 *       est dessine en {@code CullFace.BACK} : l'interieur d'un cube n'existe
 *       pas. Un mur est donc une boite mince, dont la face interieure est bien
 *       une face avant — c'est ce qui permet d'etre dedans et de le voir.</li>
 *   <li><b>Tout est bati autour du point d'ancrage au sol.</b> L'altitude
 *       fournie est celle du point le plus bas du ruban sous l'ouvrage : une
 *       grange posee sur une bosse ne doit pas avoir les pieds dans la
 *       chaussee.</li>
 * </ul>
 */
final class OuvrageNode {

    private OuvrageNode() {
    }

    /**
     * Batit un ouvrage.
     *
     * @param profil demi-largeur libre imposee par le couloir de piste, en
     *               fonction de la distance a l'ancrage
     * @param div    finesse des spheres, suivant le niveau de detail
     */
    static void batir(DecorBatch b, Ouvrage ouvrage, Random rnd, Theme theme, int div,
                      double x, double y, double z, double yaw, Ouvrage.Profil profil) {
        switch (ouvrage) {
            case GRANGE -> grange(b, rnd, x, y, z, yaw, profil);
            case TUNNEL -> tunnel(b, rnd, theme, x, y, z, yaw, profil);
            case IGLOU -> iglou(b, rnd, x, y, z, yaw, profil);
            case TEMPLE -> temple(b, rnd, x, y, z, yaw, profil);
            case NEF -> nef(b, rnd, x, y, z, yaw, profil);
            case TONNEAU -> tonneauGeant(b, rnd, x, y, z, yaw, profil);
            case BOITE -> boiteGeante(b, rnd, div, x, y, z, yaw, profil);
            case POCHE -> pocheGeante(b, rnd, div, x, y, z, yaw, profil);
        }
    }

    // --------------------------------------------------------- les outils

    /**
     * Point du repere de l'ouvrage : {@code along} le long de la piste (positif
     * vers l'avant), {@code cote} en travers.
     *
     * <p>C'est le meme repere que celui des pieces de decor, a ceci pres que
     * l'axe longitudinal est pris dans le sens de la marche : un ouvrage se
     * decrit par travees, de l'entree vers la sortie.
     */
    private static double[] poste(double x, double z, double yaw, double along, double cote) {
        double a = Math.toRadians(yaw);
        return new double[]{x + Math.cos(a) * cote + Math.sin(a) * along,
                z - Math.sin(a) * cote + Math.cos(a) * along};
    }

    /** Une boite posee dans le repere de l'ouvrage. {@code haut} est l'altitude de son centre. */
    private static void bloc(DecorBatch b, double x, double y, double z, double yaw,
                             double along, double cote, double haut,
                             double largeur, double hauteur, double longueur,
                             double inclinaison, Color couleur) {
        bloc(b, x, y, z, yaw, along, cote, haut, largeur, hauteur, longueur,
                inclinaison, 0, couleur);
    }

    /**
     * La meme boite, deviee en plan de {@code deviation} degres.
     *
     * <p>Une paroi qui suit le couloir change d'ecart sur sa longueur. Debitee
     * en panneaux tous paralleles a l'axe, elle ferait un escalier de
     * decrochements, visible de loin parce que le mur est justement ce qu'on
     * longe. Chaque panneau est donc pivote de la pente locale du couloir, et
     * les joints se referment.
     */
    private static void bloc(DecorBatch b, double x, double y, double z, double yaw,
                             double along, double cote, double haut,
                             double largeur, double hauteur, double longueur,
                             double inclinaison, double deviation, Color couleur) {
        double[] p = poste(x, z, yaw, along, cote);
        b.add(Meshes.sharedBox(), largeur, hauteur, longueur, inclinaison, yaw + deviation,
                p[0], Meshes.jy(y + haut), p[1], couleur);
    }

    /** Un fut vertical (colonne, pilier rond, poteau). */
    private static void fut(DecorBatch b, double x, double y, double z, double yaw,
                            double along, double cote, double base,
                            double rayon, double hauteur, int cotes, Color couleur) {
        double[] p = poste(x, z, yaw, along, cote);
        b.add(Meshes.sharedCylinder(cotes), rayon, hauteur, rayon, 0, yaw,
                p[0], Meshes.jy(y + base + hauteur / 2), p[1], couleur);
    }

    /**
     * Longueur nominale d'un panneau de paroi, en metres.
     *
     * <p>Huit metres est le compromis mesure : sous la nef, le couloir se
     * resserre de onze centimetres par metre, donc un panneau plus long
     * s'ecarterait du couloir de plus d'un demi-metre en son milieu, et un
     * panneau plus court multiplierait les facettes d'un decor deja fusionne
     * par troncon.
     */
    private static final double PANNEAU = 8.0;

    /** Les bornes des panneaux couvrant {@code centre} plus ou moins {@code demiLongueur}. */
    private static double[] panneaux(double centre, double demiLongueur) {
        return decoupe(centre, demiLongueur, PANNEAU);
    }

    /**
     * La meme decoupe, a un pas impose.
     *
     * <p>Une paroi plane se contente de huit metres ; une surface courbe non,
     * car son rayon varie d'un bout du panneau a l'autre sans qu'aucune
     * deviation en plan ne le rattrape.
     */
    private static double[] decoupe(double centre, double demiLongueur, double pas) {
        int n = Math.max(1, (int) Math.round(2 * demiLongueur / pas));
        double[] bornes = new double[n + 1];
        for (int i = 0; i <= n; i++) {
            bornes[i] = centre - demiLongueur + 2 * demiLongueur * i / n;
        }
        return bornes;
    }

    /**
     * Deviation en plan d'un panneau, en degres, d'apres l'ecart de ses deux
     * bouts. {@code cote} vaut -1 ou +1 : a gauche, un couloir qui s'ouvre
     * emmene le mur dans l'autre sens.
     */
    private static double deviation(int cote, double r0, double r1, double longueur) {
        return Math.toDegrees(Math.atan2(cote * (r1 - r0), longueur));
    }

    /**
     * Une paroi laterale qui suit le couloir : une file de panneaux minces,
     * chacun a l'ecart local du mur et devie de la pente locale.
     *
     * @param cote  -1 ou +1
     * @param ecart ecart algebrique au mur, positif vers l'exterieur
     */
    private static void paroi(DecorBatch b, Ouvrage.Profil profil,
                              double x, double y, double z, double yaw,
                              int cote, double ecart, double centre, double demiLongueur,
                              double haut, double epaisseur, double hauteur, Color couleur) {
        double[] bornes = panneaux(centre, demiLongueur);
        for (int i = 0; i < bornes.length - 1; i++) {
            double a0 = bornes[i], a1 = bornes[i + 1];
            double r0 = profil.demi(a0) + ecart, r1 = profil.demi(a1) + ecart;
            bloc(b, x, y, z, yaw, (a0 + a1) / 2, cote * (r0 + r1) / 2, haut,
                    epaisseur, hauteur, Math.hypot(a1 - a0, r1 - r0) * 1.02,
                    0, deviation(cote, r0, r1, a1 - a0), couleur);
        }
    }

    /**
     * Une voute en berceau : deux piedroits et un demi-cercle de claveaux.
     *
     * <p>C'est la piece maitresse de la moitie des ouvrages — tunnel, iglou,
     * nef, poche — parce que c'est la seule forme qui laisse passer une piste
     * large sans mur au milieu. Chaque claveau est une boite inclinee de son
     * angle : tangente a l'arc, elle presente sa face interieure au joueur.
     *
     * <p>Le rayon change d'un anneau au suivant, puisqu'il suit le couloir :
     * une voute posee sur une portion qui se resserre est un berceau qui se
     * resserre. Les claveaux d'un meme anneau sont devies de la pente laterale
     * du couloir a leur hauteur, ce qui ferme les joints le long des piedroits,
     * la ou l'oeil les suit ; au sommet, ou la pente est verticale et non
     * laterale, les anneaux se recouvrent comme des assises de pierre.
     *
     * @param decale decale un anneau sur deux d'un demi-claveau, comme un
     *               appareil de blocs — sans quoi les joints forment des
     *               lignes continues du sol au sommet, ce qu'aucun macon ne
     *               fait et ce que l'oeil remarque aussitot
     */
    private static void voute(DecorBatch b, Random rnd,
                              double x, double y, double z, double yaw,
                              double demiLongueur, int anneaux,
                              Ouvrage.Profil profil, double piedDroit, double epaisseur,
                              int claveaux, Color couleur, double variation,
                              boolean decale) {
        voute(b, rnd, x, y, z, yaw, demiLongueur, anneaux, profil, piedDroit, epaisseur,
                claveaux, couleur, variation, decale, 1);
    }

    /**
     * La meme voute, mais surbaissee : la fleche vaut {@code surbaissement}
     * fois la demi-portee au lieu de lui etre egale.
     *
     * <p>Une voute en plein cintre a sa clef aussi haut que le mur est loin, et
     * c'est ce qui l'a rendue inutilisable pour le dome de la Banquise. Le
     * couloir y fait 18,76 m de demi-largeur — le plus large des huit ouvrages
     * du jeu — et la paroi se tient six metres au-dela : la clef culminait a
     * 27,3 m. Or la camera vise quatorze metres devant et couvre 60 a 69
     * degres a l'horizontale, soit une demi-ouverture verticale d'environ 22
     * degres : une clef a 27,3 m ne rentre dans le cadre qu'a soixante metres
     * devant. Le dome ne faisant que soixante-quatre metres de long, sa voute
     * n'etait dans le champ qu'a l'instant precis de l'entree ; passe le
     * quart, il ne restait que deux parois laterales et du ciel devant. Mesure
     * pour comparaison : la clef du tunnel de la Colline est a 22,5 m pour un
     * ouvrage de soixante-douze metres, celle de la nef a 20,7 m pour
     * quatre-vingt-douze.
     *
     * <p>A 0,62 la clef tombe a 17,9 m et rentre dans le cadre des qu'il reste
     * trente-cinq metres d'ouvrage devant soi, soit sur les trente premiers
     * metres du passage. C'est aussi la forme juste : un dome de neige est
     * large et bas, pas une nef.
     *
     * <p>Le claveau n'est plus tangent a un cercle mais a une ellipse : son
     * inclinaison suit la normale de l'ellipse, et sa corde est mesuree entre
     * les deux points qui l'encadrent. Les deux formules redonnent exactement
     * les anciennes quand {@code surbaissement} vaut 1.
     */
    private static void voute(DecorBatch b, Random rnd,
                              double x, double y, double z, double yaw,
                              double demiLongueur, int anneaux,
                              Ouvrage.Profil profil, double piedDroit, double epaisseur,
                              int claveaux, Color couleur, double variation,
                              boolean decale, double surbaissement) {
        double longueur = 2 * demiLongueur;
        double pas = longueur / anneaux;
        for (int r = 0; r < anneaux; r++) {
            double along = -demiLongueur + pas * (r + 0.5);
            double rayon = profil.demi(along);
            double avant = profil.demi(along - pas / 2), apres = profil.demi(along + pas / 2);
            // demi-pas angulaire entre deux claveaux
            double demiPas = Math.PI / (2 * claveaux);
            double biais = decale && (r % 2 == 1) ? 0.5 : 0;
            for (int k = 0; k < claveaux; k++) {
                double theta = Math.toRadians(-90 + (k + 0.5 + biais) * 180.0 / claveaux);
                Color teinte = couleur.deriveColor(0, 1,
                        1 - variation / 2 + rnd.nextDouble() * variation, 1);
                // Corde entre les deux points de l'ellipse qui encadrent le
                // claveau. Elle depend de theta des que la voute est
                // surbaissee, et redonne exactement 2 R sin(demi-pas) — la
                // formule d'avant — quand elle ne l'est pas.
                double corde = 2 * Math.sin(demiPas) * Math.hypot(rayon * Math.cos(theta),
                        surbaissement * rayon * Math.sin(theta)) * 1.08;
                // normale de l'ellipse : elle vaut theta quand la voute est en
                // plein cintre, et se redresse a mesure qu'on l'ecrase
                double pente = Math.atan2(surbaissement * Math.sin(theta), Math.cos(theta));
                bloc(b, x, y, z, yaw, along, rayon * Math.sin(theta),
                        piedDroit + surbaissement * rayon * Math.cos(theta),
                        corde, epaisseur, pas * 1.03, Math.toDegrees(pente),
                        deviation(1, avant * Math.sin(theta), apres * Math.sin(theta), pas),
                        teinte);
            }
            if (piedDroit > 0.5) {
                for (int cote = -1; cote <= 1; cote += 2) {
                    bloc(b, x, y, z, yaw, along, cote * rayon, piedDroit / 2,
                            epaisseur, piedDroit, pas * 1.03, 0,
                            deviation(cote, avant, apres, pas),
                            couleur.deriveColor(0, 1,
                                    1 - variation / 2 + rnd.nextDouble() * variation, 1));
                }
            }
        }
    }

    /** Anneau de claveaux isole : portail, arc doubleau, cercle de fer. */
    private static void anneau(DecorBatch b, double x, double y, double z, double yaw,
                               double along, double rayon, double piedDroit,
                               double epaisseur, double largeur, int claveaux,
                               double arcDegres, Color couleur) {
        anneau(b, x, y, z, yaw, along, rayon, piedDroit, epaisseur, largeur, claveaux,
                arcDegres, couleur, 1);
    }

    /** Le meme anneau, surbaisse du meme facteur que sa voute. */
    private static void anneau(DecorBatch b, double x, double y, double z, double yaw,
                               double along, double rayon, double piedDroit,
                               double epaisseur, double largeur, int claveaux,
                               double arcDegres, Color couleur, double surbaissement) {
        double demiPas = Math.toRadians(arcDegres) / (2 * claveaux);
        for (int k = 0; k < claveaux; k++) {
            double theta = Math.toRadians(-arcDegres / 2 + (k + 0.5) * arcDegres / claveaux);
            double corde = 2 * Math.sin(demiPas) * Math.hypot(rayon * Math.cos(theta),
                    surbaissement * rayon * Math.sin(theta)) * 1.1;
            double pente = Math.atan2(surbaissement * Math.sin(theta), Math.cos(theta));
            bloc(b, x, y, z, yaw, along, rayon * Math.sin(theta),
                    piedDroit + surbaissement * rayon * Math.cos(theta),
                    corde, epaisseur, largeur, Math.toDegrees(pente), couleur);
        }
    }

    // ------------------------------------------------------------ la grange

    private static final Color BOIS_GRANGE = Color.web("#8c4a32");
    private static final Color BOIS_SOMBRE = Color.web("#5a3220");
    private static final Color PAILLE = Color.web("#d8b658");

    /**
     * Grange de ferme : on entre par la grande porte, on longe le fenil, on
     * ressort par l'autre bout. Charpente apparente, bardage a claire-voie et
     * bottes de paille jusqu'au faitage.
     */
    private static void grange(DecorBatch b, Random rnd,
                               double x, double y, double z, double yaw,
                               Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        double mur = 16, faite = 11;

        for (int cote = -1; cote <= 1; cote += 2) {
            // bardage : une paroi mince, pas un bloc — on la voit des deux cotes
            paroi(b, profil, x, y, z, yaw, cote, 0, 0, L, mur / 2, 1.2, mur, BOIS_GRANGE);
            // lisses horizontales a l'interieur, qui donnent l'echelle du mur
            for (int i = 1; i <= 4; i++) {
                paroi(b, profil, x, y, z, yaw, cote, -0.9, 0, L * 0.98,
                        i * mur / 5.0, 0.5, 0.7, BOIS_SOMBRE);
            }
            // Fondation de pierre. Elle descend deux metres quarante sous
            // l'assise, et c'est la seule raison d'etre de sa profondeur : la
            // grange est batie d'un bloc sur le point le plus bas du ruban,
            // tandis que le terrain suit le devers — du cote bas il pend
            // jusqu'a deux metres quinze plus bas, et le bardage flottait
            // au-dessus du sol. Du dedans, le tablier bouche desormais le jour ;
            // du dehors, c'est cette fondation. La ou le terrain remonte, elle
            // est simplement enterree, ce qu'une fondation ne demande pas mieux.
            paroi(b, profil, x, y, z, yaw, cote, 0, 0, L, -0.3, 2.0, 4.2,
                    Color.web("#6f6a63"));
        }

        // pignons : deux trumeaux, un linteau, et le triangle au-dessus
        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * L;
            double R = profil.demi(along);
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, along, cote * (R - 2.2), mur / 2,
                        4.4, mur, 1.4, 0, BOIS_GRANGE.deriveColor(0, 1, 0.92, 1));
            }
            bloc(b, x, y, z, yaw, along, 0, mur + 1.1, 2 * R, 2.2, 1.4, 0, BOIS_SOMBRE);
            int etages = 4;
            for (int i = 0; i < etages; i++) {
                double t = (i + 0.5) / etages;
                bloc(b, x, y, z, yaw, along, 0, mur + 2.2 + t * faite,
                        2 * R * (1 - t), faite / etages, 1.2, 0,
                        BOIS_GRANGE.deriveColor(0, 1, 0.86 + i * 0.06, 1));
            }
        }

        // charpente : une ferme tous les huit metres, entrait et arbaletriers
        for (double along = -L + 4; along <= L - 4; along += 8) {
            double R = profil.demi(along);
            double pente = Math.toDegrees(Math.atan2(faite, R));
            double rampant = Math.hypot(R, faite);
            bloc(b, x, y, z, yaw, along, 0, mur, 2 * R, 0.8, 0.8, 0, BOIS_SOMBRE);
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, along, cote * R / 2, mur + faite / 2,
                        rampant, 0.7, 0.7, cote * pente, BOIS_SOMBRE);
            }
        }

        // toit : deux pans, poses par-dessus la charpente. Ils suivent le mur,
        // donc leur pente elle-meme change d'un panneau au suivant.
        double[] bornes = panneaux(0, L + 1.5);
        for (int cote = -1; cote <= 1; cote += 2) {
            for (int i = 0; i < bornes.length - 1; i++) {
                double a0 = bornes[i], a1 = bornes[i + 1];
                double r0 = profil.demi(a0) + 1, r1 = profil.demi(a1) + 1;
                double R = (r0 + r1) / 2;
                bloc(b, x, y, z, yaw, (a0 + a1) / 2, cote * R / 2, mur + faite / 2,
                        Math.hypot(R, faite), 0.7,
                        Math.hypot(a1 - a0, (r1 - r0) / 2) * 1.03,
                        cote * Math.toDegrees(Math.atan2(faite, R)),
                        deviation(cote, r0 / 2, r1 / 2, a1 - a0),
                        Color.web("#4d4a46"));
            }
        }
        bloc(b, x, y, z, yaw, 0, 0, mur + faite + 0.5, 1.8, 1.0, 2 * L + 3, 0,
                Color.web("#3d3a37"));

        // fenil d'un cote, et la paille qui va avec
        paroi(b, profil, x, y, z, yaw, 1, -4, 0, L / 2, 7.0, 8, 0.7,
                BOIS_GRANGE.deriveColor(0, 1, 1.1, 1));
        for (double along = -L / 2; along <= L / 2; along += 7) {
            fut(b, x, y, z, yaw, along, profil.demi(along) - 4, 0, 0.5, 7.0, 6, BOIS_SOMBRE);
        }
        for (int i = 0; i < 14; i++) {
            double along = -L + rnd.nextDouble() * 2 * L;
            boolean haut = rnd.nextBoolean();
            double R = profil.demi(along);
            double fenil = R - 4;
            double cote = haut ? fenil + rnd.nextDouble() * 2 - 1 : -(R - 3 - rnd.nextDouble() * 2);
            double base = haut ? 7.4 : 0;
            for (int etage = 0; etage < 1 + rnd.nextInt(2); etage++) {
                bloc(b, x, y, z, yaw, along, cote, base + 1.1 + etage * 2.2,
                        2.6, 2.2, 3.4, 0,
                        PAILLE.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1));
            }
        }
        // brins de paille au sol, sur le passage
        for (int i = 0; i < 10; i++) {
            double along = -L + rnd.nextDouble() * 2 * L;
            bloc(b, x, y, z, yaw, along,
                    (rnd.nextDouble() * 2 - 1) * (profil.demi(along) - 1), 0.06,
                    0.6 + rnd.nextDouble(), 0.12, 0.5, 0,
                    PAILLE.deriveColor(0, 1, 1.05, 1));
        }
    }

    // ------------------------------------------------------------- le tunnel

    /**
     * Tunnel perce : une voute continue, un portail plus epais a chaque bout.
     * Sous le volcan, la roche garde des fissures de braise au ras du sol.
     */
    private static void tunnel(DecorBatch b, Random rnd, Theme theme,
                               double x, double y, double z, double yaw,
                               Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        boolean volcan = theme == Theme.VOLCANO;
        Color roche = volcan ? Color.web("#3a2f2b")
                : theme == Theme.DESERT ? Color.web("#b08a58") : Color.web("#6e6a64");

        voute(b, rnd, x, y, z, yaw, L, (int) Math.round(2 * L / 5), profil, 6.5, 2.2, 11,
                roche, 0.30, true);

        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * (L + 0.6);
            double R = profil.demi(along);
            anneau(b, x, y, z, yaw, along, R + 1.6, 6.5, 2.6, 2.4, 13, 180,
                    roche.deriveColor(0, 1, 1.12, 1));
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, along, cote * (R + 1.6), 3.2, 2.6, 6.5, 2.4, 0,
                        roche.deriveColor(0, 1, 1.12, 1));
                // eboulis au pied du portail
                for (int i = 0; i < 4; i++) {
                    double r = 0.7 + rnd.nextDouble() * 1.3;
                    bloc(b, x, y, z, yaw, along + bout * (1 + rnd.nextDouble() * 4),
                            cote * (R + 1 + rnd.nextDouble() * 3), r * 0.45,
                            r * 2, r, r * 1.6, rnd.nextDouble() * 20 - 10,
                            roche.deriveColor(0, 1, 0.8 + rnd.nextDouble() * 0.4, 1));
                }
            }
        }

        if (volcan) {
            for (int i = 0; i < 16; i++) {
                double along = -L + rnd.nextDouble() * 2 * L;
                int cote = rnd.nextBoolean() ? 1 : -1;
                bloc(b, x, y, z, yaw, along,
                        cote * (profil.demi(along) - 0.6 - rnd.nextDouble()), 0.1,
                        0.5, 0.2, 2 + rnd.nextDouble() * 4, 0,
                        Color.web("#e2622a").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.4, 1));
            }
            // stalactites de basalte
            for (int i = 0; i < 12; i++) {
                double h = 1.5 + rnd.nextDouble() * 2.5;
                double along = -L + rnd.nextDouble() * 2 * L;
                double R = profil.demi(along);
                bloc(b, x, y, z, yaw, along, (rnd.nextDouble() * 2 - 1) * R * 0.7,
                        6.5 + R - h / 2,
                        0.7, h, 0.7, rnd.nextDouble() * 16 - 8, roche.deriveColor(0, 1, 0.8, 1));
            }
        }
    }

    // -------------------------------------------------------------- l'iglou

    /**
     * Surbaissement du dome : la clef est a 0,62 fois la demi-portee.
     *
     * <p>Un dome de neige est large et bas ; le plein cintre en faisait une
     * nef de 27,3 m de clef, au-dessus du champ de la camera sur toute la
     * longueur de l'ouvrage. Voir {@code voute(..., surbaissement)}.
     */
    private static final double IGLOU_SURBAISSEMENT = 0.62;

    /** Dome de glace : la meme voute, mais batie en blocs decales et translucides de couleur. */
    private static void iglou(DecorBatch b, Random rnd,
                              double x, double y, double z, double yaw,
                              Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        Color glace = Color.web("#cfe4f2");

        voute(b, rnd, x, y, z, yaw, L, (int) Math.round(2 * L / 4.5), profil, 2.5, 2.4, 13,
                glace, 0.22, true, IGLOU_SURBAISSEMENT);

        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * (L + 1.0);
            double R = profil.demi(along);
            anneau(b, x, y, z, yaw, along, R + 1.8, 2.5, 3.0, 3.0, 15, 180,
                    glace.deriveColor(0, 1, 0.94, 1), IGLOU_SURBAISSEMENT);
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, along, cote * (R + 1.8), 1.25, 3.0, 2.5, 3.0, 0,
                        glace.deriveColor(0, 1, 0.94, 1));
            }
            // Congeres accumulees contre l'entree.
            //
            // Leur ecart lateral etait tire dans (rnd x 2 - 1) x (R + 4),
            // c'est-a-dire n'importe ou en travers du portail : six blocs de
            // neige de douze metres de large barraient les deux entrees du
            // dome, et l'un d'eux masquait completement le kart du joueur sur
            // vingt-cinq metres. Le decor n'ayant pas de collision, on le
            // traversait sans rien heurter et sans rien voir.
            //
            // Une congere s'accumule contre un obstacle, jamais au milieu d'une
            // porte : elle est donc posee hors du couloir praticable, du bon
            // cote, et son demi-flanc est compte dans l'ecart pour que le
            // flanc lui-meme reste dehors.
            //
            // Le tirage lateral est conserve tel quel — un seul appel — parce
            // qu'il porte a la fois le cote et l'eloignement : le semis du
            // circuit est cale sur une graine fixe, et lui prendre un tirage de
            // plus decalerait tout le decor de la Banquise.
            double libre = R - Ouvrage.IGLOU.marge;
            for (int i = 0; i < 6; i++) {
                double r = 1.5 + rnd.nextDouble() * 2.5;
                double ecart = rnd.nextDouble() * 2 - 1;
                double cote = ecart < 0 ? -1 : 1;
                bloc(b, x, y, z, yaw, along + bout * rnd.nextDouble() * 6,
                        cote * (libre + 1.5 + r * 1.5 + Math.abs(ecart) * 3), r * 0.3,
                        r * 3, r * 0.6, r * 2, 0,
                        Color.web("#eef6fb").deriveColor(0, 1, 0.96 + rnd.nextDouble() * 0.08, 1));
            }
        }

        // chandelles de glace au plafond, et le jour bleu qui passe entre les blocs
        for (int i = 0; i < 18; i++) {
            double h = 1.2 + rnd.nextDouble() * 2.2;
            double along = -L + rnd.nextDouble() * 2 * L;
            double R = profil.demi(along);
            double cote = (rnd.nextDouble() * 2 - 1) * R * 0.75;
            double haut = 2.5 + IGLOU_SURBAISSEMENT * Math.sqrt(Math.max(0, R * R - cote * cote))
                    - h / 2;
            bloc(b, x, y, z, yaw, along, cote, haut,
                    0.45, h, 0.45, 0, Color.web("#bcdcf0").deriveColor(0, 1, 1.05, 1));
        }
    }

    // -------------------------------------------------------------- le temple

    /**
     * Salle hypostyle : deux rangees de colonnes, une architrave, et des dalles
     * de couverture posees une travee sur deux — le jour tombe en bandes sur la
     * piste, ce qui donne au passage sa vitesse.
     */
    private static void temple(DecorBatch b, Random rnd,
                               double x, double y, double z, double yaw,
                               Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        double h = 19;
        Color gres = Color.web("#c39a5f");
        Color bande = Color.web("#a8763c");

        double travee = 11;
        for (double along = -L + travee / 2; along <= L; along += travee) {
            double R = profil.demi(along);
            for (int cote = -1; cote <= 1; cote += 2) {
                double c = cote * (R - 3.2);
                fut(b, x, y, z, yaw, along, c, 0, 3.4, 1.4, 12,
                        gres.deriveColor(0, 1, 0.88, 1));
                fut(b, x, y, z, yaw, along, c, 1.4, 2.9, h - 3.4, 12,
                        gres.deriveColor(0, 1, 0.92 + rnd.nextDouble() * 0.16, 1));
                // trois bandeaux graves sur le fut
                for (int i = 1; i <= 3; i++) {
                    fut(b, x, y, z, yaw, along, c, 1.4 + i * (h - 5) / 4.0, 3.05, 0.7, 12, bande);
                }
                // chapiteau en papyrus
                fut(b, x, y, z, yaw, along, c, h - 2.0, 3.9, 2.0, 12,
                        gres.deriveColor(0, 1, 1.08, 1));
                bloc(b, x, y, z, yaw, along, c, h + 0.6, 8.4, 1.2, 8.4, 0,
                        gres.deriveColor(0, 1, 1.12, 1));
            }
        }

        // Mur-bahut entre les futs. Une colonnade nue est une claire-voie : de
        // la piste on voyait le desert au travers du temple — un cactus du
        // semis apparaissait entre deux colonnes, et la salle cessait d'etre un
        // dedans. C'est le remede que les Egyptiens ont donne au meme probleme,
        // un mur d'appui qui ferme l'entrecolonnement jusqu'au tiers du fut :
        // il coupe la vue au ras du sol et laisse le jour entrer par le haut.
        //
        // Six metres cinquante est mesure, pas choisi. L'oeil du joueur est a
        // trois metres vingt-cinq au-dessus du kart, le semis ne pousse pas en
        // deca de vingt-neuf metres six de l'axe (paroi plus degagement) et la
        // face interne du mur se dresse a vingt-deux metres un : a cette hauteur
        // un objet de sept metres pose au plus pres est encore masque, quelle
        // que soit la voie ou roule le kart. Au-dessus il reste treize metres
        // d'air libre jusqu'a l'architrave — la salle hypostyle garde son ciel,
        // qui fait la moitie de son caractere.
        double bahut = 6.5;
        for (int cote = -1; cote <= 1; cote += 2) {
            paroi(b, profil, x, y, z, yaw, cote, -3.2, 0, L, bahut / 2, 2.6, bahut,
                    gres.deriveColor(0, 1, 0.86, 1));
            // couronnement debordant : sans lui le mur s'arrete sur une arete
            // nue, et la pierre se lit comme une plaque posee entre les futs
            paroi(b, profil, x, y, z, yaw, cote, -3.2, 0, L, bahut + 0.3, 3.4, 0.6,
                    bande);
        }

        // architraves, puis les dalles a claire-voie
        for (int cote = -1; cote <= 1; cote += 2) {
            paroi(b, profil, x, y, z, yaw, cote, -3.2, 0, L, h + 2.6, 5.0, 2.8,
                    gres.deriveColor(0, 1, 1.04, 1));
        }
        for (double along = -L + travee / 2; along <= L; along += travee) {
            bloc(b, x, y, z, yaw, along, 0, h + 4.8, 2 * profil.demi(along) - 2, 1.6,
                    travee * 0.55, 0, gres.deriveColor(0, 1, 0.96, 1));
        }

        // pylones d'entree, tronconiques, avec leur corniche
        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * (L + 1.5);
            double R = profil.demi(along);
            for (int cote = -1; cote <= 1; cote += 2) {
                int etages = 5;
                for (int i = 0; i < etages; i++) {
                    double t = i / (double) etages;
                    bloc(b, x, y, z, yaw, along, cote * (R + 1.5), 2.4 + i * 4.6,
                            11 - t * 2.4, 4.6, 5.6 - t * 1.0, 0,
                            gres.deriveColor(0, 1, 0.9 + i * 0.04, 1));
                }
                bloc(b, x, y, z, yaw, along, cote * (R + 1.5), 25.0, 12.5, 2.0, 6.6, 0, bande);
                // stele gravee contre le pylone, du cote de la piste
                bloc(b, x, y, z, yaw, along, cote * (R - 3.6), 5.0, 0.6, 10, 3.6, 0,
                        bande.deriveColor(0, 1, 1.15, 1));
            }
        }
    }

    // ------------------------------------------------------------------ la nef

    private static final Color PIERRE_NEF = Color.web("#6d6a72");

    /**
     * Nef : deux files de piliers, une voute de pierre et ses arcs doubleaux.
     * C'est le plus long des ouvrages — on y roule cinq secondes sans revoir le
     * plafond du donjon. Les files de piliers ne sont pas paralleles : elles
     * suivent le couloir, qui se resserre de dix metres entre l'entree et la
     * sortie.
     */
    private static void nef(DecorBatch b, Random rnd,
                            double x, double y, double z, double yaw,
                            Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        double pilier = 15;

        voute(b, rnd, x, y, z, yaw, L, (int) Math.round(2 * L / 5.5), profil, pilier, 2.0, 11,
                PIERRE_NEF, 0.26, true);

        double travee = 11.5;
        for (double along = -L + travee / 2; along <= L; along += travee) {
            double R = profil.demi(along);
            // arc doubleau : un anneau plus epais, en pierre plus sombre
            anneau(b, x, y, z, yaw, along, R + 0.2, pilier, 2.6, 2.6, 11, 180,
                    PIERRE_NEF.deriveColor(0, 1, 0.82, 1));
            for (int cote = -1; cote <= 1; cote += 2) {
                double c = cote * (R + 1.2);
                bloc(b, x, y, z, yaw, along, c, pilier / 2, 3.4, pilier, 3.4, 0,
                        PIERRE_NEF.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.16, 1));
                // colonnettes engagees, cote piste
                for (int i = -1; i <= 1; i += 2) {
                    fut(b, x, y, z, yaw, along + i * 1.2,
                            cote * (profil.demi(along + i * 1.2) - 0.4), 0,
                            0.6, pilier, 8, PIERRE_NEF.deriveColor(0, 1, 1.1, 1));
                }
                bloc(b, x, y, z, yaw, along, c, pilier + 0.8, 4.6, 1.6, 4.6, 0,
                        PIERRE_NEF.deriveColor(0, 1, 1.14, 1));
            }
        }

        // bas-cotes : un mur perce d'ouvertures hautes, un peu en retrait
        for (int cote = -1; cote <= 1; cote += 2) {
            paroi(b, profil, x, y, z, yaw, cote, 3.4, 0, L, 4.5, 1.6, 9,
                    PIERRE_NEF.deriveColor(0, 1, 0.86, 1));
            for (double along = -L + travee / 2; along <= L; along += travee) {
                bloc(b, x, y, z, yaw, along, cote * (profil.demi(along) + 3.4), 14,
                        1.6, 10, travee * 0.45, 0,
                        PIERRE_NEF.deriveColor(0, 1, 0.86, 1));
            }
            paroi(b, profil, x, y, z, yaw, cote, 3.4, 0, L, 19.5, 1.6, 3,
                    PIERRE_NEF.deriveColor(0, 1, 0.9, 1));
        }

        // Torcheres accrochees aux piliers : la seule lumiere de la nef.
        //
        // C'etait un cube orange pose sur un cube sombre, a neuf metres de
        // haut : de la piste on lisait deux taches rectangulaires, pas un feu.
        // La console, la coupe et les deux cones de flamme donnent la meme
        // silhouette qu'une torche posee au sol — celle que le joueur vient de
        // longer trois fois avant d'entrer.
        for (double along = -L + travee; along <= L; along += travee * 2) {
            double c = profil.demi(along) - 1.0;
            for (int cote = -1; cote <= 1; cote += 2) {
                double[] p = poste(x, z, yaw, along, cote * c);
                b.add(Meshes.sharedBox(), 0.6, 0.5, 0.6, 0, yaw,
                        p[0], Meshes.jy(y + 7.4), p[1], Color.web("#3a2c1e"));
                b.add(Meshes.sharedCylinder(6), 0.22, 1.9, 0.22, 14 * cote, yaw + 90,
                        p[0], Meshes.jy(y + 8.5), p[1], Color.web("#3a2c1e"));
                double dx = Math.cos(Math.toRadians(yaw)) * cote * 0.45;
                double dz = -Math.sin(Math.toRadians(yaw)) * cote * 0.45;
                b.add(Meshes.sharedCone(9), 0.55, 0.8, 0.55, 180, yaw,
                        p[0] + dx, Meshes.jy(y + 9.5), p[1] + dz, Color.web("#4a3a28"));
                b.add(Meshes.sharedCone(9), 0.5, 1.5, 0.5, 0, yaw,
                        p[0] + dx, Meshes.jy(y + 10.2), p[1] + dz, Color.web("#f09028"));
                b.add(Meshes.sharedCone(9), 0.24, 0.9, 0.24, 0, yaw,
                        p[0] + dx, Meshes.jy(y + 10.5), p[1] + dz, Color.web("#ffd97a"));
            }
        }
    }

    // --------------------------------------------------------- le tonneau

    /**
     * Tonneau geant couche : on entre par un fond et on ressort par l'autre.
     * Le ventre bombe au milieu, comme un vrai tonneau.
     *
     * <p>Les douelles sont debitees en troncons de trois metres, et non plus en
     * trois gros : le rayon suit le couloir, qui se resserre de cinq metres sous
     * le comptoir, et le galbe du ventre s'y ajoute. Avec trois troncons, le
     * passage de l'un a l'autre ouvrait une fente de deux metres a la clef, par
     * laquelle on voyait le plancher du comptoir.
     */
    private static void tonneauGeant(DecorBatch b, Random rnd,
                                     double x, double y, double z, double yaw,
                                     Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        Color chene = Color.web("#8a5a30");
        Color fer = Color.web("#4a4741");

        int douelles = 30;
        double[] bornes = decoupe(0, L, 3.0);
        for (int k = 0; k < douelles; k++) {
            double theta = k * 2 * Math.PI / douelles;
            Color teinte = chene.deriveColor(0, 1, 0.86 + rnd.nextDouble() * 0.3, 1);
            for (int t = 0; t < bornes.length - 1; t++) {
                double a0 = bornes[t], a1 = bornes[t + 1];
                double milieu = (a0 + a1) / 2;
                double rayon = ventre(profil, milieu, L);
                // l'axe est remonte : le bas du tonneau est enterre, son
                // plancher est la piste elle-meme. Il suit le couloir et non le
                // galbe, sinon le tonneau piquerait du nez a chaque bout.
                double haut = profil.demi(milieu) * 0.55 + rayon * Math.cos(theta);
                if (haut < 0.15) continue;
                bloc(b, x, y, z, yaw, milieu, rayon * Math.sin(theta), haut,
                        2 * rayon * Math.sin(Math.PI / douelles) * 1.15, 1.2,
                        (a1 - a0) * 1.02, Math.toDegrees(theta),
                        deviation(1, ventre(profil, a0, L) * Math.sin(theta),
                                ventre(profil, a1, L) * Math.sin(theta), a1 - a0),
                        teinte);
            }
        }

        // cercles de fer : deux aux bouts, deux au ventre
        for (double along : new double[]{-L * 0.92, -L * 0.34, L * 0.34, L * 0.92}) {
            double rayon = ventre(profil, along, L) + 0.5;
            int segments = 26;
            for (int k = 0; k < segments; k++) {
                double theta = k * 2 * Math.PI / segments;
                double haut = profil.demi(along) * 0.55 + rayon * Math.cos(theta);
                if (haut < 0.1) continue;
                bloc(b, x, y, z, yaw, along, rayon * Math.sin(theta), haut,
                        2 * rayon * Math.sin(Math.PI / segments) * 1.2, 0.7, 2.2,
                        Math.toDegrees(theta), fer);
            }
        }

        // bonde et robinet, sur le flanc, cote piste
        double milieuR = profil.demi(0);
        bloc(b, x, y, z, yaw, 0, milieuR * 0.95, milieuR * 0.55 + milieuR * 0.55,
                1.6, 1.6, 1.6, 0, fer);
        double robinetR = profil.demi(-L * 0.6);
        bloc(b, x, y, z, yaw, -L * 0.6, robinetR * 0.9, robinetR * 0.55 * 0.6,
                1.2, 1.2, 3.2, 0, Color.web("#c9a227"));

        // Fond de tonneau : une couronne de douves, l'ouverture restant libre.
        // Elle se cale sur le rayon de la bouche et non sur la demi-largeur, qui
        // la posait trois metres en dehors du bord — une collerette flottant
        // autour de l'entree —, et son arc descend jusqu'au plancher : a deux
        // cents degres fixes elle s'arretait huit metres au-dessus du sol, ses
        // deux bouts coupes net en plein air.
        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * L * 1.02;
            double rayon = ventre(profil, bout * L, L) + 1.0;
            double axe = profil.demi(along) * 0.55;
            double arc = Math.min(300, 2 * Math.toDegrees(
                    Math.acos(Math.max(-1, Math.min(1, -axe / rayon)))));
            anneau(b, x, y, z, yaw, along, rayon, axe, 1.6, 1.6,
                    (int) Math.round(15 * arc / 200), arc,
                    chene.deriveColor(0, 1, 1.1, 1));
        }
    }

    /** Rayon du tonneau : la demi-largeur du couloir, galbee par le ventre. */
    private static double ventre(Ouvrage.Profil profil, double along, double demiLongueur) {
        return profil.demi(along) * (0.88 + 0.12 * Math.cos(Math.PI * along / demiLongueur));
    }

    // ----------------------------------------------------------- la boite

    /**
     * Boite en carton renversee sur le tapis, rabats ouverts aux deux bouts.
     * Dedans il fait sombre, ca sent le carton, et le papier d'emballage traine
     * contre les parois.
     */
    private static void boiteGeante(DecorBatch b, Random rnd, int div,
                                    double x, double y, double z, double yaw,
                                    Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        Color carton = Color.web("#c39355");
        Color pli = Color.web("#a97a41");

        double[] bornes = panneaux(0, L);
        for (int i = 0; i < bornes.length - 1; i++) {
            double a0 = bornes[i], a1 = bornes[i + 1];
            double r0 = profil.demi(a0), r1 = profil.demi(a1);
            double R = (r0 + r1) / 2, h = couvercle(R);
            double lg = Math.hypot(a1 - a0, r1 - r0) * 1.02;
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, (a0 + a1) / 2, cote * R, h / 2, 1.6, h, lg,
                        0, deviation(cote, r0, r1, a1 - a0), carton);
            }
            bloc(b, x, y, z, yaw, (a0 + a1) / 2, 0, h, 2 * R + 3.2, 1.6, (a1 - a0) * 1.02, 0,
                    carton.deriveColor(0, 1, 1.06, 1));
            // ruban adhesif sur l'arete du dessus
            bloc(b, x, y, z, yaw, (a0 + a1) / 2, 0, h + 0.9, 5.0, 0.3, (a1 - a0) * 1.02, 0,
                    Color.web("#e6d8a8"));
        }
        // impression sur le flanc exterieur : une etiquette et deux traits
        for (int cote = -1; cote <= 1; cote += 2) {
            double hE = couvercle(profil.demi(L * 0.25)), hT = couvercle(profil.demi(-L * 0.4));
            paroi(b, profil, x, y, z, yaw, cote, 0.9, L * 0.25, L * 0.3,
                    hE * 0.55, 0.3, hE * 0.32, Color.web("#efe6d2"));
            paroi(b, profil, x, y, z, yaw, cote, 0.9, -L * 0.4, L * 0.22,
                    hT * 0.62, 0.3, 1.2, pli.deriveColor(0, 1, 0.7, 1));
        }

        for (int bout = -1; bout <= 1; bout += 2) {
            double along = bout * L;
            double R = profil.demi(along), h = couvercle(R);
            // cannelures : la tranche du carton, visible a l'entree
            for (int i = 0; i < 16; i++) {
                double cote = -R + (i + 0.5) * 2 * R / 16;
                bloc(b, x, y, z, yaw, along, cote, h + 0.1, 2 * R / 20, 1.0, 1.7, 0, pli);
            }
            // Rabat du dessus, charniere sur la tranche du carton.
            //
            // Il etait pose a plat cinq metres au-dessus du couvercle et decale
            // de cinq et demi vers l'exterieur : vu de l'entree, une grande
            // plaque brune levitait au-dessus de la baie, sans rien qui la
            // rattache a la boite. Il pivote maintenant autour de l'arete du
            // dessus. Le pivot demande un basculement dans le plan de la piste,
            // et {@code inclinaison} roule autour de l'axe longitudinal : la
            // piece est donc deviee d'un quart de tour, sa largeur prend le
            // long de la piste et sa longueur le travers.
            double replie = Math.toRadians(52);
            bloc(b, x, y, z, yaw,
                    bout * (L - 0.2 + RABAT / 2 * Math.cos(replie)), 0,
                    h + 0.6 + RABAT / 2 * Math.sin(replie),
                    RABAT, 1.2, 2 * R + 3.2, -bout * Math.toDegrees(replie), 90,
                    carton.deriveColor(0, 1, 0.94, 1));
            for (int cote = -1; cote <= 1; cote += 2) {
                bloc(b, x, y, z, yaw, along + bout * 3.5, cote * (R + 4.5), h * 0.62,
                        11, h * 0.5, 1.2, cote * 62, carton.deriveColor(0, 1, 0.9, 1));
            }
        }

        // Boulettes de papier d'emballage, contre les parois.
        //
        // C'etaient des ellipsoides lisses de 1,4 a 3,6 metres de rayon, semees
        // jusqu'a un metre de l'axe : sur les captures de la traversee, des
        // blocs erratiques poses au milieu d'un salon, et c'est ce qu'on
        // regardait pendant tout l'ouvrage. Deux corrections. Du papier
        // froisse, ce sont des plans francs qui se coupent en aretes vives —
        // trois plaques croisees, jamais une sphere adoucie, et la finesse n'y
        // change rien puisque la boite n'a plus de facettes a doser. Et une
        // boulette se pousse du pied contre la paroi : elle se cale maintenant
        // au mur, hors de la trajectoire.
        //
        // Le nombre de tirages est celui d'avant, un pour un et dans le meme
        // ordre : ce {@code Random} est celui des objets poses du circuit, et
        // en gagner ou en perdre un decalerait les trente pieces suivantes.
        for (int i = 0; i < 9; i++) {
            double r = 1.0 + rnd.nextDouble() * 1.4;
            double along = -L + rnd.nextDouble() * 2 * L;
            int cote = rnd.nextBoolean() ? 1 : -1;
            double contreMur = profil.demi(along) - r - 0.5 - rnd.nextDouble() * 1.4;
            double[] p = poste(x, z, yaw, along, cote * contreMur);
            double vrille = rnd.nextDouble() * 60;
            Color papier = Color.web("#f2ead4")
                    .deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.16, 1);
            for (int f = 0; f < 3; f++) {
                b.add(Meshes.sharedBox(), r * (1.8 - 0.35 * f), r * (0.7 + 0.2 * f),
                        r * (1.3 + 0.3 * f), -15 + 17 * f, yaw + vrille + f * 57,
                        p[0], Meshes.jy(y + r * 0.55), p[1],
                        papier.deriveColor(0, 1, 1.0 - 0.1 * f, 1));
            }
        }
    }

    /**
     * Longueur d'un rabat du dessus, en metres.
     *
     * <p>Un vrai rabat fait la moitie de la largeur de la caisse, pour que les
     * deux se rejoignent quand la boite est fermee : ici la caisse fait de dix-
     * sept a dix-neuf metres au couvercle, donc entre huit et dix. Onze reprend
     * la longueur de la plaque qu'il remplace, un peu au-dessus de la cote
     * juste ; c'est la valeur verifiee a la capture, ou le rabat se lit de la
     * ligne droite qui precede l'entree.
     */
    private static final double RABAT = 11;

    /**
     * Hauteur du couvercle de la boite.
     *
     * <p>Elle ne suit plus la largeur : proportionnelle, elle placait le
     * couvercle a vingt-quatre metres, c'est-a-dire hors du champ de vision
     * d'un kart. On voyait deux murs et le ciel de la piece entre les deux,
     * jamais un dedans. Une boite couchee se lit a son couvercle — d'ou ce
     * plafond bas, celui d'une boite a chaussures plutot que d'un cube.
     */
    private static double couvercle(double demiLargeur) {
        return Math.min(demiLargeur * 1.05, 12.5);
    }

    // ------------------------------------------------------------ la poche

    /**
     * Retrait du nez de bande sous le mur declare de l'ouvrage, en metres.
     *
     * <p>C'est la seule paroi du jeu batie <b>en deca</b> de son profil, et la
     * raison tient en un mot : une poche est un retrecissement. Le mur declare
     * de la poche se tient a la marge de sept metres, mais l'ouvrage est une
     * boite droite sur une piste qui tourne encore, et la derive mange trois de
     * ces sept metres : mesure faite travee par travee, le degagement reel
     * entre le bord du couloir et le mur declare tombe a 4,00 m au pire point
     * de l'emprise, pas a sept. Deux metres vingt de retrait laissent donc
     * 1,80 m — au-dessus du metre et demi que l'invariant demande — et
     * rapprochent la bande de trois metres, ce qui vaut six metres sur la
     * gueule.
     *
     * <p>Le mur <i>declare</i>, lui, ne bouge pas. Le baisser d'un centimetre
     * changerait l'exclusion du semis, et le semis se decide tirage par tirage
     * : tout le decor de la Table de billard se deplacerait a partir du
     * cinq cent vingtieme metre. Mesure faite d'ailleurs, la poche n'est pas
     * une aberration de largeur — son mur est a 18,71 m de l'axe quand les huit
     * ouvrages s'echelonnent de 16,00 m (tunnel de la Colline) a 26,63 m
     * (temple du Desert).
     */
    private static final double NEZ_DE_BANDE = 2.2;

    /** Hauteur du drap de la bande, en metres : au-dessus commence le bois. */
    private static final double HAUT_FEUTRE = 4.6;

    /** Hauteur du dessus de bande, en metres : au-dessus commence le cuir. */
    private static final double HAUT_BANDE = 7.0;

    /**
     * Poche de coin : les deux bandes de la table avec leur drap, leur bois
     * verni et leurs pastilles d'ivoire, la gueule cerclee de bois, le sac de
     * cuir tendu par-dessus et le filet qui le double.
     *
     * <p>Elle etait batie <b>a l'echelle d'une bille</b> : un berceau de cuir en
     * plein cintre, et une bande — une seule poutre de bois — posee a 27,8 m
     * d'altitude et 22,8 m de l'axe. A cette hauteur elle n'entrait jamais dans
     * le cadre : l'oeil est a cinq metres du sol et la demi-ouverture verticale
     * vaut une vingtaine de degres, si bien qu'un objet a {@code H} metres ne se
     * voit qu'a {@code 3,5 x (H - 5)} devant, soit soixante-dix metres pour
     * cette poutre-la, dans un ouvrage qui n'en fait que cinquante-six. La clef
     * du berceau, elle, culminait a 23,7 m — meme calcul, meme verdict. On
     * traversait donc un tube de douelles brunes, c'est-a-dire le tonneau du
     * Comptoir, et rien de ce qui dit « billard » n'etait visible.
     *
     * <p>Tout le mobilier de la salle est a l'etalon d'interieur — neuf unites
     * de monde pour un metre reel, mesure sur le canape du Salon — et la poche
     * ne peut pas y tenir : a cet etalon elle ferait une unite de large, et une
     * poche d'une unite ne se traverse pas en kart. Elle ne se rapporte donc
     * pas au mobilier de la salle mais a <b>la table qu'on parcourt</b>, dont
     * elle emprunte les quatre matieres : le drap de la bande, le bois verni du
     * cadre — celui de l'autre table, meme teinte —, l'ivoire des pastilles et
     * le cuir du sac. C'est cette parente-la qui se lit, pas un rapport de
     * taille.
     *
     * <p>Trois cotes decident du reste, et toutes trois sortent du champ de la
     * camera : le drap monte a 4,6 m, le bois s'arrete a 7,0 m et la clef du sac
     * a 15,1 m au lieu de 23,7 — le sac est surbaisse a 0,40, comme le dome de
     * glace l'est a 0,62 et pour la meme raison. Une poche est de toute facon
     * un sac affaisse, pas une nef.
     *
     * <p>Ce qui ne se repare pas, et qu'il vaut mieux ecrire que bricoler : la
     * table qu'on parcourt fait neuf cent vingt-quatre metres de tour et sa
     * poche trente-trois de large, quand une bille de la meme salle en fait
     * 0,84 — trente-neuf billes de large pour une poche qui devrait en faire
     * deux. Aucune retouche ne rapproche ces deux nombres : la largeur de la
     * poche est celle du couloir de piste, et la longueur du circuit celle
     * d'un circuit. Ce qui se soigne, c'est la <b>lecture</b>, et elle tient
     * aux matieres : du drap, du bois verni, de l'ivoire, du cuir, un filet.
     * Aucune de ces cinq-la n'affiche de taille. Voir aussi
     * {@link #billesDevantLaPoche}, qui est le seul endroit ou les deux
     * baremes auraient pu se rencontrer dans la meme image.
     *
     * <p>Le nombre de tirages change ici, et c'est sans consequence : la poche
     * est la <b>derniere</b> piece posee de la Table de billard, donc le dernier
     * client de son flux d'alea.
     */
    private static void pocheGeante(DecorBatch b, Random rnd, int div,
                                    double x, double y, double z, double yaw,
                                    Ouvrage.Profil profil) {
        double L = profil.demiLongueur();
        Color cuir = Color.web("#33231a");
        // le bois de l'autre table de la salle, a la teinte pres : c'est la
        // meme menuiserie, et deux bruns voisins se liraient comme une faute
        Color bois = Color.web("#5a3a22");
        Color verni = bois.deriveColor(0, 1, 1.35, 1);
        // le drap de la bande est celui du tapis, pris a l'ombre : au ton exact
        // du sol (#1f7a46) la bande disparaissait dans le feutre, faute de
        // toute arete pour la detacher
        Color feutre = Color.web("#17663c");
        Color ivoire = Color.web("#efe6cf");
        Color ficelle = Color.web("#6f5b3e");

        // Le sac de cuir, tendu sur le dessus des bandes.
        sacDeCuir(b, rnd, x, y, z, yaw, L, profil, cuir);

        for (int cote = -1; cote <= 1; cote += 2) {
            bande(b, profil, x, y, z, yaw, cote, L, feutre, bois, verni, ivoire);
        }
        for (int bout = -1; bout <= 1; bout += 2) {
            gueule(b, x, y, z, yaw, bout, L, profil, verni);
            machoire(b, x, y, z, yaw, bout, L, profil, feutre, bois, verni);
        }
        filet(b, x, y, z, yaw, L, profil, ficelle);
        for (int bout = -1; bout <= 1; bout += 2) {
            billesDevantLaPoche(b, div, x, y, z, yaw, bout, L, profil);
        }
    }

    /** Surbaissement du sac : voir la javadoc de {@link #pocheGeante}. */
    private static final double SAC = 0.40;

    /** Le cuir tendu au-dessus des deux bandes. */
    private static void sacDeCuir(DecorBatch b, Random rnd, double x, double y, double z,
                                  double yaw, double L, Ouvrage.Profil profil, Color cuir) {
        // neuf panneaux au lieu de onze claveaux, et une variation de teinte
        // trois fois plus faible : un sac de cuir est une peau, pas un
        // appareil de pierre — c'est le nombre de joints qui fait la difference
        // entre les deux lectures
        voute(b, rnd, x, y, z, yaw, L, (int) Math.round(2 * L / 4.5), profil,
                HAUT_BANDE + 0.6, 2.0, 9, cuir, 0.12, false, SAC);
    }

    /**
     * Une bande de la table : le drap, le bois, le dessus verni et ses
     * pastilles.
     *
     * <p>Le profil est en gradins et c'est ce qui le rend lisible : le drap est
     * la paroi la plus avancee, le bois se retire de quatre-vingts centimetres
     * derriere lui, le dessus deborde de nouveau. Trois aretes horizontales a
     * 4,6 m, 7,0 m et 7,6 m, qu'on longe pendant cinquante-six metres.
     */
    private static void bande(DecorBatch b, Ouvrage.Profil profil,
                              double x, double y, double z, double yaw, int cote, double L,
                              Color feutre, Color bois, Color verni, Color ivoire) {
        paroi(b, profil, x, y, z, yaw, cote, -NEZ_DE_BANDE + 0.9, 0, L,
                HAUT_FEUTRE / 2, 1.8, HAUT_FEUTRE, feutre);
        // Le chanfrein du nez. Sans lui la bande est un mur vert de quatre
        // metres soixante : c'est la pente du drap, et elle seule, qui
        // distingue une bande de billard d'une cloison peinte.
        double[] bornes = panneaux(0, L);
        for (int i = 0; i < bornes.length - 1; i++) {
            double a0 = bornes[i], a1 = bornes[i + 1];
            double r0 = profil.demi(a0) - NEZ_DE_BANDE + 1.4;
            double r1 = profil.demi(a1) - NEZ_DE_BANDE + 1.4;
            bloc(b, x, y, z, yaw, (a0 + a1) / 2, cote * (r0 + r1) / 2, HAUT_FEUTRE + 0.35,
                    1.7, 1.7, Math.hypot(a1 - a0, r1 - r0) * 1.02, cote * 38,
                    deviation(cote, r0, r1, a1 - a0), feutre);
        }
        paroi(b, profil, x, y, z, yaw, cote, -NEZ_DE_BANDE + 2.3, 0, L,
                (HAUT_FEUTRE + HAUT_BANDE) / 2, 3.0, HAUT_BANDE - HAUT_FEUTRE, bois);
        paroi(b, profil, x, y, z, yaw, cote, -NEZ_DE_BANDE + 2.6, 0, L,
                HAUT_BANDE + 0.3, 4.4, 0.6, verni);
        // Les pastilles d'ivoire du dessus de bande. Une table en porte trois
        // par demi-bande ; c'est le seul ornement qui nomme un billard sans
        // qu'on ait a voir le tapis, et il tombe pile a la hauteur ou l'oeil
        // longe la bande.
        for (int k = -3; k <= 3; k++) {
            double along = k * (L / 3.5);
            double r = profil.demi(along) - NEZ_DE_BANDE + 1.4;
            bloc(b, x, y, z, yaw, along, cote * r, HAUT_BANDE + 0.75,
                    1.5, 0.5, 1.5, 0, ivoire);
        }
    }

    /**
     * La gueule : l'arc de bois qui cercle l'entree.
     *
     * <p>C'est ce qu'on voit de la poche avant d'y etre, et c'est donc ce qui
     * doit la nommer. Le tonneau du Comptoir le montre : ce qui le fait lire de
     * cinquante metres n'est pas son ventre mais la couronne de douves de son
     * fond, vue de face. La poche n'avait qu'un arc mince, de la teinte du
     * cuir, a demi cache par le berceau.
     */
    private static void gueule(DecorBatch b, double x, double y, double z, double yaw,
                               int bout, double L, Ouvrage.Profil profil,
                               Color verni) {
        double along = bout * (L + 1.8);
        // Le rayon est celui du dessus de bande : l'arc retombe exactement sur
        // les deux bandes, et c'est ce qui le rattache a la table. Il a d'abord
        // eu deux jambages jusqu'au sol — mesure faite a l'image, ils lisaient
        // « portail de pont » et non « coin de billard », parce qu'un pied
        // droit de sept metres est une architecture. Le bois d'une table ne
        // touche pas le tapis : il court sur la bande.
        double R = profil.demi(along) - NEZ_DE_BANDE + 2.6;
        anneau(b, x, y, z, yaw, along, R, HAUT_BANDE + 0.6, 2.8, 4.2, 15, 180, verni, SAC);
    }

    /**
     * Les deux machoires, au-dela de la gueule : la bande continue dehors et
     * s'ecarte, si bien qu'on voit de loin deux bandes converger vers un trou.
     *
     * <p>C'est la moitie du travail, et pas la moins importante : la camera
     * couvre soixante-deux degres a l'horizontale, donc un mur a dix-neuf
     * metres de l'axe n'entre dans le cadre qu'a trente-deux metres de l'oeil.
     * Passe la moitie de l'ouvrage il n'y a plus de mur dans l'image : la poche
     * ne se lit vraiment que sur ses trente premiers metres, et de l'exterieur. C'est le tonneau du Comptoir qui
     * l'enseigne : ce qui le nomme de cinquante metres est la couronne de son
     * fond, vue de face, pas ce qu'on voit dedans.
     */
    private static void machoire(DecorBatch b, double x, double y, double z, double yaw,
                                 int bout, double L, Ouvrage.Profil profil,
                                 Color feutre, Color bois, Color verni) {
        double R0 = profil.demi(bout * L) - NEZ_DE_BANDE + 0.9;
        double debut = L + 2.6, fin = L + 17;
        double pente = Math.tan(Math.toRadians(24));
        int n = 3;
        for (int cote = -1; cote <= 1; cote += 2) {
            for (int i = 0; i < n; i++) {
                double a0 = debut + (fin - debut) * i / n;
                double a1 = debut + (fin - debut) * (i + 1) / n;
                double r0 = R0 + (a0 - debut) * pente, r1 = R0 + (a1 - debut) * pente;
                double r = (r0 + r1) / 2;
                double along = bout * (a0 + a1) / 2;
                double longueur = Math.hypot(a1 - a0, r1 - r0) * 1.02;
                // la machoire de l'entree court a rebours de l'axe : sa pente
                // en plan change de signe avec le bout, sans quoi les panneaux
                // s'ouvrent du mauvais cote et laissent un escalier de joints
                double dev = bout * deviation(cote, r0, r1, a1 - a0);
                bloc(b, x, y, z, yaw, along, cote * r, HAUT_FEUTRE / 2,
                        1.8, HAUT_FEUTRE, longueur, 0, dev, feutre);
                bloc(b, x, y, z, yaw, along, cote * (r + 0.5), HAUT_FEUTRE + 0.35,
                        1.7, 1.7, longueur, cote * 38, dev, feutre);
                bloc(b, x, y, z, yaw, along, cote * (r + 1.4),
                        (HAUT_FEUTRE + HAUT_BANDE) / 2,
                        3.0, HAUT_BANDE - HAUT_FEUTRE, longueur, 0, dev, bois);
                bloc(b, x, y, z, yaw, along, cote * (r + 1.7), HAUT_BANDE + 0.3,
                        4.4, 0.6, longueur, 0, dev, verni);
                // Une bande ne se coupe pas net : elle bute sur le bois de la
                // poche suivante. Sans ce bout, la machoire s'arretait sur une
                // section de drap a vif, et de loin on lisait une caisse verte
                // posee la. Une plaque de neuf centimetres et le bois sombre,
                // non le verni : epaisse et claire, elle se lisait a son tour
                // comme une caisse, cette fois brune — on passe a sept metres
                // d'elle.
                if (i == n - 1) {
                    bloc(b, x, y, z, yaw, bout * (a1 + 0.5), cote * (r1 + 0.9),
                            HAUT_BANDE / 2 + 0.3, 5.0, HAUT_BANDE + 0.6, 0.9,
                            0, dev, bois);
                }
            }
        }
    }

    /**
     * Le filet qui double le cuir : six cerceaux et onze cordes.
     *
     * <p>Un filet de poche est tricote en rond, cerceau par cerceau — c'est
     * pour cela que la maille est polaire et non carree. Il etait fait de douze
     * batons verticaux pendus a l'entree : sans cerceau, une maille n'existe
     * pas, et de loin ces batons se lisaient comme des stalactites.
     *
     * <p>Il est pose sous le cuir et non dedans : une corde plaquee sur la peau
     * ne se voit pas, le rendu etant fusionne sans ombre portee entre pieces.
     */
    private static void filet(DecorBatch b, double x, double y, double z, double yaw,
                              double L, Ouvrage.Profil profil, Color ficelle) {
        double pied = HAUT_BANDE + 0.6;
        for (double along : new double[]{-L * 0.82, -L * 0.49, -L * 0.16,
                L * 0.16, L * 0.49, L * 0.82}) {
            anneau(b, x, y, z, yaw, along, profil.demi(along) - 1.1, pied,
                    0.38, 0.38, 15, 180, ficelle, SAC);
        }
        double[] bornes = panneaux(0, L);
        for (int k = -5; k <= 5; k++) {
            double theta = Math.toRadians(k * 16);
            for (int i = 0; i < bornes.length - 1; i++) {
                double a0 = bornes[i], a1 = bornes[i + 1];
                double r0 = profil.demi(a0) - 1.1, r1 = profil.demi(a1) - 1.1;
                double r = (r0 + r1) / 2;
                bloc(b, x, y, z, yaw, (a0 + a1) / 2, r * Math.sin(theta),
                        pied + SAC * r * Math.cos(theta), 0.38, 0.38,
                        (a1 - a0) * 1.02,
                        Math.toDegrees(Math.atan2(SAC * Math.sin(theta), Math.cos(theta))),
                        deviation(1, r0 * Math.sin(theta), r1 * Math.sin(theta), a1 - a0),
                        ficelle);
            }
        }
    }

    /**
     * Trois billes calees contre la machoire, devant la gueule.
     *
     * <p>Elles etaient deux, dans la poche, et faisaient 6,4 m de diametre.
     * Une bille de cette salle en fait 0,84 — c'est la piece {@code boule},
     * posee une vingtaine de fois le long du tour — et deux spheres huit fois
     * trop grosses au pied de la poche dementaient l'echelle que tout le reste
     * du circuit affirme.
     *
     * <p>Elles sont dehors et non dedans, et ce n'est pas un detail. Les deux
     * baremes de ce circuit ne se reconcilient pas : une poche large de
     * trente-trois metres fait trente-neuf billes de large, la ou une vraie en
     * fait deux — mais sa largeur est celle du couloir de piste, elle ne se
     * negocie pas. Poser des billes <b>dans</b> la gueule mettrait les deux
     * baremes dans la meme image, c'est-a-dire montrerait l'irreconciliable ;
     * posees sur le tapis devant elle, elles disent seulement « des billes
     * roulent vers la poche », ce qui est l'image meme du billard.
     */
    private static void billesDevantLaPoche(DecorBatch b, int div, double x, double y, double z,
                                            double yaw, int bout, double L,
                                            Ouvrage.Profil profil) {
        Color[] billes = {Color.web("#f2f0e6"), Color.web("#e9c93a"), Color.web("#2f5fc0"),
                Color.web("#c0392b"), Color.web("#6f3fa8"), Color.web("#e07a2f")};
        double r = 0.42;
        // {distance au-dela de la gueule, cote, teinte}
        double[][] posees = {{7.5, -1, 0}, {10.4, -1, 3}, {12.0, 1, 1}};
        for (double[] p : posees) {
            double a = L + p[0];
            double face = profil.demi(bout * L) - NEZ_DE_BANDE
                    + (a - (L + 2.6)) * Math.tan(Math.toRadians(24));
            double[] q = poste(x, z, yaw, bout * a, p[1] * (face - r - 0.3));
            b.add(Meshes.sharedSphere(div), r, r, r, 0, yaw,
                    q[0], Meshes.jy(y + r), q[1], billes[(int) p[2]]);
        }
    }
}
