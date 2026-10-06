package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.transform.Rotate;
import org.tuxkart.core.Quality;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Ouvrage;
import org.tuxkart.track.Theme;
import org.tuxkart.track.Track;

import java.util.Random;

/** Construit la totalite du decor 3D d'un circuit : piste, terrain, decor, ciel. */
public final class TrackNode {

    private static final double KERB_WIDTH = 1.7;

    /** Longueur d'un troncon de decor fusionne, en metres. */
    private static final double CHUNK_LENGTH = 130;

    /**
     * Longueur de circuit pour laquelle la densite de semis est calee.
     *
     * @see #etalement
     */
    private static final double LONGUEUR_ETALON = 1050;

    /**
     * Largeur de la bande semee de chaque cote du couloir, en metres, a la
     * densite etalon.
     *
     * @see #bandeSemee
     */
    private static final double BANDE_ETALON = 90;

    /**
     * Densite de decor pour laquelle {@link #BANDE_ETALON} est calee : c'est
     * celle a laquelle le semis a ete juge a l'oeil, troncs detaches et ciel
     * visible entre les cones.
     */
    private static final double DENSITE_ETALON = 30;


    private TrackNode() {
    }

    /** Nombre de triangles du dernier decor construit, pour le diagnostic. */
    public static long lastTriangleCount;

    /**
     * Le decor d'un circuit : la scene a accrocher, et de quoi ecarter du rendu
     * les troncons lointains.
     */
    public record Decor(Group root, DecorCulling culling) {
    }

    /**
     * @param gridSlots nombre d'emplacements a peindre sur la grille : autant
     *                  que de karts engages, sinon la piste porte des cases
     *                  numerotees que personne n'occupe
     */
    public static Decor build(Track track, Quality quality, int gridSlots) {
        Group root = new Group();
        Terrain terrain = new Terrain(track, quality);
        java.util.List<Node> chunks = new java.util.ArrayList<>();

        root.getChildren().add(terrain.build(chunks));
        root.getChildren().add(roadSurface(track, terrain));
        root.getChildren().add(startLine(track));
        root.getChildren().add(startGrid(track, gridSlots));
        // Tout ce qui appartient au circuit automobile s'arrete a la porte.
        // Une plaque d'egout sur un drap de billard, une publicite peinte sur
        // un tapis de salon, un panneau « 300 m » sur un mat d'acier au bord
        // d'un comptoir : chacun de ces objets porte sa taille reelle et la
        // donne a toute la scene, qui cesse alors d'etre une piece. La ligne
        // damier, elle, reste : sans elle on ne sait plus ou finit le tour.
        if (!track.def.theme.indoor) {
            root.getChildren().add(roadMarkings(track));
            root.getChildren().add(distanceBoards(track));
        }
        root.getChildren().add(startGate(track));
        root.getChildren().add(scenery(track, terrain, quality, chunks));
        root.getChildren().add(sky(track));
        lastTriangleCount = countTriangles(root);
        System.out.printf("Decor construit : %.2f million de triangles%n",
                lastTriangleCount / 1e6);
        return new Decor(root, new DecorCulling(chunks, decorCone()));
    }

    /** -Dtuxkart.decorcone=<degres> ; 0 dessine tout le decor, meme derriere la camera. */
    private static double decorCone() {
        String spec = System.getProperty("tuxkart.decorcone");
        if (spec == null) return DecorCulling.DEFAULT_HALF_ANGLE;
        try {
            return Double.parseDouble(spec);
        } catch (NumberFormatException e) {
            System.err.println("Demi-angle de decor illisible : " + spec);
            return DecorCulling.DEFAULT_HALF_ANGLE;
        }
    }

    /** Nombre de triangles portes par un noeud et ses descendants. */
    public static long triangles(javafx.scene.Node node) {
        return countTriangles(node);
    }

    /**
     * Espacement supplementaire du semis sur un long circuit.
     *
     * <p>Le budget de geometrie est <b>par circuit</b>, pas par metre : ce qui
     * decroche quand le decor grossit, ce n'est pas la carte graphique — la
     * cadence mediane ne bouge pas — c'est la <b>memoire</b> et la conversion
     * des maillages a la premiere image. Le mur mesure a quatre millions et
     * demi de triangles, ou le tas touche son plafond par defaut. Un trace de
     * deux kilometres et demi seme donc moitie moins dense au metre qu'un trace
     * de mille, et coute autant.
     *
     * <p>Le prix se paie en densite le long du bas-cote, et il est modere par
     * la geometrie meme d'un long circuit : ses courbes sont plus longues, on y
     * voit plus loin, et l'oeil compare les bosquets a ce qu'il voit devant lui,
     * pas au souvenir d'un autre circuit.
     */
    private static double etalement(Track track) {
        return Math.max(1, track.length / LONGUEUR_ETALON);
    }

    /**
     * Densite du semis, {@code -Dtuxkart.decordensity=<n>} pour l'imposer.
     *
     * <p>Ce n'est pas un reglage de jeu mais un outil de mesure. Deux densites
     * ne se comparent qu'alternees dans la meme session — la machine est
     * chargee, et sa charge derive plus vite que l'ecart cherche — donc les
     * comparer en recompilant entre chaque passe ne marche pas. Toute la
     * campagne qui a fixe la densite d'{@code ULTRA} tient a cette poignee.
     */
    private static double densiteSemis(Quality quality) {
        String spec = System.getProperty("tuxkart.decordensity");
        if (spec == null) return quality.decorDensity;
        try {
            return Double.parseDouble(spec);
        } catch (NumberFormatException e) {
            System.err.println("Densite de decor illisible : " + spec);
            return quality.decorDensity;
        }
    }

    /**
     * Largeur de la bande semee de chaque cote du couloir, en metres.
     *
     * <p>La densite ne commandait que l'ecart <b>le long</b> du ruban
     * ({@code step = 7 / densite}) : la doubler ne repartissait pas plus de
     * decor sur plus de terrain, elle en tassait deux fois plus sur la meme
     * bande de 90 m. Or ce qui soude les silhouettes, c'est la distance entre
     * voisins immediats, et elle se resserre comme la racine du nombre
     * d'objets par metre carre. A densite 60 la sapiniere de la Colline
     * devenait un mur vert sans un tronc ni un trou de ciel, et les mesas du
     * Desert s'interpenetraient — alors que la cadence, elle, tenait.
     *
     * <p>La bande s'elargit donc <b>proportionnellement a la densite</b> : le
     * nombre d'objets et la surface semee montent du meme facteur, la surface
     * par objet ne bouge pas, et le gain part ou on le voulait — dans la
     * profondeur du paysage. Plus de decor veut alors dire un horizon plus
     * loin, pas une haie plus epaisse.
     *
     * <p>La compensation n'est pas tout a fait exacte, et c'est voulu : le
     * tirage lateral est en {@code u^1,6}, donc concentre pres du ruban, et la
     * densite au bord de piste ne suit pas la largeur de la bande mais sa
     * puissance 0,625. A densite doublee il reste ainsi un tiers de decor en
     * plus au bord de la piste — la ou on le regarde — pour un semis deux fois
     * plus nombreux.
     *
     * <p>En dessous de la densite etalon, la bande ne se resserre pas. Le
     * defaut qu'on corrige est l'encombrement, et un niveau leger n'en a
     * aucun : lui retirer de la largeur ne ferait que creuser un couloir vide
     * borde de deux haies.
     *
     * <p><b>Elargir ne coute rien</b>, et cela ne va pas de soi : ce que le tri
     * par cone perd, le niveau de detail par distance le rend. Mesure sur la
     * Piste de Tux a densite 60, les deux bandes alternees dans la meme
     * session : bande de 90 m, 3,44 M de triangles batis dont 14,0 troncons sur
     * 21 dessines, soit 2,29 M soumis ; bande de 180 m, 3,01 M batis — les
     * objets partis au loin passent sous {@link #LOD_FAR} et perdent leurs
     * facettes — dont 16,4 troncons sur 21, soit 2,35 M soumis. Trois pour cent
     * d'ecart : la bande large echange du tri contre de la finesse, a somme
     * nulle. C'est le nombre de triangles <b>soumis</b> qui commande la
     * cadence, pas celui du circuit.
     *
     * <p>{@code -Dtuxkart.decorband=<metres>} impose la largeur, pour refaire
     * cette mesure-la sans recompiler entre deux passes.
     */
    private static double bandeSemee(double densite) {
        String spec = System.getProperty("tuxkart.decorband");
        if (spec != null) {
            try {
                return Double.parseDouble(spec);
            } catch (NumberFormatException e) {
                System.err.println("Bande de decor illisible : " + spec);
            }
        }
        return BANDE_ETALON * Math.max(1, densite / DENSITE_ETALON);
    }

    private static long countTriangles(javafx.scene.Node node) {
        if (node instanceof MeshView view
                && view.getMesh() instanceof javafx.scene.shape.TriangleMesh mesh) {
            return mesh.getFaces().size() / (mesh.getVertexFormat().getVertexIndexSize() * 3);
        }
        if (node instanceof Group group) {
            long sum = 0;
            for (javafx.scene.Node child : group.getChildren()) sum += countTriangles(child);
            return sum;
        }
        return 0;
    }

    // ------------------------------------------------------------- la piste

    private static Group roadSurface(Track track, Terrain terrain) {
        Group g = new Group();
        Theme theme = track.def.theme;
        int n = track.n;
        double half = track.halfRoad;

        Vec3[] roadL = new Vec3[n], roadR = new Vec3[n];
        Vec3[] kerbLo = new Vec3[n], kerbRo = new Vec3[n];
        Vec3[] shoL = new Vec3[n], shoR = new Vec3[n];
        Vec3[] wallLtop = new Vec3[n], wallRtop = new Vec3[n];

        for (int i = 0; i < n; i++) {
            // largeur locale : le ruban se resserre et s'ouvre le long du tour
            double hw = track.halfRoadAt[i];
            double hc = track.halfCorridorAt[i];
            roadL[i] = at(track, i, hw, 0.05);
            roadR[i] = at(track, i, -hw, 0.05);
            kerbLo[i] = at(track, i, hw + KERB_WIDTH, 0.02);
            kerbRo[i] = at(track, i, -(hw + KERB_WIDTH), 0.02);
            shoL[i] = at(track, i, hc, -0.16);
            shoR[i] = at(track, i, -hc, -0.16);
            wallLtop[i] = at(track, i, hc, track.def.barrier.height);
            wallRtop[i] = at(track, i, -hc, track.def.barrier.height);
        }

        PhongMaterial groundMat = Meshes.material(Textures.ground(theme),
                Textures.groundBump(theme), Color.rgb(10, 10, 10), 6);
        PhongMaterial kerbMat = Meshes.material(Textures.kerb(),
                Textures.kerbBump(), Color.rgb(70, 70, 70), 24);
        PhongMaterial vergeMat = Meshes.material(Textures.verge(theme),
                Textures.vergeBump(theme), Color.rgb(30, 30, 30), 10);
        var surface = track.def.surface;
        PhongMaterial roadMat = Meshes.material(Textures.surface(theme, surface),
                Textures.surfaceBump(theme, surface),
                surface == org.tuxkart.track.Surface.BITUME
                        ? Color.rgb(22, 22, 24) : Color.rgb(8, 8, 8), 10);
        PhongMaterial wallMat = Meshes.material(Textures.barrier(track.def.barrier, theme),
                Textures.barrierBump(track.def.barrier, theme), Color.rgb(50, 50, 50), 18);

        PhongMaterial kerbAltMat = Meshes.material(Textures.kerbAlt(),
                Textures.kerbAltBump(), Color.rgb(70, 70, 70), 24);
        PhongMaterial runoffMat = Meshes.material(Textures.runoff(),
                Textures.runoffBump(), Color.rgb(24, 24, 24), 10);
        PhongMaterial gravelMat = Meshes.material(Textures.gravel(theme),
                Textures.gravelBump(theme), Color.rgb(26, 26, 26), 8);
        // secteur refait a neuf : meme texture, teinte differente. Le bitume
        // neuf est nettement plus sombre que l'ancien, une terre ou une neige
        // fraiche a peine plus marquee.
        PhongMaterial roadNewMat = Meshes.material(Textures.surface(theme, track.def.surface),
                Textures.surfaceBump(theme, track.def.surface), Color.rgb(20, 20, 22), 12);
        roadNewMat.setDiffuseColor(Color.gray(
                track.def.surface == org.tuxkart.track.Surface.BITUME ? 0.78 : 0.86));

        // Les rubans sont tous poses du meme sens : le premier bord recoit u=0.
        // Les textures de bordure et de muret ne sont pas symetriques — vibreur
        // sali cote piste, main courante en haut de la glissiere, ombre au
        // pied — donc inverser l'ordre d'un cote les retournerait.
        double shoulderTiles = Math.max(1, track.shoulder / 6);

        // Un ouvrage traverse impose deux choses au ruban, et pour la meme
        // raison : dedans, on n'est plus au bord d'une route.
        //
        // Ses murs d'abord. Le muret de la piste et sa glissiere s'arretent a
        // l'entree : sans cette interruption, le rail rouge et blanc traversait
        // la grange de la Piste de Tux de part en part, et la barriere de bois
        // courait a l'interieur du tunnel de la Colline — deux decors qui se
        // contredisaient a trois metres l'un de l'autre.
        //
        // Son sol ensuite. Le bas-cote, lui, ne peut pas s'interrompre : c'est
        // le raccord entre la chaussee et le sol de la piece, et le supprimer
        // laisserait un trou entre les deux. Il change donc de matiere. On
        // roulait sinon dans une grange dont l'aire etait du gazon tondu, et
        // dans une galerie percee sous une crete bordee d'un caniveau de ville.
        boolean[] sousOuvrage = new boolean[n];
        Ouvrage[] couvert = new Ouvrage[n];
        var emprises = emprises(track);
        for (int i = 0; !emprises.isEmpty() && i < n; i++) {
            couvert[i] = ouvrageSur(emprises, track, track.arc[i], track.halfCorridorAt[i], 0, 0);
            sousOuvrage[i] = couvert[i] != null;
        }
        // bas-cotes : herbe, et bac a gravier a la sortie de certains virages.
        // Sous un ouvrage, rien : le tablier ci-dessous les remplace tous les
        // deux d'un seul tenant.
        int[] shoulderGroups = shoulderStyle(track);
        PhongMaterial[] shoulderMats = {groundMat, gravelMat};
        g.getChildren().addAll(Meshes.ribbonGroups(kerbLo, shoL, track.arc, 6,
                shoulderTiles, shoulderMats, shoulderGroups, sousOuvrage, track.center));
        g.getChildren().addAll(Meshes.ribbonGroups(kerbRo, shoR, track.arc, 6,
                shoulderTiles, shoulderMats, shoulderGroups, sousOuvrage, track.center));

        // Sous un ouvrage, un seul ruban du bord de la chaussee au pied du mur :
        // le tablier. Il remplace la bordure et le bas-cote, tous deux
        // interrompus ci-dessus — et il part bien du bord de la chaussee, non
        // de la ligne de vibreur : parti de la, il laissait derriere lui les
        // un metre soixante-dix de la bordure supprimee, un lisere de gazon
        // fluorescent le long du bitume sur toute la traversee du tunnel.
        // Il couvre en plus la bande de terrain qui
        // restait nue entre le bord du couloir et la paroi — large de la marge
        // batie de l'ouvrage, cinq metres sous le tunnel, neuf dans la grange,
        // dix sous le temple. C'etait du gazon tondu sous un toit de ferme et
        // de l'herbe dans une galerie percee dans la roche.
        //
        // Un seul ruban, parce que l'echelle de texture en est une propriete :
        // versee dans la bordure, la terre battue de la grange se posait en
        // tuiles de deux metres quarante, et dans le bas-cote en tuiles de six.
        // Ces deux-la se raccordaient par une couture de un metre soixante-dix
        // le long de la chaussee, sur la seule portion du tour ou l'on voit le
        // sol de pres.
        //
        // Lateralement, le tablier ne s'ecarte pas d'un nombre : il suit le
        // mur, calcule dans le repere de l'ouvrage comme le mur lui-meme (voir
        // tablier). Deux essais a debord constant ont echoue de la meme facon,
        // soixante centimetres puis un metre vingt : il restait chaque fois une
        // frange d'herbe au pied du bardage dans les dernieres travees de la
        // grange. Aucune constante ne peut convenir — projete sur la travee du
        // ruban, le mur s'ecarte du couloir de quinze centimetres sous le
        // tunnel mais de trois metres sous la nef, si bien qu'un debord taille
        // pour la nef sortirait de trois metres dehors ailleurs.
        Terrain.Sample ech = new Terrain.Sample();
        ech.index = -1;
        for (Emprise e : emprises) {
            double demiLongueur = e.ouvrage().demiLongueur;
            Ouvrage.Profil profil = e.profil();
            // Six ouvrages sur huit n'ont pas de sol propre : leur tablier est
            // celui du theme — le sable du desert, la neige, le tapis du salon.
            // C'est deja ce que le terrain porte a cet endroit ; ce que le
            // tablier y gagne, c'est de chasser le vibreur et le bac a gravier,
            // que rien n'ecartait d'un temple ou d'un dome de glace.
            PhongMaterial sol = solOuvrage(e.ouvrage(), theme);
            PhongMaterial mat = sol == null ? groundMat : sol;
            // Le sol d'un ouvrage ne descend pas plus bas que l'ouvrage.
            //
            // Un ouvrage est bati d'un bloc sur le point le plus bas du ruban
            // sous son emprise, tandis que le terrain, lui, suit le devers et
            // le relief : du cote bas, il pend jusqu'a deux metres quinze sous
            // cette assise. Le tablier, qui se cale sur le terrain, y plongeait
            // donc sous le pied du mur — et par ce jour rasant on voyait dehors
            // sur une vingtaine de metres au-dela du bardage de la grange.
            //
            // Le sol d'une piece est celui de la piece : le tablier ne descend
            // pas sous l'assise. Le relever ne peut pas le faire crever par le
            // terrain, la correction n'allant que dans un sens.
            double base = assise(track, terrain, e.ouvrage(), e.s(), 0, e.ancre());
            Vec3[] dehorsL = new Vec3[n], dehorsR = new Vec3[n];
            boolean[] saute = new boolean[n];
            for (int i = 0; i < n; i++) {
                double dedans = demiLongueur + 4
                        - Math.abs(track.deltaS(e.s(), track.arc[i]));
                saute[i] = dedans <= 0;
                // Le tablier s'ouvre en biseau sur quatre metres, du bord du
                // bas-cote au pied du mur. La fenetre d'exclusion deborde
                // l'ouvrage de quatre metres a chaque bout, donc le biseau
                // tombe entierement hors des murs. Il porte l'altitude autant
                // que la largeur : sans lui, la premiere travee du tablier
                // tomberait d'un coup des seize centimetres du bas-cote aux
                // quarante-cinq du terrain, et ferait une marche a l'entree.
                double f = MathUtil.smoothstep(0, 4, dedans);
                dehorsL[i] = Vec3.lerp(shoL[i],
                        tablier(track, terrain, ech, e, profil, i, roadL[i], 1, base), f);
                dehorsR[i] = Vec3.lerp(shoR[i],
                        tablier(track, terrain, ech, e, profil, i, roadR[i], -1, base), f);
            }
            double tuiles = Math.max(1,
                    (e.ouvrage().marge + track.shoulder + KERB_WIDTH) / 6);
            g.getChildren().add(Meshes.ribbon(roadL, dehorsL, track.arc, 6, mat, tuiles,
                    saute, track.center));
            g.getChildren().add(Meshes.ribbon(roadR, dehorsR, track.arc, 6, mat, tuiles,
                    saute, track.center));
        }

        // bordures : beton en ligne droite, puis un style de vibreur par virage.
        // Sous un ouvrage, ni l'un ni l'autre : le caniveau pave et le vibreur
        // peint sont du mobilier de circuit automobile, et une aire de grange
        // n'en a pas plus qu'un tapis de salon. C'est le tablier qui prend le
        // relais, sur toute la largeur d'un coup.
        int[] borderGroups = track.def.surface.kerbs
                ? borderStyle(track) : new int[track.n];
        PhongMaterial earthEdge = Meshes.material(Textures.ground(theme),
                Textures.groundBump(theme), Color.rgb(10, 10, 10), 6);
        PhongMaterial[] borderMats = track.def.surface.kerbs
                ? new PhongMaterial[]{vergeMat, kerbMat, kerbAltMat, runoffMat}
                : new PhongMaterial[]{earthEdge};
        g.getChildren().addAll(Meshes.ribbonGroups(roadL, kerbLo, track.arc, 2.4, 1,
                borderMats, borderGroups, sousOuvrage, track.center));
        g.getChildren().addAll(Meshes.ribbonGroups(roadR, kerbRo, track.arc, 2.4, 1,
                borderMats, borderGroups, sousOuvrage, track.center));

        // bitume : un secteur refait a neuf, plus clair que le reste
        int[] roadGroups = resurfacedSectors(track);
        g.getChildren().addAll(Meshes.ribbonGroups(roadL, roadR, track.arc, half * 2, 1,
                new PhongMaterial[]{roadMat, roadNewMat}, roadGroups, track.center));

        // Murets : u = 0 en haut des deux cotes. Un circuit ouvert n'en a pas
        // — on sort du ruban et on roule a cote — mais il garde son bas-cote,
        // qui reste le raccord entre la piste et le sol de la piece.
        if (!track.def.open) {
            g.getChildren().add(Meshes.ribbon(wallLtop, shoL, track.arc, 6, wallMat, 1,
                    sousOuvrage, track.center));
            g.getChildren().add(Meshes.ribbon(wallRtop, shoR, track.arc, 6, wallMat, 1,
                    sousOuvrage, track.center));
            // la main courante blanche n'appartient qu'a la glissiere metallique
            if (track.def.barrier == org.tuxkart.track.Barrier.RAIL) {
                g.getChildren().add(rail(track, wallLtop, sousOuvrage));
                g.getChildren().add(rail(track, wallRtop, sousOuvrage));
            }
        }
        // Voile sombre au pied des murets : sans lui les objets flottent.
        //
        // Largeur locale et non largeur de reference, comme partout ailleurs
        // sur le ruban : parti de {@code halfCorridor - 1,5}, le voile passait
        // <b>a l'exterieur</b> du bas-cote partout ou le couloir se resserre
        // sous sa largeur nominale, et le ruban se retournait sur lui-meme.
        // Mesure faite sur le Donjon et le Comptoir : trente pour cent de ses
        // triangles avaient la normale a l'envers, et le voile s'y eclairait
        // par en dessous.
        PhongMaterial contact = new PhongMaterial(Color.WHITE);
        contact.setDiffuseMap(Textures.contactShadow());
        contact.setSpecularColor(Color.TRANSPARENT);
        Vec3[] inL = new Vec3[n], inR = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double dedans = Math.max(0.5, track.halfCorridorAt[i] - 1.5);
            inL[i] = at(track, i, dedans, -0.12);
            inR[i] = at(track, i, -dedans, -0.12);
        }
        g.getChildren().add(Meshes.ribbon(shoL, inL, track.arc, 8, contact, 1,
                sousOuvrage, track.center));
        g.getChildren().add(Meshes.ribbon(shoR, inR, track.arc, 8, contact, 1,
                sousOuvrage, track.center));
        return g;
    }

    /** Tube blanc qui coiffe le muret : cadre bien la piste de loin. */
    private static MeshView rail(Track track, Vec3[] top, boolean[] sousOuvrage) {
        int n = track.n;
        Vec3[] a = new Vec3[n], b = new Vec3[n];
        for (int i = 0; i < n; i++) {
            a[i] = top[i].add(0, 0.22, 0);
            b[i] = top[i].add(0, 0.02, 0);
        }
        PhongMaterial m = Meshes.material(Color.web("#f0f2f4"));
        m.setSpecularColor(Color.web("#ffffff"));
        m.setSpecularPower(48);
        MeshView v = Meshes.ribbon(a, b, track.arc, 4, m, 1, sousOuvrage, track.center);
        v.setCullFace(CullFace.NONE);
        return v;
    }

    /**
     * Ou poser des vibreurs : la ou la piste tourne, plus une marge de part et
     * d'autre pour que la transition tombe avant l'entree du virage.
     */
    private static boolean[] kerbMask(Track track) {
        int n = track.n;
        boolean[] mask = new boolean[n];
        int spread = Math.max(2, (int) Math.round(11 / track.spacing));
        for (int i = 0; i < n; i++) {
            if (track.curvatureAhead(track.arc[i] - 5, 10) <= 0.011) continue;
            for (int k = -spread; k <= spread; k++) {
                mask[MathUtil.mod(i + k, n)] = true;
            }
        }
        return mask;
    }

    /**
     * Style de bordure au pas i : 0 beton, 1 vibreur rouge, 2 vibreur bleu,
     * 3 degagement peint. Chaque virage recoit son propre style, ce qui evite
     * l'impression d'un circuit fait d'un seul morceau.
     */
    private static int[] borderStyle(Track track) {
        int n = track.n;
        boolean[] corner = kerbMask(track);
        int[] style = new int[n];
        int[] palette = {1, 2, 1, 3, 2, 1};
        int cornerIndex = -1;
        boolean inside = false;
        for (int i = 0; i < n; i++) {
            if (corner[i] && !inside) cornerIndex++;
            inside = corner[i];
            style[i] = corner[i] ? palette[Math.floorMod(cornerIndex, palette.length)] : 0;
        }
        return style;
    }

    /**
     * Bac a gravier a l'exterieur d'un virage sur trois.
     *
     * <p>Dehors seulement. Un degagement de gravier est du mobilier de circuit
     * automobile au meme titre qu'une tribune ou qu'un vibreur peint : sur le
     * feutre du Billard, il posait une bande de gravillons a la sortie d'un
     * virage sur trois, et cette bande porte sa taille reelle — elle annule
     * d'un coup l'echelle de jouet que toute la piece raconte. Sans lui, le
     * bas-cote des quatre pieces redevient ce qu'il doit etre : la matiere du
     * sol de la piece, feutre, plancher, laine ou dalle, sans interruption.
     */
    private static int[] shoulderStyle(Track track) {
        int n = track.n;
        if (track.def.theme.indoor) return new int[n];
        boolean[] corner = kerbMask(track);
        int[] style = new int[n];
        int cornerIndex = -1;
        boolean inside = false;
        for (int i = 0; i < n; i++) {
            if (corner[i] && !inside) cornerIndex++;
            inside = corner[i];
            style[i] = (corner[i] && Math.floorMod(cornerIndex, 3) == 1) ? 1 : 0;
        }
        return style;
    }

    /** Un ou deux secteurs de bitume refait, comme sur un vrai circuit. */
    private static int[] resurfacedSectors(Track track) {
        int n = track.n;
        int[] groups = new int[n];
        Random rnd = new Random(track.def.id.hashCode() * 4241L);
        int sectors = 1 + rnd.nextInt(2);
        for (int s = 0; s < sectors; s++) {
            int start = rnd.nextInt(n);
            int length = (int) ((0.08 + rnd.nextDouble() * 0.10) * n);
            for (int k = 0; k < length; k++) groups[MathUtil.mod(start + k, n)] = 1;
        }
        return groups;
    }

    /**
     * Le sol a poser sous un ouvrage traverse, ou {@code null} si celui du
     * circuit convient deja.
     *
     * <p>Deux ouvrages seulement en demandent un. Le dome de glace de la
     * Banquise est bati sur la neige, le temple du Desert sur le sable, et les
     * quatre ouvrages d'interieur sur le plancher, le feutre ou la laine de leur
     * piece : leur bas-cote est deja de la bonne matiere, et le retoucher ne
     * serait qu'une regression de plus.
     */
    private static PhongMaterial solOuvrage(Ouvrage ouvrage, Theme theme) {
        switch (ouvrage) {
            case GRANGE -> {
                // Aire de ferme : la texture de piste de terre, assombrie parce
                // qu'on est sous un toit. C'est le revetement le plus proche
                // d'une terre battue que le jeu sache calculer, et ses deux
                // ornieres tombent juste — une aire de grange est un sol de
                // passage, pas un jardin.
                var terre = org.tuxkart.track.Surface.TERRE;
                PhongMaterial m = Meshes.material(Textures.surface(theme, terre),
                        Textures.surfaceBump(theme, terre), Color.rgb(12, 12, 10), 6);
                m.setDiffuseColor(Color.gray(0.80));
                return m;
            }
            case TUNNEL -> {
                // Galerie percee : le gravier des bacs de degagement, ramene au
                // gris de la roche dont la voute est faite juste au-dessus.
                //
                // La teinte est bleutee parce qu'elle multiplie la texture, et
                // qu'un gris multiplicateur assombrit sans desaturer : a
                // gray(0.62) le gravier culminait a (150,138,112), toujours
                // beige-sable a cote de blocs de paroi a (85,81,84). Le facteur
                // porte donc la correction canal par canal, et le produit tombe
                // sur un gris neutre d'environ (145,145,141).
                PhongMaterial m = Meshes.material(Textures.gravel(theme),
                        Textures.gravelBump(theme), Color.rgb(20, 20, 20), 8);
                m.setDiffuseColor(Color.rgb(152, 166, 200));
                return m;
            }
            default -> {
                return null;
            }
        }
    }



    /**
     * Bord exterieur du tablier pose sous un ouvrage : le pied du mur.
     *
     * <p>Le mur ne se deduit pas d'un ecart curviligne constant. L'ouvrage est
     * une boite droite batie dans son propre repere, et la piste tourne encore
     * un peu sous lui. Projete sur la travee du ruban qui lui fait face, son
     * mur s'ecarte donc du couloir de plus que sa marge, et pas qu'un peu :
     * quinze centimetres de plus sous le tunnel, un metre quarante-huit dans
     * la grange, trois metres tout rond sous la nef comme sous la poche du billard,
     * a six centimetres pres. (Ce n'est pas la meme mesure que le
     * depassement de 0,71 m que verrouille {@code TrackTest} : celui-la se
     * prend dans le repere de l'ouvrage, celui-ci sur le ruban, et la rotation
     * de la travee s'ajoute a la derive de l'axe.) Un debord constant laissait
     * donc une lisiere d'herbe au pied du bardage a un bout de la grange, et
     * debordait dehors a l'autre. Le bord est calcule dans le repere de
     * l'ouvrage, comme le mur lui-meme, et il le suit.
     *
     * <p>Trente centimetres de plus pour glisser sous la paroi : une plaque
     * mince posee juste au bord laisserait passer le terrain sur un pixel.
     *
     * <p>Son altitude se cale sur le relief, parce que le terrain n'est pas
     * dans le plan de la chaussee : il pend d'un demi-metre sous le bord du
     * couloir, puis remonte du relief a mesure qu'on s'en eloigne. Mais une
     * travee est un seul quad, donc une corde tendue entre ses deux bords :
     * poser le bord exterieur sur l'altitude du terrain au pied du mur ne
     * suffit pas, la bosse locale creverait le tablier a mi-portee des qu'elle
     * se leve de vingt centimetres. Le bord est releve d'autant qu'il faut
     * pour que la corde passe au-dessus du terrain en cinq points de la portee.
     *
     * <p>L'echantillon est reutilise d'un appel a l'autre : son index sert
     * d'amorce a la recherche suivante, et deux points consecutifs du tablier
     * sont voisins sur le ruban.
     */
    private static Vec3 tablier(Track track, Terrain terrain, Terrain.Sample ech,
                                Emprise e, Ouvrage.Profil profil, int i,
                                Vec3 dedans, int cote, double assise) {
        double along = track.deltaS(e.s(), track.arc[i]) * e.echelle();
        double demi = (profil.demi(along / Math.max(1e-6, e.echelle())) + 0.3) * e.echelle();
        // le repere de l'ouvrage, tel que OuvrageNode.poste le pose
        double a = e.cap();
        double x = e.ancre().x + Math.cos(a) * cote * demi + Math.sin(a) * along;
        double z = e.ancre().z - Math.sin(a) * cote * demi + Math.cos(a) * along;

        terrain.sample(x, z, ech.index, ech);
        double y = ech.height + 0.05;
        for (int k = 1; k < 5; k++) {
            double t = k / 5.0;
            terrain.sample(MathUtil.lerp(dedans.x, x, t), MathUtil.lerp(dedans.z, z, t), ech.index, ech);
            y = Math.max(y, dedans.y + (ech.height + 0.05 - dedans.y) / t);
        }
        return new Vec3(x, Math.max(y, assise), z);
    }


    /** Point de la section transversale au pas i, devers compris. */
    private static Vec3 at(Track track, int i, double lateral, double dy) {
        Vec3 c = track.center[i];
        Vec3 s = track.side[i];
        return new Vec3(c.x + s.x * lateral,
                c.y + lateral * track.bankSlope[i] + dy,
                c.z + s.z * lateral);
    }

    // --------------------------------------------------------- ligne et arche

    /** Emplacements peints de la grille de depart, cales sur {@link Track#gridS}. */
    private static Group startGrid(Track track, int slots) {
        Group g = new Group();
        for (int rank = 0; rank < slots; rank++) {
            double s = track.gridS(rank);
            double lat = track.gridLateral(rank);
            Vec3 a = track.worldAt(s - 1.7, lat - 1.15).add(0, 0.07, 0);
            Vec3 b = track.worldAt(s - 1.7, lat + 1.15).add(0, 0.07, 0);
            Vec3 c = track.worldAt(s + 1.7, lat + 1.15).add(0, 0.07, 0);
            Vec3 d = track.worldAt(s + 1.7, lat - 1.15).add(0, 0.07, 0);
            g.getChildren().add(Meshes.quad(a, b, c, d, 1, Textures.gridSlot(rank + 1)));
        }
        return g;
    }

    /**
     * Marquages au sol : rustines de reparation reparties au hasard et chevrons
     * d'avertissement avant les freinages. Une piste uniformement propre sur
     * mille metres sonne faux.
     */
    private static Group roadMarkings(Track track) {
        Group g = new Group();
        Random rnd = new Random(track.def.id.hashCode() * 7919L);
        var patch = Textures.roadPatch(track.def.theme);

        for (double s = 20; s < track.length - 20; s += 34) {
            if (rnd.nextDouble() > 0.55) continue;
            double len = 3 + rnd.nextDouble() * 5;
            double wid = 1.5 + rnd.nextDouble() * 3;
            double lat = (rnd.nextDouble() - 0.5) * (track.halfRoad * 1.3);
            double s0 = s + rnd.nextDouble() * 10;
            g.getChildren().add(Meshes.quad(
                    track.worldAt(s0, lat - wid / 2).add(0, 0.035, 0),
                    track.worldAt(s0, lat + wid / 2).add(0, 0.035, 0),
                    track.worldAt(s0 + len, lat + wid / 2).add(0, 0.035, 0),
                    track.worldAt(s0 + len, lat - wid / 2).add(0, 0.035, 0),
                    1, patch));
        }

        // trainees de gomme en sortie de virage, sur la trajectoire
        var rubber = Textures.rubber();
        for (double s = 0; s < track.length; s += 5) {
            if (track.curvatureAhead(s - 12, 12) < 0.028) continue;
            double bend = Math.clamp(
                    MathUtil.wrapPi(track.headingAtS(s)
                            - track.headingAtS(s - 18)) * 3, -1, 1);
            double lat = bend * track.halfRoad * 0.45;
            for (int side = -1; side <= 1; side += 2) {
                double l = lat + side * 0.85;
                g.getChildren().add(Meshes.quad(
                        track.worldAt(s, l - 0.55).add(0, 0.03, 0),
                        track.worldAt(s, l + 0.55).add(0, 0.03, 0),
                        track.worldAt(s + 5.4, l + 0.55).add(0, 0.03, 0),
                        track.worldAt(s + 5.4, l - 0.55).add(0, 0.03, 0),
                        1, rubber));
            }
        }

        // Plaques d'egout en bordure de chaussee, hors de l'emprise des
        // ouvrages : une grange, un tunnel ou un temple n'ont pas de reseau
        // d'assainissement sous leur sol, et la plaque y contredisait la terre
        // battue ou le gravier qu'on vient justement d'y poser. C'est la meme
        // faute que le caniveau pave, retire pour la meme raison.
        //
        // Le compteur avance meme sur un pas ecarte : c'est lui qui alterne les
        // cotes, et le figer ferait sauter d'un bord a l'autre les plaques qui
        // restent.
        var drain = Textures.drain();
        java.util.List<Emprise> emprises = emprises(track);
        int drains = 0;
        for (double s = 40; s < track.length; s += 63) {
            int sign = (drains++ % 2 == 0) ? 1 : -1;
            double lat = sign * (track.halfRoad - 0.75);
            if (sousOuvrage(emprises, track, s, lat)) continue;
            g.getChildren().add(Meshes.quad(
                    track.worldAt(s, lat - 0.42).add(0, 0.04, 0),
                    track.worldAt(s, lat + 0.42).add(0, 0.04, 0),
                    track.worldAt(s + 0.84, lat + 0.42).add(0, 0.04, 0),
                    track.worldAt(s + 0.84, lat - 0.42).add(0, 0.04, 0),
                    1, drain));
        }

        // publicites peintes sur le bitume, lisibles dans le sens de la marche
        String[] painted = {"TUX KART", "GNU", "JAVAFX", "LINUX"};
        Color[] paintBg = {Color.web("#1d6fb8"), Color.web("#c0392b"),
                Color.web("#2c8a4b"), Color.web("#6b3fb8")};
        int painting = 0;
        for (double s = 90; s < track.length - 40; s += 155) {
            if (track.curvatureAhead(s, 24) > 0.012) continue;
            // La publicite peinte s'arrete a l'entree d'un ouvrage, comme le
            // reste du mobilier de circuit : « GNU » peint sur l'aire de terre
            // battue d'une grange ou sur le gravier d'un tunnel raconte deux
            // choses a la fois. Elle mesure neuf metres et se teste donc par
            // son milieu et sa demi-longueur, sans quoi elle entre sous
            // l'ouvrage par la moitie de son bandeau.
            double lat = (painting % 2 == 0 ? 1 : -1) * track.halfRoad * 0.5;
            if (sousOuvrage(emprises, track, s + 4.5, lat, 4.5)) continue;
            var decal = Textures.banner(painted[painting % painted.length],
                    paintBg[painting % paintBg.length], Color.web("#f2f2ee"));
            g.getChildren().add(Meshes.quad(
                    track.worldAt(s, lat - 2.4).add(0, 0.03, 0),
                    track.worldAt(s + 9, lat - 2.4).add(0, 0.03, 0),
                    track.worldAt(s + 9, lat + 2.4).add(0, 0.03, 0),
                    track.worldAt(s, lat + 2.4).add(0, 0.03, 0),
                    1, decal));
            painting++;
        }

        var chevron = Textures.chevron();
        for (double s = 0; s < track.length; s += 8) {
            if (track.curvatureAhead(s + 26, 10) < 0.032) continue;
            if (track.curvatureAhead(s, 8) > 0.018) continue;
            for (int sign = -1; sign <= 1; sign += 2) {
                double lat = sign * track.halfRoad * 0.80;
                g.getChildren().add(Meshes.quad(
                        track.worldAt(s, lat - 0.9).add(0, 0.045, 0),
                        track.worldAt(s, lat + 0.9).add(0, 0.045, 0),
                        track.worldAt(s + 5, lat + 0.9).add(0, 0.045, 0),
                        track.worldAt(s + 5, lat - 0.9).add(0, 0.045, 0),
                        1, chevron));
            }
            s += 30;
        }
        return g;
    }

    /** Panneaux 300 / 200 / 100 m avant les virages les plus serres. */
    private static Group distanceBoards(Track track) {
        Group g = new Group();
        java.util.List<Emprise> emprises = emprises(track);
        java.util.List<Double> corners = new java.util.ArrayList<>();
        for (double s = 0; s < track.length; s += 8) {
            if (track.curvatureAhead(s - 4, 8) < 0.030) continue;
            boolean tooClose = false;
            for (double c : corners) {
                if (Math.abs(track.deltaS(c, s)) < 140) tooClose = true;
            }
            if (!tooClose) corners.add(s);
            if (corners.size() >= 3) break;
        }
        for (double corner : corners) {
            for (int meters : new int[]{300, 200, 100}) {
                // a la distance annoncee, pas a une fraction de celle-ci : un
                // panneau « 300 » plante a 126 m ment au pilote
                double s = corner - meters;
                // Largeur locale, pas largeur de reference. Le panneau se
                // plantait a {@code halfCorridor + 1,1}, qui n'est au bord du
                // couloir que la ou la piste garde sa largeur nominale : six des
                // trente-six panneaux se dressaient sur la chaussee, jusqu'a
                // deux metres trente-trois a l'interieur du couloir de la
                // Banquise. C'est le meme defaut que le semis avait, et il se
                // corrige de la meme facon.
                double lat = track.halfCorridorAtS(s) + 1.1;
                // Cette boucle ne consultait pas les ouvrages : le panneau
                // « 100 m » de la Colline se plantait a s=276, dans la paroi du
                // tunnel, et celui de « 300 m » du Desert a s=1667, sous le
                // temple. Un panneau de bord de piste s'arrete a l'entree comme
                // tout le reste du mobilier, d'ou le meme predicat.
                //
                // La borne s'arrete a l'emprise et pas a la baie, au contraire
                // de la reclame : c'est une indication que le pilote cherche,
                // pas une image. Deux metres de carton blanc apercus a cent
                // metres au bout d'un tunnel se lisent comme un panneau de bord
                // de route ; dix metres de reclame doree, non. La regle de la
                // ligne de vue lui couterait deux bornes sur six a la Piste de
                // Tux, dont le « 300 m » d'un virage — un renseignement contre
                // une gene qu'on n'a pas.
                if (sousOuvrage(emprises, track, s, lat)) continue;
                Vec3 p = track.worldAt(s, lat);
                Box board = new Box(2.0, 2.0, 0.14);
                board.setMaterial(Meshes.material(Textures.distanceBoard(meters)));
                board.setTranslateX(p.x);
                board.setTranslateZ(p.z);
                board.setTranslateY(Meshes.jy(p.y + 1.9));
                board.getTransforms().add(new Rotate(
                        Math.toDegrees(track.headingAtS(s)) + 180, Rotate.Y_AXIS));
                MeshView post = Meshes.cylinder(0.09, 1.9, 8,
                        Meshes.material(Color.web("#9aa0a6")));
                post.setTranslateX(p.x);
                post.setTranslateZ(p.z);
                post.setTranslateY(Meshes.jy(p.y + 0.95));
                g.getChildren().addAll(board, post);
            }
        }
        return g;
    }

    private static Group startLine(Track track) {
        Group g = new Group();
        Vec3 a = track.worldAt(0, track.halfRoad).add(0, 0.09, 0);
        Vec3 b = track.worldAt(0, -track.halfRoad).add(0, 0.09, 0);
        Vec3 c = track.worldAt(3.4, -track.halfRoad).add(0, 0.09, 0);
        Vec3 d = track.worldAt(3.4, track.halfRoad).add(0, 0.09, 0);
        g.getChildren().add(Meshes.quad(a, b, c, d, 4, Textures.checker()));
        return g;
    }

    /**
     * Le portique de la ligne, et sa version d'interieur.
     *
     * <p>Dehors, c'est l'ouvrage d'un circuit automobile : deux mats d'acier de
     * huit metres, une poutre « START / FINISH », un auvent et une barre de
     * chronometrage. Dedans, tout cela porterait sa taille reelle et
     * ecraserait la piece — mais le retirer entierement laissait la ligne
     * invisible, et un test le dit : sur le billard, plus rien ne se detachait
     * du fond au depart.
     *
     * <p>Ce qui reste dedans est donc ce qu'on tendrait au-dessus d'un circuit
     * de jouets : deux tiges de 3,8 m et une guirlande de fanions a 3,3 m. Cela
     * marque le tour sans rien dire de l'echelle du monde.
     */
    private static Group startGate(Track track) {
        Group g = new Group();
        boolean dedans = track.def.theme.indoor;
        double lat = track.halfRoad + KERB_WIDTH + 0.6;
        Vec3 base = track.centerAtS(2.0);
        Vec3 side = track.sideAtS(2.0);
        double heading = Math.toDegrees(track.headingAtS(2.0));
        // Dedans, la guirlande monte a hauteur de regard plutot qu'a hauteur de
        // kart : plus bas, elle passe sous le champ de vision des qu'on est a
        // quelques dizaines de metres, et la ligne d'arrivee cesse de
        // s'annoncer — c'est un test de rendu qui l'a dit, en ne trouvant plus
        // rien qui se decoupe dans le haut de l'image du billard.
        double hauteurMat = dedans ? 3.8 : 8.2;
        double hauteurGuirlande = dedans ? 3.3 : 6.2;
        PhongMaterial poleMat = Meshes.material(
                dedans ? Color.web("#b39a72") : Color.web("#cfd4d8"));
        poleMat.setSpecularColor(dedans ? Color.web("#d8c9ac") : Color.web("#ffffff"));
        poleMat.setSpecularPower(40);

        for (int k = -1; k <= 1; k += 2) {
            double px = base.x + side.x * lat * k;
            double pz = base.z + side.z * lat * k;
            Cylinder pole = new Cylinder(dedans ? 0.09 : 0.34, hauteurMat, 14);
            pole.setMaterial(poleMat);
            pole.setTranslateX(px);
            pole.setTranslateZ(pz);
            pole.setTranslateY(Meshes.jy(base.y + hauteurMat / 2));
            g.getChildren().add(pole);
            if (dedans) continue;
            Box foot = new Box(1.5, 0.5, 1.5);
            foot.setMaterial(Meshes.material(Color.web("#8d949a")));
            foot.setTranslateX(px);
            foot.setTranslateZ(pz);
            foot.setTranslateY(Meshes.jy(base.y + 0.25));
            g.getChildren().add(foot);
        }

        // guirlande de fanions tendue sous le portique : le passage du tour
        // se remarque de loin
        Color[] bunting = {Color.web("#f6a723"), Color.web("#e04a3a"),
                Color.web("#3fa2e0"), Color.web("#5ad46a"), Color.web("#f2f2ee")};
        int flags = 18;
        for (int i = 0; i < flags; i++) {
            double frac = (i + 0.5) / flags;
            double off = (frac - 0.5) * 2 * lat;
            double sag = Math.sin(frac * Math.PI) * 0.55;
            Box pennant = new Box(0.34, 0.5, 0.04);
            pennant.setMaterial(Meshes.material(bunting[i % bunting.length]));
            pennant.setTranslateX(base.x + side.x * off);
            pennant.setTranslateZ(base.z + side.z * off);
            pennant.setTranslateY(Meshes.jy(base.y + hauteurGuirlande - sag));
            pennant.getTransforms().add(new Rotate(heading - 90, Rotate.Y_AXIS));
            g.getChildren().add(pennant);
        }

        if (dedans) return g;

        Box beam = new Box(2 * lat + 1.2, 2.0, 0.55);
        beam.setMaterial(Meshes.material(Textures.banner("START / FINISH",
                Color.web("#123a6b"), Color.web("#ffd94a"))));
        beam.setTranslateX(base.x);
        beam.setTranslateZ(base.z);
        beam.setTranslateY(Meshes.jy(base.y + 7.6));
        beam.getTransforms().add(new Rotate(heading, Rotate.Y_AXIS));

        Box roof = new Box(2 * lat + 1.6, 0.25, 1.1);
        roof.setMaterial(Meshes.material(Color.web("#e8433a")));
        roof.setTranslateX(base.x);
        roof.setTranslateZ(base.z);
        roof.setTranslateY(Meshes.jy(base.y + 8.75));
        roof.getTransforms().add(new Rotate(heading, Rotate.Y_AXIS));

        // poutre de chronometrage, juste au-dessus de la piste
        Box timing = new Box(2 * lat, 0.22, 0.3);
        timing.setMaterial(Meshes.material(Color.web("#22262b")));
        timing.setTranslateX(base.x);
        timing.setTranslateZ(base.z);
        timing.setTranslateY(Meshes.jy(base.y + 5.5));
        timing.getTransforms().add(new Rotate(heading, Rotate.Y_AXIS));

        g.getChildren().addAll(beam, roof, timing);

        return g;
    }

    // ------------------------------------------------------------------ decor

    /**
     * @param cullable recoit les troncons fusionnes, pour que l'ecran de course
     *                 puisse ecarter du rendu ceux qui sont trop loin
     */
    private static Group scenery(Track track, Terrain terrain, Quality quality,
                                 java.util.List<Node> cullable) {
        Group g = new Group();
        // -Dtuxkart.nodecor : circuit nu, pour mesurer ce que coute le decor
        if (System.getProperty("tuxkart.nodecor") != null) return g;
        Random rnd = new Random(track.def.id.hashCode() * 31L + 7);
        Theme theme = track.def.theme;

        // Le decor est fusionne par troncon de piste : un seul maillage par
        // troncon au lieu de milliers de noeuds, et un troncon entier se retire
        // du rendu d'un seul setVisible quand il est trop loin. JavaFX ne le
        // fait pas de lui-meme : il n'ecarte rien de la scene 3D, tout noeud
        // visible part a la carte graphique, devant comme derriere.
        int chunks = Math.max(1, (int) Math.ceil(track.length / CHUNK_LENGTH));
        DecorBatch[] batches = new DecorBatch[chunks];
        for (int i = 0; i < chunks; i++) batches[i] = new DecorBatch();
        java.util.List<Emprise> emprises = emprises(track);
        java.util.List<Champ> champs = champs(track);

        // la densite est corrigee du cout de la vegetation, pour que tous les
        // circuits demandent un travail comparable a la carte graphique
        double densite = densiteSemis(quality);
        double step = 7.0 / (densite * theme.decorDensity) * etalement(track);
        // et la bande semee s'elargit avec elle, pour que le decor gagne en
        // profondeur plutot qu'en encombrement : voir bandeSemee
        double bande = bandeSemee(densite);
        Terrain.Sample sample = new Terrain.Sample();
        // Lot de rebut : ce qui tombe dans la tribune y est bati puis oublie.
        // Voir plus bas — c'est le seul moyen d'ecarter une piece sans toucher
        // au nombre de tirages.
        DecorBatch rebut = new DecorBatch();
        for (double s = 0; s < track.length; s += step) {
            DecorBatch batch = batches[Math.min(chunks - 1, (int) (s / CHUNK_LENGTH))];
            for (int sign = -1; sign <= 1; sign += 2) {
                if (rnd.nextDouble() > 0.62) continue;
                // en interieur le decor commence au ras du ruban : on doit
                // pouvoir longer un jouet, pas seulement l'apercevoir au loin
                //
                // La bande part de la largeur *locale*, pas de la largeur de
                // reference. Elle partait de {@code halfCorridor}, qui n'est le
                // couloir reel que sur deux circuits : la Table de billard passe
                // quatre-vingt-dix pour cent de son tour plus large que sa
                // valeur de reference, et jusqu'a onze metres sept plus large.
                // Tout ce qui tombait dans cet ecart etait rejete par le test de
                // distance ci-dessous — le tirage lateral etant en u^1,6, c'est
                // quatorze pour cent des tirages qui se perdaient, et precisement
                // ceux qui seraient tombes au ras du ruban. Le decor ne commencait donc
                // pas a un metre et demi du bord mais au bord lui-meme, et
                // clairseme, dans la piece qui promettait qu'on longe un jouet.
                double bord = theme.indoor ? 1.5 : 6;
                double lat = sign * (track.halfCorridorAtS(s) + bord
                        + Math.pow(rnd.nextDouble(), 1.6) * bande);
                Vec3 p = track.worldAt(s + rnd.nextDouble() * step, lat);
                // Un point loin de cote peut retomber sur une autre portion du
                // circuit la ou la boucle repasse pres d'elle-meme. On verifie
                // donc la distance a la piste la plus proche, pas seulement a
                // celle dont on est parti. Distance et altitude sortent de la
                // meme projection : a cette densite, les demander separement
                // doublerait le temps de construction du decor.
                terrain.sample(p.x, p.z, -1, sample);
                if (sample.distance < (theme.indoor ? 1.5 : 5)) continue;
                if (sousOuvrage(emprises, track, s, lat)) continue;
                double y = sample.height;
                double yaw = rnd.nextDouble() * 360;
                // La tribune est un volume plein, comme un ouvrage : rien n'y
                // poussait pourtant, et trois ou quatre sapins enneiges
                // sortaient du milieu de la foule de la Banquise, tronc
                // compris. La piece est donc semee comme les autres — memes
                // tirages, meme suite — mais versee dans un lot qu'on jette :
                // l'ecarter par un « continue » aurait saute les tirages de
                // {@code plant}, et le semis d'un circuit se decide tirage par
                // tirage. Tout le decor de la fin du tour, mobilier de bord de
                // piste compris, se serait deplace.
                // Meme mecanique pour le champ degage devant un repere de
                // paysage : la ferme, le silo et le moulin ne se voyaient pas
                // derriere le rideau de feuillus, et un « continue » aurait
                // decale tout le semis du circuit. Voir dansUnChamp.
                DecorBatch cible = dansLaTribune(track, s, lat)
                        || dansUnChamp(champs, track, s, lat) ? rebut : batch;
                double radius = plant(cible, theme, rnd, quality, p.x, y, p.z, yaw, Math.abs(lat));
                if (quality.decorShadows && radius > 0 && rnd.nextDouble() < 0.7) {
                    blob(cible, p.x, y + 0.06, p.z, radius);
                }
            }
        }

        // mobilier de bord de piste : quatre emplacements qui tournent, un
        // tous les vingt et un metres, en alternant les cotes.
        //
        // Dedans, la cadence est la meme mais le repertoire change : un plot de
        // chantier, une botte de paille ou un lampadaire de rue sur un tapis de
        // salon annulent d'un coup l'echelle que toute la piece raconte. Le
        // bord de piste est donc meuble par le fourbi de la piece elle-meme —
        // c'est ce qu'on longe quand on roule au ras du canape.
        //
        // Dehors, le repertoire dependait du seul rang de l'emplacement et pas
        // du theme : la meme botte de paille se posait sur une prairie, sur un
        // glacier et sur une coulee de lave. Une botte de paille est un objet
        // de securite de circuit de campagne ; sur la banquise et sur le
        // basalte elle raconte une ferme qui n'existe pas. Les quatre
        // emplacements consultent donc le theme, comme le fait deja plant() —
        // voir pieceDeSecurite, marshalPost et lampPost.
        int furniture = 0;
        for (double s = 18; s < track.length; s += 21) {
            DecorBatch batch = batches[Math.min(chunks - 1, (int) (s / CHUNK_LENGTH))];
            int sign = (furniture % 2 == 0) ? 1 : -1;
            double lat = sign * (track.halfCorridorAtS(s) + 1.6);
            Vec3 p = track.worldAt(s, lat);
            if (terrain.distanceToTrack(p.x, p.z) < 1.0) continue;
            if (sousOuvrage(emprises, track, s, lat)) continue;
            double heading = Math.toDegrees(track.headingAtS(s));
            if (theme.indoor) {
                double rayon = plant(batch, theme, rnd, quality,
                        p.x, p.y - 0.1, p.z, rnd.nextDouble() * 360, POSE + 1);
                if (quality.decorShadows && rayon > 0) {
                    blob(batch, p.x, p.y + 0.04, p.z, rayon);
                }
            } else {
                switch (furniture % 4) {
                    case 0 -> trafficCones(batch, p.x, p.y - 0.1, p.z, heading, sign);
                    case 1 -> pieceDeSecurite(batch, rnd, p.x, p.y - 0.1, p.z, heading, theme);
                    case 2 -> marshalPost(batch, p.x, p.y - 0.1, p.z, heading, theme);
                    default -> lampPost(batch, p.x, p.y - 0.1, p.z, heading, sign, theme);
                }
            }
            furniture++;
        }

        // piles de pneus a l'entree des virages serres — et, dedans, ce qui en
        // tient lieu : la caisse ou la pile de livres qui signale la corde
        for (double s = 0; s < track.length; s += 6) {
            if (track.curvatureAhead(s, 12) < 0.030) continue;
            if (rnd.nextDouble() > 0.35) continue;
            DecorBatch batch = batches[Math.min(chunks - 1, (int) (s / CHUNK_LENGTH))];
            for (int sign = -1; sign <= 1; sign += 2) {
                double lat = sign * (track.halfCorridorAtS(s) + 1.4);
                if (sousOuvrage(emprises, track, s, lat)) continue;
                Vec3 p = track.worldAt(s, lat);
                if (terrain.distanceToTrack(p.x, p.z) < 0.8) continue;
                if (theme.indoor) {
                    plant(batch, theme, rnd, quality,
                            p.x, p.y - 0.1, p.z, rnd.nextDouble() * 360, POSE + 1);
                } else {
                    tyreStack(batch, rnd, p.x, p.y - 0.1, p.z);
                }
            }
        }

        // Paille repandue sur l'aire de la grange, d'un bord a l'autre du
        // bas-cote. La terre battue seule faisait un chemin de terre sous un
        // toit ; c'est la paille au sol qui dit la ferme, et la grange n'en
        // semait que dix brins sur soixante-quatre metres.
        //
        // Elle est tiree par son propre Random : le semis est cale sur une
        // graine fixe et decide de son implantation tirage par tirage, donc lui
        // en prendre un decalerait tout le decor du circuit.
        for (Emprise e : emprises) {
            if (e.ouvrage() != Ouvrage.GRANGE) continue;
            Random brins = new Random(track.def.id.hashCode() * 6151L + 3);
            double L = e.ouvrage().demiLongueur;
            for (double d = -L; d <= L; d += 1.3) {
                double s = MathUtil.mod(e.s() + d, track.length);
                DecorBatch batch = batches[Math.min(chunks - 1, (int) (s / CHUNK_LENGTH))];
                int cote = brins.nextBoolean() ? 1 : -1;
                // entre la bordure et le bord du couloir : la bande qui vient
                // justement de passer de l'herbe a la terre
                double t = brins.nextDouble();
                double lat = cote * MathUtil.lerp(track.halfRoadAtS(s) + KERB_WIDTH,
                        track.halfCorridorAtS(s), t);
                Vec3 p = track.worldAt(s, lat);
                batch.add(Meshes.sharedBox(),
                        0.06 + brins.nextDouble() * 0.07, 0.05,
                        0.4 + brins.nextDouble() * 0.7,
                        0, brins.nextDouble() * 360,
                        p.x, Meshes.jy(p.y + MathUtil.lerp(0.02, -0.16, t) + 0.04), p.z,
                        Color.web("#c9ab5e").deriveColor(0, 1, 0.85 + brins.nextDouble() * 0.35, 1));
            }
        }

        for (DecorBatch batch : batches) {
            MeshView view = batch.build();
            if (view != null) {
                g.getChildren().add(view);
                cullable.add(view);
            }
        }

        // Drapeaux, panneaux publicitaires et tribune : le mobilier du circuit
        // automobile, celui qui dit « course » avant meme qu'on ait roule. Rien
        // de tout cela dedans : une tribune de trente metres posee sur une table
        // de billard ne fait pas rire, elle fait perdre l'echelle. Les pieces
        // dressees contre les murs suffisent a garnir l'horizon d'une piece.
        if (theme.indoor) {
            poserLesObjets(track, terrain, quality, cullable, g);
            return g;
        }

        // drapeaux de bord de piste
        int flagIndex = 0;
        for (double s = 12; s < track.length; s += 46) {
            int sign = (flagIndex++ % 2 == 0) ? 1 : -1;
            double lat = sign * (track.halfCorridorAtS(s) + 2.6);
            Vec3 p = track.worldAt(s, lat);
            if (terrain.distanceToTrack(p.x, p.z) < 1.2) continue;
            if (sousOuvrage(emprises, track, s, lat)) continue;
            place(g, flag(flagIndex), p.x, p.y - 0.1, p.z, 0);
        }

        // panneaux publicitaires colles au muret
        String[] slogans = {"TUX KART", "GNU/LINUX", "OPEN SOURCE", "PENGUIN POWER",
                "FREE SOFTWARE", "KERNEL 6.0", "JAVAFX INSIDE"};
        Color[] bgs = {Color.web("#1d6fb8"), Color.web("#b83c1d"), Color.web("#2c8a4b"),
                Color.web("#6b3fb8"), Color.web("#c9a227"), Color.web("#136e6e"),
                Color.web("#8a2f5e")};
        // Le panneau est pose en long : ses dix metres courent le long du
        // muret, et c'est par eux qu'il faut l'ecarter d'un ouvrage.
        double panneau = 10.0;
        int k = 0;
        for (double s = 25; s < track.length; s += 78) {
            int sign = (k % 2 == 0) ? 1 : -1;
            double lat = sign * (track.halfCorridorAtS(s) + 0.35);
            // teste par son seul centre, il entrait sous l'ouvrage par sa
            // moitie : c'est ce qui plantait une publicite sous le portail du
            // dome de glace de la Banquise
            if (dansLaBaie(emprises, track, s, lat, panneau / 2)) {
                k++;
                continue;
            }
            Vec3 p = track.worldAt(s, lat);
            Box panel = new Box(panneau, 2.5, 0.3);
            panel.setMaterial(Meshes.material(Textures.banner(
                    slogans[k % slogans.length], bgs[k % bgs.length], Color.WHITE)));
            panel.setTranslateX(p.x);
            panel.setTranslateZ(p.z);
            panel.setTranslateY(Meshes.jy(p.y + 3.2));
            panel.getTransforms().add(new Rotate(
                    Math.toDegrees(track.headingAtS(s)) - 90, Rotate.Y_AXIS));
            g.getChildren().add(panel);
            k++;
        }

        g.getChildren().add(grandstand(track));
        poserLesObjets(track, terrain, quality, cullable, g);
        return g;
    }

    /**
     * Depose les objets declares un a un par le circuit.
     *
     * <p>Un semis statistique fabrique un tapis : chaque objet y est
     * interchangeable, et l'oeil ne retient rien. Une piece habitee, elle, tient
     * en quelques dizaines de pieces dont chacune se voit — le canape au fond,
     * la caisse renversee dans le virage, la bouteille couchee au bord du
     * passage. Elles sont donc placees a la main, a une abscisse et un ecart
     * lateral donnes, et fusionnees dans le troncon qui les contient pour ne
     * rien couter de plus au rendu.
     */
    private static void poserLesObjets(Track track, Terrain terrain, Quality quality,
                                       java.util.List<Node> cullable, Group g) {
        if (track.def.props.length == 0) return;
        Random rnd = new Random(track.def.id.hashCode() * 7919L + 13);
        Theme theme = track.def.theme;
        // fusionnees par troncon, comme le semis : un circuit qui pose cent
        // pieces et deux ouvrages ferait sans cela un seul maillage long d'un
        // kilometre, que le tri par cone ne peut plus ecarter de rien
        int chunks = Math.max(1, (int) Math.ceil(track.length / CHUNK_LENGTH));
        DecorBatch[] batches = new DecorBatch[chunks];
        for (int i = 0; i < chunks; i++) batches[i] = new DecorBatch();

        // Les pieces posees a la main sont toutes proches de la piste, et
        // il y en a quelques dizaines la ou le semis en compte des milliers :
        // pas de reduction par la distance ici, et un plancher de finesse. Une
        // boule de billard a six meridiens est un diamant, et sur les circuits
        // d'interieur ces pieces sont tout le decor — la vingtaine de triangles
        // economisee par piece ne vaut pas ce qu'elle coute a voir.
        int sphereDiv = Math.max(9, quality.sphereDiv);
        int coneSides = Math.max(10, quality.coneSides);
        int lumpDiv = 9;
        for (int i = 0; i < track.def.props.length; i++) {
            double[] p = track.def.props[i];
            double s = p[0], lat = p[1], capRelatif = p[2], echelle = p[3];
            String nom = track.def.propKinds[i];
            Ouvrage ouvrage = Ouvrage.parNom(nom);
            Vec3 w = track.worldAt(s, lat);
            double y = assise(track, terrain, ouvrage, s, lat, w);
            double yaw = Math.toDegrees(track.headingAtS(s)) + capRelatif;
            DecorBatch batch = batches[Math.min(chunks - 1,
                    (int) (MathUtil.mod(s, track.length) / CHUNK_LENGTH))];
            batch.setScale(echelle, w.x, Meshes.jy(y), w.z);
            piece(batch, nom, rnd, theme, sphereDiv, coneSides, lumpDiv,
                    w.x, y, w.z, yaw, quality,
                    ouvrage == null ? null : ouvrage.profil(track, s));
            batch.resetScale();
        }
        for (DecorBatch batch : batches) {
            MeshView view = batch.build();
            if (view != null) {
                g.getChildren().add(view);
                cullable.add(view);
            }
        }
    }

    /**
     * Altitude a laquelle asseoir une piece posee.
     *
     * <p>Trois cas, et ils ne se confondent pas. Un <b>ouvrage</b> traverse
     * repose sur le point le plus bas du ruban sous son emprise : batir une
     * grange sur l'altitude de son milieu la ferait crever la chaussee des que
     * le terrain descend. Une piece posee <b>hors du couloir</b> suit le
     * terrain, qui n'est pas le plan du ruban prolonge — a vingt metres de la
     * piste, une colline a deja pris deux metres, et un arbre pose sur le plan
     * de la chaussee y flotterait. Une piece posee <b>sur le couloir</b>, elle,
     * suit le ruban, devers compris.
     */
    private static double assise(Track track, Terrain terrain, Ouvrage ouvrage,
                                 double s, double lat, Vec3 w) {
        if (ouvrage != null) {
            double bas = Double.MAX_VALUE;
            for (double d = -ouvrage.demiLongueur; d <= ouvrage.demiLongueur; d += 2) {
                bas = Math.min(bas, track.heightAtS(s + d, 0));
            }
            return bas;
        }
        if (Math.abs(lat) <= track.halfCorridorAtS(s)) return w.y;
        return terrain.heightAt(w.x, w.z);
    }

    /**
     * Un ouvrage pose, et le repere dans lequel il est bati.
     *
     * <p>L'ancre, le cap et l'echelle sont ceux de la piece — {@code props} et
     * {@code propKinds} — parce que le tablier a besoin de retrouver ou tombe
     * le mur, et qu'un mur ne se deduit pas d'une abscisse : l'ouvrage est une
     * boite droite, la piste tourne sous lui.
     *
     * <p>Le profil est echantillonne une fois pour toutes : le tablier de sol,
     * le semis et le mobilier de bord de piste l'interrogent chacun quelques
     * milliers de fois, et le rebatir a chaque question rechercherait autant de
     * fois dans le ruban.
     */
    private record Emprise(Ouvrage ouvrage, double s, Ouvrage.Profil profil,
                           Vec3 ancre, double cap, double echelle) {
    }

    private static java.util.List<Emprise> emprises(Track track) {
        java.util.List<Emprise> liste = new java.util.ArrayList<>();
        for (int i = 0; i < track.def.props.length; i++) {
            Ouvrage o = Ouvrage.parNom(track.def.propKinds[i]);
            if (o == null) continue;
            double s = track.def.props[i][0];
            double lat = track.def.props[i][1];
            liste.add(new Emprise(o, s, o.profil(track, s), track.worldAt(s, lat),
                    track.headingAtS(s) + Math.toRadians(track.def.props[i][2]),
                    track.def.props[i][3]));
        }
        return liste;
    }

    /**
     * Vrai si ce point tombe sous un ouvrage traverse.
     *
     * <p>Tout ce que le decor seme ou aligne s'y arrete : un bosquet ne pousse
     * pas dans une grange, un fanion ne se plante pas dans un mur, et une pile
     * de pneus au milieu d'une nef ne raconte rien.
     */
    private static boolean sousOuvrage(java.util.List<Emprise> emprises, Track track,
                                       double s, double lat) {
        return ouvrageSur(emprises, track, s, lat, 0, 0) != null;
    }

    /**
     * Portee de la ligne de vue au-dela d'un portail, en metres.
     *
     * <p>Un ouvrage ne se regarde pas seulement du dedans : on le voit d'abord
     * de loin, par un bout, et son portail cadre alors tout ce que la piste
     * porte a l'autre extremite. Le tunnel de la Colline le montrait en toutes
     * lettres — vu de l'entree, sa baie encadrait une reclame de dix metres
     * plantee dix-huit metres apres la sortie. L'exclusion faisait pourtant son
     * travail : le panneau etait bien dehors, et celui qui serait tombe dedans
     * etait bien ecarte.
     *
     * <p>La geometrie ne donne aucune distance a partir de laquelle l'objet
     * cesse d'etre cadre : sur une piste droite, un objet au bord du couloir
     * reste dans la baie aussi loin qu'on l'emmene, puisque la baie est plus
     * large que le couloir. Seule la courbure finit par l'en sortir — mesure
     * faite, a cent metres pour le tunnel de la Colline, jamais pour le dome de
     * la Banquise, qui ne tourne pas. Quarante metres est donc un choix, et
     * voici ce qu'il vaut : c'est ce qu'il faut pour vider la baie du tunnel, et
     * ca ne coute qu'une reclame par ouvrage — celle de s=337 a la Colline, de
     * s=571 a la Piste de Tux, de s=1351 a la Banquise, de s=1585 au Desert, et
     * aucune au Petit Volcan, qui n'a pas d'ouvrage.
     *
     * <p>Elle ne vaut que pour la reclame. Un fanion est un bout de tissu sur un
     * mat, il appartient au bord de piste comme la rambarde, qui traverse la
     * baie sans gener personne — et l'ecarter creuserait un trou de cent
     * vingt-six metres dans une cadence qui en compte quarante-six. Une borne de
     * distance est une indication que le pilote cherche, pas une image.
     */
    private static final double LIGNE_DE_VUE = 40;

    /**
     * La meme question pour un objet qui a une longueur.
     *
     * @param debord demi-longueur de l'objet pose, en metres. Un panneau
     *               publicitaire fait dix metres de long : teste par son seul
     *               centre, il entre sous l'ouvrage par la moitie de sa
     *               longueur. C'est ainsi qu'une publicite se retrouvait sous
     *               le dome de glace de la Banquise — plantee a s=1273 pour une
     *               fenetre d'exclusion qui commence a 1274, elle finissait a
     *               1278, sous un portail qui se dresse a 1277.
     */
    private static boolean sousOuvrage(java.util.List<Emprise> emprises, Track track,
                                       double s, double lat, double debord) {
        return ouvrageSur(emprises, track, s, lat, debord, 0) != null;
    }

    /**
     * Vrai si ce panneau tombe dans la baie d'un ouvrage : sous lui, ou dans la
     * ligne de vue qui le traverse de part en part.
     *
     * <p>Le test lateral est le meme que celui de l'emprise, et c'est ce qui le
     * rend juste : au-dela des bouts, {@code Profil.demi} prolonge la derniere
     * travee, donc la comparaison se fait bien contre l'ouverture du portail.
     * Un panneau plante au-dela du portail mais plus large que la baie n'est
     * pas cadre par elle, et reste.
     */
    private static boolean dansLaBaie(java.util.List<Emprise> emprises, Track track,
                                      double s, double lat, double debord) {
        return ouvrageSur(emprises, track, s, lat, debord, LIGNE_DE_VUE) != null;
    }

    /**
     * L'ouvrage qui couvre ce point, ou {@code null}.
     *
     * <p>La question se pose dans le repere de l'ouvrage, pas dans celui du
     * ruban. Un ouvrage est une boite droite et la piste tourne sous lui : a
     * l'abscisse {@code s} le mur n'est pas a un ecart lateral constant, il
     * derive — de quinze centimetres sous le tunnel de la Colline, de deux
     * metres soixante-dix sous la poche du billard. Projeter le point dans le
     * repere de la boite fait disparaitre cette derive, et la bande laissee
     * libre autour de la paroi vaut alors {@link Ouvrage#degagement}
     * <b>partout</b>, au lieu de ce degagement moins la derive — sous la poche,
     * l'ancienne regle tombait a un metre vingt-trois par endroits.
     *
     * <p>L'exclusion suivait auparavant une enveloppe unique par ouvrage — le
     * couloir le plus large de l'emprise, augmente de la marge. Elle avait le
     * defaut que la geometrie avait eu avant elle : la nef du donjon attrape par
     * un bout l'elargissement de la grille de depart, si bien qu'elle ecartait
     * le decor a trente-cinq metres six de l'axe quand son mur est a vingt-deux
     * metres trois — treize metres de dallage nu sur cent metres de long, et la
     * salle se lisait comme une plaine, son mobilier seme au loin sur un sol
     * vide. Chaque ouvrage laisse maintenant sa propre bande, la meme sous
     * toutes ses travees.
     *
     * <p>Ce test decide du <b>nombre de tirages</b> du semis, qui est tire par
     * un {@code Random} de graine fixe : le retoucher decale toute
     * l'implantation du circuit. Ce qu'un appelant peut faire sans cela, c'est
     * declarer la taille de ce qu'il pose ({@code debord}) — le mobilier de bord
     * de piste, lui, tire apres le test et passe donc zero.
     */
    private static Ouvrage ouvrageSur(java.util.List<Emprise> emprises, Track track,
                                      double s, double lat, double debord, double portee) {
        if (emprises.isEmpty()) return null;
        Vec3 p = track.worldAt(s, lat);
        for (Emprise e : emprises) {
            // repere de la boite : l'axe long suit le cap, comme la batit
            // OuvrageNode — avant = (sin cap, cos cap), travers = (cos cap, -sin cap)
            double dx = p.x - e.ancre().x;
            double dz = p.z - e.ancre().z;
            double cos = Math.cos(e.cap());
            double sin = Math.sin(e.cap());
            double along = dx * sin + dz * cos;
            double travers = Math.abs(dx * cos - dz * sin);
            if (Math.abs(along) < e.ouvrage().demiLongueur + 4 + debord + portee
                    && travers < e.profil().demi(along) + e.ouvrage().degagement) {
                return e.ouvrage();
            }
        }
        return null;
    }

    /**
     * Construit une piece du repertoire par son nom.
     *
     * <p>Le repertoire est commun a tous les circuits : une botte de foin est
     * une botte de foin, qu'elle soit semee au hasard le long d'une prairie ou
     * posee a la main a l'entree d'un virage. Les especes du semis sont donc
     * toutes nommables ici — c'est ce qui permet a un circuit de plein air de
     * composer ses reperes sans qu'on ait a redessiner un arbre.
     *
     * @param profil largeur libre imposee par le couloir travee par travee,
     *               pour les seuls ouvrages traverses
     */
    private static void piece(DecorBatch b, String nom, Random rnd, Theme theme,
                              int sphereDiv, int coneSides, int lumpDiv,
                              double x, double y, double z, double yaw,
                              Quality quality, Ouvrage.Profil profil) {
        Ouvrage ouvrage = Ouvrage.parNom(nom);
        if (ouvrage != null) {
            OuvrageNode.batir(b, ouvrage, rnd, theme, sphereDiv, x, y, z, yaw, profil);
            return;
        }
        switch (nom) {
            // le repertoire de plein air, pose a la main
            case "feuillu" -> feuillu(b, rnd, quality, sphereDiv, x, y, z, yaw, false);
            case "feuillufruit" -> feuillu(b, rnd, quality, sphereDiv, x, y, z, yaw, true);
            case "bouleau" -> bouleau(b, rnd, sphereDiv, x, y, z, yaw);
            case "sapin" -> sapin(b, rnd, coneSides, x, y, z, yaw, false);
            case "sapinneige" -> sapin(b, rnd, coneSides, x, y, z, yaw, true);
            case "arbremort" -> arbreMort(b, rnd, x, y, z, yaw);
            case "souche" -> souche(b, rnd, x, y, z, yaw, Color.web("#5b432c"));
            case "tronccouche" -> troncCouche(b, rnd, x, y, z, yaw);
            case "buisson" -> bush(b, theme, rnd, x, y, z, yaw, lumpDiv);
            case "fleurs" -> fleurs(b, rnd, x, y, z, yaw);
            case "foin" -> botteDeFoin(b, rnd, x, y, z, yaw);
            case "cactus" -> cactus(b, rnd, x, y, z, yaw);
            case "agave" -> agave(b, rnd, coneSides, x, y, z, yaw);
            case "butte" -> butte(b, rnd, x, y, z, yaw);
            case "herbeseche" -> herbeSeche(b, rnd, coneSides, x, y, z, yaw);
            case "pierredressee" -> pierreDressee(b, rnd, x, y, z, yaw);
            case "picdeglace" -> picDeGlace(b, rnd, coneSides, x, y, z, yaw);
            case "blocdeglace" -> blocDeGlace(b, rnd, x, y, z, yaw);
            case "balise" -> balise(b, rnd, x, y, z, yaw);
            case "bonhomme" -> bonhommeDeNeige(b, rnd, sphereDiv, x, y, z, yaw);
            case "orgues" -> orguesBasaltiques(b, rnd, x, y, z, yaw);
            case "obsidienne" -> obsidienne(b, rnd, coneSides, x, y, z, yaw);
            case "fumerolle" -> fumerolle(b, rnd, coneSides, sphereDiv, x, y, z, yaw);
            case "braise" -> rocherIncandescent(b, rnd, lumpDiv, x, y, z, yaw);
            case "coulee" -> couleeDeLave(b, rnd, x, y, z, yaw);
            case "soufre" -> depotDeSoufre(b, rnd, lumpDiv, x, y, z, yaw);
            case "cratere" -> cratere(b, rnd, coneSides, sphereDiv, x, y, z, yaw);
            // les reperes du paysage, plus gros que tout ce que seme le hasard
            case "ferme" -> ferme(b, rnd, x, y, z, yaw);
            case "silo" -> silo(b, rnd, x, y, z, yaw);
            case "moulin" -> moulin(b, rnd, x, y, z, yaw);
            case "phare" -> phare(b, rnd, x, y, z, yaw);
            case "eolienne" -> eolienne(b, rnd, x, y, z, yaw);
            case "epave" -> epave(b, rnd, x, y, z, yaw);
            case "totem" -> totem(b, rnd, x, y, z, yaw);
            // salon
            case "canape" -> canape(b, rnd, x, y, z, yaw);
            case "fauteuil" -> fauteuil(b, rnd, x, y, z, yaw);
            case "table" -> tableBasse(b, rnd, x, y, z, yaw);
            case "bibliotheque" -> bibliotheque(b, rnd, x, y, z, yaw);
            case "tele" -> meubleTele(b, rnd, x, y, z, yaw);
            case "lampadaire" -> lampadaire(b, rnd, coneSides, x, y, z, yaw);
            case "plante" -> planteVerte(b, rnd, sphereDiv, x, y, z, yaw);
            case "carton" -> carton(b, rnd, x, y, z, yaw);
            case "livres" -> pileDeLivres(b, rnd, x, y, z, yaw);
            case "coussin" -> coussin(b, rnd, x, y, z, yaw);
            case "nounours" -> nounours(b, rnd, sphereDiv, x, y, z, yaw);
            case "ballon" -> ballon(b, rnd, sphereDiv, x, y, z, yaw);
            case "brique" -> brique(b, rnd, x, y, z, yaw);
            case "voiture" -> petiteVoiture(b, rnd, x, y, z, yaw);
            case "crayon" -> crayon(b, rnd, coneSides, x, y, z, yaw);
            case "cube" -> cubeDeBois(b, rnd, x, y, z, yaw);
            case "de" -> de(b, rnd, x, y, z, yaw);
            case "quille" -> quille(b, rnd, sphereDiv, x, y, z, yaw);
            case "bille" -> bille(b, rnd, sphereDiv, x, y, z, yaw);
            // bar
            case "comptoir" -> comptoir(b, rnd, x, y, z, yaw);
            case "etagere" -> etagereABouteilles(b, rnd, x, y, z, yaw);
            case "tableronde" -> tableRonde(b, rnd, x, y, z, yaw);
            case "jukebox" -> jukebox(b, rnd, x, y, z, yaw);
            case "tabouret" -> tabouret(b, rnd, x, y, z, yaw);
            case "tonneau" -> tonneau(b, rnd, x, y, z, yaw);
            case "caisse" -> caisseDeBouteilles(b, rnd, x, y, z, yaw);
            case "bouteille" -> bouteille(b, rnd, x, y, z, yaw);
            case "chope" -> chope(b, rnd, sphereDiv, x, y, z, yaw);
            case "verre" -> verreAPied(b, rnd, coneSides, x, y, z, yaw);
            case "cible" -> cible(b, rnd, x, y, z, yaw);
            case "cacahuetes" -> bolDeCacahuetes(b, rnd, sphereDiv, x, y, z, yaw);
            case "sousverres" -> sousVerres(b, rnd, x, y, z, yaw);
            // donjon
            case "sarcophage" -> sarcophage(b, rnd, x, y, z, yaw);
            case "arche" -> arche(b, rnd, x, y, z, yaw);
            case "arcade" -> arcade(b, rnd, x, y, z, yaw, false);
            case "gravats" -> gravats(b, rnd, x, y, z, yaw);
            case "colonne" -> colonneBrisee(b, rnd, x, y, z, yaw);
            case "armoire" -> armoire(b, rnd, x, y, z, yaw);
            case "trone" -> trone(b, rnd, x, y, z, yaw);
            case "gargouille" -> gargouille(b, rnd, sphereDiv, x, y, z, yaw);
            case "tombe" -> pierreTombale(b, rnd, x, y, z, yaw);
            case "coffre" -> coffre(b, rnd, x, y, z, yaw);
            case "chaudron" -> chaudron(b, rnd, sphereDiv, x, y, z, yaw);
            case "torche" -> torche(b, rnd, coneSides, x, y, z, yaw);
            case "chandelier" -> chandelier(b, rnd, coneSides, x, y, z, yaw);
            case "crane" -> crane(b, rnd, sphereDiv, x, y, z, yaw);
            case "champignon" -> champignon(b, rnd, sphereDiv, x, y, z, yaw);
            case "rocher" -> rock(b, rnd, x, y, z, yaw, lumpDiv);
            // billard
            case "autretable" -> autreTable(b, rnd, x, y, z, yaw);
            case "portequeues" -> porteQueues(b, rnd, x, y, z, yaw);
            case "score" -> tableauDeScore(b, rnd, x, y, z, yaw);
            case "queue" -> queueDeBillard(b, rnd, x, y, z, yaw);
            case "rack" -> triangleDeRack(b, rnd, sphereDiv, x, y, z, yaw);
            case "poche" -> poche(b, rnd, x, y, z, yaw);
            case "boule" -> bouleDeBillard(b, rnd, sphereDiv, x, y, z, yaw, 1);
            case "boules" -> bouleDeBillard(b, rnd, sphereDiv, x, y, z, yaw, 4);
            case "craie" -> craie(b, rnd, x, y, z, yaw);
            default -> System.err.println("Piece de decor inconnue : " + nom);
        }
    }


    // ------------------------------------------------ le donjon hante

    /**
     * Le donjon est le seul decor d'interieur bati a l'echelle du monde.
     *
     * <p>Les trois autres pieces racontent un kart minuscule sur un meuble
     * d'humain — un metre reel y vaut neuf unites. Le donjon, lui, est une
     * salle qu'on traverse a l'echelle du kart : sa nef fait quatre-vingt-douze
     * metres de long et ses piliers quinze de haut, sa chaussee douze a
     * vingt-six metres de large. Une unite y vaut donc un metre, et une piece
     * de mobilier se dessine a sa taille reelle : une armoire de trois metres,
     * un sarcophage de deux metres huit, un coffre d'un metre vingt.
     *
     * <p>C'est ce qui manquait. Le mobilier etait bati quatre a cinq fois trop
     * grand — armoire de quinze a vingt metres, trone de quatorze, sarcophage
     * de douze — alors que les tombes, les torches et les chaudrons etaient,
     * eux, a l'echelle du metre. Deux echelles cohabitaient dans la meme salle,
     * et le fond du donjon se lisait comme une ville de blocs.
     */
    private static final Color PIERRE = Color.web("#6b6b73");

    /**
     * Position d'un point dessine dans le plan d'une dalle inclinee.
     *
     * <p>Une pierre tombale penche, et ce qu'on grave dessus penche avec elle.
     * Poser la croix a l'aplomb du sol la decalait du bord de la dalle d'une
     * demi-largeur des sept degres d'inclinaison : elle debordait dans le vide.
     *
     * @return {x, y, z} monde du point de coordonnees {@code cote, haut} pris
     *         dans le plan de la dalle, {@code avant} devant sa face
     */
    private static double[] surDalle(double x, double y, double z, double yaw,
                                     double tilt, double cote, double haut, double avant) {
        double c = Math.cos(Math.toRadians(tilt)), s = Math.sin(Math.toRadians(tilt));
        double[] p = local(x, z, yaw, avant, cote * c - haut * s);
        return new double[]{p[0], y + cote * s + haut * c, p[1]};
    }

    /**
     * Pierre tombale : socle, stele a sommet cintre, croix en relief.
     *
     * <p>C'etait une dalle nue de vingt-deux centimetres d'epaisseur, plantee
     * de travers : a vingt metres, dans une salle qui en compte des centaines,
     * on lisait un bloc gris parmi des blocs gris. Le socle la pose au sol au
     * lieu de la planter, et la croix claire donne le seul signe qui la nomme
     * sans qu'on ait a s'approcher.
     */
    private static double pierreTombale(DecorBatch b, Random rnd,
                                        double x, double y, double z, double yaw) {
        double h = 1.2 + rnd.nextDouble() * 0.8;
        double w = 0.8 + rnd.nextDouble() * 0.4;
        Color p = PIERRE.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.3, 1);
        double tilt = rnd.nextDouble() * 14 - 7;
        Color clair = p.deriveColor(0, 1, 1.22, 1);
        // socle : la stele penche, lui reste d'aplomb — c'est le contraste des
        // deux qui fait lire l'inclinaison plutot qu'un bloc de guingois
        b.add(Meshes.sharedBox(), w * 1.5, 0.26, 0.62, 0, yaw,
                x, Meshes.jy(y + 0.13), z, p.deriveColor(0, 1, 0.88, 1));
        double[] pied = surDalle(x, y, z, yaw, tilt, 0, 0.26 + h / 2, 0);
        b.add(Meshes.sharedBox(), w, h, 0.26, tilt, yaw,
                pied[0], Meshes.jy(pied[1]), pied[2], p);
        // Le sommet cintre : une demi-sphere ecrasee a l'epaisseur de la stele.
        // C'etait un cylindre couche, et {@code DecorBatch.add} incline autour
        // de Z : son axe tombait donc dans la largeur au lieu de l'epaisseur.
        // Il debordait de trente-sept centimetres devant et derriere, et se
        // lisait de trois quarts comme un disque sombre plante en travers de
        // la stele — un champignon de pierre, vu a la capture.
        double[] cintre = surDalle(x, y, z, yaw, tilt, 0, 0.26 + h, 0);
        b.add(Meshes.sharedSphere(8), w / 2, w * 0.30, 0.13, tilt, yaw,
                cintre[0], Meshes.jy(cintre[1]), cintre[2], p);
        // la croix : deux barres en saillie de six centimetres, en pierre plus
        // claire, sur la face qui regarde le semis
        double[] mont = surDalle(x, y, z, yaw, tilt, 0, 0.26 + h * 0.66, 0.16);
        b.add(Meshes.sharedBox(), w * 0.17, h * 0.52, 0.08, tilt, yaw,
                mont[0], Meshes.jy(mont[1]), mont[2], clair);
        double[] trav = surDalle(x, y, z, yaw, tilt, 0, 0.26 + h * 0.78, 0.16);
        b.add(Meshes.sharedBox(), w * 0.55, h * 0.16, 0.08, tilt, yaw,
                trav[0], Meshes.jy(trav[1]), trav[2], clair);
        return w * 0.75;
    }

    /**
     * Gravats : un a trois blocs de pierre taillee tombes de la voute.
     *
     * <p>Le donjon semait ici le {@code rocher} du repertoire commun — des
     * boules brunes de six metres de large, les plus gros volumes de la salle
     * une fois le mobilier remis a l'echelle, et des galets de riviere sous une
     * voute de pierre.
     *
     * <p>Les blocs qui les ont remplaces etaient <b>plus mauvais encore</b>, et
     * il a fallu la capture pour le voir : trois metres de large, deux metres
     * vingt de haut, un metre soixante-dix d'epaisseur seulement. Ce n'etait
     * plus un bloc mais une <em>dalle dressee</em>, et le bord de piste en
     * comptait une tous les trente metres — des panneaux gris sans lecture,
     * les plus grandes surfaces plates de la salle. Un bloc taille se reconnait
     * a ce qu'il est aussi epais que large : la profondeur suit donc maintenant
     * la largeur, et non plus le seul rayon. Mesures : cinquante centimetres a
     * un metre quarante de large, un metre seize au plus de haut, et une
     * profondeur qui reste entre 0,8 et 0,9 fois la largeur.
     *
     * <p>Ils consomment exactement la meme suite de tirages que {@code rock} —
     * un pour le nombre de blocs, six par bloc — parce que le semis est tire
     * par un {@code Random} de graine fixe : un tirage de plus ou de moins
     * deplacerait tout le decor du circuit a partir d'ici. Seules les bornes
     * ont bouge, jamais leur nombre.
     */
    private static double gravats(DecorBatch b, Random rnd,
                                  double x, double y, double z, double yaw) {
        int blocs = 1 + rnd.nextInt(3);
        double large = 0;
        for (int i = 0; i < blocs; i++) {
            double r = 0.30 + rnd.nextDouble() * 0.25;
            large = Math.max(large, r);
            double a = 0.85 + rnd.nextDouble() * 0.45;
            double h = 0.65 + rnd.nextDouble() * 0.40;
            double ox = (rnd.nextDouble() - 0.5) * r * 2.2;
            double oz = (rnd.nextDouble() - 0.5) * r * 2.2;
            b.add(Meshes.sharedBox(), r * a * 2, r * h * 2, r * a * 1.7,
                    i * 11 - 7, yaw + i * 37,
                    x + ox, Meshes.jy(y + r * h), z + oz,
                    PIERRE.deriveColor(0, 1, 0.72 + rnd.nextDouble() * 0.36, 1));
        }
        return large * 0.9;
    }

    /** Colonne brisee de bord de piste. */
    private static double colonneBrisee(DecorBatch b, Random rnd,
                                        double x, double y, double z, double yaw) {
        return colonneBrisee(b, rnd, x, y, z, yaw, false);
    }

    /**
     * Colonne brisee : plinthe carree, tore, fut casse en biais, tambour tombe.
     *
     * <p>Le fut seul, pose a meme le sol, se lisait comme un tuyau. Ce qui
     * nomme une colonne, c'est sa base a etages et la cassure — d'ou le tambour
     * couche a cote, qui dit d'ou vient le morceau manquant.
     *
     * @param haute fut de huit a quinze metres au lieu d'un a quatre. Au-dela
     *              de cinquante metres de la piste le semis ne pose plus du
     *              mobilier mais l'architecture de la salle : une colonnade qui
     *              monte vers la voute, seule chose qui tienne le fond d'un
     *              donjon sans le remplir de meubles hors d'echelle.
     */
    private static double colonneBrisee(DecorBatch b, Random rnd,
                                        double x, double y, double z, double yaw,
                                        boolean haute) {
        double h = haute ? 8 + rnd.nextDouble() * 7 : 1.4 + rnd.nextDouble() * 2.6;
        double r = haute ? 0.7 + rnd.nextDouble() * 0.5 : 0.42 + rnd.nextDouble() * 0.2;
        Color p = PIERRE.deriveColor(0, 1, 0.76 + rnd.nextDouble() * 0.3, 1);
        b.add(Meshes.sharedBox(), r * 2.7, 0.3, r * 2.7, 0, yaw,
                x, Meshes.jy(y + 0.15), z, p.deriveColor(0, 1, 0.9, 1));
        b.add(Meshes.sharedCylinder(9), r * 1.35, 0.28, r * 1.35, 0, yaw,
                x, Meshes.jy(y + 0.44), z, p.deriveColor(0, 1, 1.1, 1));
        b.add(Meshes.sharedCylinder(9), r, h * 0.55, r, 0, yaw,
                x, Meshes.jy(y + 0.58 + h * 0.275), z, p);
        b.add(Meshes.sharedCylinder(9), r * 0.92, h * 0.45, r * 0.92, 0, yaw,
                x, Meshes.jy(y + 0.58 + h * 0.775), z, p.deriveColor(0, 1, 1.06, 1));
        // la cassure : un dernier tambour de travers, plus clair que le fut
        b.add(Meshes.sharedCylinder(9), r * 1.06, 0.5, r * 1.06, 17, yaw,
                x, Meshes.jy(y + 0.58 + h), z, p.deriveColor(0, 1, 0.86, 1));
        double[] q = local(x, z, yaw, -r * 2.6, r * 1.4);
        b.add(Meshes.sharedCylinder(9), r * 0.85, r * 1.8, r * 0.85, 90, yaw + 35,
                q[0], Meshes.jy(y + r * 0.85), q[1], p.deriveColor(0, 1, 0.92, 1));
        return r * 2;
    }

    /**
     * Crane : calotte, orbites creuses, maxillaire et sa rangee de dents.
     *
     * <p>Une demi-sphere claire et deux points sombres, c'etait une tasse
     * renversee. Les dents et le creux du nez sont ce qui fait basculer la
     * lecture — trois taches sombres en triangle sur une masse claire, et le
     * crane se nomme tout seul.
     */
    private static double crane(DecorBatch b, Random rnd, int sphereDiv,
                                double x, double y, double z, double yaw) {
        Color os = Color.web("#ded6c4").deriveColor(0, 1, 0.95 + rnd.nextDouble() * 0.2, 1);
        double r = 0.24 + rnd.nextDouble() * 0.10;
        Color creux = Color.web("#17140f");
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.95, r, 0, yaw,
                x, Meshes.jy(y + r * 0.95), z, os);
        double[] face = local(x, z, yaw, r * 0.62, 0);
        b.add(Meshes.sharedBox(), r * 1.25, r * 0.55, r * 0.9, 0, yaw,
                face[0], Meshes.jy(y + r * 0.42), face[1], os.deriveColor(0, 1, 0.94, 1));
        // les dents : une barre claire barree de sombre au ras du maxillaire
        double[] dents = local(x, z, yaw, r * 0.95, 0);
        b.add(Meshes.sharedBox(), r * 1.05, r * 0.22, r * 0.16, 0, yaw,
                dents[0], Meshes.jy(y + r * 0.30), dents[1], Color.web("#f4efe2"));
        double[] nez = local(x, z, yaw, r * 1.0, 0);
        b.add(Meshes.sharedCone(6), r * 0.17, r * 0.34, r * 0.17, 180, yaw,
                nez[0], Meshes.jy(y + r * 0.78), nez[1], creux);
        for (int i = -1; i <= 1; i += 2) {
            double[] o = local(x, z, yaw, r * 0.86, i * r * 0.38);
            b.add(Meshes.sharedSphere(6), r * 0.3, r * 0.26, r * 0.22, 0, yaw,
                    o[0], Meshes.jy(y + r * 1.05), o[1], creux);
        }
        return r;
    }

    /**
     * Torche : pied lourd, hampe, corbeille de fer et sa flamme.
     *
     * <p>La flamme est peinte en clair — sans lumiere ponctuelle c'est la seule
     * facon de faire croire qu'elle eclaire — mais elle reste sous le blanc
     * pur : une teinte poussee au-dela de cent pour cent de clarte ressort
     * comme une tache de soleil, ce qui n'a pas de sens sous une voute.
     */
    private static double torche(DecorBatch b, Random rnd, int coneSides,
                                 double x, double y, double z, double yaw) {
        double h = 1.7 + rnd.nextDouble() * 0.7;
        Color fer = Color.web("#2f2a26");
        b.add(Meshes.sharedCylinder(8), 0.3, 0.16, 0.3, 0, yaw,
                x, Meshes.jy(y + 0.08), z, fer);
        b.add(Meshes.sharedCylinder(6), 0.08, h, 0.08, 0, yaw,
                x, Meshes.jy(y + h / 2), z, fer);
        // corbeille : un cone renverse, plus large que la hampe, qui donne a la
        // silhouette le renflement qu'on cherche a mi-hauteur du champ
        b.add(Meshes.sharedCone(coneSides), 0.24, 0.34, 0.24, 180, yaw,
                x, Meshes.jy(y + h + 0.12), z, Color.web("#3a3229"));
        b.add(Meshes.sharedCone(coneSides), 0.17, 0.55, 0.17, 0, yaw,
                x, Meshes.jy(y + h + 0.42), z, Color.web("#e88a2a"));
        b.add(Meshes.sharedCone(coneSides), 0.09, 0.34, 0.09, 0, yaw,
                x, Meshes.jy(y + h + 0.54), z, Color.web("#ffce6e"));
        return 0.3;
    }

    /**
     * Chaudron : panse de fonte sur trois pieds, col evase, anse et brouet.
     *
     * <p>La panse seule etait un galet sombre. Le col et l'anse sont ce qui
     * dit qu'on peut le porter et qu'il est creux — un chaudron se reconnait a
     * son ouverture, pas a son ventre.
     */
    private static double chaudron(DecorBatch b, Random rnd, int sphereDiv,
                                   double x, double y, double z, double yaw) {
        double r = 0.5 + rnd.nextDouble() * 0.3;
        Color fonte = Color.web("#26221f");
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(yaw + i * 120);
            b.add(Meshes.sharedCylinder(5), 0.08, 0.26, 0.08, 0, yaw,
                    x + Math.cos(a) * r * 0.6, Meshes.jy(y + 0.13),
                    z - Math.sin(a) * r * 0.6, fonte);
        }
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.75, r, 0, yaw,
                x, Meshes.jy(y + 0.26 + r * 0.6), z, fonte);
        double bord = y + 0.26 + r * 1.05;
        b.add(Meshes.sharedCylinder(9), r * 0.92, 0.16, r * 0.92, 0, yaw,
                x, Meshes.jy(bord), z, fonte.deriveColor(0, 1, 1.6, 1));
        b.add(Meshes.sharedDisc(9), r * 0.84, 1, r * 0.84, 0, yaw,
                x, Meshes.jy(bord + 0.03), z, Color.web("#5fd08a"));
        // anse : deux montants obliques et leur traverse, au-dessus du brouet
        for (int i = -1; i <= 1; i += 2) {
            double[] m = local(x, z, yaw, 0, i * r * 0.86);
            b.add(Meshes.sharedCylinder(5), 0.06, r * 0.9, 0.06, i * 16, yaw,
                    m[0], Meshes.jy(bord + r * 0.4), m[1], fonte.deriveColor(0, 1, 1.4, 1));
        }
        b.add(Meshes.sharedCylinder(5), 0.06, r * 1.2, 0.06, 90, yaw,
                x, Meshes.jy(bord + r * 0.82), z, fonte.deriveColor(0, 1, 1.4, 1));
        return r;
    }

    // ------------------------------------------------ la table de billard

    /** Les couleurs des billes, dans l'ordre du jeu. */
    private static final Color[] BILLES = {
            Color.web("#f2f0e6"), Color.web("#e9c93a"), Color.web("#2f5fc0"), Color.web("#c0392b"),
            Color.web("#6f3fa8"), Color.web("#e07a2f"), Color.web("#2e8b57"), Color.web("#8a2f3a"),
            Color.web("#1a1a1c")};

    /**
     * Bille de billard, seule ou en petit tas : pleine, rayee, ou la blanche.
     *
     * <p>Le rond du numero etait une sphere aplatie <b>plantee dans le flanc</b>
     * de la bille, et debordant d'un dixieme de rayon. De face on lisait une
     * bille ; de trois quarts, la pastille se detachait sur le feutre comme une
     * lentille blanche posee a cote — deux objets la ou il n'y en a qu'un. Elle
     * est maintenant un disque pose a plat sur le pole, la ou une bille numerotee
     * le porte vraiment, et une bille sur deux troque le rond contre la ceinture
     * de couleur des rayees : c'est cette ceinture, plus que le numero, qui dit
     * « billard » et non « bille de verre ».
     *
     * @param nb nombre de billes du tas
     */
    private static double bouleDeBillard(DecorBatch b, Random rnd, int sphereDiv,
                                         double x, double y, double z, double yaw, int nb) {
        double r = 0.42;
        double widest = 0;
        for (int i = 0; i < nb; i++) {
            int teinte = rnd.nextInt(BILLES.length);
            double ox = i == 0 ? 0 : (rnd.nextDouble() - 0.5) * r * 2.4;
            double oz = i == 0 ? 0 : (rnd.nextDouble() - 0.5) * r * 2.4;
            widest = Math.max(widest, Math.hypot(ox, oz) + r);
            bille(b, sphereDiv, r, teinte, x + ox, y, z + oz, yaw);
        }
        return widest;
    }

    /**
     * Une bille posee sur le feutre.
     *
     * <p>La teinte 0 est l'ivoire : c'est la blanche, elle ne porte ni numero ni
     * ceinture. Les autres alternent pleines et rayees selon la parite du
     * tirage, ce qui ne coute aucun tirage de plus — et le semis d'un circuit
     * entier se decalerait pour un seul appel supplementaire au hasard.
     */
    private static void bille(DecorBatch b, int sphereDiv, double r, int teinte,
                              double x, double y, double z, double yaw) {
        Color c = BILLES[teinte];
        boolean rayee = teinte > 0 && teinte % 2 == 1;
        Color corps = rayee ? BILLES[0] : c;
        b.add(Meshes.sharedSphere(sphereDiv), r, r, r, 0, yaw,
                x, Meshes.jy(y + r), z, corps);
        if (rayee) {
            // la ceinture deborde d'un demi pour cent : moins, elle disparaitrait
            // dans la sphere ; plus, elle ferait un bourrelet. Elle couvre les
            // deux tiers du diametre : a la moitie, exacte pourtant, un rack vu
            // de dessus ne montrait que des poles ivoire — quinze oeufs dans un
            // cadre de bois.
            b.add(Meshes.sharedCylinder(Math.max(7, sphereDiv)), r * 1.005, r * 1.34, r * 1.005,
                    0, yaw, x, Meshes.jy(y + r), z, c);
        } else if (teinte > 0) {
            // le rond du numero : un quart de rayon. A deux cinquiemes, il
            // coiffait la bille d'une assiette blanche
            b.add(Meshes.sharedDisc(Math.max(6, sphereDiv - 3)), r * 0.26, 1, r * 0.26,
                    0, yaw, x, Meshes.jy(y + r * 1.97), z, BILLES[0]);
        }
    }

    /**
     * Cube de craie bleue et sa bande de papier.
     *
     * <p>Vingt-six centimes d'unite, soit trois centimetres a l'etalon des
     * pieces d'interieur : c'etait la vraie taille d'une craie, et c'etait un
     * point sur le tapis. Elle passe a quatre centimetres et demi et prend sa
     * manchette de papier — le seul detail qui la distingue d'un gravier bleu.
     */
    private static double craie(DecorBatch b, Random rnd,
                                double x, double y, double z, double yaw) {
        double c = 0.40;
        double a = yaw + rnd.nextDouble() * 40;
        Color bleu = Color.web("#2f6fa8").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1);
        b.add(Meshes.sharedBox(), c, c, c, 0, a, x, Meshes.jy(y + c / 2), z, bleu);
        b.add(Meshes.sharedBox(), c * 1.06, c * 0.44, c * 1.06, 0, a,
                x, Meshes.jy(y + c * 0.40), z, bleu.deriveColor(0, 0.55, 1.45, 1));
        return c * 0.7;
    }

    /**
     * Queue de billard posee sur le feutre : talon, bague, fleche, virole.
     *
     * <p>Deux pannes, et la meme piece les portait toutes les deux. La
     * longueur, d'abord : cinq a sept unites, soit soixante a soixante-dix-huit
     * centimetres a l'etalon des pieces d'interieur — une demi-queue. Le
     * decalage du talon, ensuite : il etait pose a {@code (-sin a, -cos a)} de
     * l'axe, c'est-a-dire <b>en travers</b>, quand l'axe du cylindre couche
     * court en {@code (-cos a, sin a)}. Le manche gisait donc a cote de la
     * fleche au lieu d'en etre le prolongement, et de loin on lisait deux
     * madriers paralleles — le chevron de charpente vu sur les captures.
     *
     * <p>Un metre quarante, cinq troncons, et le decalage pris sur l'axe par
     * {@link #local} : la silhouette effilee se lit du talon a la virole.
     */
    private static double queueDeBillard(DecorBatch b, Random rnd,
                                         double x, double y, double z, double yaw) {
        double l = 12.4 + rnd.nextDouble() * 1.8;
        double a = yaw + rnd.nextDouble() * 30;
        // {longueur, rayon, position sur l'axe} et la teinte du troncon
        double[][] troncons = {{0.38 * l, 0.155, -0.31 * l}, {0.05 * l, 0.165, -0.095 * l},
                {0.54 * l, 0.112, 0.19 * l}, {0.030 * l, 0.098, 0.475 * l},
                {0.020 * l, 0.092, 0.500 * l}};
        Color[] teintes = {Color.web("#3a2a1c"), Color.web("#c9a227"), Color.web("#d8b271"),
                Color.web("#eae2d0"), Color.web("#2f6fa8")};
        for (int i = 0; i < troncons.length; i++) {
            double[] q = local(x, z, a, 0, troncons[i][2]);
            b.add(Meshes.sharedCylinder(7), troncons[i][1], troncons[i][0], troncons[i][1],
                    90, a, q[0], Meshes.jy(y + 0.165), q[1], teintes[i]);
        }
        return 0.4;
    }

    /**
     * Triangle de rack et ses quinze billes, pose a plat sur le feutre.
     *
     * <p>Les trois barres ne fermaient pas de triangle : chacune etait portee a
     * trente degres de son propre axe au lieu de quatre-vingt-dix, si bien que
     * les extremites ne se rejoignaient pas et que la figure tournait en
     * moulinet. Deux centimetres et demi de section, deux metres soixante de
     * cote a l'etalon — trente centimetres reels, c'est juste — mais un
     * moulinet de baguettes brunes ne se nomme pas.
     *
     * <p>Le cadre est maintenant construit sur son cercle inscrit, et il est
     * <b>garni</b> : c'est le triangle plein de billes, pas le bois seul, qui
     * dit le billard au premier coup d'oeil. Cinq billes par cote fixent le
     * cote interieur, et l'epaisseur du bois le cote exterieur.
     */
    private static double triangleDeRack(DecorBatch b, Random rnd, int sphereDiv,
                                         double x, double y, double z, double yaw) {
        double d = 0.84;                       // diametre d'une bille
        double dedans = 5 * d;                 // cinq billes par cote
        double bois = 0.44;                    // section du cadre
        double rInterieur = dedans / (2 * Math.sqrt(3));
        double rBarre = rInterieur + bois / 2;
        // la barre porte la longueur du cote *median*, pas celle du cote
        // exterieur : debitee a l'exterieur elle depassait de quatre dixiemes a
        // chaque bout, et les trois coins faisaient etoile
        double cote = 2 * Math.sqrt(3) * rBarre + bois;
        Color chene = Color.web("#8a5a2f");
        for (int i = 0; i < 3; i++) {
            double beta = yaw + i * 120;
            double[] q = local(x, z, beta, 0, rBarre);
            // la barre est perpendiculaire au rayon qui la porte : c'est cette
            // rotation d'un quart de tour qui manquait
            b.add(Meshes.sharedBox(), cote, 0.52, bois, 0, beta + 90,
                    q[0], Meshes.jy(y + 0.26), q[1], chene);
        }
        // les quinze billes, rangee par rangee depuis la barre de base
        int div = Math.min(sphereDiv, 7);
        int teinte = 0;
        for (int rangee = 0; rangee < 5; rangee++) {
            double avance = rInterieur - d / 2 - rangee * d * Math.sqrt(3) / 2;
            int n = 5 - rangee;
            for (int i = 0; i < n; i++) {
                double[] q = local(x, z, yaw, (i - (n - 1) / 2.0) * d, avance);
                bille(b, div, d / 2, teinte % BILLES.length, q[0], y, q[1], yaw);
                teinte++;
            }
        }
        return cote * 0.62;
    }

    /**
     * Poche : deux machoires de bande, la gueule noire et le sac de cuir.
     *
     * <p>C'etait un disque sombre de dix-sept centimetres cercle d'un anneau
     * brun, pose a plat : sur un tapis vert, a la vitesse ou on passe, on ne
     * voyait rien du tout — et ce qu'on aurait vu, un rond sombre cercle sur le
     * sol, est justement la silhouette de la plaque d'egout, la seule chose
     * qu'une piece d'interieur ne doit jamais montrer.
     *
     * <p>Une poche ne se lit pas par son trou mais par ce qui l'encadre : les
     * deux machoires de bande qui s'ouvrent en entonnoir, et le cuir dessous.
     * Elle est donc batie quatre fois plus grande que l'echelle des autres
     * pieces — quarante centimetres de gueule — parce qu'a la taille exacte
     * elle redevient invisible ; c'est la meme licence que prend l'ouvrage
     * homonyme, la poche geante qu'on traverse a mi-parcours, et elle va dans
     * le meme sens.
     */
    private static double poche(DecorBatch b, Random rnd,
                                double x, double y, double z, double yaw) {
        Color bois = Color.web("#5b3c22");
        Color feutre = Color.web("#1f7a46");
        Color cuir = Color.web("#3f2a1c");
        double r = 1.9;
        // les deux machoires, ouvertes de dix-huit degres vers la piste
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, -1.4, i * 4.3);
            b.add(Meshes.sharedBox(), 5.6, 1.8, 2.6, 0, yaw + i * 18,
                    q[0], Meshes.jy(y + 0.9), q[1], bois);
            double[] t = local(q[0], q[1], yaw + i * 18, 0.75, 0);
            b.add(Meshes.sharedBox(), 5.6, 0.45, 1.1, 0, yaw + i * 18,
                    t[0], Meshes.jy(y + 1.85), t[1], feutre);
        }
        // La gueule est une ouverture debout, pas un rond par terre. Le cuir a
        // d'abord ete un cylindre coiffe d'un disque noir — un tonneau contre
        // un mur — puis un disque a plat, qu'on ne voyait plus du tout : le sol
        // est en pente sous la piece et la camera rase le feutre, si bien qu'un
        // rond au sol se derobe derriere la machoire la plus proche. Une poche
        // se lit a son noir : un panneau sombre tendu entre les deux machoires,
        // et le rebord de cuir a son pied.
        double[] g = local(x, z, yaw, -1.5, 0);
        b.add(Meshes.sharedBox(), r * 2.0, 2.4, 0.7, 0, yaw,
                g[0], Meshes.jy(y + 1.2), g[1], Color.web("#120c08"));
        double[] l = local(x, z, yaw, -0.7, 0);
        b.add(Meshes.sharedBox(), r * 2.1, 0.35, 1.8, 0, yaw,
                l[0], Meshes.jy(y + 0.18), l[1], cuir);
        return r * 1.6;
    }

    // ------------------------------------------------------------ le bar

    private static final Color[] VERRES = {
            Color.web("#3f7a3f"), Color.web("#7a4a2a"), Color.web("#2f4f7a"),
            Color.web("#6a2f3f"), Color.web("#c9b26a")};

    /**
     * Bouteille : panse, etiquette, epaule, col et capsule.
     *
     * <p>Elle culminait a deux metres quatre quand le comptoir qu'elle borde en
     * fait sept et demi. Le salon fixe le rapport de ces pieces d'interieur :
     * son canape mesure quinze a vingt et un metres pour deux metres reels, sa
     * bibliotheque seize a vingt-deux pour deux — un metre reel vaut donc neuf
     * metres de jeu. La bouteille faisait vingt-deux centimetres a cette
     * mesure, moitie moins qu'une vraie ; elle monte a trois metres. L'unique
     * volume ajoute est l'etiquette : de loin, c'est sa bande claire qui
     * distingue une bouteille d'un tube.
     */
    private static double bouteille(DecorBatch b, Random rnd,
                                    double x, double y, double z, double yaw) {
        double h = 2.6 + rnd.nextDouble() * 1.0;
        double r = 0.42;
        Color verre = VERRES[rnd.nextInt(VERRES.length)]
                .deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.3, 1);
        b.add(Meshes.sharedCylinder(9), r, h * 0.62, r, 0, yaw,
                x, Meshes.jy(y + h * 0.31), z, verre);
        b.add(Meshes.sharedCylinder(9), r * 1.05, h * 0.24, r * 1.05, 0, yaw,
                x, Meshes.jy(y + h * 0.30), z, Color.web("#e8dcc0"));
        // la panse s'arrete a 0,62 h : l'epaule s'ancrait a 0,71 h, c'est-a-dire
        // a la demi-hauteur du cone au-dessus, et laissait trente centimetres
        // de vide entre la panse et le col
        b.add(Meshes.sharedCone(9), r, h * 0.18, r, 0, yaw,
                x, Meshes.jy(y + h * 0.62), z, verre);
        b.add(Meshes.sharedCylinder(7), r * 0.34, h * 0.28, r * 0.34, 0, yaw,
                x, Meshes.jy(y + h * 0.86), z, verre);
        b.add(Meshes.sharedCylinder(7), r * 0.40, h * 0.08, r * 0.40, 0, yaw,
                x, Meshes.jy(y + h), z, Color.web("#c9a227"));
        return r * 1.2;
    }

    /**
     * Verre a pied : socle, tige, calice et son fond de vin.
     *
     * <p>Il faisait un metre trois de haut, soit onze centimetres au rapport du
     * salon. Un verre a vin en fait vingt : la piece est reglee sur cette
     * mesure, forme inchangee — de face comme de dos, un calice sur pied se
     * nomme du premier coup d'oeil.
     */
    private static double verreAPied(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        double h = 1.5 + rnd.nextDouble() * 0.6;
        Color cristal = Color.web("#cfe4ef");
        b.add(Meshes.sharedCylinder(9), 0.42, 0.08, 0.42, 0, yaw,
                x, Meshes.jy(y + 0.04), z, cristal);
        b.add(Meshes.sharedCylinder(6), 0.08, h * 0.45, 0.08, 0, yaw,
                x, Meshes.jy(y + h * 0.25), z, cristal);
        b.add(Meshes.sharedCone(coneSides), 0.46, h * 0.5, 0.46, 180, yaw,
                x, Meshes.jy(y + h * 0.72), z, cristal);
        // un fond de vin
        b.add(Meshes.sharedCone(coneSides), 0.29, h * 0.22, 0.29, 180, yaw,
                x, Meshes.jy(y + h * 0.58), z, Color.web("#7a1f2f"));
        return 0.46;
    }

    /**
     * Chope : le verre, la mousse qui deborde, l'anse.
     *
     * <p>Un demi-litre fait quinze centimetres de haut, soit un metre trente-
     * cinq au rapport du salon ; elle en faisait un tout juste. La forme
     * tenait deja — c'est la mousse blanche debordante qui la nomme —, seules
     * les mesures bougent.
     */
    private static double chope(DecorBatch b, Random rnd, int sphereDiv,
                                double x, double y, double z, double yaw) {
        double h = 1.25 + rnd.nextDouble() * 0.35;
        double r = 0.44;
        b.add(Meshes.sharedCylinder(9), r, h, r, 0, yaw,
                x, Meshes.jy(y + h / 2), z, Color.web("#d8a63c"));
        b.add(Meshes.sharedCylinder(8), r * 0.94, h * 0.16, r * 0.94, 0, yaw,
                x, Meshes.jy(y + h + 0.06), z, Color.web("#f7f1e0"));
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.55, r * 0.4, r * 0.55, 0, yaw,
                x + r * 0.3, Meshes.jy(y + h + 0.16), z, Color.web("#fbf7ea"));
        double a = Math.toRadians(yaw);
        b.add(Meshes.sharedCylinder(6), 0.09, h * 0.55, 0.09, 0, yaw,
                x + Math.cos(a) * r * 1.25, Meshes.jy(y + h * 0.5), z - Math.sin(a) * r * 1.25,
                Color.web("#c9973a"));
        return r * 1.3;
    }

    /**
     * Tabouret de bar : quatre pieds, la ceinture de repose-pied, l'assise
     * rembourree.
     *
     * <p>Il mesurait un metre soixante-dix a deux metres dix pour un comptoir
     * de sept metres et demi : au rapport du salon, un tabouret de vingt
     * centimetres, un marchepied. Il monte a l'aplomb du plateau — sept metres,
     * soit soixante-quinze centimetres, la mesure d'un vrai — et gagne la
     * ceinture de repose-pied a mi-hauteur. C'est elle, plus que la hauteur,
     * qui distingue un tabouret de bar d'une chaise : rien d'autre dans une
     * salle n'a de barreau a mi-jambe.
     *
     * <p>Les pieds sont droits et non evases : le pincement passe par
     * {@code tilt}, qui incline la piece autour de son <em>milieu</em>, si bien
     * que l'evasement d'un cote enterre l'autre bout. Quatre pieds verticaux et
     * une assise plus large qu'eux donnent la meme silhouette sans ce risque.
     */
    private static double tabouret(DecorBatch b, Random rnd,
                                   double x, double y, double z, double yaw) {
        double h = 6.6 + rnd.nextDouble() * 1.2;
        double r = 1.95;
        double e = r * 0.82;
        Color bois = Color.web("#6b4526");
        for (int i = 0; i < 4; i++) {
            double[] q = local(x, z, yaw + 45 + i * 90, 0, e);
            b.add(Meshes.sharedCylinder(6), 0.27, h, 0.27, 0, yaw,
                    q[0], Meshes.jy(y + h / 2), q[1], bois);
        }
        // la ceinture : quatre barreaux qui relient les pieds deux a deux, donc
        // portes par le milieu de chaque cote et tournes d'un quart de tour
        for (int i = 0; i < 4; i++) {
            double phi = yaw + i * 90;
            double[] q = local(x, z, phi, 0, e * 0.71);
            b.add(Meshes.sharedBox(), e * 1.44, 0.34, 0.34, 0, phi + 90,
                    q[0], Meshes.jy(y + h * 0.34), q[1], bois.deriveColor(0, 1, 1.15, 1));
        }
        b.add(Meshes.sharedCylinder(10), r, 0.5, r, 0, yaw,
                x, Meshes.jy(y + h + 0.25), z, bois);
        b.add(Meshes.sharedCylinder(10), r * 0.96, 0.8, r * 0.96, 0, yaw,
                x, Meshes.jy(y + h + 0.9), z, Color.web("#b0392e"));
        return r * 1.1;
    }

    /**
     * Tonneau : panse renflee, deux abouts pinces, cercles de fer et fonds
     * clairs. Couche ou debout, un tirage sur cinq le couche.
     *
     * <p>C'etait un cylindre lisse de deux metres pour une bouteille de deux :
     * au rapport du salon, un tonneau de vingt-deux centimetres, plus petit
     * qu'un litre de biere. Il passe a sept metres — quatre-vingts centimetres,
     * la mesure d'une barrique — et cesse d'etre un tube : les abouts sont
     * pinces a quatre-vingt-six pour cent de la panse, ce qui donne le galbe,
     * et les fonds clairs se voient de bout, la ou un cylindre sombre n'offrait
     * qu'un disque de la meme couleur que ses douves.
     */
    private static double tonneau(DecorBatch b, Random rnd,
                                  double x, double y, double z, double yaw) {
        double r = 2.0 + rnd.nextDouble() * 0.8;
        double h = r * 2.55;
        boolean couche = rnd.nextDouble() < 0.4;
        double tilt = couche ? 90 : 0;
        double cy = couche ? r : h / 2;
        Color douve = Color.web("#a06a30").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
        // l'axe du fut : la longueur du cote quand il est couche, la verticale
        // quand il est debout — un seul jeu de decalages pour les deux cas
        double ax = couche ? Math.cos(Math.toRadians(yaw)) : 0;
        double az = couche ? -Math.sin(Math.toRadians(yaw)) : 0;
        double ay = couche ? 0 : 1;
        double[][] fut = {{0, r, h * 0.54}, {h * 0.36, r * 0.86, h * 0.28},
                {-h * 0.36, r * 0.86, h * 0.28}};
        for (double[] f : fut) {
            b.add(Meshes.sharedCylinder(11), f[1], f[2], f[1], tilt, yaw,
                    x + ax * f[0], Meshes.jy(y + cy + ay * f[0]), z + az * f[0], douve);
        }
        for (int i = -1; i <= 1; i += 2) {
            double d = i * h * 0.24;
            b.add(Meshes.sharedCylinder(11), r * 1.03, h * 0.08, r * 1.03, tilt, yaw,
                    x + ax * d, Meshes.jy(y + cy + ay * d), z + az * d, Color.web("#37373d"));
            double f = i * h * 0.5;
            b.add(Meshes.sharedCylinder(11), r * 0.78, h * 0.05, r * 0.78, tilt, yaw,
                    x + ax * f, Meshes.jy(y + cy + ay * f), z + az * f,
                    douve.deriveColor(0, 1, 1.28, 1));
        }
        return couche ? h * 0.5 : r * 1.05;
    }

    /**
     * Bol de cacahuetes.
     *
     * <p>Le bol faisait un metre de diametre, onze centimetres au rapport du
     * salon : un de a coudre. Un bol d'apero en fait vingt.
     */
    private static double bolDeCacahuetes(DecorBatch b, Random rnd, int sphereDiv,
                                          double x, double y, double z, double yaw) {
        double r = 0.95;
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.5, r, 0, yaw,
                x, Meshes.jy(y + r * 0.25), z, Color.web("#d8d2c4"));
        for (int i = 0; i < 4; i++) {
            b.add(Meshes.sharedSphere(6), 0.21, 0.15, 0.21, 0, yaw,
                    x + (rnd.nextDouble() - 0.5) * r, Meshes.jy(y + r * 0.42),
                    z + (rnd.nextDouble() - 0.5) * r, Color.web("#c8a05a"));
        }
        return r;
    }

    /** Pile de sous-verres. */
    private static double sousVerres(DecorBatch b, Random rnd,
                                     double x, double y, double z, double yaw) {
        int n = 2 + rnd.nextInt(3);
        for (int i = 0; i < n; i++) {
            b.add(Meshes.sharedCylinder(10), 0.46, 0.06, 0.46, 0, yaw + i * 17,
                    x, Meshes.jy(y + 0.03 + i * 0.06), z,
                    i % 2 == 0 ? Color.web("#c9b8a0") : Color.web("#a8907a"));
        }
        return 0.46;
    }

    /**
     * Cible de flechettes : panneau au sol, deux battants ouverts, cible et
     * trois flechettes plantees.
     *
     * <p>Elle tenait en un disque de soixante centimetres de jeu perche sur un
     * piquet de deux metres : au rapport du salon, une cible de sept
     * centimetres a hauteur de genou, qu'on prenait a l'ecran pour une sucette
     * ou un panneau. Elle passe a quatre metres soixante de diametre —
     * cinquante centimetres, la mesure d'une vraie avec son cerclage — et son
     * centre monte a treize metres, un metre quarante-cinq.
     *
     * <p>Le panneau a battants est une <b>invention</b> : TuxKart n'a pas de
     * cible de flechettes, et rien dans le jeu ne dit a quoi elle devrait
     * ressembler. Il repond a une contrainte du semis, qui oriente les pieces
     * au hasard : sans lui la cible n'existe que de face, et se presente de dos
     * une fois sur deux comme un disque sans epaisseur. Les deux battants
     * ouverts portent les ardoises de marque, et donnent de trois quarts la
     * silhouette en eventail qui n'appartient qu'a cette piece.
     */
    private static double cible(DecorBatch b, Random rnd,
                                double x, double y, double z, double yaw) {
        double axe = 11.5;
        double c = 3.0;
        Color bois = Color.web("#4a2f1c");
        // Le panneau descend jusqu'au sol, il ne perche pas.
        //
        // Le caisson etait porte par un mat : de face il se lisait, mais de dos
        // — une fois sur deux, le semis orientant les pieces au hasard — c'etait
        // un tableau brun juche a treize metres, soit exactement la silhouette
        // d'un panneau publicitaire, la seule chose qu'une piece d'interieur ne
        // doit jamais montrer. Il est maintenant adosse a un panneau de bois
        // pose au sol : de dos, une cloison de coin de salle.
        b.add(Meshes.sharedBox(), 8.0, 1.0, 3.2, 0, yaw,
                x, Meshes.jy(y + 0.5), z, bois.deriveColor(0, 1, 0.8, 1));
        b.add(Meshes.sharedBox(), 7.0, axe + 4.0, 1.2, 0, yaw,
                x, Meshes.jy(y + 1.0 + (axe + 4.0) / 2), z, bois);
        b.add(Meshes.sharedBox(), 7.6, 0.9, 1.8, 0, yaw,
                x, Meshes.jy(y + axe + 4.6), z, bois.deriveColor(0, 1, 1.25, 1));
        // les deux battants ouverts, charnieres aux montants
        double ouv = Math.toRadians(55);
        for (int i = -1; i <= 1; i += 2) {
            double demi = c * 0.5;
            double cote = i * (3.5 + demi * Math.cos(ouv));
            double avant = 0.6 + demi * Math.sin(ouv);
            double[] q = local(x, z, yaw, avant, cote);
            double cap = i > 0 ? yaw + Math.toDegrees(ouv) : yaw + 180 - Math.toDegrees(ouv);
            b.add(Meshes.sharedBox(), c, c * 2, 0.5, 0, cap,
                    q[0], Meshes.jy(y + axe), q[1], bois);
            // l'ardoise de marque, sur la face interieure du battant
            double[] p = local(q[0], q[1], cap, -0.35, 0);
            b.add(Meshes.sharedBox(), c * 0.78, c * 1.6, 0.2, 0, cap,
                    p[0], Meshes.jy(y + axe), p[1], Color.web("#2b3a30"));
        }
        // la cible : cerclage noir, couronne creme, anneau rouge, coeur
        double[][] anneaux = {{2.3, 0.6, 0.0}, {2.0, 0.4, 0.35}, {1.25, 0.35, 0.45},
                {0.72, 0.32, 0.52}, {0.3, 0.3, 0.58}};
        Color[] teintes = {Color.web("#1e1e22"), Color.web("#e8dcbe"), Color.web("#c9302c"),
                Color.web("#e8dcbe"), Color.web("#2f8a44")};
        for (int i = 0; i < anneaux.length; i++) {
            double[] q = local(x, z, yaw, 0.6 + anneaux[i][2], 0);
            b.add(Meshes.sharedCylinder(12), anneaux[i][0], anneaux[i][1], anneaux[i][0],
                    90, yaw, q[0], Meshes.jy(y + axe), q[1], teintes[i]);
        }
        // trois flechettes plantees, fut clair et empenne de couleur
        double[][] tirs = {{0.5, 0.9}, {-0.8, -0.2}, {0.2, -1.1}};
        Color[] plumes = {Color.web("#f0c020"), Color.web("#2f6fd0"), Color.web("#3aa84a")};
        for (int i = 0; i < tirs.length; i++) {
            double[] q = local(x, z, yaw, 1.6, tirs[i][0]);
            b.add(Meshes.sharedCylinder(5), 0.13, 1.5, 0.13, 90, yaw,
                    q[0], Meshes.jy(y + axe + tirs[i][1]), q[1], Color.web("#c9c9d2"));
            double[] f = local(x, z, yaw, 2.5, tirs[i][0]);
            b.add(Meshes.sharedBox(), 0.12, 0.7, 0.7, 0, yaw,
                    f[0], Meshes.jy(y + axe + tirs[i][1]), f[1], plumes[i]);
        }
        return c * 1.6;
    }

    // ---------------------------------------------------------- le salon

    private static final Color[] JOUETS = {
            Color.web("#d33b30"), Color.web("#2f6fd0"), Color.web("#f0c020"),
            Color.web("#3aa84a"), Color.web("#e07ab0"), Color.web("#f08030")};

    /**
     * Brique a tenons, la piece maitresse du salon.
     *
     * <p>Les proportions sont celles d'une vraie brique, au pas de tenon pres :
     * hauteur 1,2 pas, tenon large de 0,6 pas et haut de 0,22. Elles ne
     * l'etaient pas — un tenon de 0,16 sur un pas de 0,8 en faisait une dalle
     * verruqueuse, et la brique ne se lisait pas de loin. Deux rangees de
     * tenons, aussi : une seule donnait une reglette.
     *
     * <p>Le pas, lui, valait 0,8 : une brique de quatre tenons faisait alors
     * 3,2 de long contre 1,8 a la petite voiture posee a cote d'elle, quand un
     * jouet reel en fait la moitie. A 0,45, la brique va de 0,9 a 1,8 selon le
     * nombre de tenons — au plus la longueur de la voiture, un peu plus de la
     * moitie du kart. Descendre plus bas rendrait le tenon, large de 0,27,
     * invisible des la deuxieme rangee de mobilier.
     */
    private static double brique(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw) {
        Color c = JOUETS[rnd.nextInt(JOUETS.length)];
        double pas = 0.45;
        int tenons = 2 + (int) (rnd.nextDouble() * 3);
        double w = tenons * pas, d = 2 * pas, h = pas * 1.2;
        b.add(Meshes.sharedBox(), w, h, d, 0, yaw, x, Meshes.jy(y + h / 2), z, c);
        Color dessus = c.deriveColor(0, 1, 1.1, 1);
        for (int j = 0; j < 2; j++) {
            for (int i = 0; i < tenons; i++) {
                double[] q = local(x, z, yaw, (j - 0.5) * pas, (i - (tenons - 1) / 2.0) * pas);
                b.add(Meshes.sharedCylinder(8), pas * 0.6, pas * 0.22, pas * 0.6, 0, yaw,
                        q[0], Meshes.jy(y + h + pas * 0.11), q[1], dessus);
            }
        }
        return w * 0.5;
    }

    /**
     * Cube d'eveil : un cube de bois et sa vignette de couleur.
     *
     * <p>La vignette portait son propre lacet, tire a part de celui du cube :
     * elle flottait donc de travers devant une face qui regardait ailleurs. Un
     * seul cap pour les deux, et trois faces vignetees plutot qu'une — de
     * trois quarts, un cube de bois uni n'est qu'un caillou carre.
     */
    private static double cubeDeBois(DecorBatch b, Random rnd,
                                     double x, double y, double z, double yaw) {
        double c = 0.7 + rnd.nextDouble() * 0.3;
        double ang = yaw + rnd.nextDouble() * 40;
        Color bois = Color.web("#e0b878").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
        double v = 0.45 + rnd.nextDouble() * 0.15;
        Color vignette = JOUETS[rnd.nextInt(JOUETS.length)];
        b.add(Meshes.sharedBox(), c, c, c, 0, ang, x, Meshes.jy(y + c / 2), z, bois);
        b.add(Meshes.sharedBox(), c * v, 0.04, c * v, 0, ang,
                x, Meshes.jy(y + c + 0.02), z, vignette);
        double[] f = local(x, z, ang, c * 0.51, 0);
        b.add(Meshes.sharedBox(), c * v, c * v, 0.04, 0, ang,
                f[0], Meshes.jy(y + c / 2), f[1], vignette);
        double[] g = local(x, z, ang, 0, c * 0.51);
        b.add(Meshes.sharedBox(), 0.04, c * v, c * v, 0, ang,
                g[0], Meshes.jy(y + c / 2), g[1], vignette);
        return c * 0.7;
    }

    /**
     * Crayon de couleur, couche sur le tapis.
     *
     * <p>Le bois taille etait pose sur la perpendiculaire au crayon, pas dans
     * son axe : deux batons croises au lieu d'un crayon. Il l'est maintenant
     * par {@link #local}, et le bout porte sa mine, sa bague et sa gomme —
     * c'est par ses deux extremites qu'un crayon se reconnait, pas par son fut.
     */
    private static double crayon(DecorBatch b, Random rnd, int coneSides,
                                 double x, double y, double z, double yaw) {
        Color c = JOUETS[rnd.nextInt(JOUETS.length)];
        double l = 2.8 + rnd.nextDouble() * 1.4;
        double a = yaw + rnd.nextDouble() * 40;
        double r = 0.15;
        int pans = Math.max(5, coneSides / 2);
        b.add(Meshes.sharedCylinder(6), r, l, r, 90, a, x, Meshes.jy(y + r), z, c);
        // le bout taille : bois clair puis mine
        double[] p1 = local(x, z, a, 0, l / 2 + 0.22);
        b.add(Meshes.sharedCone(pans), r, 0.45, r, -90, a,
                p1[0], Meshes.jy(y + r), p1[1], Color.web("#e8c88c"));
        double[] p2 = local(x, z, a, 0, l / 2 + 0.44);
        b.add(Meshes.sharedCone(pans), r * 0.42, 0.2, r * 0.42, -90, a,
                p2[0], Meshes.jy(y + r), p2[1], Color.web("#2e2a28"));
        // l'autre bout : bague de metal et gomme
        double[] p3 = local(x, z, a, 0, -(l / 2 + 0.09));
        b.add(Meshes.sharedCylinder(6), r * 1.08, 0.18, r * 1.08, 90, a,
                p3[0], Meshes.jy(y + r), p3[1], Color.web("#b9bec4"));
        double[] p4 = local(x, z, a, 0, -(l / 2 + 0.3));
        b.add(Meshes.sharedCylinder(6), r * 0.98, 0.24, r * 0.98, 90, a,
                p4[0], Meshes.jy(y + r), p4[1], Color.web("#e88fa0"));
        return 0.3;
    }

    /**
     * Petite voiture de bois : caisse, cabine et quatre roues.
     *
     * <p>Elle mesurait 1,8 pour 0,9 de large, soit un carrosse aussi court que
     * large a l'oeil et plus court qu'une brique de quatre tenons. Deux metres
     * vingt lui donnent le rapport de deux et demi qu'a une voiture, et la
     * remettent au-dessus des briques qui trainent autour.
     */
    private static double petiteVoiture(DecorBatch b, Random rnd,
                                        double x, double y, double z, double yaw) {
        Color c = JOUETS[rnd.nextInt(JOUETS.length)];
        double l = 2.2, w = 0.9, h = 0.45;
        b.add(Meshes.sharedBox(), l, h, w, 0, yaw, x, Meshes.jy(y + 0.3 + h / 2), z, c);
        // la cabine se calait sur le Z du monde : elle glissait hors de la
        // caisse des que la voiture n'etait pas orientee au nord
        double[] cab = local(x, z, yaw, -0.15, 0);
        b.add(Meshes.sharedBox(), l * 0.46, h * 0.9, w * 0.82, 0, yaw,
                cab[0], Meshes.jy(y + 0.3 + h + h * 0.45), cab[1], c.deriveColor(0, 1, 1.14, 1));
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                double[] q = local(x, z, yaw, j * w * 0.5, i * l * 0.32);
                b.add(Meshes.sharedCylinder(8), 0.28, 0.16, 0.28, 90, yaw,
                        q[0], Meshes.jy(y + 0.28), q[1], Color.web("#2a2a2e"));
            }
        }
        return l * 0.6;
    }

    /**
     * De a jouer.
     *
     * <p>Trois points sur la seule face du dessus ne se voyaient que du ciel :
     * de cote, c'etait un cube blanc. Les cinq faces visibles portent donc les
     * leurs, opposees deux a deux comme sur un vrai de — 1 en haut, 2 et 5,
     * 3 et 4. Les points sont de petites plaques et non des spheres : a cette
     * taille rien ne les distingue, et douze triangles valent mieux que
     * soixante-douze la ou le salon en pose une centaine.
     */
    private static double de(DecorBatch b, Random rnd,
                             double x, double y, double z, double yaw) {
        double c = 0.8 + rnd.nextDouble() * 0.3;
        double ang = yaw + rnd.nextDouble() * 90;
        Color point = Color.web("#26221f");
        b.add(Meshes.sharedBox(), c, c, c, 0, ang, x, Meshes.jy(y + c / 2), z,
                Color.web("#f4f1e8"));
        double s = c * 0.19, d = c * 0.24, e = c * 0.505;
        int[][] un = {{0, 0}};
        int[][] deux = {{-1, -1}, {1, 1}};
        int[][] trois = {{-1, -1}, {0, 0}, {1, 1}};
        int[][] quatre = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
        int[][] cinq = {{-1, -1}, {-1, 1}, {0, 0}, {1, -1}, {1, 1}};
        for (int[] p : un) {
            double[] q = local(x, z, ang, p[1] * d, p[0] * d);
            b.add(Meshes.sharedBox(), s, 0.04, s, 0, ang,
                    q[0], Meshes.jy(y + c + 0.02), q[1], point);
        }
        for (int i = -1; i <= 1; i += 2) {
            for (int[] p : i > 0 ? deux : cinq) {
                double[] q = local(x, z, ang, p[0] * d, i * e);
                b.add(Meshes.sharedBox(), 0.04, s, s, 0, ang,
                        q[0], Meshes.jy(y + c / 2 + p[1] * d), q[1], point);
            }
            for (int[] p : i > 0 ? trois : quatre) {
                double[] q = local(x, z, ang, i * e, p[0] * d);
                b.add(Meshes.sharedBox(), s, s, 0.04, 0, ang,
                        q[0], Meshes.jy(y + c / 2 + p[1] * d), q[1], point);
            }
        }
        return c * 0.7;
    }

    /** Quille de bois, avec sa tete ronde. */
    private static double quille(DecorBatch b, Random rnd, int sphereDiv,
                                 double x, double y, double z, double yaw) {
        double h = 1.2 + rnd.nextDouble() * 0.4;
        Color c = rnd.nextDouble() < 0.5 ? Color.web("#f4f1e8") : JOUETS[rnd.nextInt(JOUETS.length)];
        b.add(Meshes.sharedCone(9), 0.28, h * 0.7, 0.28, 0, yaw,
                x, Meshes.jy(y + h * 0.35), z, c);
        b.add(Meshes.sharedSphere(sphereDiv), 0.2, 0.24, 0.2, 0, yaw,
                x, Meshes.jy(y + h * 0.82), z, c);
        return 0.3;
    }

    /** Ballon, a moitie degonfle sur le tapis. */
    private static double ballon(DecorBatch b, Random rnd, int sphereDiv,
                                 double x, double y, double z, double yaw) {
        double r = 0.75 + rnd.nextDouble() * 0.4;
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.92, r, 0, yaw,
                x, Meshes.jy(y + r * 0.9), z, JOUETS[rnd.nextInt(JOUETS.length)]);
        return r;
    }

    /**
     * Ours en peluche assis : la piece rare du salon.
     *
     * <p>Il mesurait seize centimetres a l'echelle de la piece — un canape en
     * fait deux metres, une bibliotheque deux — et se perdait donc contre les
     * meubles du fond, la ou le README en fait « le gros ours ». Il tient
     * maintenant quarante-cinq centimetres, et il est assis : deux cuisses
     * devant, deux bras, un museau et deux yeux. Sans les yeux, de face, ce
     * n'etait qu'un tas de boules brunes.
     */
    private static double nounours(DecorBatch b, Random rnd, int sphereDiv,
                                   double x, double y, double z, double yaw) {
        Color poil = Color.web("#b07c3e").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
        Color clair = poil.deriveColor(0, 0.7, 1.25, 1);
        double r = 1.3;
        // le corps, puis la tete
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 1.05, r * 0.9, 0, yaw,
                x, Meshes.jy(y + r * 1.0), z, poil);
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.72, r * 0.72, r * 0.72, 0, yaw,
                x, Meshes.jy(y + r * 2.35), z, poil);
        for (int i = -1; i <= 1; i += 2) {
            // oreilles
            double[] o = local(x, z, yaw, 0, i * r * 0.58);
            b.add(Meshes.sharedSphere(sphereDiv), r * 0.26, r * 0.26, r * 0.2, 0, yaw,
                    o[0], Meshes.jy(y + r * 2.92), o[1], poil);
            // bras le long du corps
            double[] a = local(x, z, yaw, r * 0.15, i * r * 1.0);
            b.add(Meshes.sharedSphere(sphereDiv), r * 0.34, r * 0.5, r * 0.34, 0, yaw,
                    a[0], Meshes.jy(y + r * 1.25), a[1], poil);
            // cuisses en avant : c'est ce qui dit « assis »
            double[] j = local(x, z, yaw, r * 0.85, i * r * 0.52);
            b.add(Meshes.sharedSphere(sphereDiv), r * 0.42, r * 0.34, r * 0.55, 0, yaw,
                    j[0], Meshes.jy(y + r * 0.36), j[1], poil);
            // yeux
            double[] e = local(x, z, yaw, r * 0.6, i * r * 0.25);
            b.add(Meshes.sharedSphere(7), r * 0.09, r * 0.09, r * 0.09, 0, yaw,
                    e[0], Meshes.jy(y + r * 2.5), e[1], Color.web("#241d18"));
        }
        // museau
        double[] m = local(x, z, yaw, r * 0.62, 0);
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.26, r * 0.2, r * 0.22, 0, yaw,
                m[0], Meshes.jy(y + r * 2.24), m[1], clair);
        // ventre
        double[] v = local(x, z, yaw, r * 0.72, 0);
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.5, r * 0.55, r * 0.3, 0, yaw,
                v[0], Meshes.jy(y + r * 1.0), v[1], clair);
        return r * 1.1;
    }


    // ------------------------------------------------ le mobilier du salon

    /**
     * Tissus d'ameublement.
     *
     * <p>C'etait un seul gris-mauve (#7d6a86) decline en quatre clartes
     * separees de quinze pour cent : de vingt metres, canape, accoudoirs et
     * coussins fondaient en une seule dalle violette, et le fond de la piece
     * n'etait qu'une file de dalles. La direction artistique de TuxKart est
     * faite d'aplats francs — cinq teintes tranchees, donc, et un ecart de
     * clarte de moitie entre le bati et les coussins.
     */
    private static final Color[] TISSUS = {
            Color.web("#c05340"), Color.web("#3f7fa8"), Color.web("#7d9e46"),
            Color.web("#d09a35"), Color.web("#8a5b96"), Color.web("#3f9e8a")};
    private static final Color BOIS_CLAIR = Color.web("#b98a52");
    private static final Color BOIS_SOMBRE = Color.web("#4a3524");

    /**
     * Decalage dans le repere de la piece : {@code avant} le long du cap,
     * {@code cote} perpendiculairement. Sans lui, chaque meuble refaisait ses
     * cosinus a la main et les dossiers finissaient de travers.
     */
    private static double[] local(double x, double z, double yaw, double avant, double cote) {
        double a = Math.toRadians(yaw);
        return new double[]{
                x + Math.cos(a) * cote - Math.sin(a) * avant,
                z - Math.sin(a) * cote - Math.cos(a) * avant};
    }

    /**
     * Canape : pieds, bati, coussins d'assise, dossier et deux accoudoirs.
     *
     * <p>Le dossier ne depassait les accoudoirs que d'un metre et demi a
     * l'echelle de la piece, et les quatre teintes se tenaient a quinze pour
     * cent l'une de l'autre : de dos comme de trois quarts, c'etait un pave.
     * Le dossier monte maintenant a quatre-vingt-quinze centimetres, les
     * accoudoirs s'arretent a soixante-dix, et les coussins d'assise sont d'un
     * quart plus clairs que le bati — trois etages qui se lisent de loin.
     */
    private static double canape(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 15 + rnd.nextDouble() * 6, p = 7.5;
        Color t = TISSUS[(int) (rnd.nextDouble() * TISSUS.length)]
                .deriveColor(0, 1, 0.88 + rnd.nextDouble() * 0.24, 1);
        double sol = y + 1.3;
        double hBati = 2.0, hBras = 3.2, hDossier = 5.4;
        // pieds
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                double[] q = local(x, z, yaw, j * (p / 2 - 1.0), i * (l / 2 - 1.1));
                b.add(Meshes.sharedBox(), 0.9, 1.3, 0.9, 0, yaw,
                        q[0], Meshes.jy(y + 0.65), q[1], BOIS_SOMBRE);
            }
        }
        // bati
        b.add(Meshes.sharedBox(), l, hBati, p, 0, yaw,
                x, Meshes.jy(sol + hBati / 2), z, t.deriveColor(0, 1, 0.78, 1));
        // dossier, contre l'arriere
        double[] d = local(x, z, yaw, -(p / 2 - 0.9), 0);
        b.add(Meshes.sharedBox(), l, hDossier, 1.8, 0, yaw,
                d[0], Meshes.jy(sol + hBati + hDossier / 2), d[1], t.deriveColor(0, 1, 0.92, 1));
        // accoudoirs
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * (l / 2 - 0.95));
            b.add(Meshes.sharedBox(), 1.9, hBras, p, 0, yaw,
                    q[0], Meshes.jy(sol + hBati + hBras / 2 - 0.6), q[1],
                    t.deriveColor(0, 1, 1.06, 1));
        }
        // coussins d'assise, entre les accoudoirs
        double libre = l - 4.4;
        int n = Math.max(2, (int) Math.round(libre / 7));
        for (int i = 0; i < n; i++) {
            double[] q = local(x, z, yaw, 0.5, (i - (n - 1) / 2.0) * (libre / n));
            b.add(Meshes.sharedBox(), libre / n * 0.94, 1.4, p * 0.8, 0, yaw,
                    q[0], Meshes.jy(sol + hBati + 0.7), q[1], t.deriveColor(0, 1, 1.3, 1));
        }
        return l * 0.55;
    }

    /** Fauteuil : le canape en plus court, meme etagement de teintes. */
    private static double fauteuil(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double c = 7 + rnd.nextDouble() * 2;
        Color t = TISSUS[(int) (rnd.nextDouble() * TISSUS.length)]
                .deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.24, 1);
        double sol = y + 1.3;
        double hBati = 2.0, hBras = 3.2, hDossier = 5.2;
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                double[] q = local(x, z, yaw, j * (c / 2 - 1.0), i * (c / 2 - 1.0));
                b.add(Meshes.sharedBox(), 0.9, 1.3, 0.9, 0, yaw,
                        q[0], Meshes.jy(y + 0.65), q[1], BOIS_SOMBRE);
            }
        }
        b.add(Meshes.sharedBox(), c, hBati, c, 0, yaw,
                x, Meshes.jy(sol + hBati / 2), z, t.deriveColor(0, 1, 0.78, 1));
        double[] d = local(x, z, yaw, -(c / 2 - 0.8), 0);
        b.add(Meshes.sharedBox(), c, hDossier, 1.6, 0, yaw,
                d[0], Meshes.jy(sol + hBati + hDossier / 2), d[1], t.deriveColor(0, 1, 0.92, 1));
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * (c / 2 - 0.85));
            b.add(Meshes.sharedBox(), 1.7, hBras, c, 0, yaw,
                    q[0], Meshes.jy(sol + hBati + hBras / 2 - 0.6), q[1],
                    t.deriveColor(0, 1, 1.06, 1));
        }
        double[] q = local(x, z, yaw, 0.5, 0);
        b.add(Meshes.sharedBox(), c - 3.8, 1.4, c * 0.8, 0, yaw,
                q[0], Meshes.jy(sol + hBati + 0.7), q[1], t.deriveColor(0, 1, 1.3, 1));
        return c * 0.6;
    }

    /** Table basse : un plateau sur quatre pieds, et ce qui traine dessus. */
    private static double tableBasse(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 11 + rnd.nextDouble() * 4, p = 6, h = 2.6;
        b.add(Meshes.sharedBox(), l, 0.6, p, 0, yaw, x, Meshes.jy(y + h), z, BOIS_CLAIR);
        for (int i = -1; i <= 1; i += 2) {
            for (int j = -1; j <= 1; j += 2) {
                double[] q = local(x, z, yaw, j * (p / 2 - 0.8), i * (l / 2 - 0.8));
                b.add(Meshes.sharedBox(), 0.7, h, 0.7, 0, yaw,
                        q[0], Meshes.jy(y + h / 2), q[1], BOIS_CLAIR.deriveColor(0, 1, 0.85, 1));
            }
        }
        double[] r = local(x, z, yaw, 0, 1.5);
        b.add(Meshes.sharedBox(), 2.6, 0.35, 1.9, 0, yaw + 20,
                r[0], Meshes.jy(y + h + 0.5), r[1], Color.web("#c9302c"));
        return l * 0.55;
    }

    /**
     * Bibliotheque : deux montants, un fond, quatre tablettes, des livres.
     *
     * <p>Elle etait bloc plein : la caisse etait une boite fermee, et les
     * tablettes comme les livres etaient poses *dedans*, ou aucune face
     * tournee vers le joueur ne les montrait. Le decor est dessine en
     * {@code CullFace.BACK}, l'interieur d'une boite n'existe pas — on ne
     * voyait donc qu'un panneau brun de deux metres, et c'est ce panneau que le
     * fond du salon repetait de bout en bout. La caisse est maintenant ouverte
     * sur l'avant : montants, fond, dessus, dessous, et les rangees de livres
     * devant le fond.
     */
    private static double bibliotheque(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 14 + rnd.nextDouble() * 6, h = 16 + rnd.nextDouble() * 6, p = 4.5;
        Color bois = Color.web("#7a5230");
        Color fond = bois.deriveColor(0, 1, 0.7, 1);
        // Montants plus profonds que la caisse, socle et corniche debordants.
        //
        // De face la bibliotheque se lisait a ses rangees de livres, mais le
        // semis l'oriente au hasard contre le mur du fond : une bonne moitie se
        // presente de dos ou de champ, et de dos ce n'etait qu'une planche
        // brune dressee de vingt metres. Un meuble se reconnait de derriere a
        // trois choses — le panneau de fond en retrait, les montants qui le
        // debordent, et le socle et la corniche qui coiffent le tout. La caisse
        // gagne au passage un metre de profondeur : a 3,5 pour 20 de haut, sa
        // silhouette de trois quarts restait celle d'une planche.
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * (l / 2 - 0.45));
            b.add(Meshes.sharedBox(), 0.9, h, p + 1.0, 0, yaw,
                    q[0], Meshes.jy(y + h / 2), q[1], bois);
        }
        // fond, en retrait entre les montants
        double[] f = local(x, z, yaw, -(p / 2 - 0.2), 0);
        b.add(Meshes.sharedBox(), l - 1.8, h, 0.7, 0, yaw,
                f[0], Meshes.jy(y + h / 2), f[1], fond);
        // socle et corniche
        b.add(Meshes.sharedBox(), l + 0.8, 1.3, p + 1.6, 0, yaw,
                x, Meshes.jy(y + 0.65), z, bois.deriveColor(0, 1, 0.82, 1));
        b.add(Meshes.sharedBox(), l + 1.2, 1.1, p + 1.9, 0, yaw,
                x, Meshes.jy(y + h - 0.55), z, bois.deriveColor(0, 1, 1.18, 1));
        int etages = 4;
        for (int e = 0; e < etages; e++) {
            // le pas des tablettes se prend entre le dessus du socle et le
            // dessous de la corniche, pas entre les deux bouts du meuble : les
            // livres les plus hauts traversaient sinon la tablette suivante
            double ey = y + 1.3 + e * (h - 2.4) / etages;
            if (e > 0) {
                b.add(Meshes.sharedBox(), l - 1.8, 0.4, p * 0.92, 0, yaw,
                        x, Meshes.jy(ey), z, bois.deriveColor(0, 1, 1.25, 1));
            }
            int livres = 7;
            for (int i = 0; i < livres; i++) {
                double haut = 2.0 + rnd.nextDouble() * 1.2;
                double[] q = local(x, z, yaw, 0.45,
                        (i - (livres - 1) / 2.0) * (l - 2.4) / livres);
                b.add(Meshes.sharedBox(), (l - 2.4) / livres * 0.82, haut, p * 0.62, 0, yaw,
                        q[0], Meshes.jy(ey + 0.2 + haut / 2), q[1],
                        JOUETS[rnd.nextInt(JOUETS.length)].deriveColor(0, 1, 0.85, 1));
            }
        }
        return l * 0.55;
    }

    /**
     * Meuble television, son pied et son ecran.
     *
     * <p>Deux pannes tenaient dans quatre lignes. L'ecran flottait : son centre
     * etait a 4,2 au-dessus d'un meuble haut de 4,5 pour une hauteur de 7,5,
     * soit quarante-cinq centimetres de vide sous sa tranche — invisible de
     * face, net des qu'on passait de trois quarts. Et sa coque etait une plaque
     * de 0,8 : le semis oriente les meubles au hasard contre le mur du fond,
     * une bonne moitie des televiseurs se presentaient donc de dos ou de champ,
     * et de dos une plaque noire de vingt centimetres posee sur rien n'est pas
     * un televiseur. La coque fait maintenant deux metres quarante d'epaisseur,
     * elle repose sur un pied central, et son dos porte le bloc de connexion :
     * la silhouette se lit sous n'importe quel angle.
     */
    private static double meubleTele(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 12 + rnd.nextDouble() * 4, h = 4.5, p = 4;
        b.add(Meshes.sharedBox(), l, h, p, 0, yaw, x, Meshes.jy(y + h / 2), z, BOIS_SOMBRE);
        // pied : socle sur le meuble, puis colonne sous la coque
        b.add(Meshes.sharedBox(), l * 0.30, 0.5, p * 0.55, 0, yaw,
                x, Meshes.jy(y + h + 0.25), z, Color.web("#3a3a40"));
        b.add(Meshes.sharedBox(), l * 0.14, 1.3, p * 0.30, 0, yaw,
                x, Meshes.jy(y + h + 1.15), z, Color.web("#3a3a40"));
        double base = y + h + 1.8, he = 7.0;
        b.add(Meshes.sharedBox(), l * 0.75, he, 2.4, 0, yaw,
                x, Meshes.jy(base + he / 2), z, Color.web("#17171a"));
        // la dalle, en avant de la coque
        double[] q = local(x, z, yaw, 1.35, 0);
        b.add(Meshes.sharedBox(), l * 0.66, he * 0.78, 0.3, 0, yaw,
                q[0], Meshes.jy(base + he / 2), q[1], Color.web("#2b3a4a"));
        // le bloc de connexion, en saillie du dos : c'est lui qui dit « tele »
        // quand le meuble est vu de derriere
        double[] d = local(x, z, yaw, -1.5, 0);
        b.add(Meshes.sharedBox(), l * 0.34, he * 0.34, 0.8, 0, yaw,
                d[0], Meshes.jy(base + he * 0.34), d[1], Color.web("#25252a"));
        return l * 0.55;
    }

    /**
     * Lampadaire : socle, mat et abat-jour.
     *
     * <p>L'abat-jour etait un cone retourne, pointe en bas : un verre a pied
     * de deux metres au fond du salon. Il est maintenant a l'endroit, pointe
     * en haut et large en bas, coiffant le mat — la silhouette d'un abat-jour.
     */
    private static double lampadaire(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        double h = 15 + rnd.nextDouble() * 5;
        b.add(Meshes.sharedCylinder(10), 2.4, 0.6, 2.4, 0, yaw, x, Meshes.jy(y + 0.3), z,
                Color.web("#2f2a26"));
        b.add(Meshes.sharedCylinder(6), 0.3, h, 0.3, 0, yaw, x, Meshes.jy(y + h / 2), z,
                Color.web("#8d7a5e"));
        b.add(Meshes.sharedCone(coneSides), 3.0, 3.6, 3.0, 0, yaw,
                x, Meshes.jy(y + h - 0.6), z, Color.web("#ffe9a8"));
        return 3.0;
    }

    /** Plante verte en pot. */
    private static double planteVerte(DecorBatch b, Random rnd, int sphereDiv,
                                      double x, double y, double z, double yaw) {
        double h = 8 + rnd.nextDouble() * 5;
        // Le pot est un cone renverse : incline de 180 degres, c'est sa base —
        // le bord du pot — qui tombe sur l'ancrage, et sa pointe trois metres
        // plus bas. Ancre a y+1,5 il etait enterre de moitie et son bord
        // arrivait un metre cinquante sous la tige, qui partait de y+3.
        b.add(Meshes.sharedCone(9), 2.2, 3.0, 2.2, 180, yaw, x, Meshes.jy(y + 3), z,
                Color.web("#c2653a"));
        b.add(Meshes.sharedCylinder(6), 0.3, h, 0.3, 0, yaw, x, Meshes.jy(y + 3 + h / 2), z,
                Color.web("#4a7a34"));
        for (int i = 0; i < 5; i++) {
            double a = Math.toRadians(yaw + i * 72);
            b.add(Meshes.sharedSphere(sphereDiv), 2.6, 0.7, 1.6, 20, Math.toDegrees(a),
                    x + Math.cos(a) * 2.2, Meshes.jy(y + 3 + h * (0.55 + i * 0.09)),
                    z - Math.sin(a) * 2.2,
                    Color.web("#3f8a3f").deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.35, 1));
        }
        return 3;
    }

    /**
     * Carton ouvert.
     *
     * <p>C'etait un bloc plein coiffe de deux plaques inclinees a 55 degres.
     * Vues de champ, ces plaques etaient deux batons — un carton sur deux
     * ressemblait a une niche, l'autre a une planche posee contre un cube. Le
     * carton est maintenant creux : quatre parois, un fond, et quatre rabats
     * ouverts en couronne. C'est la couronne qui dit « boite ouverte » ; deux
     * rabats seulement disaient « toit ».
     *
     * <p>Les deux rabats de l'axe long sont poses avec un lacet tourne d'un
     * quart : {@code add} n'incline qu'autour de Z, et c'est le seul moyen de
     * faire basculer une plaque autour de l'autre arete.
     */
    private static double carton(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double c = 4.5 + rnd.nextDouble() * 3, h = c * 0.72;
        Color k = Color.web("#c9915a").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
        Color sombre = k.deriveColor(0, 1, 0.86, 1);
        double e = 0.32, f = c * 0.42;
        double ct = Math.cos(Math.toRadians(50)), st = Math.sin(Math.toRadians(50));
        b.add(Meshes.sharedBox(), c, e, c, 0, yaw, x, Meshes.jy(y + e / 2), z, sombre);
        for (int i = -1; i <= 1; i += 2) {
            double[] pa = local(x, z, yaw, i * (c / 2 - e / 2), 0);
            b.add(Meshes.sharedBox(), c, h, e, 0, yaw, pa[0], Meshes.jy(y + h / 2), pa[1], k);
            double[] pc = local(x, z, yaw, 0, i * (c / 2 - e / 2));
            b.add(Meshes.sharedBox(), e, h, c, 0, yaw, pc[0], Meshes.jy(y + h / 2), pc[1], sombre);
            // rabats : sur l'axe travers, puis sur l'axe long (lacet + 90)
            double[] ra = local(x, z, yaw, 0, i * (c / 2 + f / 2 * ct));
            b.add(Meshes.sharedBox(), f, e, c, -50 * i, yaw,
                    ra[0], Meshes.jy(y + h + f / 2 * st), ra[1], k);
            double[] rb = local(x, z, yaw, i * (c / 2 + f / 2 * ct), 0);
            b.add(Meshes.sharedBox(), f, e, c, -50 * i, yaw,
                    rb[0], Meshes.jy(y + h + f / 2 * st), rb[1], sombre);
        }
        return c * 0.6;
    }

    /** Pile de livres posee a plat. */
    private static double pileDeLivres(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        int n = 2 + rnd.nextInt(4);
        double h = 0;
        for (int i = 0; i < n; i++) {
            double e = 0.45 + rnd.nextDouble() * 0.35;
            b.add(Meshes.sharedBox(), 3.4 - i * 0.2, e, 2.4 - i * 0.15, 0, yaw + i * 11,
                    x, Meshes.jy(y + h + e / 2), z,
                    JOUETS[rnd.nextInt(JOUETS.length)].deriveColor(0, 1, 0.8, 1));
            h += e;
        }
        return 2;
    }

    /**
     * Coussin tombe du canape.
     *
     * <p>Une dalle de dix centimetres d'epaisseur ne fait pas un coussin : trois
     * boites empilees, la mediane debordante, en donnent le galbe pour douze
     * triangles de plus.
     */
    private static double coussin(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double c = 3.4 + rnd.nextDouble() * 1.6;
        double tilt = rnd.nextDouble() * 16 - 8;
        Color t = TISSUS[(int) (rnd.nextDouble() * TISSUS.length)].deriveColor(0, 1, 1.12, 1);
        b.add(Meshes.sharedBox(), c * 0.86, 0.5, c * 0.78, tilt, yaw,
                x, Meshes.jy(y + 0.25), z, t.deriveColor(0, 1, 0.88, 1));
        b.add(Meshes.sharedBox(), c, 0.9, c * 0.9, tilt, yaw,
                x, Meshes.jy(y + 0.85), z, t);
        b.add(Meshes.sharedBox(), c * 0.86, 0.5, c * 0.78, tilt, yaw,
                x, Meshes.jy(y + 1.45), z, t.deriveColor(0, 1, 1.1, 1));
        return c * 0.6;
    }

    /** Bille de verre. */
    private static double bille(DecorBatch b, Random rnd, int sphereDiv,
                                double x, double y, double z, double yaw) {
        double r = 0.28 + rnd.nextDouble() * 0.12;
        b.add(Meshes.sharedSphere(sphereDiv), r, r, r, 0, yaw, x, Meshes.jy(y + r), z,
                JOUETS[rnd.nextInt(JOUETS.length)].deriveColor(0, 0.6, 1.2, 1));
        return r;
    }

    // ------------------------------------------------ le mobilier du donjon

    /**
     * Sarcophage : socle, cuve, couvercle deborde et le gisant sculpte dessus.
     *
     * <p>C'etait une cuve de douze metres de long — quatre karts — surmontee
     * d'une dalle : de loin, un wagon de pierre. Reduit a deux metres huit, il
     * lui fallait le signe qui le nomme : le gisant, buste et tete de pierre
     * couches sur le couvercle, se lit de trois quarts comme de profil et ne
     * ressemble a rien d'autre dans la salle.
     */
    private static double sarcophage(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 2.6 + rnd.nextDouble() * 0.7, p = 1.15, h = 0.75;
        Color pierre = PIERRE.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.25, 1);
        b.add(Meshes.sharedBox(), p + 0.3, 0.2, l + 0.3, 0, yaw,
                x, Meshes.jy(y + 0.1), z, pierre.deriveColor(0, 1, 0.85, 1));
        b.add(Meshes.sharedBox(), p, h, l, 0, yaw,
                x, Meshes.jy(y + 0.2 + h / 2), z, pierre);
        b.add(Meshes.sharedBox(), p + 0.16, 0.22, l + 0.16, 0, yaw,
                x, Meshes.jy(y + 0.2 + h + 0.11), z, pierre.deriveColor(0, 1, 1.15, 1));
        double dalle = y + 0.2 + h + 0.22;
        // Le gisant : buste, tete, pieds joints au bout de la cuve. Il etait
        // haut de vingt-six centimetres et de la teinte de la cuve : a
        // quatorze metres, en plongee, on lisait un bloc. Il monte a trente-six
        // et passe en pierre claire, ce qui le detache du couvercle.
        Color os = pierre.deriveColor(0, 1, 1.45, 1);
        double[] buste = local(x, z, yaw, -l * 0.04, 0);
        b.add(Meshes.sharedBox(), p * 0.58, 0.36, l * 0.52, 0, yaw,
                buste[0], Meshes.jy(dalle + 0.18), buste[1], os);
        double[] tete = local(x, z, yaw, l * 0.33, 0);
        b.add(Meshes.sharedSphere(7), 0.23, 0.26, 0.23, 0, yaw,
                tete[0], Meshes.jy(dalle + 0.24), tete[1], os);
        double[] pieds = local(x, z, yaw, -l * 0.37, 0);
        b.add(Meshes.sharedBox(), p * 0.34, 0.26, 0.34, 0, yaw,
                pieds[0], Meshes.jy(dalle + 0.13), pieds[1], os);
        return l * 0.55;
    }

    /**
     * Armoire : caisson, corniche, plinthe, deux vantaux et leurs poignees.
     *
     * <p>Elle etait haute de quinze a vingt metres — un immeuble — et le jour
     * entre ses vantaux etait pose en {@code z} du monde et non dans son
     * repere : la facade regardait le nord quel que soit le cap de la piece.
     * Trois metres, une corniche qui deborde et une plinthe suffisent a la
     * nommer ; c'est la corniche qui la distingue d'une porte a distance.
     */
    private static double armoire(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 1.7 + rnd.nextDouble() * 0.5, h = 2.7 + rnd.nextDouble() * 0.6;
        double p = 0.75;
        Color bois = Color.web("#4a3524").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.3, 1);
        b.add(Meshes.sharedBox(), l + 0.12, 0.22, p + 0.1, 0, yaw,
                x, Meshes.jy(y + 0.11), z, bois.deriveColor(0, 1, 0.7, 1));
        b.add(Meshes.sharedBox(), l, h, p, 0, yaw,
                x, Meshes.jy(y + 0.22 + h / 2), z, bois);
        b.add(Meshes.sharedBox(), l + 0.24, 0.24, p + 0.22, 0, yaw,
                x, Meshes.jy(y + 0.22 + h + 0.12), z, bois.deriveColor(0, 1, 1.25, 1));
        // les deux vantaux en saillie et le jour sombre qui les separe, batis
        // dans le repere de la piece
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, p / 2 + 0.03, i * l * 0.24);
            b.add(Meshes.sharedBox(), l * 0.42, h * 0.82, 0.06, 0, yaw,
                    q[0], Meshes.jy(y + 0.22 + h * 0.52), q[1],
                    bois.deriveColor(0, 1, 1.35, 1));
            double[] po = local(x, z, yaw, p / 2 + 0.08, i * l * 0.06);
            b.add(Meshes.sharedSphere(6), 0.07, 0.07, 0.07, 0, yaw,
                    po[0], Meshes.jy(y + 0.22 + h * 0.5), po[1], Color.web("#c9a227"));
        }
        return l * 0.6;
    }

    /**
     * Trone : estrade, assise, haut dossier a fleuron et deux accoudoirs.
     *
     * <p>Quatorze metres de haut et pas d'assise visible : on lisait deux
     * plaques en V sur un pilier. L'assise avancee, le dossier qui deborde des
     * accoudoirs et le coussin rouge donnent la silhouette de chaise haute
     * qu'on reconnait de dos comme de face.
     */
    private static double trone(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double c = 1.05, h = 2.3;
        Color pierre = PIERRE.deriveColor(0, 1, 1.0, 1);
        b.add(Meshes.sharedBox(), c * 1.9, 0.24, c * 2.1, 0, yaw,
                x, Meshes.jy(y + 0.12), z, pierre.deriveColor(0, 1, 0.85, 1));
        b.add(Meshes.sharedBox(), c, 0.5, c * 1.05, 0, yaw,
                x, Meshes.jy(y + 0.24 + 0.25), z, pierre);
        double[] coussin = local(x, z, yaw, 0, 0);
        b.add(Meshes.sharedBox(), c * 0.86, 0.16, c * 0.9, 0, yaw,
                coussin[0], Meshes.jy(y + 0.24 + 0.58), coussin[1], Color.web("#8e2431"));
        double[] dos = local(x, z, yaw, -c * 0.46, 0);
        b.add(Meshes.sharedBox(), c * 1.1, h, 0.2, 0, yaw,
                dos[0], Meshes.jy(y + 0.24 + h / 2), dos[1], pierre.deriveColor(0, 1, 1.08, 1));
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * c * 0.52);
            b.add(Meshes.sharedBox(), 0.18, 0.22, c * 1.0, 0, yaw,
                    q[0], Meshes.jy(y + 0.24 + 0.86), q[1], pierre.deriveColor(0, 1, 1.14, 1));
            b.add(Meshes.sharedBox(), 0.18, 0.62, 0.18, 0, yaw,
                    q[0], Meshes.jy(y + 0.24 + 0.44), q[1], pierre.deriveColor(0, 1, 0.95, 1));
        }
        // Fronton et fleuron : le semis oriente les pieces au hasard, et de
        // dos un dossier nu n'est qu'une plaque de deux metres — c'est ce
        // qu'on voyait au fond de la salle une fois sur deux.
        double[] fronton = local(x, z, yaw, -c * 0.46, 0);
        b.add(Meshes.sharedBox(), c * 1.3, 0.2, 0.3, 0, yaw,
                fronton[0], Meshes.jy(y + 0.24 + h + 0.1), fronton[1],
                pierre.deriveColor(0, 1, 1.2, 1));
        b.add(Meshes.sharedCone(7), 0.2, 0.44, 0.2, 0, yaw,
                fronton[0], Meshes.jy(y + 0.24 + h + 0.42), fronton[1], Color.web("#c9a227"));
        return c * 1.1;
    }

    /**
     * Gargouille : socle, bete accroupie penchee en avant, ailes et gueule.
     *
     * <p>Le socle faisait six a neuf metres et la bete deux : de la piste on ne
     * voyait qu'un pilier coiffe d'un V. Reduit, il restait un V — les ailes,
     * hautes d'un metre, mangeaient un corps de trente centimetres. C'est la
     * bete qui porte la silhouette : corps massif penche en avant, grosse tete
     * a gueule sombre et cornes, ailes repliees plus basses que le dos.
     */
    private static double gargouille(DecorBatch b, Random rnd, int sphereDiv,
                                     double x, double y, double z, double yaw) {
        double h = 0.8 + rnd.nextDouble() * 0.5;
        Color pierre = PIERRE.deriveColor(0, 1, 0.82, 1);
        Color bete = PIERRE.deriveColor(0, 1, 1.15, 1);
        b.add(Meshes.sharedBox(), 1.0, h, 1.0, 0, yaw,
                x, Meshes.jy(y + h / 2), z, pierre);
        b.add(Meshes.sharedBox(), 1.25, 0.18, 1.25, 0, yaw,
                x, Meshes.jy(y + h + 0.09), z, pierre.deriveColor(0, 1, 1.12, 1));
        double socle = y + h + 0.18;
        b.add(Meshes.sharedSphere(sphereDiv), 0.5, 0.52, 0.72, 0, yaw,
                x, Meshes.jy(socle + 0.5), z, bete);
        double[] tete = local(x, z, yaw, 0.55, 0);
        b.add(Meshes.sharedBox(), 0.46, 0.42, 0.5, 0, yaw,
                tete[0], Meshes.jy(socle + 0.86), tete[1], bete);
        double[] gueule = local(x, z, yaw, 0.85, 0);
        b.add(Meshes.sharedBox(), 0.3, 0.24, 0.36, 14, yaw,
                gueule[0], Meshes.jy(socle + 0.74), gueule[1], Color.web("#2b2620"));
        for (int i = -1; i <= 1; i += 2) {
            double[] aile = local(x, z, yaw, -0.34, i * 0.42);
            b.add(Meshes.sharedBox(), 0.12, 0.72, 0.8, 28 * i, yaw,
                    aile[0], Meshes.jy(socle + 0.72), aile[1],
                    bete.deriveColor(0, 1, 0.9, 1));
            double[] corne = local(x, z, yaw, 0.44, i * 0.16);
            b.add(Meshes.sharedCone(6), 0.09, 0.3, 0.09, 0, yaw,
                    corne[0], Meshes.jy(socle + 1.2), corne[1], bete);
        }
        return 0.7;
    }

    /**
     * Coffre : caisse, couvercle bombe, deux cercles de fer et la serrure.
     *
     * <p>A cinq metres de long et deux et demi de haut, le couvercle bombe se
     * lisait comme une hutte — porte comprise, la serrure sombre en tenant
     * lieu. A un metre vingt, les ferrures claires en travers du couvercle
     * suffisent : c'est leur ecartement qui donne l'echelle du meuble.
     */
    private static double coffre(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 1.0 + rnd.nextDouble() * 0.45;
        double p = l * 0.62, h = l * 0.42;
        Color bois = Color.web("#5a3f27");
        Color fer = Color.web("#26262b");
        b.add(Meshes.sharedBox(), l, h, p, 0, yaw,
                x, Meshes.jy(y + h / 2), z, bois);
        b.add(Meshes.sharedCylinder(9), p * 0.5, l, p * 0.5, 90, yaw,
                x, Meshes.jy(y + h), z, bois.deriveColor(0, 1, 1.2, 1));
        for (int i = -1; i <= 1; i += 2) {
            b.add(Meshes.sharedCylinder(9), p * 0.53, l * 0.09, p * 0.53, 90, yaw,
                    x + Math.cos(Math.toRadians(yaw)) * i * l * 0.32,
                    Meshes.jy(y + h),
                    z - Math.sin(Math.toRadians(yaw)) * i * l * 0.32, fer);
            b.add(Meshes.sharedBox(), l * 0.09, h, p + 0.02, 0, yaw,
                    x + Math.cos(Math.toRadians(yaw)) * i * l * 0.32,
                    Meshes.jy(y + h / 2),
                    z - Math.sin(Math.toRadians(yaw)) * i * l * 0.32, fer);
        }
        // serrure et moraillon, sur la face longue : c'est ce qu'on voit du
        // bord de piste, la piece etant posee de flanc
        double[] serrure = local(x, z, yaw, p * 0.52, 0);
        b.add(Meshes.sharedBox(), l * 0.16, h * 0.5, 0.05, 0, yaw,
                serrure[0], Meshes.jy(y + h * 0.62), serrure[1],
                Color.web("#c9a227"));
        return l * 0.6;
    }

    /**
     * Chandelier : pied a trois griffes, fut a noeud, trois bras et leurs
     * bougies.
     *
     * <p>Les bougies flottaient a quarante centimetres du fut, sans rien qui
     * les y rattache : de loin, trois batons blancs en l'air. Les bras obliques
     * ferment la silhouette en candelabre.
     */
    private static double chandelier(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        double h = 2.2 + rnd.nextDouble() * 1.0;
        Color metal = Color.web("#3a3a42");
        b.add(Meshes.sharedCylinder(8), 0.5, 0.16, 0.5, 0, yaw,
                x, Meshes.jy(y + 0.08), z, metal);
        b.add(Meshes.sharedCylinder(8), 0.3, 0.22, 0.3, 0, yaw,
                x, Meshes.jy(y + 0.22), z, metal.deriveColor(0, 1, 1.3, 1));
        b.add(Meshes.sharedCylinder(6), 0.13, h, 0.13, 0, yaw,
                x, Meshes.jy(y + h / 2), z, metal);
        b.add(Meshes.sharedSphere(7), 0.22, 0.18, 0.22, 0, yaw,
                x, Meshes.jy(y + h * 0.62), z, metal.deriveColor(0, 1, 1.3, 1));
        for (int i = -1; i <= 1; i++) {
            double[] q = local(x, z, yaw, 0, i * 0.62);
            if (i != 0) {
                // bras : une potence oblique du fut vers la bougie, sans quoi
                // la bougie est un baton en l'air
                double[] m = local(x, z, yaw, 0, i * 0.31);
                b.add(Meshes.sharedCylinder(5), 0.07, 0.8, 0.07, 48 * i, yaw,
                        m[0], Meshes.jy(y + h + 0.12), m[1], metal);
                b.add(Meshes.sharedCylinder(6), 0.16, 0.1, 0.16, 0, yaw,
                        q[0], Meshes.jy(y + h + 0.36), q[1], metal);
            }
            double pied = i == 0 ? h + 0.06 : h + 0.41;
            b.add(Meshes.sharedCylinder(6), 0.1, 0.5, 0.1, 0, yaw,
                    q[0], Meshes.jy(y + pied + 0.25), q[1], Color.web("#e8e0c8"));
            b.add(Meshes.sharedCone(coneSides), 0.09, 0.3, 0.09, 0, yaw,
                    q[0], Meshes.jy(y + pied + 0.62), q[1], Color.web("#ffcf6a"));
        }
        return 0.9;
    }

    /**
     * Arcade de donjon : deux piedroits et un arc plein cintre en claveaux.
     *
     * <p>Le donjon posait ici l'{@code arche} du Desert de GNU — un trilithe de
     * gres orange, deux montants et un linteau droit. Un dolmen dans une salle
     * voutee : c'est le decor d'un autre theme, et rien ne le dementait a
     * l'ecran. L'arcade est batie en pierre grise, celle de la nef, et son arc
     * est un demi-anneau de claveaux, ce que le linteau droit ne pouvait pas
     * dire.
     *
     * @param haute arcade de sept a dix metres, pour le fond de la salle
     */
    private static double arcade(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw, boolean haute) {
        double h = haute ? 6.5 + rnd.nextDouble() * 3.5 : 3.4 + rnd.nextDouble() * 1.4;
        double ecart = h * (0.52 + rnd.nextDouble() * 0.12);
        Color p = PIERRE.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.25, 1);
        double montant = h * 0.62, r = ecart / 2;
        double ep = Math.max(0.35, h * 0.11);
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * (r + ep / 2));
            b.add(Meshes.sharedBox(), ep * 1.4, 0.3, ep * 1.9, 0, yaw,
                    q[0], Meshes.jy(y + 0.15), q[1], p.deriveColor(0, 1, 0.86, 1));
            b.add(Meshes.sharedBox(), ep, montant, ep * 1.5, 0, yaw,
                    q[0], Meshes.jy(y + montant / 2), q[1], p);
            b.add(Meshes.sharedBox(), ep * 1.3, 0.22, ep * 1.7, 0, yaw,
                    q[0], Meshes.jy(y + montant + 0.11), q[1], p.deriveColor(0, 1, 1.18, 1));
        }
        // l'arc : sept claveaux poses en demi-cercle, un peu plus clairs
        int claveaux = 7;
        for (int k = 0; k < claveaux; k++) {
            double a = Math.PI * (k + 0.5) / claveaux;
            double[] q = local(x, z, yaw, 0, Math.cos(a) * (r + ep / 2));
            b.add(Meshes.sharedBox(), ep, ep * 1.15, ep * 1.5,
                    Math.toDegrees(a) - 90, yaw,
                    q[0], Meshes.jy(y + montant + 0.22 + Math.sin(a) * (r + ep / 2)), q[1],
                    p.deriveColor(0, 1, k == claveaux / 2 ? 1.3 : 1.1, 1));
        }
        return ecart * 0.6;
    }

    // ------------------------------------------------- le mobilier du bar

    /**
     * Comptoir : socle en retrait, devanture, plateau debordant, repose-pied de
     * laiton et la rampe de pompes a biere.
     *
     * <p>C'etait un pave brun coiffe d'une planche sombre. A l'ecran — et
     * surtout de dos, le semis orientant les pieces au hasard — rien ne le
     * distinguait d'une caisse posee de champ, et la salle en compte une
     * dizaine. Trois volumes le nomment maintenant : le plateau deborde de
     * quatre-vingts centimetres de chaque cote, ce qui donne le profil en T
     * qu'a tout comptoir vu de biais ; le socle en retrait le decolle du sol ;
     * et les trois pompes dressees sur le plateau, avec leur poignee rouge, se
     * voient des deux cotes et n'appartiennent qu'a un bar.
     *
     * <p>Il monte de sept metres et demi a neuf et demi : un metre cinq au
     * rapport du salon, la hauteur d'un comptoir, et l'aplomb du tabouret qui
     * le borde.
     */
    private static double comptoir(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 22 + rnd.nextDouble() * 10, h = 9.5, p = 5.0;
        Color bois = Color.web("#7a4a24");
        b.add(Meshes.sharedBox(), l - 1.8, 1.4, p - 1.4, 0, yaw,
                x, Meshes.jy(y + 0.7), z, Color.web("#3a2415"));
        b.add(Meshes.sharedBox(), l, h - 1.4, p, 0, yaw,
                x, Meshes.jy(y + 1.4 + (h - 1.4) / 2), z, bois);
        // une moulure claire sous le plateau : elle etage la devanture, qui
        // sans elle est un aplat de neuf metres
        b.add(Meshes.sharedBox(), l + 0.4, 0.55, p + 0.4, 0, yaw,
                x, Meshes.jy(y + h - 1.6), z, bois.deriveColor(0, 1, 1.3, 1));
        b.add(Meshes.sharedBox(), l + 1.6, 0.9, p + 3.2, 0, yaw,
                x, Meshes.jy(y + h + 0.45), z, Color.web("#2f2a26"));
        // repose-pied de laiton, cote client, et ses deux montants : la barre
        // seule flottait a vingt centimetres du sol
        Color laiton = Color.web("#c9a227");
        double[] rail = local(x, z, yaw, p / 2 + 1.4, 0);
        b.add(Meshes.sharedCylinder(7), 0.30, l * 0.95, 0.30, 90, yaw,
                rail[0], Meshes.jy(y + 2.0), rail[1], laiton);
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, p / 2 + 1.4, i * l * 0.36);
            b.add(Meshes.sharedCylinder(6), 0.26, 2.0, 0.26, 0, yaw,
                    q[0], Meshes.jy(y + 1.0), q[1], laiton);
        }
        // la rampe de pompes : c'est elle qui dit « bar » avant tout le reste
        for (int i = -1; i <= 1; i++) {
            double[] q = local(x, z, yaw, -0.7, i * 2.7);
            b.add(Meshes.sharedCylinder(8), 0.34, 2.6, 0.34, 0, yaw,
                    q[0], Meshes.jy(y + h + 2.2), q[1], Color.web("#c9ccd4"));
            b.add(Meshes.sharedBox(), 0.55, 1.6, 0.55, 0, yaw,
                    q[0], Meshes.jy(y + h + 4.1), q[1], Color.web("#b0392e"));
            double[] bec = local(x, z, yaw, 0.5, i * 2.7);
            b.add(Meshes.sharedCylinder(6), 0.17, 1.6, 0.17, 90, yaw + 90,
                    bec[0], Meshes.jy(y + h + 1.5), bec[1], Color.web("#c9ccd4"));
        }
        return l * 0.55;
    }

    /**
     * Etagere a bouteilles : deux montants, un fond clair, un socle, une
     * corniche et trois tablettes garnies.
     *
     * <p>La caisse etait pleine : le fond, les tablettes et les dix-huit
     * bouteilles etaient tous noyes dans un bloc de deux metres soixante
     * d'epaisseur qui les cachait. A l'ecran, la piece la plus haute du bar
     * n'etait qu'une planche dressee de dix-huit metres — la panne exacte qu'a
     * eue la bibliotheque du salon. Il n'y a donc plus de caisse : les
     * tablettes sont portees <em>devant</em> le fond, et les bouteilles avec
     * elles.
     *
     * <p>Le fond est clair et froid, la ou tout le reste est bois : un bar se
     * reconnait au miroir de son arriere-comptoir, et c'est aussi la seule
     * tache non brune d'une salle qui, sans elle, l'est de bout en bout.
     */
    private static double etagereABouteilles(DecorBatch b, Random rnd,
                                             double x, double y, double z, double yaw) {
        double l = 14 + rnd.nextDouble() * 6, h = 13 + rnd.nextDouble() * 5;
        double p = 3.6;
        Color bois = Color.web("#5a3a1e");
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * (l / 2 - 0.5));
            b.add(Meshes.sharedBox(), 1.0, h, p, 0, yaw, q[0], Meshes.jy(y + h / 2), q[1], bois);
        }
        // Le fond est double : une planche de bois, puis le miroir en applique
        // devant elle. Le miroir seul, traversant, presentait de dos une dalle
        // bleu pale de dix-huit metres — le semis oriente les pieces au hasard,
        // et une etagere sur deux se montre par l'arriere.
        double[] f = local(x, z, yaw, -(p / 2 - 0.3), 0);
        b.add(Meshes.sharedBox(), l - 2.0, h, 0.6, 0, yaw,
                f[0], Meshes.jy(y + h / 2), f[1], bois.deriveColor(0, 1, 0.75, 1));
        double[] m = local(x, z, yaw, -(p / 2 - 0.85), 0);
        b.add(Meshes.sharedBox(), l - 3.2, h * 0.76, 0.3, 0, yaw,
                m[0], Meshes.jy(y + h * 0.54), m[1], Color.web("#8fa2ab"));
        b.add(Meshes.sharedBox(), l + 0.8, 1.2, p + 1.2, 0, yaw,
                x, Meshes.jy(y + 0.6), z, bois.deriveColor(0, 1, 0.82, 1));
        b.add(Meshes.sharedBox(), l + 1.0, 1.0, p + 1.4, 0, yaw,
                x, Meshes.jy(y + h - 0.5), z, bois.deriveColor(0, 1, 1.2, 1));
        for (int e = 0; e < 3; e++) {
            double ey = y + 1.2 + e * (h - 2.2) / 3;
            b.add(Meshes.sharedBox(), l - 2.0, 0.4, p * 0.9, 0, yaw, x, Meshes.jy(ey), z,
                    bois.deriveColor(0, 1, 1.35, 1));
            for (int i = 0; i < 6; i++) {
                double off = (i - 2.5) * (l - 2.6) / 6;
                Color verre = VERRES[rnd.nextInt(VERRES.length)];
                double[] q = local(x, z, yaw, 0.5, off);
                b.add(Meshes.sharedCylinder(7), 0.5, 2.1, 0.5, 0, yaw,
                        q[0], Meshes.jy(ey + 1.25), q[1], verre);
                b.add(Meshes.sharedCylinder(5), 0.18, 1.1, 0.18, 0, yaw,
                        q[0], Meshes.jy(ey + 2.75), q[1], verre);
            }
        }
        return l * 0.55;
    }

    /** Table ronde et ses deux chaises. */
    private static double tableRonde(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double r = 4.5 + rnd.nextDouble() * 1.5, h = 6.5;
        Color bois = Color.web("#6b4526");
        b.add(Meshes.sharedCylinder(11), r, 0.5, r, 0, yaw, x, Meshes.jy(y + h), z, bois);
        b.add(Meshes.sharedCylinder(7), 0.6, h, 0.6, 0, yaw, x, Meshes.jy(y + h / 2), z, bois);
        b.add(Meshes.sharedCylinder(9), r * 0.5, 0.4, r * 0.5, 0, yaw, x, Meshes.jy(y + 0.2), z, bois);
        for (int i = -1; i <= 1; i += 2) {
            double a = Math.toRadians(yaw + (i > 0 ? 0 : 180));
            double cx = x + Math.cos(a) * (r + 2.5), cz = z - Math.sin(a) * (r + 2.5);
            b.add(Meshes.sharedBox(), 3.0, 0.5, 3.0, 0, yaw, cx, Meshes.jy(y + 4.2), cz, bois);
            b.add(Meshes.sharedBox(), 3.0, 5.0, 0.4, 0, yaw,
                    cx + Math.cos(a) * 1.3, Meshes.jy(y + 6.6), cz - Math.sin(a) * 1.3,
                    bois.deriveColor(0, 1, 1.1, 1));
            for (int j = 0; j < 4; j++) {
                double aa = Math.toRadians(yaw + 45 + j * 90);
                b.add(Meshes.sharedCylinder(5), 0.22, 4.2, 0.22, 0, yaw,
                        cx + Math.cos(aa) * 1.1, Meshes.jy(y + 2.1), cz - Math.sin(aa) * 1.1, bois);
            }
        }
        return r + 4;
    }

    /**
     * Juke-box : socle, caisse, dome lumineux cercle de rouge, hublot a disque,
     * grille de haut-parleur et les deux tubes de couleur.
     *
     * <p>Deux fautes, et la meme cause : la piece etait batie dans les axes du
     * monde et non dans les siens. Le dome etait un cylindre couche <em>en
     * travers</em> de la caisse — sa demi-lune ne se voyait que de profil, et
     * de face l'appareil se presentait comme une boite a chapeau ; il est
     * maintenant couche dans la profondeur ({@code tilt 90} et
     * {@code yaw + 90}), comme l'arc d'un Wurlitzer. Et la facade lumineuse
     * etait posee a {@code z + 2,4} en coordonnees du monde : elle se
     * decrochait de la caisse des que le semis orientait la piece ailleurs
     * qu'au nord. Tout passe desormais par {@link #local}.
     */
    private static double jukebox(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 8.5, p = 5.6, hc = 8.4;
        Color caisse = Color.web("#a8302c");
        double dessus = y + 1.2 + hc;
        b.add(Meshes.sharedBox(), l, 1.2, p, 0, yaw,
                x, Meshes.jy(y + 0.6), z, Color.web("#2a1c18"));
        b.add(Meshes.sharedBox(), l, hc, p, 0, yaw, x, Meshes.jy(y + 1.2 + hc / 2), z, caisse);
        // le dome, couche dans la profondeur : sa demi-lune se lit de face. Le
        // cylindre est entier, sa moitie basse enfermee dans la caisse — une
        // demi-coque demanderait un maillage a elle seule.
        b.add(Meshes.sharedCylinder(12), l / 2, p * 0.98, l / 2, 90, yaw + 90,
                x, Meshes.jy(dessus), z, caisse);
        // et sa face avant en ambre, la vitre eclairee. Elle etait bordee de
        // rouge des deux bouts : un cylindre n'a pas de couronne, seulement des
        // fonds pleins, et ce fond rouge masquait tout l'ambre de face.
        double[] arc = local(x, z, yaw, p * 0.46, 0);
        b.add(Meshes.sharedCylinder(12), l / 2 * 1.03, p * 0.14, l / 2 * 1.03, 90, yaw + 90,
                arc[0], Meshes.jy(dessus), arc[1], Color.web("#f0b83c"));
        // le hublot ou tourne le disque, dans la moitie visible de l'arc
        double[] hub = local(x, z, yaw, p * 0.54, 0);
        b.add(Meshes.sharedBox(), l * 0.5, 3.0, 0.5, 0, yaw,
                hub[0], Meshes.jy(dessus + 1.3), hub[1], Color.web("#1a1418"));
        double[] disq = local(x, z, yaw, p * 0.58, 0);
        b.add(Meshes.sharedCylinder(11), 1.15, 0.3, 1.15, 90, yaw + 90,
                disq[0], Meshes.jy(dessus + 1.3), disq[1], Color.web("#e8e2d4"));
        // grille de haut-parleur et bandeau de selection
        double[] gr = local(x, z, yaw, p / 2 + 0.1, 0);
        b.add(Meshes.sharedBox(), l * 0.66, 3.6, 0.4, 0, yaw,
                gr[0], Meshes.jy(y + 3.6), gr[1], Color.web("#d8c48a"));
        b.add(Meshes.sharedBox(), l * 0.72, 1.4, 0.4, 0, yaw,
                gr[0], Meshes.jy(y + 7.4), gr[1], Color.web("#2a2228"));
        // les deux tubes de couleur des montants, la marque de la maison
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, p / 2 + 0.2, i * (l / 2 - 0.4));
            b.add(Meshes.sharedCylinder(8), 0.5, hc * 0.94, 0.5, 0, yaw,
                    q[0], Meshes.jy(y + 1.2 + hc / 2), q[1], Color.web("#3fa88a"));
        }
        return l * 0.6;
    }

    /**
     * Caisse de bouteilles : le bac, sa ceinture, et les six cols qui
     * depassent.
     *
     * <p>Les bouteilles etaient posees <em>sur</em> le couvercle du bac, panse
     * comprise : de loin, une caisse bleue herissee de gros batons de couleur,
     * et c'est ainsi qu'elle se lisait a l'ecran. Elles sont maintenant dedans,
     * et seuls le col et la capsule sortent — l'image qu'a une caisse pleine.
     * Le bac monte de un metre six a deux metres quatre, soit vingt-sept
     * centimetres au rapport du salon, la hauteur d'un casier.
     */
    private static double caisseDeBouteilles(DecorBatch b, Random rnd,
                                             double x, double y, double z, double yaw) {
        double c = 3.6, hb = 2.4;
        b.add(Meshes.sharedBox(), c, hb, c * 0.72, 0, yaw,
                x, Meshes.jy(y + hb / 2), z, Color.web("#35619b"));
        b.add(Meshes.sharedBox(), c * 1.06, 0.5, c * 0.78, 0, yaw,
                x, Meshes.jy(y + hb - 0.25), z, Color.web("#5f8ec4"));
        for (int i = 0; i < 6; i++) {
            Color verre = VERRES[rnd.nextInt(VERRES.length)];
            double[] q = local(x, z, yaw, ((i / 3) - 0.5) * c * 0.3, ((i % 3) - 1) * c * 0.3);
            b.add(Meshes.sharedCylinder(6), 0.26, 1.6, 0.26, 0, yaw,
                    q[0], Meshes.jy(y + hb + 0.6), q[1], verre);
            b.add(Meshes.sharedCylinder(6), 0.32, 0.28, 0.32, 0, yaw,
                    q[0], Meshes.jy(y + hb + 1.5), q[1], Color.web("#c9a227"));
        }
        return c * 0.6;
    }

    // -------------------------------------------- le mobilier de la salle

    /**
     * Une autre table de billard, au fond de la salle.
     *
     * <p>C'etait un cageot : un bloc plein de vingt-six unites, une dalle verte
     * dessus et deux bandes sur deux cotes. Les captures le montrent tel quel —
     * une caisse brune surmontee d'un trait vert, la plus grosse piece de la
     * salle et personne ne saurait la nommer. Une table de billard tient a
     * trois choses, et il en manquait deux : les six pieds massifs qui la font
     * flotter au-dessus du sol, le cadre de bandes ferme sur les quatre cotes,
     * et les billes restees sur le tapis.
     *
     * <p>Cotes prises a l'etalon des pieces d'interieur — un metre reel pour
     * neuf unites, mesure sur le canape du salon et le comptoir du bar : deux
     * metres huit sur un metre quarante, plateau a quatre-vingt-cinq
     * centimetres, ce qui la met a hauteur d'assise du tabouret voisin.
     */
    private static double autreTable(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 24 + rnd.nextDouble() * 5, p = 12.6;
        double hPied = 5.4, hCeinture = 1.7, hBande = 1.4;
        double plateau = hPied + hCeinture;
        Color bois = Color.web("#5a3a22");
        Color bande = bois.deriveColor(0, 1, 1.25, 1);
        Color feutre = Color.web("#1f7a46");
        // six pieds, comme une vraie table : deux rangs de trois
        for (int i = -1; i <= 1; i++) {
            for (int j = -1; j <= 1; j += 2) {
                double[] q = local(x, z, yaw, j * (p / 2 - 2.0), i * (l / 2 - 2.0));
                b.add(Meshes.sharedBox(), 2.4, hPied, 2.4, 0, yaw,
                        q[0], Meshes.jy(y + hPied / 2), q[1], bois.deriveColor(0, 1, 0.85, 1));
            }
        }
        b.add(Meshes.sharedBox(), l - 1.2, hCeinture, p - 1.2, 0, yaw,
                x, Meshes.jy(y + hPied + hCeinture / 2), z, bois);
        b.add(Meshes.sharedBox(), l - 4.4, 0.5, p - 4.4, 0, yaw,
                x, Meshes.jy(y + plateau + 0.25), z, feutre);
        // le cadre de bandes, ferme sur les quatre cotes
        for (int j = -1; j <= 1; j += 2) {
            double[] q = local(x, z, yaw, j * (p / 2 - 1.1), 0);
            b.add(Meshes.sharedBox(), l, hBande, 2.2, 0, yaw,
                    q[0], Meshes.jy(y + plateau + hBande / 2), q[1], bande);
            double[] t = local(x, z, yaw, 0, j * (l / 2 - 1.1));
            b.add(Meshes.sharedBox(), 2.2, hBande, p - 4.4, 0, yaw,
                    t[0], Meshes.jy(y + plateau + hBande / 2), t[1], bande);
        }
        // trois billes et la blanche restees sur le tapis : de loin, c'est la
        // seule chose qui distingue ce plateau vert d'une table de jardin
        double[][] restes = {{2.2, -5.0, 3}, {1.0, -3.2, 5}, {-1.8, 4.4, 7}, {-2.6, 6.6, 0}};
        for (double[] r : restes) {
            double[] q = local(x, z, yaw, r[0], r[1]);
            bille(b, 7, 0.42, (int) r[2], q[0], y + plateau + 0.5, q[1], yaw);
        }
        return l * 0.55;
    }

    /**
     * Ratelier a queues, pose au sol.
     *
     * <p>Une planche de six unites sur quatorze, quatre batons plaques dessus :
     * de loin, un poteau. Le ratelier se lit par ses queues dressees cote a
     * cote, pas par sa planche — il lui fallait donc un socle ou poser les
     * talons, deux montants et deux traverses qui tiennent les fleches, et six
     * queues au lieu de quatre. Un metre quatre-vingts de haut, un metre dix de
     * large a l'etalon d'interieur.
     */
    private static double porteQueues(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double h = 16, w = 10;
        Color bois = Color.web("#4a2f19");
        Color clair = bois.deriveColor(0, 1, 1.35, 1);
        b.add(Meshes.sharedBox(), w + 1.4, 1.4, 3.2, 0, yaw,
                x, Meshes.jy(y + 0.7), z, bois);
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * w / 2);
            b.add(Meshes.sharedBox(), 1.1, h, 2.2, 0, yaw,
                    q[0], Meshes.jy(y + h / 2), q[1], bois);
        }
        b.add(Meshes.sharedBox(), w + 1.4, 1.3, 2.4, 0, yaw,
                x, Meshes.jy(y + h - 0.65), z, clair);
        b.add(Meshes.sharedBox(), w, 0.9, 1.6, 0, yaw,
                x, Meshes.jy(y + 9.0), z, clair);
        // six queues, talon dans le socle et fleche en l'air
        for (int i = 0; i < 6; i++) {
            double[] q = local(x, z, yaw, -0.5, (i - 2.5) * 1.7);
            b.add(Meshes.sharedCylinder(6), 0.19, 4.4, 0.19, 0, yaw,
                    q[0], Meshes.jy(y + 3.6), q[1], Color.web("#3a2a1c"));
            b.add(Meshes.sharedCylinder(6), 0.13, 8.8, 0.13, 0, yaw,
                    q[0], Meshes.jy(y + 10.2), q[1], Color.web("#d8b271"));
            b.add(Meshes.sharedCylinder(6), 0.11, 0.5, 0.11, 0, yaw,
                    q[0], Meshes.jy(y + 14.8), q[1], Color.web("#eae2d0"));
        }
        return w * 0.6;
    }

    /**
     * Tableau de score : l'ardoise, son cadre, sa tablette a craie.
     *
     * <p>C'etait un panneau de quatre-vingt-dix centimetres juche au bout d'un
     * mat d'un metre vingt, c'est-a-dire trait pour trait la silhouette d'un
     * panneau publicitaire — la seule chose qu'une piece d'interieur ne doit
     * jamais montrer, et le defaut qu'on venait de corriger sur la cible de
     * flechettes du comptoir. Le remede est le meme : l'ardoise descend au sol.
     * Elle repose sur deux pieds et une tablette, ou trainent deux craies, et
     * porte les batons de la marque ; de dos, c'est un panneau de bois pose
     * dans un coin de salle, pas une affiche.
     */
    private static double tableauDeScore(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double w = 10.4, hCadre = 7.2, sol = 2.2;
        Color bois = Color.web("#6b4526");
        Color ardoise = Color.web("#1e2a22");
        Color craie = Color.web("#e8e2d0");
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * 3.6);
            b.add(Meshes.sharedBox(), 1.6, sol, 4.0, 0, yaw,
                    q[0], Meshes.jy(y + sol / 2), q[1], bois.deriveColor(0, 1, 0.8, 1));
        }
        b.add(Meshes.sharedBox(), w, hCadre, 1.2, 0, yaw,
                x, Meshes.jy(y + sol + hCadre / 2), z, bois);
        double[] face = local(x, z, yaw, 0.75, 0);
        b.add(Meshes.sharedBox(), w - 1.6, hCadre - 1.6, 0.4, 0, yaw,
                face[0], Meshes.jy(y + sol + hCadre / 2), face[1], ardoise);
        // la tablette a craie, et les deux craies dessus
        double[] tab = local(x, z, yaw, 1.2, 0);
        b.add(Meshes.sharedBox(), w, 0.7, 2.2, 0, yaw,
                tab[0], Meshes.jy(y + sol + 0.35), tab[1], bois.deriveColor(0, 1, 1.3, 1));
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 1.2, i * 2.4);
            b.add(Meshes.sharedBox(), 0.5, 0.5, 0.5, 0, yaw,
                    q[0], Meshes.jy(y + sol + 0.95), q[1], Color.web("#2f6fa8"));
        }
        // les batons de la marque : deux colonnes separees d'un trait
        b.add(Meshes.sharedBox(), 0.22, hCadre - 2.6, 0.2, 0, yaw,
                face[0], Meshes.jy(y + sol + hCadre / 2), face[1], craie);
        for (int col = -1; col <= 1; col += 2) {
            for (int i = 0; i < 3; i++) {
                double[] q = local(x, z, yaw, 0.95, col * 2.3);
                b.add(Meshes.sharedBox(), 2.6, 0.28, 0.2, 0, yaw,
                        q[0], Meshes.jy(y + sol + hCadre - 2.0 - i * 1.3), q[1], craie);
            }
        }
        return w * 0.55;
    }

    private static void place(Group parent, Node node, double x, double y, double z, double rotDeg) {
        node.setTranslateX(x);
        node.setTranslateZ(z);
        node.setTranslateY(Meshes.jy(y));
        node.setRotationAxis(Rotate.Y_AXIS);
        node.setRotate(rotDeg);
        parent.getChildren().add(node);
    }

    /** Ombre portee approximative : un disque sombre pose au sol. */
    private static void blob(DecorBatch batch, double x, double y, double z, double radius) {
        batch.add(Meshes.sharedDisc(8), radius, 1, radius, 0, 0,
                x, Meshes.jy(y), z, Color.rgb(38, 38, 34));
    }

    /**
     * Finesse des formes selon l'eloignement de la piste.
     *
     * La camera suit le ruban : un arbre pose a 60 m du bitume ne sera jamais
     * vu de pres. Lui laisser les facettes d'un arbre de bord de piste, c'est
     * payer des triangles plus petits qu'un pixel — et une carte graphique les
     * rasterise par blocs de quatre, donc au prix fort. Deux paliers suffisent
     * a ramener le decor lointain a un cout raisonnable sans qu'on voie
     * l'objet changer : on ne s'en approche jamais assez.
     *
     * Seule la finesse varie, jamais le nombre de pieces ni de tirages : le
     * decor est implante par un {@code Random} de graine fixe, et sauter un
     * tirage decalerait toute la suite — donc l'implantation entiere.
     */
    private static final double LOD_MID = 45;
    private static final double LOD_FAR = 80;

    /**
     * Depose une plante et renvoie le rayon de son ombre, ou 0 si l'objet n'en
     * merite pas.
     *
     * @param lat distance a la piste, qui commande la finesse
     */
    private static double plant(DecorBatch b, Theme theme, Random rnd, Quality quality,
                                double x, double y, double z, double yaw, double lat) {
        int sphereDiv = quality.sphereDiv;
        int coneSides = quality.coneSides;
        if (lat > LOD_FAR) {
            sphereDiv = Math.min(sphereDiv, 6);
            coneSides = Math.min(coneSides, 8);
        } else if (lat > LOD_MID) {
            sphereDiv = Math.min(sphereDiv, 8);
            coneSides = Math.min(coneSides, 10);
        }
        int lumpDiv = lat > LOD_FAR ? 5 : (lat > LOD_MID ? 7 : 9);

        // Un tirage unique commande l'espece : chaque theme a son repertoire,
        // et les seuils sont ranges du plus commun au plus rare. Ce qui donne
        // le sentiment d'un paysage, ce n'est pas le nombre d'especes mais leur
        // dosage — un bosquet qui n'aurait que des pieces rares serait aussi
        // monotone qu'un bosquet qui n'en aurait aucune.
        double roll = rnd.nextDouble();

        // Echelle de la piece. Dehors, un arbre reste un arbre : la variation
        // est faible. Dedans, c'est l'inverse qui fait le decor — un salon est
        // fait d'un gros ours contre le mur et d'un de perdu dans le tapis, et
        // c'est cet ecart de taille qui donne l'impression d'etre minuscule.
        double taille = rnd.nextDouble();
        double echelle = theme.indoor
                // dedans, le mobilier lointain porte deja sa taille : on ne fait
                // plus varier que les objets poses, et moins brutalement
                ? (lat > MOBILIER ? 0.85 + taille * 0.4
                        : taille < 0.7 ? 0.6 + taille * 0.9 : 1.23 + (taille - 0.7) * 4.5)
                : 0.85 + taille * 0.35;
        // le pivot est donne dans le repere de JavaFX, comme les translations
        // des pieces : le prendre en coordonnees jeu, ou Y est inverse, faisait
        // decoller les gros jouets du sol
        b.setScale(echelle, x, Meshes.jy(y), z);
        double rayon = switch (theme) {
            case GRASS -> prairie(b, rnd, roll, quality, sphereDiv, lumpDiv, x, y, z, yaw);
            case FOREST -> foret(b, rnd, roll, sphereDiv, coneSides, lumpDiv, x, y, z, yaw);
            case SNOW -> banquise(b, rnd, roll, sphereDiv, coneSides, lumpDiv, x, y, z, yaw);
            case DESERT -> desert(b, rnd, roll, sphereDiv, coneSides, lumpDiv, x, y, z, yaw);
            case VOLCANO -> volcan(b, rnd, roll, sphereDiv, coneSides, lumpDiv, x, y, z, yaw);
            case DONJON -> donjon(b, rnd, roll, sphereDiv, coneSides, lumpDiv, x, y, z, yaw, lat);
            case BILLARD -> billard(b, rnd, roll, sphereDiv, x, y, z, yaw, lat);
            case BAR -> bar(b, rnd, roll, sphereDiv, coneSides, x, y, z, yaw, lat);
            case SALON -> salon(b, rnd, roll, sphereDiv, coneSides, x, y, z, yaw, lat);
        };
        b.resetScale();
        return rayon * echelle;
    }

    // ------------------------------------------------------- les repertoires

    private static double prairie(DecorBatch b, Random rnd, double roll, Quality quality,
                                  int sphereDiv, int lumpDiv,
                                  double x, double y, double z, double yaw) {
        if (roll < 0.30) return feuillu(b, rnd, quality, sphereDiv, x, y, z, yaw, false);
        if (roll < 0.44) return feuillu(b, rnd, quality, sphereDiv, x, y, z, yaw, true);
        if (roll < 0.58) return bouleau(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.70) return bush(b, Theme.GRASS, rnd, x, y, z, yaw, lumpDiv);
        if (roll < 0.78) return rock(b, rnd, x, y, z, yaw, lumpDiv);
        if (roll < 0.90) return fleurs(b, rnd, x, y, z, yaw);
        return botteDeFoin(b, rnd, x, y, z, yaw);
    }

    private static double foret(DecorBatch b, Random rnd, double roll,
                                int sphereDiv, int coneSides, int lumpDiv,
                                double x, double y, double z, double yaw) {
        if (roll < 0.40) return sapin(b, rnd, coneSides, x, y, z, yaw, false);
        if (roll < 0.54) return arbreMort(b, rnd, x, y, z, yaw);
        if (roll < 0.64) return souche(b, rnd, x, y, z, yaw, Color.web("#5b432c"));
        if (roll < 0.74) return troncCouche(b, rnd, x, y, z, yaw);
        if (roll < 0.84) return champignon(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.93) return bush(b, Theme.FOREST, rnd, x, y, z, yaw, lumpDiv);
        return rock(b, rnd, x, y, z, yaw, lumpDiv);
    }

    private static double banquise(DecorBatch b, Random rnd, double roll,
                                   int sphereDiv, int coneSides, int lumpDiv,
                                   double x, double y, double z, double yaw) {
        if (roll < 0.34) return sapin(b, rnd, coneSides, x, y, z, yaw, true);
        if (roll < 0.48) return picDeGlace(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.60) return blocDeGlace(b, rnd, x, y, z, yaw);
        if (roll < 0.72) return bush(b, Theme.SNOW, rnd, x, y, z, yaw, lumpDiv);
        if (roll < 0.82) return rock(b, rnd, x, y, z, yaw, lumpDiv);
        if (roll < 0.92) return balise(b, rnd, x, y, z, yaw);
        return bonhommeDeNeige(b, rnd, sphereDiv, x, y, z, yaw);
    }

    /**
     * Le repertoire du desert, retaille pour qu'il fasse un paysage et non un
     * bandeau.
     *
     * <p>Les huit especes sont les memes et <b>les seuils n'ont pas bouge</b>
     * d'un centieme : l'implantation du semis est donc exactement celle
     * d'avant, chaque piece est restee a sa place. Ce qui change est ailleurs.
     *
     * <p>D'abord les valeurs. Le sol du theme est descendu a l'ocre orange
     * pour qu'on voie ou finit la chaussee, et tout le repertoire mineral
     * pesait justement cette valeur-la : mesas, monolithes et arches se
     * seraient noyes dedans. Ils se repartissent maintenant sur trois marches
     * franches — le gres d'ombre du corps des mesas et des monolithes, le gres
     * clair de leur chapeau et des arches, et le vert des cactus — de part et
     * d'autre du sol. C'est l'ecart de clarte, et non la saturation, qui
     * separe deux formes a trente metres.
     *
     * <p>Ensuite les tailles. Toutes les buttes tenaient entre deux et cinq
     * metres de haut : a la densite du desert, cela faisait une haie de bornes
     * identiques a hauteur d'horizon. La meme piece va maintenant du caillou
     * de deux metres a la cheminee de quinze, sans un tirage de plus.
     */
    private static double desert(DecorBatch b, Random rnd, double roll,
                                 int sphereDiv, int coneSides, int lumpDiv,
                                 double x, double y, double z, double yaw) {
        if (roll < 0.28) return cactus(b, rnd, x, y, z, yaw);
        if (roll < 0.42) return agave(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.54) return butte(b, rnd, x, y, z, yaw);
        if (roll < 0.64) return bush(b, Theme.DESERT, rnd, x, y, z, yaw, lumpDiv);
        // le rocher partage prend la teinte du desert par la surcharge, sans un
        // tirage de plus : le gris brun des quatre autres decors de plein air
        // se lisait comme une piece rapportee sur un sol ocre
        if (roll < 0.74) return rock(b, rnd, x, y, z, yaw, lumpDiv, ROCHE_DESERT);
        if (roll < 0.84) return herbeSeche(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.93) return pierreDressee(b, rnd, x, y, z, yaw);
        return arche(b, rnd, x, y, z, yaw);
    }

    /**
     * Les trois valeurs minerales du desert.
     *
     * <p>Le sol du theme pese 117 de clarte moyenne. Le gres d'ombre est a 74
     * et le gres clair a 182 : un corps de mesa se detache du sable par le
     * bas, son chapeau par le haut, et c'est ce contraste interne qui fait
     * lire la table a cent metres. Le rocher, lui, est un brun sombre et non
     * le gris brun commun aux autres decors — le meme caillou, la meme piece,
     * juste teinte par la surcharge.
     */
    private static final Color GRES_SOMBRE = Color.web("#7a4126");
    private static final Color GRES_CLAIR = Color.web("#d6b78b");
    private static final Color ROCHE_DESERT = Color.web("#4a3225");

    /**
     * Le repertoire du volcan, refait pour qu'il se distingue a trente metres.
     *
     * <p>Les sept especes precedentes etaient toutes de la meme valeur
     * gris-noir — roche, orgues, souche brulee, obsidienne, cone de cendre,
     * braise, arbre mort — et {@code VOLCANO.decorDensity} vaut 2,70, la plus
     * forte du jeu : on empilait donc massivement des formes qu'aucune capture
     * ne permettait de nommer. Ce qui manque a un champ de lave n'est pas une
     * espece de plus, c'est du <b>contraste</b> : la scorie est un gris de
     * cendre clair, le basalte un noir franc, le soufre un ocre vif, la lave
     * une orange saturee. Chaque tranche du tirage porte donc maintenant une
     * valeur differente de sa voisine, et non une nuance d'anthracite.
     *
     * <p>Les seuils ont bouge, donc <b>l'implantation du semis a bouge</b> :
     * les especes ne consomment pas le meme nombre de tirages, et changer
     * laquelle repond a un tirage donne decale toute la suite. C'est assume —
     * on ne pouvait pas redistribuer huit frequences en gardant l'implantation
     * — et le circuit a ete repasse a l'oeil apres chaque passe, quatre fois
     * en tout. Aucun autre theme n'est touche : les pieces partagees
     * ({@code rock}, {@code arbreMort}) ont recu une teinte en parametre sans
     * un seul tirage de plus, et une capture de controle sur la Colline du
     * Manchot le verifie.
     */
    private static double volcan(DecorBatch b, Random rnd, double roll,
                                 int sphereDiv, int coneSides, int lumpDiv,
                                 double x, double y, double z, double yaw) {
        // scorie claire : la valeur haute du repertoire, celle qui detache
        // toutes les autres du sol
        if (roll < 0.22) return blocDeScorie(b, rnd, x, y, z, yaw);
        if (roll < 0.38) return orguesBasaltiques(b, rnd, x, y, z, yaw);
        if (roll < 0.50) return couleeDeLave(b, rnd, x, y, z, yaw);
        // 6 % et pas 8 : c'est la seule piece jaune, la plus saturee de toutes,
        // et a huit pour cent on comptait dix taches vives dans un seul cadre
        if (roll < 0.56) return depotDeSoufre(b, rnd, lumpDiv, x, y, z, yaw);
        if (roll < 0.72) return obsidienne(b, rnd, coneSides, x, y, z, yaw);
        // La fumerolle est passee a 15 % le temps d'une capture : avec une
        // densite de 2,70 et un panache de huit metres, l'horizon entier
        // devenait un champ de choux-fleurs pales et plus rien d'autre ne se
        // voyait. Une colonne de fumee est un accent, pas un fond — 6 %.
        if (roll < 0.78) return fumerolle(b, rnd, coneSides, sphereDiv, x, y, z, yaw);
        if (roll < 0.90) return rocherIncandescent(b, rnd, lumpDiv, x, y, z, yaw);
        // l'arbre calcine reste a 10 % : sur un ciel rouge, un tronc noir est
        // la meilleure silhouette du repertoire — ce qui en faisait un rideau
        // n'etait pas sa frequence mais le fait que tout le reste avait la
        // meme valeur que lui
        return arbreMort(b, rnd, x, y, z, yaw, CHARBON);
    }

    /**
     * Cendre soudee : la seule valeur claire du volcan, et son fond commun.
     *
     * <p>Un cran plus sombre que le gris qu'elle valait a la premiere passe :
     * la piece eclaircit sa teinte jusqu'a 1,3 fois, et a #8e8378 les blocs
     * les plus clairs ressortaient creme — de la craie sur une coulee.
     */
    private static final Color SCORIE = Color.web("#6f665d");
    /** Bois calcine : un arbre mort de coulee n'a plus de brun. */
    private static final Color CHARBON = Color.web("#241e1b");

    // ------------------------------------------- les repertoires d'interieur

    /**
     * Distances qui decoupent une piece en trois plans.
     *
     * Une piece n'est pas un semis uniforme : les meubles sont contre les murs,
     * les gros objets a mi-distance, et le petit fourbi trainer au sol la ou on
     * marche. Le decor est donc choisi par eloignement du ruban, ce qui donne
     * une piece habitee sans avoir a placer quoi que ce soit a la main.
     */
    private static final double POSE = 22;
    private static final double MOBILIER = 52;

    private static double donjon(DecorBatch b, Random rnd, double roll,
                                 int sphereDiv, int coneSides, int lumpDiv,
                                 double x, double y, double z, double yaw, double lat) {
        if (lat > MOBILIER) {
            if (roll < 0.22) return sarcophage(b, rnd, x, y, z, yaw);
            if (roll < 0.40) return arcade(b, rnd, x, y, z, yaw, true);
            if (roll < 0.56) return colonneBrisee(b, rnd, x, y, z, yaw, true);
            if (roll < 0.70) return armoire(b, rnd, x, y, z, yaw);
            if (roll < 0.82) return trone(b, rnd, x, y, z, yaw);
            if (roll < 0.92) return gargouille(b, rnd, sphereDiv, x, y, z, yaw);
            return torche(b, rnd, coneSides, x, y, z, yaw);
        }
        if (lat > POSE) {
            if (roll < 0.24) return pierreTombale(b, rnd, x, y, z, yaw);
            if (roll < 0.40) return coffre(b, rnd, x, y, z, yaw);
            if (roll < 0.54) return chaudron(b, rnd, sphereDiv, x, y, z, yaw);
            if (roll < 0.68) return torche(b, rnd, coneSides, x, y, z, yaw);
            if (roll < 0.82) return colonneBrisee(b, rnd, x, y, z, yaw);
            if (roll < 0.93) return gravats(b, rnd, x, y, z, yaw);
            return champignon(b, rnd, sphereDiv, x, y, z, yaw);
        }
        if (roll < 0.28) return gravats(b, rnd, x, y, z, yaw);
        if (roll < 0.46) return crane(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.60) return chandelier(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.74) return champignon(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.88) return pierreTombale(b, rnd, x, y, z, yaw);
        return chaudron(b, rnd, sphereDiv, x, y, z, yaw);
    }

    private static double billard(DecorBatch b, Random rnd, double roll, int sphereDiv,
                                  double x, double y, double z, double yaw, double lat) {
        if (lat > MOBILIER) {
            if (roll < 0.30) return autreTable(b, rnd, x, y, z, yaw);
            if (roll < 0.50) return porteQueues(b, rnd, x, y, z, yaw);
            if (roll < 0.68) return tabouret(b, rnd, x, y, z, yaw);
            if (roll < 0.84) return tableauDeScore(b, rnd, x, y, z, yaw);
            return autreTable(b, rnd, x, y, z, yaw);
        }
        if (lat > POSE) {
            if (roll < 0.28) return triangleDeRack(b, rnd, sphereDiv, x, y, z, yaw);
            if (roll < 0.50) return queueDeBillard(b, rnd, x, y, z, yaw);
            if (roll < 0.70) return bouleDeBillard(b, rnd, sphereDiv, x, y, z, yaw, 6);
            if (roll < 0.86) return poche(b, rnd, x, y, z, yaw);
            return craie(b, rnd, x, y, z, yaw);
        }
        if (roll < 0.46) return bouleDeBillard(b, rnd, sphereDiv, x, y, z, yaw, 1);
        if (roll < 0.68) return bouleDeBillard(b, rnd, sphereDiv, x, y, z, yaw, 3);
        if (roll < 0.86) return craie(b, rnd, x, y, z, yaw);
        return poche(b, rnd, x, y, z, yaw);
    }

    private static double bar(DecorBatch b, Random rnd, double roll, int sphereDiv, int coneSides,
                              double x, double y, double z, double yaw, double lat) {
        if (lat > MOBILIER) {
            if (roll < 0.24) return comptoir(b, rnd, x, y, z, yaw);
            if (roll < 0.44) return etagereABouteilles(b, rnd, x, y, z, yaw);
            if (roll < 0.62) return tableRonde(b, rnd, x, y, z, yaw);
            if (roll < 0.76) return jukebox(b, rnd, x, y, z, yaw);
            if (roll < 0.88) return tonneau(b, rnd, x, y, z, yaw);
            return cible(b, rnd, x, y, z, yaw);
        }
        if (lat > POSE) {
            if (roll < 0.26) return tabouret(b, rnd, x, y, z, yaw);
            if (roll < 0.46) return tonneau(b, rnd, x, y, z, yaw);
            if (roll < 0.64) return bouteille(b, rnd, x, y, z, yaw);
            if (roll < 0.80) return caisseDeBouteilles(b, rnd, x, y, z, yaw);
            if (roll < 0.92) return chope(b, rnd, sphereDiv, x, y, z, yaw);
            return cible(b, rnd, x, y, z, yaw);
        }
        if (roll < 0.26) return chope(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.46) return verreAPied(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.64) return bouteille(b, rnd, x, y, z, yaw);
        if (roll < 0.80) return sousVerres(b, rnd, x, y, z, yaw);
        return bolDeCacahuetes(b, rnd, sphereDiv, x, y, z, yaw);
    }

    private static double salon(DecorBatch b, Random rnd, double roll, int sphereDiv, int coneSides,
                                double x, double y, double z, double yaw, double lat) {
        if (lat > MOBILIER) {
            // le fond de la piece : les meubles, contre les murs
            if (roll < 0.20) return canape(b, rnd, x, y, z, yaw);
            if (roll < 0.34) return fauteuil(b, rnd, x, y, z, yaw);
            if (roll < 0.48) return bibliotheque(b, rnd, x, y, z, yaw);
            if (roll < 0.60) return tableBasse(b, rnd, x, y, z, yaw);
            if (roll < 0.70) return meubleTele(b, rnd, x, y, z, yaw);
            if (roll < 0.80) return lampadaire(b, rnd, coneSides, x, y, z, yaw);
            if (roll < 0.88) return planteVerte(b, rnd, sphereDiv, x, y, z, yaw);
            if (roll < 0.94) return nounours(b, rnd, sphereDiv, x, y, z, yaw);
            return carton(b, rnd, x, y, z, yaw);
        }
        if (lat > POSE) {
            // a mi-distance : le gros du fourbi, ce qu'on enjambe
            if (roll < 0.16) return pileDeLivres(b, rnd, x, y, z, yaw);
            if (roll < 0.30) return ballon(b, rnd, sphereDiv, x, y, z, yaw);
            if (roll < 0.44) return nounours(b, rnd, sphereDiv, x, y, z, yaw);
            if (roll < 0.58) return brique(b, rnd, x, y, z, yaw);
            if (roll < 0.70) return coussin(b, rnd, x, y, z, yaw);
            if (roll < 0.82) return petiteVoiture(b, rnd, x, y, z, yaw);
            if (roll < 0.92) return quille(b, rnd, sphereDiv, x, y, z, yaw);
            return carton(b, rnd, x, y, z, yaw);
        }
        // au ras du tapis : ce qui traine vraiment
        if (roll < 0.22) return brique(b, rnd, x, y, z, yaw);
        if (roll < 0.38) return cubeDeBois(b, rnd, x, y, z, yaw);
        if (roll < 0.52) return crayon(b, rnd, coneSides, x, y, z, yaw);
        if (roll < 0.66) return petiteVoiture(b, rnd, x, y, z, yaw);
        if (roll < 0.78) return de(b, rnd, x, y, z, yaw);
        if (roll < 0.88) return bille(b, rnd, sphereDiv, x, y, z, yaw);
        if (roll < 0.96) return quille(b, rnd, sphereDiv, x, y, z, yaw);
        return ballon(b, rnd, sphereDiv, x, y, z, yaw);
    }

    // ------------------------------------------------------------- les pieces

    /** Feuillu : le tronc et ses lobes. {@code fleuri} le teinte en floraison. */
    private static double feuillu(DecorBatch b, Random rnd, Quality quality, int sphereDiv,
                                  double x, double y, double z, double yaw, boolean fleuri) {
        double h = 5.0 + rnd.nextDouble() * 4.0;
        double trunkR = 0.28 + rnd.nextDouble() * 0.12;
        b.add(Meshes.sharedCylinder(8), trunkR, h, trunkR, 0, yaw,
                x, Meshes.jy(y + h / 2), z,
                Color.web("#6b4a2b").deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.3, 1));
        Color leaf = fleuri
                ? Color.web("#d97ba8").deriveColor(rnd.nextDouble() * 24 - 12, 1,
                        0.9 + rnd.nextDouble() * 0.25, 1)
                : Color.web("#2f7a2a").deriveColor(rnd.nextDouble() * 18 - 9, 1,
                        0.80 + rnd.nextDouble() * 0.45, 1);
        int lobes = quality.decorDensity > 0.8 ? 3 : 1;
        double widest = 0;
        for (int i = 0; i < lobes; i++) {
            double r = 2.4 + rnd.nextDouble() * 1.2 - i * 0.35;
            widest = Math.max(widest, r);
            b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.85, r, 0, yaw,
                    x + (rnd.nextDouble() - 0.5) * 1.8,
                    Meshes.jy(y + h + 0.9 + i * 1.1),
                    z + (rnd.nextDouble() - 0.5) * 1.8,
                    leaf.deriveColor(0, 1, 1 - i * 0.06, 1));
        }
        return widest * 0.75;
    }

    /** Bouleau : haut, mince, tronc clair — il tranche sur les feuillus. */
    private static double bouleau(DecorBatch b, Random rnd, int sphereDiv,
                                  double x, double y, double z, double yaw) {
        double h = 7.0 + rnd.nextDouble() * 4.0;
        b.add(Meshes.sharedCylinder(6), 0.20, h, 0.20, 0, yaw,
                x, Meshes.jy(y + h / 2), z,
                Color.web("#e8e4da").deriveColor(0, 1, 0.92 + rnd.nextDouble() * 0.14, 1));
        Color leaf = Color.web("#8fc24a").deriveColor(rnd.nextDouble() * 14 - 7, 1,
                0.85 + rnd.nextDouble() * 0.3, 1);
        for (int i = 0; i < 2; i++) {
            double r = 1.5 + rnd.nextDouble() * 0.7 - i * 0.3;
            b.add(Meshes.sharedSphere(sphereDiv), r, r * 1.35, r, 0, yaw,
                    x + (rnd.nextDouble() - 0.5) * 0.9,
                    Meshes.jy(y + h + 0.6 + i * 1.3),
                    z + (rnd.nextDouble() - 0.5) * 0.9, leaf);
        }
        return 1.4;
    }

    /** Sapin, avec ou sans coiffe de neige. */
    private static double sapin(DecorBatch b, Random rnd, int coneSides,
                                double x, double y, double z, double yaw, boolean enneige) {
        double h = 6.5 + rnd.nextDouble() * 5;
        b.add(Meshes.sharedCylinder(6), 0.28, h * 0.35, 0.28, 0, yaw,
                x, Meshes.jy(y + h * 0.175), z, Color.web("#5a3f27"));
        Color needles = Color.web("#1f5b30").deriveColor(
                rnd.nextDouble() * 14 - 7, 1, 0.85 + rnd.nextDouble() * 0.4, 1);
        for (int i = 0; i < 3; i++) {
            double r = 2.4 - i * 0.55;
            b.add(Meshes.sharedCone(coneSides), r, h * 0.44, r, 0, yaw,
                    x, Meshes.jy(y + h * 0.24 + i * h * 0.24), z, needles);
        }
        if (enneige) {
            // La coiffe etait un cone de 1,0 m de rayon pose a 0,86 h et haut
            // de 0,22 h : il s'arretait a 1,08 h quand la pointe verte monte a
            // 1,16 h, et il rentrait sous le feuillage — un collier blanc aux
            // deux tiers de la hauteur, la pointe verte au-dessus. De pres cela
            // se lit comme un anneau peint et non comme de la neige.
            // Elle coiffe maintenant la pointe : meme volume de neige
            // qu'avant — une coiffe plus large aurait donne une foret de cones
            // blancs, essayee et rejetee a la capture — mais posee de 0,92 h a
            // 1,16 h, ou finit le feuillage, et plus large que lui a cette
            // hauteur (0,71 m) pour qu'elle deborde de la branche.
            b.add(Meshes.sharedCone(coneSides), 1.0, h * 0.24, 1.0, 0, yaw,
                    x, Meshes.jy(y + h * 0.92), z, Color.web("#fbfdff"));
        }
        return 1.9;
    }

    /** Cactus a deux bras. */
    private static double cactus(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw) {
        double h = 2.6 + rnd.nextDouble() * 2.8;
        Color skin = Color.web("#3f7d43").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.3, 1);
        b.add(Meshes.sharedCylinder(7), 0.5, h, 0.5, 0, yaw,
                x, Meshes.jy(y + h / 2), z, skin);
        b.add(Meshes.sharedSphere(7), 0.5, 0.5, 0.5, 0, yaw,
                x, Meshes.jy(y + h), z, skin);
        for (int i = 0; i < 2; i++) {
            int sign = i == 0 ? 1 : -1;
            double a = Math.toRadians(yaw);
            double ox = Math.cos(a) * 0.65 * sign;
            double oz = -Math.sin(a) * 0.65 * sign;
            b.add(Meshes.sharedCylinder(6), 0.3, 1.5, 0.3, sign * 70, yaw,
                    x + ox, Meshes.jy(y + h * (0.55 + i * 0.12)), z + oz, skin);
            b.add(Meshes.sharedCylinder(6), 0.3, 1.1, 0.3, 0, yaw,
                    x + ox * 1.9, Meshes.jy(y + h * (0.55 + i * 0.12) + 0.6),
                    z + oz * 1.9, skin);
        }
        return 0.9;
    }

    /** Touffe de fleurs : quelques tiges et autant de taches de couleur. */
    private static double fleurs(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw) {
        Color[] palette = {Color.web("#f2d24b"), Color.web("#e86a8f"),
                Color.web("#8f6ae8"), Color.web("#f0f0f0")};
        Color petale = palette[rnd.nextInt(palette.length)];
        for (int i = 0; i < 5; i++) {
            double ox = (rnd.nextDouble() - 0.5) * 1.6;
            double oz = (rnd.nextDouble() - 0.5) * 1.6;
            double h = 0.45 + rnd.nextDouble() * 0.35;
            b.add(Meshes.sharedCylinder(4), 0.045, h, 0.045, 0, yaw,
                    x + ox, Meshes.jy(y + h / 2), z + oz, Color.web("#4f8a3a"));
            b.add(Meshes.sharedSphere(5), 0.16, 0.10, 0.16, 0, yaw,
                    x + ox, Meshes.jy(y + h), z + oz,
                    petale.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1));
        }
        return 0;
    }

    /** Botte de foin ronde, couchee sur le flanc. */
    private static double botteDeFoin(DecorBatch b, Random rnd,
                                      double x, double y, double z, double yaw) {
        double r = 0.75 + rnd.nextDouble() * 0.35;
        b.add(Meshes.sharedCylinder(10), r, r * 1.8, r, 90, yaw,
                x, Meshes.jy(y + r), z,
                Color.web("#d8bf6a").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1));
        return r;
    }

    /** Arbre mort : un tronc penche et quelques branches nues. */
    private static double arbreMort(DecorBatch b, Random rnd,
                                    double x, double y, double z, double yaw) {
        return arbreMort(b, rnd, x, y, z, yaw, Color.web("#5e5045"));
    }

    /**
     * Le meme arbre mort, dans la teinte qu'on lui demande — brun sec en
     * foret, charbon sur une coulee. Aucun tirage de plus, pour que la Foret
     * de FreeBSD ne bouge pas d'un arbre.
     */
    private static double arbreMort(DecorBatch b, Random rnd,
                                    double x, double y, double z, double yaw, Color teinte) {
        double h = 4.5 + rnd.nextDouble() * 3.5;
        Color bois = teinte.deriveColor(0, 1, 0.8 + rnd.nextDouble() * 0.4, 1);
        b.add(Meshes.sharedCylinder(6), 0.26, h, 0.22, 4, yaw,
                x, Meshes.jy(y + h / 2), z, bois);
        for (int i = 0; i < 3; i++) {
            double a = Math.toRadians(yaw + i * 120 + rnd.nextDouble() * 40);
            double lift = h * (0.55 + i * 0.13);
            b.add(Meshes.sharedCylinder(5), 0.12, 1.4 + rnd.nextDouble(), 0.12,
                    55 + rnd.nextDouble() * 20, Math.toDegrees(a),
                    x + Math.cos(a) * 0.5, Meshes.jy(y + lift), z - Math.sin(a) * 0.5, bois);
        }
        return 0.5;
    }

    /** Souche : le tronc coupe net, avec sa section claire. */
    private static double souche(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw, Color bois) {
        double r = 0.45 + rnd.nextDouble() * 0.3;
        double h = 0.6 + rnd.nextDouble() * 0.5;
        b.add(Meshes.sharedCylinder(8), r, h, r, 0, yaw, x, Meshes.jy(y + h / 2), z, bois);
        b.add(Meshes.sharedDisc(8), r * 0.95, 1, r * 0.95, 0, yaw,
                x, Meshes.jy(y + h + 0.01), z, bois.deriveColor(0, 0.7, 1.45, 1));
        return r;
    }

    /** Tronc couche, a demi enfonce dans le sol. */
    private static double troncCouche(DecorBatch b, Random rnd,
                                      double x, double y, double z, double yaw) {
        double r = 0.32 + rnd.nextDouble() * 0.18;
        double l = 2.5 + rnd.nextDouble() * 2.5;
        b.add(Meshes.sharedCylinder(7), r, l, r, 90, yaw,
                x, Meshes.jy(y + r * 0.7), z,
                Color.web("#5b432c").deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.3, 1));
        return r * 1.4;
    }

    /** Champignon de conte : pied pale et chapeau vif. */
    private static double champignon(DecorBatch b, Random rnd, int sphereDiv,
                                     double x, double y, double z, double yaw) {
        double h = 0.5 + rnd.nextDouble() * 0.7;
        double r = 0.35 + rnd.nextDouble() * 0.35;
        b.add(Meshes.sharedCylinder(6), r * 0.32, h, r * 0.32, 0, yaw,
                x, Meshes.jy(y + h / 2), z, Color.web("#efe6d2"));
        Color chapeau = rnd.nextDouble() < 0.6 ? Color.web("#c0392b") : Color.web("#a06a3a");
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.62, r, 0, yaw,
                x, Meshes.jy(y + h), z,
                chapeau.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1));
        return r;
    }

    /**
     * Pic de glace : un eclat clair, plante de travers.
     *
     * <p>Les deux eclats etaient ancres a la moitie de leur hauteur, comme on
     * ancre un cylindre ou une sphere. Mais {@code sharedCone} a sa base a
     * l'origine et non en son milieu : avec une hauteur tiree entre 1,60 m et
     * 4,20 m, le grand eclat levitait de 0,80 m a 2,10 m et le petit de 0,45 m
     * a 1,20 m. C'est la meme faute que celle qui rendait la fumerolle du
     * volcan invisible, et elle coutait ici bien plus cher : le pic est
     * l'espece qui dit la banquise, et il en fait le septieme du semis.
     */
    private static double picDeGlace(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        double h = 1.6 + rnd.nextDouble() * 2.6;
        double r = 0.35 + rnd.nextDouble() * 0.35;
        Color glace = Color.web("#bfe6f5").deriveColor(0, 1, 0.92 + rnd.nextDouble() * 0.18, 1);
        b.add(Meshes.sharedCone(coneSides), r, h, r, rnd.nextDouble() * 12 - 6, yaw,
                x, Meshes.jy(y), z, glace);
        b.add(Meshes.sharedCone(coneSides), r * 0.5, h * 0.55, r * 0.5, 8, yaw + 40,
                x + r, Meshes.jy(y), z + r * 0.4, glace);
        return r * 1.6;
    }

    /** Bloc de glace : deux pans anguleux echoues sur la banquise. */
    private static double blocDeGlace(DecorBatch b, Random rnd,
                                      double x, double y, double z, double yaw) {
        double w = 1.0 + rnd.nextDouble() * 1.6;
        double h = 0.6 + rnd.nextDouble() * 1.0;
        Color glace = Color.web("#d6ecf7").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
        b.add(Meshes.sharedBox(), w, h, w * 0.7, rnd.nextDouble() * 10 - 5, yaw,
                x, Meshes.jy(y + h / 2), z, glace);
        b.add(Meshes.sharedBox(), w * 0.55, h * 0.8, w * 0.5, rnd.nextDouble() * 14 - 7,
                yaw + 35, x + w * 0.4, Meshes.jy(y + h * 0.5), z + w * 0.25,
                glace.deriveColor(0, 1, 0.92, 1));
        return w * 0.6;
    }

    /** Balise de piste : un mat raye, le seul repere sur la neige. */
    private static double balise(DecorBatch b, Random rnd,
                                 double x, double y, double z, double yaw) {
        double h = 1.8 + rnd.nextDouble() * 0.8;
        b.add(Meshes.sharedCylinder(5), 0.07, h, 0.07, 0, yaw,
                x, Meshes.jy(y + h / 2), z, Color.web("#f2f2f2"));
        b.add(Meshes.sharedCylinder(5), 0.075, h * 0.22, 0.075, 0, yaw,
                x, Meshes.jy(y + h * 0.78), z, Color.web("#d0342c"));
        return 0;
    }

    /** Bonhomme de neige : la piece rare, celle qu'on remarque. */
    private static double bonhommeDeNeige(DecorBatch b, Random rnd, int sphereDiv,
                                          double x, double y, double z, double yaw) {
        double r = 0.55 + rnd.nextDouble() * 0.2;
        Color neige = Color.web("#fbfdff");
        b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.9, r, 0, yaw,
                x, Meshes.jy(y + r * 0.85), z, neige);
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.72, r * 0.66, r * 0.72, 0, yaw,
                x, Meshes.jy(y + r * 2.1), z, neige);
        b.add(Meshes.sharedSphere(sphereDiv), r * 0.5, r * 0.46, r * 0.5, 0, yaw,
                x, Meshes.jy(y + r * 3.0), z, neige);
        b.add(Meshes.sharedCone(6), 0.09, 0.35, 0.09, 90, yaw,
                x, Meshes.jy(y + r * 3.05), z + r * 0.5, Color.web("#e8853a"));
        return r;
    }

    /** Agave : une etoile de feuilles raides. */
    private static double agave(DecorBatch b, Random rnd, int coneSides,
                                double x, double y, double z, double yaw) {
        Color feuille = Color.web("#6f9a55").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.3, 1);
        int lames = 7;
        for (int i = 0; i < lames; i++) {
            double a = yaw + i * (360.0 / lames);
            b.add(Meshes.sharedCone(Math.max(4, coneSides / 2)), 0.16, 1.3 + rnd.nextDouble() * 0.6,
                    0.16, 62, a, x + Math.cos(Math.toRadians(a)) * 0.35,
                    Meshes.jy(y + 0.45), z - Math.sin(Math.toRadians(a)) * 0.35, feuille);
        }
        return 0.8;
    }

    /**
     * Mesa : un socle d'eboulis, une falaise, un chapeau de gres en surplomb.
     *
     * <p>Deux cylindres empiles ne font pas une mesa, ils font une pile de
     * bidons — c'est le verdict d'un controle visuel, et l'agrandi le
     * confirmait : meme rayon, une marche franche au sommet, la meme piece
     * repetee a hauteur d'horizon sur tout le tour. Ce qui dit la mesa, c'est
     * la <b>silhouette trapezoidale</b>, d'ou le tronc de cone plutot que le
     * cylindre — la meme piece qui a sorti le cone du volcan de ses terrasses
     * de riziere — et le <b>chapeau qui deborde la falaise</b>, dont l'ombre
     * portee souligne la table. Le socle evase est l'eboulis qui s'accumule au
     * pied de toute falaise de gres, et c'est lui qui donne la double pente.
     *
     * <p>Ce sont les <b>proportions</b> qui portent tout, et une premiere passe
     * l'a appris a ses depens : en tirant le rayon et la hauteur
     * independamment, on obtenait des futs deux fois plus hauts que larges,
     * coiffes d'un disque pale — l'agrandi montrait une rangee de bidons a
     * couvercle, exactement le defaut qu'on venait corriger. La hauteur est
     * donc maintenant <b>proportionnelle au rayon</b> : une mesa fait deux fois
     * et demie plus large que haut, et c'est cet aplatissement-la qu'on
     * reconnait de loin. Le fruit des parois est franc — le sommet ne fait que
     * les deux tiers de la base — parce qu'un pan a peine incline se lit comme
     * un cylindre.
     *
     * <p>Le premier tirage commande la forme : douze mesas etalees pour une
     * <b>aiguille</b> de rocher, qui n'a ni chapeau ni sommet plat mais une
     * pointe. Le chapeau clair est reserve aux mesas : pose sur un fut mince,
     * c'etait lui le couvercle.
     *
     * <p>Trois tirages, comme avant, dans le meme ordre : l'implantation du
     * semis ne bouge pas d'un objet.
     */
    private static double butte(DecorBatch b, Random rnd,
                                double x, double y, double z, double yaw) {
        double forme = rnd.nextDouble();
        double taille = rnd.nextDouble();
        Color roche = GRES_SOMBRE.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.35, 1);
        // Un tronc de cone porte sa base a l'origine, comme le cone : le point
        // de pose est l'assise, pas le milieu. Le poser a y + h / 2 le ferait
        // flotter d'une demi-hauteur — la panne trouvee sur le volcan.
        Color eboulis = roche.deriveColor(0, 0.94, 1.14, 1);
        if (forme > 0.92) {
            double r = 1.0 + forme * 0.7;
            double h = r * (4.5 + taille * 3.5);
            b.add(Meshes.sharedFrustum(7, 0.72), r * 1.5, h * 0.22, r * 1.5 * 0.85, 0, yaw,
                    x, Meshes.jy(y), z, eboulis);
            b.add(Meshes.sharedFrustum(7, 0.45), r * 1.15, h * 0.82, r * 1.15 * 0.85, 0, yaw,
                    x, Meshes.jy(y + h * 0.18), z, roche);
            return r * 1.3;
        }
        // Puissance 2,2 : le rayon moyen reste celui d'avant, 3,5 m — au-dela,
        // a la densite du desert, les grandes s'interpenetraient — mais la
        // queue de la loi va jusqu'a huit metres. Un paysage, ce sont quelques
        // reperes et beaucoup de cailloux, pas une population uniforme.
        double r = 1.3 + Math.pow(forme, 2.2) * 8.5;
        double h = r * (0.45 + taille * 0.65);
        b.add(Meshes.sharedFrustum(7, 0.86), r * 1.26, h * 0.30, r * 1.26 * 0.85, 0, yaw,
                x, Meshes.jy(y), z, eboulis);
        b.add(Meshes.sharedFrustum(7, 0.66), r * 1.06, h * 0.66, r * 1.06 * 0.85, 0, yaw,
                x, Meshes.jy(y + h * 0.26), z, roche);
        // le chapeau deborde d'un dixieme le haut de la falaise, et il est
        // epais : une plaque mince se lisait comme un couvercle pose dessus,
        // alors qu'un banc de gres est une strate parmi d'autres
        b.add(Meshes.sharedFrustum(7, 0.90), r * 0.78, h * 0.14, r * 0.78 * 0.85, 0, yaw,
                x, Meshes.jy(y + h * 0.90), z, GRES_CLAIR.deriveColor(0, 1.12, 0.86, 1));
        return r * 1.15;
    }

    /**
     * Herbe seche : quelques brins raides, presque rien.
     *
     * <p>La touffe flottait a trente-cinq centimetres du sol. Un cone partage
     * a sa base a l'origine, pas en son milieu : l'altitude qu'on lui donne
     * est celle du pied du brin, et non celle de sa moitie. Elle est
     * maintenant plantee dans le sable, ce qui se voit surtout depuis que le
     * sol est sombre — sur du beige clair, l'ombre manquante ne se remarquait
     * pas. Dix-sept tirages, comme avant.
     */
    private static double herbeSeche(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        Color paille = Color.web("#c9ae64").deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.4, 1);
        for (int i = 0; i < 4; i++) {
            double a = yaw + i * 90 + rnd.nextDouble() * 40;
            b.add(Meshes.sharedCone(Math.max(4, coneSides / 3)), 0.1, 0.7 + rnd.nextDouble() * 0.5,
                    0.1, 20 + rnd.nextDouble() * 15, a,
                    x + (rnd.nextDouble() - 0.5) * 0.5, Meshes.jy(y + 0.04),
                    z + (rnd.nextDouble() - 0.5) * 0.5, paille);
        }
        return 0;
    }

    /**
     * Pierre dressee : un monolithe penche, pose la depuis longtemps.
     *
     * <p>Plus haute et plus mince qu'avant — jusqu'a six metres pour un metre
     * de large — et taillee dans le gres d'ombre : sur un sol devenu ocre, un
     * brun moyen de un metre quatre-vingt ne se distinguait plus d'une motte.
     * Une verticale sombre et etroite est la meilleure ponctuation d'un
     * horizon d'aplats horizontaux. Quatre tirages, comme avant.
     */
    private static double pierreDressee(DecorBatch b, Random rnd,
                                        double x, double y, double z, double yaw) {
        double h = 2.4 + rnd.nextDouble() * 3.6;
        double w = 0.55 + rnd.nextDouble() * 0.55;
        b.add(Meshes.sharedBox(), w, h, w * 0.55, rnd.nextDouble() * 14 - 7, yaw,
                x, Meshes.jy(y + h / 2), z,
                GRES_SOMBRE.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.4, 1));
        return w;
    }

    /**
     * Arche de gres : deux piles evasees et un arc en trois claveaux.
     *
     * <p>C'etait un trilithe — deux montants droits et un linteau pose en
     * travers, soit un dolmen et non une arche. Le donjon avait deja fait le
     * meme constat pour l'arcade qu'il lui empruntait. Une arche naturelle de
     * gres n'a pas de linteau : elle a un <b>arc</b>, et trois segments
     * suffisent a le dire — deux rampants a quarante-cinq degres et une clef
     * horizontale. Les piles s'evasent du haut vers le bas, comme un pied de
     * falaise que le sable a deblaye.
     *
     * <p>Trois tirages, dans le meme ordre qu'avant.
     */
    private static double arche(DecorBatch b, Random rnd,
                                double x, double y, double z, double yaw) {
        double elance = rnd.nextDouble();
        double ecart = 3.4 + rnd.nextDouble() * 2.6;
        // La baie est aussi haute que large. Une premiere passe avait garde la
        // hauteur d'avant, tiree independamment de l'ecart : l'arc, haut de
        // trente pour cent de l'ecart sur des piles de cinq metres, se lisait
        // comme un chapeau pose sur deux poteaux, et de loin comme deux barres
        // en croix. Une arche se reconnait a son <b>vide</b>, pas a sa pierre.
        double h = ecart * (0.85 + elance * 0.55);
        double fleche = ecart * 0.44;
        // Le gres d'ombre, et non le gres clair. Une passe en pierre pale a
        // couvert l'horizon d'une confetti de batons blancs : a cent metres,
        // devant un ciel de sable, un objet plus clair que le ciel n'a plus de
        // contour, alors qu'un objet sombre se decoupe net. C'est la meme
        // lecon que le tronc calcine du volcan sur son ciel rouge. Seule la
        // clef reste blonde, pour qu'on voie que l'arc en est un.
        Color gres = GRES_SOMBRE.deriveColor(0, 1, 0.95 + rnd.nextDouble() * 0.35, 1);
        Color clef = GRES_CLAIR.deriveColor(0, 1.1, 0.88, 1);
        for (int i = -1; i <= 1; i += 2) {
            double[] pile = local(x, z, yaw, 0, i * ecart / 2);
            // base a l'origine pour un tronc de cone : le pied de la pile est
            // le point de pose
            b.add(Meshes.sharedFrustum(6, 0.62), 0.9, h, 0.72, 0, yaw,
                    pile[0], Meshes.jy(y), pile[1], gres);
            // rampant : du haut de la pile a la clef, soit 0,33 ecart de cote
            // pour 0,43 de haut — cinquante-deux degres
            double[] q = local(x, z, yaw, 0, i * ecart * 0.335);
            // signe inverse : l'axe X local suit l'ecart lateral croissant, donc
            // le rampant de gauche doit monter vers la droite
            b.add(Meshes.sharedBox(), ecart * 0.55, 0.62, 0.95, -i * 52, yaw,
                    q[0], Meshes.jy(y + h + fleche * 0.48), q[1], gres);
        }
        b.add(Meshes.sharedBox(), ecart * 0.42, 0.62, 0.95, 0, yaw,
                x, Meshes.jy(y + h + fleche), z, clef);
        return ecart * 0.6;
    }

    /**
     * Les quatre valeurs du volcan.
     *
     * <p>Un champ de lave n'est pas monochrome : il y a le basalte noir, la
     * cendre soudee gris clair, le soufre depose autour des bouches, et la
     * lave elle-meme. Ces quatre-la sont volontairement <b>tres separees en
     * clarte</b> — 9 %, 55 %, 85 %, 100 % — parce que c'est l'ecart de valeur,
     * et non la saturation, qui permet de distinguer deux formes a trente
     * metres. Le repertoire precedent tenait dans une seule de ces marches.
     */
    private static final Color BASALTE = Color.web("#1c1715");
    private static final Color CENDRE = Color.web("#2b2522");
    private static final Color SOUFRE = Color.web("#e0bb26");
    private static final Color SOUFRE_PALE = Color.web("#eddc6a");
    /** La croute qui borde le depot : le meme jaune, deux crans plus bas. */
    private static final Color SOUFRE_OCRE = Color.web("#9c7c1a");
    /**
     * La lave ne monte pas plus haut que cet orange.
     *
     * <p>Sur le Salon, un degrade qui montait a 145 % de clarte sur un fond
     * deja clair ressortait blanc pur et se lisait comme du soleil. Une lueur
     * de lave qui vire au blanc n'est plus de la lave : le rouge est deja a
     * fond ici, donc {@code deriveColor} ne peut plus que jaunir, et on borne
     * son gain a 1,35.
     */
    private static final Color LAVE = Color.web("#ff5412");
    private static final Color BRAISE = Color.web("#8a2c12");
    /**
     * La fumee ne palit pas au-dela de ce gris.
     *
     * <p>A #c8bfb4, et une fois l'eclairage de scene applique, les panaches
     * ressortaient blanc pur : ce n'etait plus de la fumee mais des nuages
     * poses au sol. Un gris moyen se detache tres bien d'un ciel rouge, et il
     * reste de la fumee.
     */
    private static final Color FUMEE = Color.web("#5d554c");
    private static final Color FUMEE_PALE = Color.web("#97897c");

    /**
     * Orgues basaltiques : un massif de prismes accoles, en paroi.
     *
     * <p>Les colonnes etaient disposees en cercle autour du point d'ancrage :
     * de loin cela faisait un buisson de batons, et rien qui rappelle une
     * orgue. Une orgue est un <b>alignement</b> de prismes verticaux serres,
     * plus haut au milieu qu'aux flancs — c'est cette silhouette-la qui la
     * nomme, et le noir franc qui la detache de la scorie claire posee a cote.
     */
    private static double orguesBasaltiques(DecorBatch b, Random rnd,
                                            double x, double y, double z, double yaw) {
        int colonnes = 3 + rnd.nextInt(3);
        double a = Math.toRadians(yaw);
        double dx = Math.cos(a), dz = -Math.sin(a);
        double widest = 0;
        for (int i = 0; i < colonnes; i++) {
            double r = 0.42 + rnd.nextDouble() * 0.28;
            // les prismes du bord sont les plus courts : sans cet affaissement
            // l'alignement se lisait comme une palissade
            double bord = Math.abs(i - (colonnes - 1) / 2.0) / Math.max(1.0, colonnes / 2.0);
            double h = (2.8 + rnd.nextDouble() * 3.2) * (1 - bord * 0.45);
            double d = (i - (colonnes - 1) / 2.0) * 1.05;
            widest = Math.max(widest, Math.abs(d) + r);
            b.add(Meshes.sharedCylinder(6), r, h, r, 0, yaw + i * 13,
                    x + dx * d, Meshes.jy(y + h / 2), z + dz * d,
                    BASALTE.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.55, 1));
        }
        return widest;
    }

    /**
     * Eclat d'obsidienne : une lame noire, et son eclat de verre.
     *
     * <p>Le basalte est mat, l'obsidienne luit — sans cette seconde lame
     * bleutee, les deux pieces avaient exactement la meme valeur et la meme
     * silhouette effilee, et rien ne les separait a l'ecran.
     */
    private static double obsidienne(DecorBatch b, Random rnd, int coneSides,
                                     double x, double y, double z, double yaw) {
        double h = 1.4 + rnd.nextDouble() * 2.2;
        double r = 0.35 + rnd.nextDouble() * 0.3;
        double pente = 10 + rnd.nextDouble() * 16;
        Color verre = Color.web("#0e0c12").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.5, 1);
        int lames = Math.max(4, coneSides / 2);
        b.add(Meshes.sharedCone(lames), r, h, r * 0.55, pente, yaw,
                x, Meshes.jy(y), z, verre);
        b.add(Meshes.sharedCone(lames), r * 0.55, h * 0.6, r * 0.3, -(pente + 16), yaw + 55,
                x, Meshes.jy(y), z, Color.web("#4b4d6b"));
        return r * 1.7;
    }

    /**
     * Fumerolle : un cone de cendre et le panache qui en sort.
     *
     * <p>Deux fautes la rendaient invisible. La premiere est un bug de repere :
     * {@code sharedCone} a sa base a l'origine et non en son milieu, si bien
     * que le cone pose a {@code y + h/2} flottait d'une demi-hauteur et que sa
     * bouffee, calee sur {@code y + h}, retombait <b>a l'interieur</b> du cone.
     * La seconde est l'echelle : un cone d'un metre coiffe d'une boule de
     * cinquante centimetres ne se voit pas passe dix metres. Le panache monte
     * maintenant a cinq ou six metres et palit en s'elargissant. Il en a fait
     * huit et compte quatre bouffees le temps d'une capture : a cette hauteur
     * et a la frequence qu'avait la piece, il ne restait plus rien d'autre a
     * l'horizon. Un accent vertical, donc, pas un fond.
     */
    private static double fumerolle(DecorBatch b, Random rnd, int coneSides, int sphereDiv,
                                    double x, double y, double z, double yaw) {
        double h = 1.3 + rnd.nextDouble() * 1.2;
        double r = 1.1 + rnd.nextDouble() * 0.9;
        b.add(Meshes.sharedCone(coneSides), r, h, r, 0, yaw, x, Meshes.jy(y), z, CENDRE);
        // la bouche, rouge sombre : sans elle le cone n'est qu'un tas de terre
        b.add(Meshes.sharedDisc(Math.max(6, coneSides / 2)), r * 0.3, 1, r * 0.3, 0, yaw,
                x, Meshes.jy(y + h * 0.98), z, BRAISE);
        for (int i = 0; i < 3; i++) {
            double t = i / 2.0;
            double br = r * (0.4 + t * 0.42);
            b.add(Meshes.sharedSphere(sphereDiv), br, br * 0.8, br, 0, yaw + i * 37,
                    x + r * 0.3 * t, Meshes.jy(y + h + 0.5 + t * 2.9), z - r * 0.4 * t,
                    FUMEE.interpolate(FUMEE_PALE, t));
        }
        return r;
    }

    /**
     * Rocher incandescent : la roche fendue laisse voir la braise.
     *
     * <p>La braise etait un caillou de quarante centimetres colle au flanc
     * d'un bloc sombre, donc invisible passe dix metres. Elle sort maintenant
     * par le haut et occupe plus du tiers de la silhouette : c'est la tache
     * orange qui nomme la piece, il faut qu'elle se voie.
     */
    private static double rocherIncandescent(DecorBatch b, Random rnd, int lumpDiv,
                                             double x, double y, double z, double yaw) {
        double r = 1.2 + rnd.nextDouble() * 1.6;
        b.add(Meshes.sharedSphere(lumpDiv), r, r * 0.72, r * 0.9, 0, yaw,
                x, Meshes.jy(y + r * 0.36), z, BASALTE);
        // Deux braises decentrees, et non une calotte au milieu. Centree, elle
        // coiffait le bloc d'un dome regulier, et de loin la piece se lisait
        // comme un parasol orange — la meme silhouette repetee dix fois dans
        // un cadre. Decalees de part et d'autre, elles donnent une roche
        // fendue en deux endroits, ce qu'elles sont censees etre.
        //
        // Six meridiens au minimum et non quatre : la finesse tombe a 5 au-dela
        // de 80 m, et une sphere a quatre meridiens est un octaedre.
        Color braise = LAVE.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.45, 1);
        double a = Math.toRadians(yaw + 30);
        b.add(Meshes.sharedSphere(Math.max(6, lumpDiv - 2)), r * 0.5, r * 0.46, r * 0.44,
                0, yaw + 30,
                x + Math.cos(a) * r * 0.42, Meshes.jy(y + r * 0.66), z - Math.sin(a) * r * 0.42,
                braise);
        b.add(Meshes.sharedSphere(Math.max(6, lumpDiv - 3)), r * 0.3, r * 0.24, r * 0.26,
                0, yaw - 60,
                x - Math.cos(a) * r * 0.62, Meshes.jy(y + r * 0.4), z + Math.sin(a) * r * 0.62,
                braise);
        return r * 0.85;
    }

    /**
     * Bloc de scorie : de la lave figee, cassee en dalles anguleuses.
     *
     * <p>C'etait {@code rock} reteinte en clair, donc un tas de galets ronds :
     * a l'ecran, un champ de boules pales qui evoquait des champignons plus
     * qu'une coulee. Une scorie est cassante et se debite en plaques a aretes
     * vives — des boites inclinees disent cela en trois volumes, et la
     * silhouette anguleuse la separe enfin des bouffees de fumerolle, qui sont
     * la seule autre chose claire du repertoire.
     */
    private static double blocDeScorie(DecorBatch b, Random rnd,
                                       double x, double y, double z, double yaw) {
        int dalles = 2 + rnd.nextInt(2);
        double widest = 0;
        double h = 0;
        for (int i = 0; i < dalles; i++) {
            double l = 1.6 + rnd.nextDouble() * 2.2;
            double e = 0.5 + rnd.nextDouble() * 0.7;
            double p = l * (0.55 + rnd.nextDouble() * 0.4);
            widest = Math.max(widest, Math.max(l, p) * 0.6);
            // chaque dalle est posee de guingois sur la precedente : c'est le
            // desordre des aretes qui fait lire la roche cassee
            b.add(Meshes.sharedBox(), l, e, p, (rnd.nextDouble() - 0.5) * 26,
                    yaw + i * 47 + rnd.nextDouble() * 30,
                    x + (rnd.nextDouble() - 0.5) * l * 0.5, Meshes.jy(y + h + e * 0.35),
                    z + (rnd.nextDouble() - 0.5) * p * 0.5,
                    SCORIE.deriveColor(0, 1, 0.8 + rnd.nextDouble() * 0.5, 1));
            h += e * 0.7;
        }
        return widest;
    }

    /**
     * Coulee de lave : une croute noire fendue sur une veine incandescente.
     *
     * <p>C'est la piece qui manquait le plus — le circuit s'appelle « Petit
     * Volcan » et rien au sol ne coulait. Elle remplace la souche brulee, qui
     * doublait l'arbre mort en plus petit et en aussi noir.
     *
     * <p>Le dessin est une <b>invention</b> : TuxKart ne fournit pas de modele
     * dont on puisse s'inspirer ici, et le depot n'en contient aucune copie.
     * On s'en tient donc a ce qu'une coulee refroidie montre — une croute
     * sombre bombee, fendue en son milieu, et la lave visible au fond de la
     * fente — en quatre volumes d'aplat franc.
     */
    private static double couleeDeLave(DecorBatch b, Random rnd,
                                       double x, double y, double z, double yaw) {
        double l = 6 + rnd.nextDouble() * 8;
        double w = 1.8 + rnd.nextDouble() * 2.2;
        // la croute, a demi enterree : une coulee n'est pas une dalle posee
        b.add(Meshes.sharedBox(), l, 0.5, w, 0, yaw, x, Meshes.jy(y + 0.16), z,
                Color.web("#14100e"));
        // le dos bombe, plus etroit : c'est lui qui donne le relief de biais
        b.add(Meshes.sharedBox(), l * 0.92, 0.34, w * 0.62, 0, yaw,
                x, Meshes.jy(y + 0.42), z, Color.web("#1f1815"));
        // la fente, et son coeur clair. Deux bandes plutot qu'une : la large
        // porte la couleur, l'etroite porte la lumiere, et l'ecart entre les
        // deux se lit comme une profondeur sans qu'on ait a creuser.
        b.add(Meshes.sharedBox(), l * 0.88, 0.2, w * 0.3, 0, yaw,
                x, Meshes.jy(y + 0.6), z, LAVE);
        b.add(Meshes.sharedBox(), l * 0.66, 0.14, w * 0.13, 0, yaw,
                x, Meshes.jy(y + 0.66), z, LAVE.deriveColor(0, 0.96, 1.12, 1));
        return w * 0.7;
    }

    /**
     * Depot de soufre : la croute jaune qui borde une bouche.
     *
     * <p>La seule couleur chaude non rouge du repertoire, et la seule piece
     * claire avec la scorie. Les solfatares deposent bien ce jaune vif autour
     * de leurs fumerolles ; le <b>dessin</b>, lui, est une invention — un dome
     * bas, un bourgeon a cote, et l'aureole au sol qui le fait lire de loin.
     */
    private static double depotDeSoufre(DecorBatch b, Random rnd, int lumpDiv,
                                        double x, double y, double z, double yaw) {
        double r = 1.3 + rnd.nextDouble() * 1.5;
        // Trois couches, et il a fallu trois essais pour qu'elles se lisent.
        // La croute au sol etait d'abord un disque plat de trois metres :
        // parfaitement horizontal sur un terrain qui ne l'est jamais, il se
        // lisait comme une flaque de peinture. Bombee, elle epousait la pente,
        // mais peinte du meme jaune pale que le dome elle l'avalait — de loin
        // on ne voyait plus qu'un coussin jaune uni. Elle est donc passee a
        // l'ocre sombre : c'est le <b>contraste entre les deux</b> qui donne
        // le bourrelet vif au milieu d'une croute, et non le jaune tout seul.
        b.add(Meshes.sharedSphere(Math.max(5, lumpDiv - 3)), r * 1.05, r * 0.14, r * 1.05, 0, yaw,
                x, Meshes.jy(y), z, SOUFRE_OCRE);
        b.add(Meshes.sharedSphere(lumpDiv), r * 0.72, r * 0.5, r * 0.62, 0, yaw,
                x, Meshes.jy(y + r * 0.14), z,
                SOUFRE.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.3, 1));
        b.add(Meshes.sharedSphere(Math.max(4, lumpDiv - 3)), r * 0.38, r * 0.3, r * 0.34,
                0, yaw + 40, x + r * 0.6, Meshes.jy(y + r * 0.12), z - r * 0.32, SOUFRE_PALE);
        return r * 1.1;
    }

    // ------------------------------------------- les reperes du paysage

    /**
     * Les pieces qui suivent ne sont jamais semees : elles sont trop grosses
     * pour tomber au hasard. Un moulin, un phare ou une ferme sont des
     * <b>reperes</b> — on s'en sert pour savoir ou l'on en est dans le tour,
     * et cela ne marche que s'il n'y en a qu'un, pose la ou l'auteur l'a voulu.
     */

    /**
     * Le volcan, pose a cent cinq metres du ruban.
     *
     * <p>Un controle visuel a classe ce circuit « a refaire » sur un constat
     * qui tient en une phrase : <i>le circuit s'appelle « Petit Volcan » et on
     * ne voit aucun volcan</i>. Ni cratere, ni coulee, ni fumee — le README
     * promettait « la roche, la fumee et le saut du cratere », et il n'y avait
     * que la roche. Aucune retouche de couleur ne repond a cela : il fallait
     * poser l'objet que le titre annonce.
     *
     * <p>Il ne va <b>pas</b> au centre de la boucle, et c'est un balayage qui
     * l'en a sorti : sur un anneau, le centre reste a soixante ou quatre-vingt-
     * dix degres du cap presque tout le tour, donc hors du champ de la camera.
     * Pose a 105 m du ruban, il tombe a moins de vingt-huit degres du cap sur
     * 27 % du tour, dont un plan continu de 190 m — le detail du balayage est
     * dans {@code PetitVolcan}. Son pied de 76 m de rayon y laisse encore
     * trente metres entre le cone et le bitume.
     *
     * <p>Trois troncs de cone de pente croissante, pas un cone simple : un
     * stratovolcan a un flanc concave et un sommet tronque, et un cone pointu
     * se lit comme un terril. Le pied part treize metres <b>sous</b> l'assise
     * parce que le relief interieur monte jusqu'a 35 m alors que le centre est
     * a 15 : sans cet enfoncement, une bosse du terrain passait devant le pied
     * du volcan et le donnait a voir en l'air.
     *
     * <p>Le dessin est une <b>invention</b> : le depot ne contient aucune
     * copie de TuxKart, on ne peut donc pas mesurer a quoi ressemblait son
     * volcan, et rien de ce qui est ecrit ici ne pretend le reproduire.
     */
    private static double cratere(DecorBatch b, Random rnd, int coneSides, int sphereDiv,
                                  double x, double y, double z, double yaw) {
        // Assez de pans pour que le contour ne soit pas un decagone a cent
        // metres, mais l'aplat franc reste la regle : trois troncs de vingt
        // pans coutent 360 triangles, soit un arbre et demi du semis.
        int pans = Math.max(16, coneSides * 2);
        // {rayon du bas, altitude du bas, rayon du haut, altitude du haut}
        double[][] etages = {
                {76, -13, 48, 7},
                {48, 7, 27, 27},
                {27, 27, 15, 43},
        };
        Color[] teintes = {Color.web("#332a25"), Color.web("#251e1b"), Color.web("#191413")};
        for (int i = 0; i < etages.length; i++) {
            double rb = etages[i][0], yb = etages[i][1];
            double rh = etages[i][2], yh = etages[i][3];
            b.add(Meshes.sharedFrustum(pans, rh / rb), rb, yh - yb, rb, 0, yaw,
                    x, Meshes.jy(y + yb), z, teintes[i]);
        }

        // Le fond du cratere : un disque de braise pose sur le capuchon du
        // dernier tronc, qui ne peut pas porter deux couleurs a lui seul.
        b.add(Meshes.sharedDisc(pans), 14.2, 1, 14.2, 0, yaw,
                x, Meshes.jy(y + 43.15), z, Color.web("#c2331a"));
        // La levre, en blocs inegaux : c'est la dentelure qui dit « cratere »
        // vue d'en bas, ou l'on ne voit jamais l'interieur.
        for (int i = 0; i < 11; i++) {
            double a = Math.toRadians(yaw + i * 360.0 / 11);
            double h = 2.6 + rnd.nextDouble() * 2.4;
            b.add(Meshes.sharedBox(), 5.0, h, 7.6, 0, Math.toDegrees(a),
                    x + Math.cos(a) * 14.6, Meshes.jy(y + 43 + h / 2 - 0.6),
                    z - Math.sin(a) * 14.6, Color.web("#141010"));
        }

        // Les coulees. Chacune suit la generatrice d'un etage et non la corde
        // du pied au sommet : le flanc etant concave, une bande droite d'un
        // bout a l'autre passerait sous la surface en son milieu et on ne
        // verrait que ses deux extremites.
        double[] azimuts = {18, 74, 137, 206, 268, 321};
        for (int i = 0; i < azimuts.length; i++) {
            double a = yaw + azimuts[i];
            // les coulees courtes s'arretent au deuxieme etage : toutes de la
            // meme longueur, elles faisaient une roue de rayons
            int bas = i % 3 == 0 ? 0 : 1;
            for (int e = 2; e >= bas; e--) {
                double rb = etages[e][0], yb = etages[e][1];
                double rh = etages[e][2], yh = etages[e][3];
                double dr = rb - rh, dy = yh - yb;
                double lg = Math.hypot(dr, dy);
                double tilt = Math.toDegrees(Math.atan2(dy, dr));
                double rm = (rb + rh) / 2, ym = (yb + yh) / 2;
                double ar = Math.toRadians(a);
                // ecartee de quatre-vingts centimetres vers l'exterieur, sinon
                // elle vibre contre le flanc dont elle epouse la pente
                b.add(Meshes.sharedBox(), lg, 0.9, 3.4 - e * 0.5, tilt, a,
                        x + Math.cos(ar) * (rm + 0.8), Meshes.jy(y + ym),
                        z - Math.sin(ar) * (rm + 0.8),
                        e == 2 ? LAVE.deriveColor(0, 0.92, 1.25, 1) : LAVE);
            }
        }

        // Le panache, couche par le vent. Il monte a une centaine de metres :
        // c'est ce qui rend le volcan lisible depuis le cote oppose du tour,
        // ou le cone lui-meme ne fait plus que treize degres de haut.
        for (int i = 0; i < 7; i++) {
            double t = i / 6.0;
            double r = 7 + t * 15;
            b.add(Meshes.sharedSphere(sphereDiv), r, r * 0.78, r, 0, yaw + i * 41,
                    x + Math.cos(Math.toRadians(yaw + 40)) * t * 34,
                    Meshes.jy(y + 46 + t * 42),
                    z - Math.sin(Math.toRadians(yaw + 40)) * t * 34,
                    FUMEE.interpolate(FUMEE_PALE, Math.min(1, t * 1.15)));
        }
        return 76;
    }

    /** Ferme : un corps de logis, un toit a deux pentes, une cheminee qui fume pas. */
    private static double ferme(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 15 + rnd.nextDouble() * 5, p = 9.5, h = 6.5;
        Color mur = Color.web("#e6ddc8").deriveColor(0, 1, 0.92 + rnd.nextDouble() * 0.16, 1);
        Color toit = Color.web("#8d3a2c");
        b.add(Meshes.sharedBox(), l, h, p, 0, yaw, x, Meshes.jy(y + h / 2), z, mur);
        double faite = 4.2;
        // Le toit etait deux plaques horizontales, posees a plat un metre
        // au-dessus des murs et tournees d'un quart de tour par rapport a eux :
        // l'inclinaison etait calculee et jamais appliquee, et la longueur de
        // la plaque portait sur l'axe de la piste au lieu de celui du faitage.
        // De la piste, la ferme etait un cube blanc surmonte d'un bandeau
        // rouge flottant — la planche unie qu'on redoutait.
        //
        // Le faitage court sur l'axe long du corps de logis, donc en travers de
        // la piste, et une plaque ne bascule qu'autour de son axe Z : c'est le
        // lacet tourne d'un quart qui amene cet axe-la sur le faitage, comme
        // pour les rabats du carton. Avec yaw+90, le grand cote de la plaque
        // porte l'inclinaison et le petit court le long du faitage — d'ou
        // l'echange des deux dimensions.
        double pente = Math.toDegrees(Math.atan2(faite, p / 2 + 0.6));
        double rampant = Math.hypot(p / 2 + 0.6, faite);
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, i * (p / 4 + 0.3), 0);
            b.add(Meshes.sharedBox(), rampant, 0.5, l + 1.2, -i * pente, yaw + 90,
                    q[0], Meshes.jy(y + h + faite / 2), q[1], toit);
        }
        // les deux pignons, pour boucher le triangle sous le toit
        for (int i = -1; i <= 1; i += 2) {
            double[] q = local(x, z, yaw, 0, i * l / 2);
            for (int e = 0; e < 3; e++) {
                double t = (e + 0.5) / 3;
                b.add(Meshes.sharedBox(), 0.5, faite / 3, p * (1 - t), 0, yaw,
                        q[0], Meshes.jy(y + h + t * faite), q[1], mur);
            }
        }
        double[] porte = local(x, z, yaw, p / 2, l * 0.15);
        b.add(Meshes.sharedBox(), 2.6, 4.0, 0.4, 0, yaw,
                porte[0], Meshes.jy(y + 2), porte[1], Color.web("#5b3c22"));
        for (int i = -1; i <= 1; i += 2) {
            double[] f = local(x, z, yaw, p / 2, i * l * 0.32);
            b.add(Meshes.sharedBox(), 2.0, 2.0, 0.4, 0, yaw,
                    f[0], Meshes.jy(y + 4.2), f[1], Color.web("#3f5a6b"));
        }
        double[] chem = local(x, z, yaw, -p * 0.25, l * 0.3);
        b.add(Meshes.sharedBox(), 1.6, 4.0, 1.6, 0, yaw,
                chem[0], Meshes.jy(y + h + faite - 0.5), chem[1], Color.web("#9a5a45"));
        return l * 0.6;
    }

    /** Silo a grain : un fut de tole ondulee coiffe d'un cone, et son echelle. */
    private static double silo(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double r = 3.4 + rnd.nextDouble() * 1.2, h = 15 + rnd.nextDouble() * 5;
        Color tole = Color.web("#b9bfc4");
        b.add(Meshes.sharedCylinder(14), r, h, r, 0, yaw, x, Meshes.jy(y + h / 2), z, tole);
        // cerclages : c'est ce qui donne la hauteur, un fut lisse est illisible
        for (int i = 1; i <= 6; i++) {
            b.add(Meshes.sharedCylinder(14), r * 1.03, 0.35, r * 1.03, 0, yaw,
                    x, Meshes.jy(y + i * h / 7), z, tole.deriveColor(0, 1, 0.86, 1));
        }
        // meme piege que le toit du phare : le fut monte jusqu'a y+h, le cone
        // etait ancre une demi-hauteur plus haut et flottait de 1,60 m
        b.add(Meshes.sharedCone(14), r * 1.08, 3.2, r * 1.08, 0, yaw,
                x, Meshes.jy(y + h), z, tole.deriveColor(0, 1, 0.78, 1));
        double[] e = local(x, z, yaw, r, 0);
        for (int i = 0; i < (int) (h / 1.2); i++) {
            b.add(Meshes.sharedBox(), 1.0, 0.16, 0.16, 0, yaw,
                    e[0], Meshes.jy(y + 0.8 + i * 1.2), e[1], Color.web("#7d8288"));
        }
        return r * 1.2;
    }

    /** Moulin : une tour tronconique, sa calotte et quatre ailes. */
    private static double moulin(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double h = 13 + rnd.nextDouble() * 4;
        Color pierre = Color.web("#cfc6b2");
        int etages = 5;
        for (int i = 0; i < etages; i++) {
            double t = i / (double) etages;
            b.add(Meshes.sharedCylinder(12), 4.4 - t * 1.6, h / etages, 4.4 - t * 1.6, 0, yaw,
                    x, Meshes.jy(y + (i + 0.5) * h / etages), z,
                    pierre.deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.14, 1));
        }
        b.add(Meshes.sharedSphere(10), 3.4, 2.6, 3.4, 0, yaw,
                x, Meshes.jy(y + h + 1.0), z, Color.web("#4a3a2c"));
        // Les ailes, dans le plan qui fait face a la piste — le cap relatif du
        // circuit tourne ce plan vers celui qui arrive.
        //
        // La toile etait une plaque de 13,0 x 1,2 x 0,2 posee au meme centre
        // qu'un bras de 13,5 x 1,8 x 0,4 : plus courte, plus etroite et plus
        // mince que lui dans les trois dimensions, donc entierement enfermee
        // dedans, et invisible en CullFace.BACK. Le moulin n'avait pas d'ailes,
        // il avait une croix de bois pleine — capture a l'appui a s=110 et
        // s=145 de la Piste de Tux. La toile est maintenant portee par la
        // moitie exterieure du bras, plus large que lui et decalee devant.
        double[] moyeu = local(x, z, yaw, 3.0, 0);
        double sinCap = Math.sin(Math.toRadians(yaw)), cosCap = Math.cos(Math.toRadians(yaw));
        for (int i = 0; i < 4; i++) {
            double a = Math.toRadians(i * 90 + 18);
            b.add(Meshes.sharedBox(), 13.5, 0.7, 0.35, Math.toDegrees(a), yaw,
                    moyeu[0], Meshes.jy(y + h + 0.6), moyeu[1], Color.web("#6b4526"));
            // deport le long du bras : la rotation precede la translation, il
            // faut donc porter l'ecart en coordonnees monde
            double r = 3.2;
            b.add(Meshes.sharedBox(), 7.0, 2.6, 0.14, Math.toDegrees(a), yaw,
                    moyeu[0] + Math.cos(a) * r * cosCap + 0.35 * sinCap,
                    Meshes.jy(y + h + 0.6 + Math.sin(a) * r),
                    moyeu[1] - Math.cos(a) * r * sinCap + 0.35 * cosCap,
                    Color.web("#f2ece0"));
        }
        return 5.0;
    }

    /** Phare : anneaux rouges et blancs, galerie, lanterne allumee. */
    private static double phare(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double h = 20 + rnd.nextDouble() * 6;
        int anneaux = 8;
        for (int i = 0; i < anneaux; i++) {
            double t = i / (double) anneaux;
            b.add(Meshes.sharedCylinder(12), 3.6 - t * 1.5, h / anneaux + 0.05, 3.6 - t * 1.5,
                    0, yaw, x, Meshes.jy(y + (i + 0.5) * h / anneaux), z,
                    i % 2 == 0 ? Color.web("#f2efe6") : Color.web("#c9392c"));
        }
        b.add(Meshes.sharedCylinder(12), 3.0, 0.5, 3.0, 0, yaw,
                x, Meshes.jy(y + h + 0.25), z, Color.web("#3c4045"));
        b.add(Meshes.sharedCylinder(10), 1.8, 3.0, 1.8, 0, yaw,
                x, Meshes.jy(y + h + 2.0), z, Color.web("#f7e07a"));
        // La lanterne finit a y+h+3,5 ; le toit s'ancrait a y+h+4,6, soit sa
        // demi-hauteur au-dessus — l'ancrage d'un cylindre applique a un cone,
        // dont la base est a l'origine. Un metre dix de ciel entre la lanterne
        // et son chapeau, visible de la piste.
        b.add(Meshes.sharedCone(10), 2.2, 2.2, 2.2, 0, yaw,
                x, Meshes.jy(y + h + 3.5), z, Color.web("#2f3a44"));
        return 3.6;
    }

    /** Eolienne : un mat, une nacelle, trois pales. */
    private static double eolienne(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double h = 26 + rnd.nextDouble() * 8;
        Color blanc = Color.web("#eef1f3");
        b.add(Meshes.sharedCylinder(10), 1.5, h, 1.0, 0, yaw, x, Meshes.jy(y + h / 2), z, blanc);
        double[] nacelle = local(x, z, yaw, 1.2, 0);
        b.add(Meshes.sharedBox(), 2.0, 2.0, 5.0, 0, yaw,
                nacelle[0], Meshes.jy(y + h + 1), nacelle[1], blanc.deriveColor(0, 1, 0.94, 1));
        double[] moyeu = local(x, z, yaw, 3.4, 0);
        for (int i = 0; i < 3; i++) {
            b.add(Meshes.sharedBox(), 18, 1.4, 0.5, i * 120 + 25, yaw,
                    moyeu[0], Meshes.jy(y + h + 1), moyeu[1], blanc);
        }
        return 2.0;
    }

    /**
     * Epave : une coque echouee, ses membrures a l'air, un mat casse.
     *
     * <p>Elle etait posee a quarante-deux et quarante-huit metres du ruban,
     * derriere la ligne de sapins, et on n'en voyait qu'un bout de mat : elle
     * n'avait donc jamais ete jugee de pres. Ramenee a trente metres, elle se
     * lisait comme un pain de bois — un cylindre couche, deux battons plantes
     * dedans et un mat. Ce qui nomme une epave, ce sont trois choses, et
     * aucune n'y etait : la <b>coque effilee aux deux bouts</b>, les
     * <b>membrures</b> qui sortent du bordage comme une cage thoracique, et la
     * <b>vergue</b> en croix sur le mat, qui dit que c'etait un voilier. Le
     * cylindre est donc devenu deux troncs de cone dos a dos, les membrures
     * sont passees de l'axe aux deux bords et penchent vers l'exterieur, et
     * l'etrave se dresse a la proue.
     *
     * <p>Le nombre de tirages est inchange — deux, la longueur et la gite —
     * parce que les pieces posees d'un circuit se tirent dans la meme suite.
     *
     * <p>La forme est une invention de ce clone : rien ne dit qu'il y ait une
     * epave dans TuxKart, et aucune reference n'etait consultable ici.
     */
    private static double epave(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double l = 22 + rnd.nextDouble() * 8, gite = 16 + rnd.nextDouble() * 10;
        Color bois = Color.web("#4a3a2e");
        Color bordage = bois.deriveColor(0, 1, 1.4, 1);
        double demi = l / 2;
        // Repere de la coque. Le cylindre d'origine etait pose en « yaw + 90 »
        // avec une inclinaison de 90 degres : son axe tombe donc sur le
        // « avant » de local(), sa hauteur sur 3,6 m et son bau sur 5,0 m. Les
        // membrures, elles, etaient reparties sur le « cote » — c'est-a-dire en
        // travers de la coque et non le long, ce qui les eparpillait.
        for (int bout = -1; bout <= 1; bout += 2) {
            b.add(Meshes.sharedFrustum(9, 0.14), 3.6, demi, 5.0,
                    bout > 0 ? 90 + gite : gite - 90, yaw + 90,
                    x, Meshes.jy(y + 2.6), z, bois);
        }
        // Membrures : deux files le long des bords, penchees vers l'exterieur,
        // plus courtes vers les bouts. C'est la cage thoracique qui dit
        // l'epave ; plantee dans l'axe elle disparaissait dans la coque.
        for (int i = 0; i < 4; i++) {
            double off = (i - 1.5) * l / 5;
            double effile = 1 - Math.abs(off) / demi * 0.5;
            for (int cote = -1; cote <= 1; cote += 2) {
                double[] q = local(x, z, yaw, off, cote * 4.3 * effile);
                b.add(Meshes.sharedBox(), 1.0, 5.4 * effile, 0.5, cote * 22, yaw,
                        q[0], Meshes.jy(y + 5.4), q[1], bordage);
            }
        }
        // Etrave : la piece la plus haute de l'avant, et la seule qui donne un
        // sens a la coque — sans elle les deux bouts se ressemblent.
        double[] proue = local(x, z, yaw, -demi * 0.78, 0);
        b.add(Meshes.sharedBox(), 1.4, 8.0, 1.0, 20, yaw + 90,
                proue[0], Meshes.jy(y + 5.6), proue[1], bordage);
        // Mat casse et sa vergue, en travers de la coque : la croix se lit de
        // loin et sous n'importe quel angle, et c'est elle qui dit « voilier »
        // plutot que « carcasse ».
        b.add(Meshes.sharedCylinder(6), 0.7, 13, 0.7, gite + 8, yaw,
                x, Meshes.jy(y + 7), z, bois.deriveColor(0, 1, 1.15, 1));
        double[] vergue = local(x, z, yaw, 0, -Math.sin(Math.toRadians(gite + 8)) * 3.6);
        b.add(Meshes.sharedCylinder(6), 0.4, 10, 0.4, 90, yaw,
                vergue[0], Meshes.jy(y + 10.4), vergue[1], bois.deriveColor(0, 1, 1.15, 1));
        return l * 0.4;
    }

    /**
     * Totem : quatre ou cinq visages empiles, ailes deployees au sommet.
     *
     * <p>Le totem precedent etait un empilement de cylindres a petits ergots,
     * coiffe d'une planche rouge : agrandi cinq fois, aucune figure — ni bec,
     * ni yeux, ni bouche — et de loin on le prenait pour un feu tricolore ou
     * une borne de chantier. Ce qui fait qu'un visage se lit a trente metres,
     * ce n'est pas le detail de la sculpture mais trois volumes en saillie :
     * un <b>bec</b> qui sort d'un metre du fut, et deux <b>yeux</b> ronds,
     * blancs cernes de noir, poses juste au-dessus. Le reste — les ailes du
     * sommet, les oreilles — n'est que la signature.
     *
     * <p>Le fut est un pave et non un cylindre : un mat sculpte est taille
     * dans un tronc equarri, et quatre faces planes prennent la lumiere par
     * aplats la ou le cylindre la degradait en un fondu qui effacait les
     * aretes. Les figures alternent de quart de tour en quart de tour, comme
     * avant, mais elles regardent toutes du meme cote — un totem a une face,
     * et c'est elle qu'on doit voir depuis la piste.
     *
     * <p>Meme nombre de tirages qu'avant, et dans le meme ordre : un pour le
     * nombre de figures, un par figure. Les pieces posees a la suite dans le
     * circuit — les cactus, les agaves, les mesas — ne bougent donc pas.
     *
     * <p><b>Forme inventee.</b> Le depot ne contient aucune copie de
     * l'original et rien ne permet de dire quel totem TuxKart dressait, ni
     * s'il en dressait un. La palette — rouge, noir, blanc d'os, turquoise,
     * jaune — et la grammaire — bec saillant, yeux cercles, ailes au sommet —
     * sont celles des mats sculptes de la cote nord-ouest.
     */
    private static double totem(DecorBatch b, Random rnd, double x, double y, double z, double yaw) {
        double h = 0;
        Color[] teintes = {Color.web("#c0392b"), Color.web("#efe6d2"), Color.web("#1f8f8f"),
                Color.web("#e8b81f"), Color.web("#2a221e")};
        Color noir = Color.web("#221c19");
        Color blanc = Color.web("#f2ece0");
        int figures = 4 + rnd.nextInt(2);
        for (int i = 0; i < figures; i++) {
            double e = 2.3 + rnd.nextDouble() * 1.1;
            Color c = teintes[i % teintes.length];
            // le bec et les yeux sont peints dans la teinte de la figure du
            // dessus : c'est le contraste entre deux etages voisins qui detache
            // la face, pas la teinte elle-meme
            Color accent = teintes[(i + 2) % teintes.length];
            b.add(Meshes.sharedBox(), 2.6, e, 2.4, 0, yaw,
                    x, Meshes.jy(y + h + e / 2), z, c);
            // bec : il sort d'un metre dix de la face avant, qui est a 1,2 m de
            // l'axe. C'est la seule saillie qui se voie de trois quarts.
            double[] bec = local(x, z, yaw, 1.75, 0);
            b.add(Meshes.sharedBox(), 1.0, 0.7, 1.7, 0, yaw,
                    bec[0], Meshes.jy(y + h + e * 0.42), bec[1], accent);
            // yeux : disques blancs cernes de noir, aplatis contre la face
            for (int j = -1; j <= 1; j += 2) {
                double[] oeil = local(x, z, yaw, 1.22, j * 0.62);
                b.add(Meshes.sharedSphere(8), 0.46, 0.46, 0.20, 0, yaw,
                        oeil[0], Meshes.jy(y + h + e * 0.74), oeil[1], blanc);
                double[] pupille = local(x, z, yaw, 1.34, j * 0.62);
                b.add(Meshes.sharedSphere(6), 0.22, 0.22, 0.14, 0, yaw,
                        pupille[0], Meshes.jy(y + h + e * 0.74), pupille[1], noir);
                // oreilles : deux tenons plats sur les flancs, qui elargissent
                // la silhouette juste assez pour qu'on compte les etages
                double[] oreille = local(x, z, yaw, 0.2, j * 1.55);
                b.add(Meshes.sharedBox(), 0.9, e * 0.30, 1.1, 0, yaw,
                        oreille[0], Meshes.jy(y + h + e * 0.80), oreille[1], accent);
            }
            h += e;
        }
        // l'oiseau du sommet : deux ailes ecartees, legerement relevees, et un
        // bec qui prolonge celui de la derniere figure
        for (int j = -1; j <= 1; j += 2) {
            double[] aile = local(x, z, yaw, 0, j * 2.6);
            b.add(Meshes.sharedBox(), 4.4, 0.45, 1.5, j * 8, yaw,
                    aile[0], Meshes.jy(y + h + 0.45), aile[1],
                    teintes[0].deriveColor(0, 1, 1.05, 1));
        }
        b.add(Meshes.sharedBox(), 1.5, 1.1, 1.5, 0, yaw,
                x, Meshes.jy(y + h + 0.55), z, noir);
        double[] pointe = local(x, z, yaw, 1.5, 0);
        b.add(Meshes.sharedBox(), 0.8, 0.6, 1.8, 0, yaw,
                pointe[0], Meshes.jy(y + h + 0.5), pointe[1], teintes[3]);
        return 2.4;
    }

    private static double bush(DecorBatch b, Theme theme, Random rnd,
                               double x, double y, double z, double yaw, int div) {
        // le buisson sec du desert remonte du kaki au sauge clair : sur un sol
        // passe a l'ocre orange, un kaki moyen avait exactement la valeur du
        // sable et disparaissait
        Color base = theme == Theme.SNOW ? Color.web("#e6f0f6")
                : theme == Theme.DESERT ? Color.web("#a3ac72")
                : Color.web("#356b2c");
        double widest = 0;
        for (int i = 0; i < 3; i++) {
            double r = 0.8 + rnd.nextDouble() * 0.7;
            widest = Math.max(widest, r);
            b.add(Meshes.sharedSphere(div), r, r * 0.75, r, 0, yaw,
                    x + (rnd.nextDouble() - 0.5) * 1.3, Meshes.jy(y + r * 0.6),
                    z + (rnd.nextDouble() - 0.5) * 1.3,
                    base.deriveColor(0, 1, 0.85 + rnd.nextDouble() * 0.4, 1));
        }
        return widest;
    }

    private static double rock(DecorBatch b, Random rnd,
                               double x, double y, double z, double yaw, int div) {
        return rock(b, rnd, x, y, z, yaw, div, Color.web("#4b423e"));
    }

    /**
     * Le meme rocher, dans la teinte qu'on lui demande.
     *
     * <p>Le volcan en fait de la scorie claire pour qu'elle tranche sur un sol
     * de basalte ; les quatre autres decors de plein air gardent le gris brun.
     * La surcharge ne prend <b>aucun tirage de plus</b> : c'est ce qui permet
     * de reteinter le volcan sans deplacer un seul arbre de la prairie.
     */
    private static double rock(DecorBatch b, Random rnd,
                               double x, double y, double z, double yaw, int div, Color teinte) {
        int lumps = 1 + rnd.nextInt(3);
        double widest = 0;
        for (int i = 0; i < lumps; i++) {
            double r = 0.9 + rnd.nextDouble() * 2.0;
            widest = Math.max(widest, r);
            b.add(Meshes.sharedSphere(div),
                    r * (0.8 + rnd.nextDouble() * 0.5), r * (0.55 + rnd.nextDouble() * 0.35), r,
                    0, yaw,
                    x + (rnd.nextDouble() - 0.5) * r * 1.6, Meshes.jy(y + r * 0.35),
                    z + (rnd.nextDouble() - 0.5) * r * 1.6,
                    teinte.deriveColor(0, 1, 0.75 + rnd.nextDouble() * 0.6, 1));
        }
        return widest * 0.8;
    }

    /** Pile de pneus, le garde-fou classique des circuits. */
    private static void tyreStack(DecorBatch b, Random rnd, double x, double y, double z) {
        int count = 3 + rnd.nextInt(2);
        for (int i = 0; i < count; i++) {
            b.add(Meshes.sharedCylinder(8), 0.62, 0.42, 0.62, 0, rnd.nextDouble() * 30,
                    x, Meshes.jy(y + 0.21 + i * 0.40), z, Color.web("#1c1c1e"));
        }
        b.add(Meshes.sharedCylinder(8), 0.36, 0.10, 0.36, 0, 0,
                x, Meshes.jy(y + 0.21 + count * 0.40), z, Color.web("#e0e0e0"));
    }

    /** Trois cones de chantier alignes. */
    private static void trafficCones(DecorBatch b, double x, double y, double z,
                                     double heading, int sign) {
        double a = Math.toRadians(heading);
        for (int i = 0; i < 3; i++) {
            double d = (i - 1) * 1.6;
            double cx = x + Math.sin(a) * d;
            double cz = z + Math.cos(a) * d;
            b.add(Meshes.sharedBox(), 0.55, 0.06, 0.55, 0, heading,
                    cx, Meshes.jy(y + 0.03), cz, Color.web("#1c1c1e"));
            b.add(Meshes.sharedCone(8), 0.26, 0.62, 0.26, 0, heading,
                    cx, Meshes.jy(y + 0.05), cz, Color.web("#e8621f"));
            b.add(Meshes.sharedCone(8), 0.17, 0.20, 0.17, 0, heading,
                    cx, Meshes.jy(y + 0.32), cz, Color.web("#f2f2ee"));
        }
    }

    /**
     * L'emplacement « protection » du mobilier de bord de piste, une piece par
     * theme de plein air.
     *
     * <p>C'etait la botte de paille pour les cinq themes. Elle a un sens la ou
     * il y a une ferme a moins d'un kilometre — la Piste de Tux et sa
     * moisson, la Colline du Manchot et sa foret — et aucun ailleurs : le
     * controle a l'agrandissement montre deux rouleaux de paille couches sur
     * la neige au pied des sapins de la Banquise, et deux autres derriere la
     * barriere de pneus du Petit Volcan, sur le basalte.
     *
     * <p><b>Toutes les variantes tirent exactement comme la botte de paille</b>
     * qu'elles remplacent : un {@code nextInt(2)} puis un {@code nextDouble()}
     * par element. Le semis se decide tirage par tirage, et ce qui suit cette
     * boucle — les piles de pneus a l'entree des virages, notamment — se serait
     * deplace sur trois circuits pour une simple substitution de forme.
     */
    private static void pieceDeSecurite(DecorBatch b, Random rnd, double x, double y, double z,
                                        double heading, Theme theme) {
        switch (theme) {
            case SNOW -> barriereANeige(b, rnd, x, y, z, heading);
            case DESERT -> futsBalises(b, rnd, x, y, z, heading);
            case VOLCANO -> panneauDeDanger(b, rnd, x, y, z, heading);
            default -> hayBale(b, rnd, x, y, z, heading);
        }
    }

    /** Botte de paille couchee, protection classique des bords de piste. */
    private static void hayBale(DecorBatch b, Random rnd, double x, double y, double z,
                                double heading) {
        int count = 1 + rnd.nextInt(2);
        for (int i = 0; i < count; i++) {
            b.add(Meshes.sharedCylinder(10), 0.62, 1.35, 0.62, 90, heading,
                    x, Meshes.jy(y + 0.62 + i * 1.24), z,
                    Color.web("#d9bf6a").deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.25, 1));
        }
    }

    /**
     * Barriere a neige : la protection de bord de piste de la Banquise.
     *
     * <p>C'est le seul objet de securite qu'on trouve reellement le long d'une
     * route ou d'une piste enneigee, et il y remplit le meme role que la botte
     * de paille en campagne : arreter ce qui sort et dire ou est le bord. Sa
     * silhouette est une palissade de lattes verticales espacees, tenue par
     * deux montants et une lisse — c'est l'ecart entre les lattes qui la
     * distingue d'un mur, et il faut donc le garder visible.
     *
     * <p>Orange et non bois : le sol du theme est a 232 de clarte et le
     * bas-cote enneige aussi ; une palissade brune s'y verrait, mais une
     * palissade orange s'y lit a cent metres, et c'est la teinte des filets de
     * chantier et des barrieres de piste de ski. L'orange est un cran plus
     * rouge que celui des cones (#e8621f) pour qu'on ne confonde pas les deux
     * emplacements du repertoire.
     *
     * <p>Invente : je n'ai pas de source sur le mobilier de bord de piste des
     * circuits enneiges de l'original.
     */
    private static void barriereANeige(DecorBatch b, Random rnd, double x, double y, double z,
                                       double heading) {
        int panneaux = 1 + rnd.nextInt(2);
        double largeur = 3.0, haut = 1.35;
        for (int i = 0; i < panneaux; i++) {
            Color latte = Color.web("#d9531e")
                    .deriveColor(0, 1, 0.88 + rnd.nextDouble() * 0.3, 1);
            // les panneaux se posent bout a bout le long de la piste, centres
            // sur l'ancrage : un seul panneau reste centre, deux se partagent
            double d = (i - (panneaux - 1) / 2.0) * (largeur + 0.25);
            double[] c = local(x, z, heading, d, 0);
            for (int m = -1; m <= 1; m += 2) {
                double[] q = local(x, z, heading, d + m * largeur * 0.46, 0);
                b.add(Meshes.sharedCylinder(6), 0.11, haut + 0.25, 0.11, 0, heading,
                        q[0], Meshes.jy(y + (haut + 0.25) / 2), q[1], Color.web("#4d3a2a"));
            }
            for (int l = 0; l < 6; l++) {
                double[] q = local(x, z, heading,
                        d + (l - 2.5) * largeur * 0.165, 0);
                b.add(Meshes.sharedBox(), 0.05, haut, largeur * 0.085, 0, heading,
                        q[0], Meshes.jy(y + haut / 2), q[1], latte);
            }
            // la lisse : sans elle les lattes flottent, avec elle c'est une
            // barriere
            b.add(Meshes.sharedBox(), 0.04, 0.09, largeur, 0, heading,
                    c[0], Meshes.jy(y + haut * 0.78), c[1], latte.deriveColor(0, 1, 0.8, 1));
        }
    }

    /**
     * Futs peints : la protection de bord de piste du Desert de GNU.
     *
     * <p>Le fut metallique peint est le balisage des rallyes de sable — c'est
     * ce qui marque la trace la ou il n'y a ni rail ni haie — et il tient le
     * meme role que la botte de paille : un volume lourd, pose au bord, qu'on
     * voit de loin. Deux anneaux de cerclage et un couvercle suffisent a le
     * distinguer d'une botte de paille debout, qui est le seul autre cylindre
     * du repertoire.
     *
     * <p>Creme et non rouille : le sol du theme pese 117 de clarte, un fut
     * rouille en pese 96 — il s'y noierait. Le corps creme monte a 230, la
     * ceinture rouge le nomme, et le contraste est le meme que celui qui fait
     * lire les mesas.
     */
    private static void futsBalises(DecorBatch b, Random rnd, double x, double y, double z,
                                    double heading) {
        int n = 1 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            Color corps = Color.web("#ece3cd")
                    .deriveColor(0, 1, 0.9 + rnd.nextDouble() * 0.2, 1);
            Color bande = Color.web("#c0392b");
            double[] c = local(x, z, heading, (i - (n - 1) / 2.0) * 1.05, i * 0.35);
            b.add(Meshes.sharedCylinder(10), 0.31, 0.88, 0.31, 0, heading,
                    c[0], Meshes.jy(y + 0.44), c[1], corps);
            b.add(Meshes.sharedCylinder(10), 0.335, 0.11, 0.335, 0, heading,
                    c[0], Meshes.jy(y + 0.30), c[1], bande);
            b.add(Meshes.sharedCylinder(10), 0.335, 0.11, 0.335, 0, heading,
                    c[0], Meshes.jy(y + 0.60), c[1], bande);
            b.add(Meshes.sharedCylinder(10), 0.30, 0.06, 0.30, 0, heading,
                    c[0], Meshes.jy(y + 0.90), c[1], Color.web("#8d8577"));
            // le fut couche, une seule fois : c'est lui qui donne le tas et
            // evite la borne solitaire quand le tirage ne donne qu'un fut
            if (i == 0) {
                double[] q = local(x, z, heading, -1.15, 0.9);
                b.add(Meshes.sharedCylinder(10), 0.31, 0.88, 0.31, 90, heading + 24,
                        q[0], Meshes.jy(y + 0.31), q[1], corps.deriveColor(0, 1, 0.92, 1));
                b.add(Meshes.sharedCylinder(10), 0.335, 0.11, 0.335, 90, heading + 24,
                        q[0] + Math.cos(Math.toRadians(heading + 24)) * 0.14,
                        Meshes.jy(y + 0.31),
                        q[1] - Math.sin(Math.toRadians(heading + 24)) * 0.14, bande);
            }
        }
    }

    /**
     * Panneau de danger : la protection de bord de piste du Petit Volcan.
     *
     * <p>Rien de mou ne tient au bord d'une coulee — c'est bien pour cela que
     * le circuit est ceint de pneus et non de paille. Ce qui borde vraiment un
     * champ de lave, c'est de la signalisation : le triangle jaune a lisere
     * noir se lit au premier coup d'oeil, il est la seule tache claire d'un
     * theme dont le sol pese 45 de clarte, et il dit ce que le lieu est.
     *
     * <p>Le triangle est un cone a quatre pans ecrase sur son axe travers :
     * les quatre faces laterales se replient en deux plaques dos a dos, donc
     * la piece se voit des deux cotes malgre le {@code CullFace.BACK}. Le
     * lisere est un second triangle un peu plus grand et un peu moins epais,
     * ce qui evite la lutte de profondeur entre deux plaques coplanaires.
     *
     * <p>Invente : l'original ne m'est pas accessible et je n'ai pas de source
     * sur son mobilier de circuit volcanique.
     */
    private static void panneauDeDanger(DecorBatch b, Random rnd, double x, double y, double z,
                                        double heading) {
        int n = 1 + rnd.nextInt(2);
        for (int i = 0; i < n; i++) {
            double gite = rnd.nextDouble() * 8 - 4;
            double[] c = local(x, z, heading, (i - (n - 1) / 2.0) * 2.1, 0);
            b.add(Meshes.sharedCylinder(6), 0.075, 1.40, 0.075, gite, heading,
                    c[0], Meshes.jy(y + 0.70), c[1], Color.web("#9a938a"));
            // lisere puis fond : le jaune est plus epais que le noir pour
            // ressortir des deux faces sans lutte de profondeur
            b.add(Meshes.sharedCone(4), 0.68, 0.62, 0.05, gite, heading,
                    c[0], Meshes.jy(y + 1.30), c[1], Color.web("#17130f"));
            b.add(Meshes.sharedCone(4), 0.54, 0.50, 0.07, gite, heading,
                    c[0], Meshes.jy(y + 1.36), c[1], Color.web("#f2c518"));
            // le point d'exclamation : deux boites, mais c'est lui qui fait la
            // difference entre un panneau de danger et un fanion triangulaire
            b.add(Meshes.sharedBox(), 0.11, 0.22, 0.10, gite, heading,
                    c[0], Meshes.jy(y + 1.62), c[1], Color.web("#17130f"));
            b.add(Meshes.sharedBox(), 0.11, 0.09, 0.10, gite, heading,
                    c[0], Meshes.jy(y + 1.46), c[1], Color.web("#17130f"));
        }
    }

    /**
     * Poste de commissaire : une cabane et son toit.
     *
     * <p>La cabane elle-meme tient partout : partout il y a une course, il y a
     * quelqu'un pour agiter un drapeau, et il lui faut un abri. C'est sa
     * <b>livree</b> qui doit suivre le theme, et la geometrie ne bouge pas
     * d'un centimetre d'un circuit a l'autre.
     *
     * <p>Blanc creme sous tuile rouge, c'est une cabane de commissaire de
     * circuit de campagne — juste sur la Piste de Tux et la Colline du
     * Manchot, et nulle part ailleurs. Sur une coulee, le controle visuel
     * l'avait releve a l'agrandissement, elle se lisait comme un <b>pavillon
     * de lotissement</b> ; anthracite et jaune de securite, la meme silhouette
     * se lit comme une cabine technique.
     *
     * <p>Deux livrees s'ajoutent, et chacune pour une raison mesurable :
     * <ul>
     * <li>Banquise — le sol du theme pese 232 de clarte et le corps blanc 219.
     * Quatorze points d'ecart : a l'agrandissement la cabane est un fantome
     * pose sur la neige, on ne voit que la fente sombre de sa porte. Rouge
     * plancheie (96 de clarte) sous un toit blanc de neige, c'est le refuge
     * qu'on peint en rouge pour le retrouver, et c'est le plus fort contraste
     * que le theme permette ;</li>
     * <li>Desert — le sol pese 117 de clarte. Un corps a 219 tient, mais la
     * tuile rouge en pese 96 : un toit sombre sur un mur clair, dans un
     * paysage de mesas rouges, redonne le pavillon. Murs a la chaux et
     * menuiserie bleue : le bleu est la seule teinte absente du theme — sable,
     * gres, cactus, ciel creme — donc la seule qui dise « bati » et non
     * « caillou ». <b>Invente</b> : c'est un parti de couleur, je n'ai pas de
     * source sur le mobilier du circuit desertique de l'original.</li>
     * </ul>
     */
    private static void marshalPost(DecorBatch b, double x, double y, double z,
                                    double heading, Theme theme) {
        Color corps = switch (theme) {
            case VOLCANO -> Color.web("#3b4046");
            case SNOW -> Color.web("#b0362c");
            case DESERT -> Color.web("#ece2cd");
            default -> Color.web("#d8dce0");
        };
        Color toit = switch (theme) {
            case VOLCANO -> Color.web("#cf9a17");
            case SNOW -> Color.web("#f4f8fb");
            case DESERT -> Color.web("#2f6ba8");
            default -> Color.web("#c0392b");
        };
        b.add(Meshes.sharedBox(), 1.7, 2.1, 1.4, 0, heading,
                x, Meshes.jy(y + 1.05), z, corps);
        b.add(Meshes.sharedBox(), 2.0, 0.16, 1.7, 0, heading,
                x, Meshes.jy(y + 2.2), z, toit);
        b.add(Meshes.sharedBox(), 1.1, 0.9, 0.06, 0, heading,
                x, Meshes.jy(y + 1.35), z,
                theme == Theme.DESERT ? Color.web("#2f6ba8") : Color.web("#2b3138"));
    }

    /**
     * Eclairage de bord de piste.
     *
     * <p>C'est l'emplacement <b>haut</b> du repertoire : six metres tous les
     * quatre-vingt-quatre, c'est ce qui donne l'echelle du bord de piste et ce
     * qui dessine la courbe qu'on n'a pas encore prise. La piece change donc
     * de nature avec le theme, mais jamais de hauteur.
     *
     * <p>Sur une route de campagne c'est un lampadaire — mat gris, tete
     * blanche. Sur une coulee c'est une <b>torchere</b> : le mat gris a tete
     * blanche etait la piece qui faisait le plus ressembler le Petit Volcan a
     * un bord de departementale, et il n'y a pas d'eclairage public sur un
     * champ de lave. La flamme donne en prime la lueur qui manquait au ras du
     * sol, et elle la donne <b>regulierement</b>, la ou le semis ne la donne
     * qu'au hasard.
     *
     * <p>Sur la Banquise et dans le Desert c'est une <b>manche a air</b>. Meme
     * raison qu'au volcan : il n'y a pas de reverberes sur un glacier ni sur
     * une mer de sable, et le lampadaire y racontait une rue. Une manche a air
     * est le mat qu'on plante la ou il n'y a rien d'autre — piste polaire,
     * bivouac de rallye —, sa silhouette ne ressemble a aucune autre piece du
     * jeu, et ses bandes orange et blanches sont visibles sur les deux sols
     * les plus clairs du jeu comme sur le ciel. Une seule piece pour deux
     * themes, plutot que deux pieces mediocres.
     *
     * <p><b>Invente</b> : je n'ai pas de source sur le mobilier de bord de
     * piste des circuits enneiges et desertiques de l'original.
     */
    private static void lampPost(DecorBatch b, double x, double y, double z,
                                 double heading, int sign, Theme theme) {
        if (theme == Theme.SNOW || theme == Theme.DESERT) {
            mancheAAir(b, x, y, z, heading, sign);
            return;
        }
        if (theme == Theme.VOLCANO) {
            // trois volumes : le fut trapu, la vasque, la flamme. Une flamme
            // en cone pointe en haut est la seule silhouette qui se nomme
            // toute seule a cette taille.
            b.add(Meshes.sharedFrustum(6, 0.45), 0.34, 3.6, 0.34, 0, 0,
                    x, Meshes.jy(y), z, Color.web("#221c1a"));
            // la vasque est un tronc retourne : une bascule d'un demi-tour
            // suffit, et elle pend alors sous son point d'ancrage
            b.add(Meshes.sharedFrustum(8, 0.45), 0.72, 0.75, 0.72, 180, 0,
                    x, Meshes.jy(y + 4.05), z, Color.web("#151110"));
            // Trois langues et non un cone unique : un seul cone orange de
            // cinquante-cinq centimetres pose sur un socle noir se lisait comme
            // un plot de chantier geant, ce qui est exactement le mobilier
            // qu'on essayait de chasser. Trois pointes inegales et penchees
            // font une flamme.
            b.add(Meshes.sharedCone(8), 0.34, 1.25, 0.34, 0, 0,
                    x, Meshes.jy(y + 3.95), z, LAVE);
            b.add(Meshes.sharedCone(7), 0.2, 0.9, 0.2, 22, 40,
                    x, Meshes.jy(y + 3.95), z, LAVE.deriveColor(0, 0.96, 1.12, 1));
            b.add(Meshes.sharedCone(7), 0.16, 0.62, 0.16, -26, 155,
                    x, Meshes.jy(y + 3.95), z, LAVE);
            return;
        }
        b.add(Meshes.sharedCylinder(6), 0.11, 6.2, 0.11, 0, 0,
                x, Meshes.jy(y + 3.1), z, Color.web("#9aa0a6"));
        b.add(Meshes.sharedBox(), 0.9, 0.22, 0.45, 0, 0,
                x, Meshes.jy(y + 6.2), z, Color.web("#c9ced3"));
        b.add(Meshes.sharedBox(), 0.7, 0.10, 0.35, 0, 0,
                x, Meshes.jy(y + 6.05), z, Color.web("#fff3c4"));
    }

    /**
     * Manche a air : mat bande, anneau de gueule et trois fuseaux qui
     * s'affinent.
     *
     * <p>La chaussette est <b>en travers de la piste</b> et non dans son axe :
     * pointee vers l'aval, on la verrait de bout en arrivant, c'est-a-dire un
     * disque. En travers elle donne son profil — la gueule large, la pointe
     * fine, la retombee — qui est tout ce qui la nomme. Elle part du cote
     * exterieur, {@code sign} donnant le signe de la normale de piste, pour ne
     * jamais surplomber le couloir : le decor n'a pas de collision, mais une
     * chaussette au-dessus de la trajectoire se lirait comme un obstacle.
     *
     * <p>Le mat est monte en trois troncons bandes et non d'une seule teinte :
     * sur la neige un mat blanc disparait, sur le sable un mat clair aussi. La
     * bande de couleur est au <b>milieu</b> et non au pied — le mobilier est
     * pose a un metre six au-dela du couloir, et depuis le siege du kart le
     * bourrelet de neige de la Banquise en masque les deux premiers metres :
     * une bande basse ne se voit pas.
     */
    private static void mancheAAir(DecorBatch b, double x, double y, double z,
                                   double heading, int sign) {
        double mat = 5.6;
        Color orange = Color.web("#e0561c"), blanc = Color.web("#f2f2ee");
        for (int i = 0; i < 3; i++) {
            b.add(Meshes.sharedCylinder(6), 0.10, mat / 3, 0.10, 0, 0,
                    x, Meshes.jy(y + mat * (i + 0.5) / 3), z, i == 1 ? orange : blanc);
        }
        // L'axe de la chaussette : bascule de 100 degres pour qu'elle parte a
        // l'horizontale en retombant un peu, puis lacet qui la couche sur la
        // normale de piste. tilt envoie le +Y local sur le +X local, et le
        // lacet envoie le +X local sur la normale de piste (cf. local()).
        double lacet = heading + (sign > 0 ? 0 : 180);
        double t = Math.toRadians(100), w = Math.toRadians(lacet);
        double ux = Math.sin(t) * Math.cos(w);
        double uy = Math.cos(t);
        double uz = -Math.sin(t) * Math.sin(w);
        double bx = x + ux * 0.10, by = y + mat + uy * 0.10, bz = z + uz * 0.10;
        // l'anneau de gueule : sans lui la chaussette est un cone flottant
        b.add(Meshes.sharedCylinder(8), 0.46, 0.08, 0.46, 100, lacet,
                bx, Meshes.jy(by), bz, Color.web("#4a4f55"));
        double[] rayons = {0.42, 0.33, 0.24, 0.15};
        Color[] bandes = {orange, blanc, orange};
        double d = 0;
        for (int i = 0; i < 3; i++) {
            double len = 0.60;
            b.add(Meshes.sharedFrustum(8, rayons[i + 1] / rayons[i]),
                    rayons[i], len, rayons[i], 100, lacet,
                    bx + ux * d, Meshes.jy(by + uy * d), bz + uz * d, bandes[i]);
            d += len;
        }
    }

    private static Group flag(int index) {
        Group g = new Group();
        MeshView pole = Meshes.cylinder(0.08, 4.4, 8, Meshes.material(Color.web("#d8d8d8")));
        pole.setTranslateY(Meshes.jy(2.2));
        Color[] colors = {Color.web("#f6a723"), Color.web("#1d6fb8"),
                Color.web("#2c8a4b"), Color.web("#c0392b")};
        Box cloth = new Box(1.5, 0.9, 0.06);
        cloth.setMaterial(Meshes.material(colors[index % colors.length]));
        cloth.setTranslateX(0.78);
        cloth.setTranslateY(Meshes.jy(3.9));
        g.getChildren().addAll(pole, cloth);
        return g;
    }

    /**
     * Tribune garnie, posee juste avant la ligne d'arrivee.
     *
     * <p><b>Elle tournait le dos a la piste.</b> {@code place} l'oriente a
     * {@code cap - 90}, et sous cette rotation l'axe Z local part vers
     * {@code -side}, c'est-a-dire vers le ruban ; les gradins, eux, montaient
     * vers les Z croissants. De la chaussee on ne voyait donc que le dos des
     * marches — cinq dalles grises et rien d'autre — pendant que la foule,
     * plaquee sur un panneau a l'autre extremite, restait cachee derriere
     * onze metres de gradins. C'est ce qu'un controle visuel a decrit comme
     * « des gradins vides ». Les marches descendent maintenant vers les Z
     * negatifs, en s'eloignant du ruban, et la premiere rangee est au bord de
     * la piste.
     *
     * <p>La foule n'est plus une image collee sur un panneau mais une centaine
     * de spectateurs en volume, poses sur les marches. Une image plate ne tient
     * que vue de face, alors qu'on arrive toujours sur la tribune de trois
     * quarts ; et c'est la silhouette d'une rangee de tetes sur le ciel qui dit
     * qu'une tribune est pleine. Ils sont fusionnes dans un {@link DecorBatch},
     * faute de quoi une tribune couterait deux cents noeuds JavaFX a elle
     * seule. Leur semis a sa <b>propre graine</b> : celui du decor se decide
     * tirage par tirage, et lui en prendre un decalerait tout le circuit.
     *
     * <p>La tribune est du mobilier de circuit automobile : elle est
     * legitime dehors et proscrite dans les quatre pieces, ou une tribune de
     * trente metres annulerait l'echelle. C'est l'appelant qui le tranche.
     */
    /** Recul de la tribune avant la ligne d'arrivee, en metres. */
    private static final double TRIBUNE_RECUL = 34;
    /** Ecart de la facade de la tribune au-dela du couloir, en metres. */
    private static final double TRIBUNE_ECART = 3.0;
    /** Demi-longueur de la tribune le long de la piste : le toit fait 31 m. */
    private static final double TRIBUNE_DEMI_LONGUEUR = 15.5;
    /** Profondeur de la tribune au-dela de sa facade, en metres. */
    private static final double TRIBUNE_PROFONDEUR = 12.0;

    /**
     * Vrai si ce point tombe dans l'emprise de la tribune.
     *
     * <p>Meme role que {@link #sousOuvrage} pour un ouvrage traverse : une
     * tribune est un volume plein, et rien n'y pousse. Faute de ce test, la
     * Banquise avait trois ou quatre sapins enneiges au milieu de sa foule,
     * tronc compris, et la tribune se lisait comme une clairiere.
     *
     * <p>La facade est comptee un metre six en deca de l'ancrage, ou tombe le
     * bandeau d'auvent. L'emprise deborde ensuite de deux metres a chaque bout
     * et de deux metres au-dela du fond, pour deux raisons : le semis decale son
     * point d'un pas d'echantillonnage le long de la piste apres avoir choisi
     * son abscisse, et un sapin de deux metres de rayon plante juste derriere le
     * dernier gradin depasse encore entre les poteaux.
     */
    private static boolean dansLaTribune(Track track, double s, double lat) {
        if (track.def.theme.indoor || lat <= 0) return false;
        if (Math.abs(track.deltaS(track.length - TRIBUNE_RECUL, s))
                > TRIBUNE_DEMI_LONGUEUR + 2) {
            return false;
        }
        double facade = track.halfCorridorAtS(s) + TRIBUNE_ECART - 1.6;
        return lat > facade && lat < facade + TRIBUNE_PROFONDEUR + 2;
    }

    // ------------------------------------------------- les reperes de paysage

    /**
     * Les pieces posees dont le role est de <b>dire ou l'on en est dans le
     * tour</b>, et non de meubler le bas-cote.
     *
     * <p>Un repere ne remplit son office que s'il se voit, et mesure faite
     * aucun des cinq ne se voyait. Deux causes, cumulees. La premiere est
     * geometrique : la camera ouvre 60 a 69 degres horizontaux, donc un objet a
     * {@code R} metres de cote n'entre dans le cadre qu'a environ
     * {@code 1,4 x R} devant — la ferme de la Piste de Tux, posee a trente et
     * un metres juste apres la grange, n'etait cadree que sur trente-quatre
     * metres de piste, et pendant ces trente-quatre metres on etait dans la
     * grange, dont les murs la bouchaient. Le moulin de la Piste de Tux, lui,
     * n'etait cadre nulle part : zero metre sur mille soixante-dix.
     *
     * <p>La seconde est le rideau d'arbres. La bande semee fait cent huit
     * metres de large en Ultra et se plante de feuillus de neuf a quinze
     * metres ; une ferme dont le faitage culmine a dix metres sept disparait
     * derriere, meme la ou la geometrie la donnait visible.
     *
     * <p>D'ou ce champ degage devant chaque repere. Il n'a rien d'un artifice :
     * une ferme a ses pres, un moulin a besoin de vent, et un silo se remplit
     * depuis une cour. Ce que la trouee coute au paysage, elle le rend en
     * profondeur — la lisiere reste, quinze metres au-dela du repere.
     */
    private static final java.util.Set<String> REPERES =
            java.util.Set.of("ferme", "silo", "moulin");

    /** Longueur de champ degage en amont du repere, en metres. */
    private static final double CHAMP_AMONT = 70;
    /** Longueur de champ degage au-dela du repere, en metres. */
    private static final double CHAMP_AVAL = 22;
    /** Ce que le champ deborde lateralement au-dela du repere, en metres. */
    private static final double CHAMP_DEBORD = 14;

    /** Un repere de paysage et la trouee qu'il ouvre dans le semis. */
    private record Champ(double s, double lat) {
    }

    private static java.util.List<Champ> champs(Track track) {
        java.util.List<Champ> liste = new java.util.ArrayList<>();
        for (int i = 0; i < track.def.props.length; i++) {
            if (!REPERES.contains(track.def.propKinds[i])) continue;
            liste.add(new Champ(track.def.props[i][0], track.def.props[i][1]));
        }
        return liste;
    }

    /**
     * Vrai si ce point du semis tombe dans le champ degage d'un repere.
     *
     * <p>La trouee s'ouvre <b>en amont</b> du repere, parce que c'est de la
     * qu'on le regarde : le kart roule vers les abscisses croissantes, et les
     * arbres qui le bouchent sont ceux qui se tiennent entre lui et nous. Elle
     * ne se referme qu'un peu au-dela, le temps de degager la piece elle-meme.
     *
     * <p>Comme la tribune, ce test ne saute aucun tirage : l'appelant seme
     * normalement et verse la piece dans un lot de rebut. Un {@code continue}
     * deplacerait tout le decor du circuit a partir de la.
     */
    private static boolean dansUnChamp(java.util.List<Champ> champs, Track track,
                                       double s, double lat) {
        for (Champ c : champs) {
            if (Math.signum(lat) != Math.signum(c.lat())) continue;
            double d = track.deltaS(c.s(), s);
            if (d < -CHAMP_AMONT || d > CHAMP_AVAL) continue;
            if (Math.abs(lat) < Math.abs(c.lat()) + CHAMP_DEBORD) return true;
        }
        return false;
    }

    private static Group grandstand(Track track) {
        Group g = new Group();
        double s = track.length - TRIBUNE_RECUL;
        // le couloir *local* et non la largeur de reference : le desert est
        // large de quinze metres cinquante a cet endroit pour quatorze de
        // reference, et la tribune se serait posee dans le talus
        Vec3 p = track.worldAt(s, track.halfCorridorAtS(s) + TRIBUNE_ECART);
        double heading = Math.toDegrees(track.headingAtS(s));

        // Le beton d'une tribune est gris partout, sauf au desert : c'etait le
        // seul objet froid d'un tour entierement ocre, et il s'en detachait
        // comme une piece rapportee d'un autre circuit.
        Color beton = track.def.theme == Theme.DESERT
                ? Color.web("#c8a97e") : Color.web("#9aa2a8");
        Color[] maillots = {Color.web("#e05a4a"), Color.web("#f6a723"),
                Color.web("#7ec8f2"), Color.web("#8fbf3f"), Color.web("#c9a9e8"),
                Color.web("#f2f2f2"), Color.web("#4a6ee0"), Color.web("#e8e04a")};
        Color[] peaux = {Color.web("#e8c49a"), Color.web("#c08a5a"),
                Color.web("#8a5a34"), Color.web("#f0d8b8")};

        Group stand = new Group();
        DecorBatch b = new DecorBatch();
        Random rnd = new Random(4021);

        // Socle. Sans lui la premiere rangee etait a un metre du sol, donc
        // derriere la barriere de bois de la Colline du Manchot, haute de un
        // metre quatre-vingt-dix : la capture de controle n'y montrait que le
        // haut des tetes. Une tribune est batie sur un remblai justement pour
        // que la premiere rangee voie par-dessus le rail.
        double socle = 1.8;
        int rangs = 5;
        double hautDernier = 0;
        for (int row = 0; row < rangs; row++) {
            double dessus = socle + 1.0 + row * 1.0;
            double zc = -row * 2.2 - 1.1;
            hautDernier = dessus;
            // chaque marche est pleine depuis le sol : de face on ne voit que
            // la contremarche de la premiere, et la tribune reste un volume
            b.add(Meshes.sharedBox(), 30, dessus, 2.2, 0, 0,
                    0, Meshes.jy(dessus / 2), zc,
                    row % 2 == 0 ? beton : beton.deriveColor(0, 1, 0.93, 1));
            for (int i = 0; i < 21; i++) {
                if (rnd.nextDouble() < 0.12) continue;
                double px = -14.0 + i * 1.4 + (rnd.nextDouble() - 0.5) * 0.45;
                double pz = zc - 0.25;
                b.add(Meshes.sharedBox(), 0.62, 0.9, 0.5, 0, 0,
                        px, Meshes.jy(dessus + 0.45), pz,
                        maillots[rnd.nextInt(maillots.length)]);
                b.add(Meshes.sharedSphere(6), 0.23, 0.26, 0.23, 0, 0,
                        px, Meshes.jy(dessus + 1.12), pz,
                        peaux[rnd.nextInt(peaux.length)]);
            }
        }
        // fond ferme : sans lui on voit le ciel entre la derniere rangee et le
        // toit, et la tribune se lit comme un escalier abandonne
        b.add(Meshes.sharedBox(), 30, hautDernier + 2.4, 0.4, 0, 0,
                0, Meshes.jy((hautDernier + 2.4) / 2), -rangs * 2.2 - 0.2,
                beton.deriveColor(0, 1, 0.86, 1));
        MeshView gradins = b.build();
        if (gradins != null) stand.getChildren().add(gradins);

        Box roof = new Box(31, 0.4, 13);
        roof.setMaterial(Meshes.material(Color.web("#d64b3a")));
        roof.setTranslateZ(-5.0);
        roof.setTranslateY(Meshes.jy(10.4));
        stand.getChildren().add(roof);
        // bandeau d'auvent : la tranche du toit vue de la piste, qui donne a la
        // tribune une horizontale franche au-dessus des tetes
        Box fascia = new Box(31, 0.9, 0.3);
        fascia.setMaterial(Meshes.material(Color.web("#b03a2b")));
        fascia.setTranslateZ(1.35);
        fascia.setTranslateY(Meshes.jy(9.9));
        stand.getChildren().add(fascia);
        // poteaux aux deux extremites seulement : au milieu ils masqueraient
        // justement la foule qu'on vient de poser
        for (int i = -1; i <= 1; i += 2) {
            Cylinder post = new Cylinder(0.2, 10.4, 10);
            post.setMaterial(Meshes.material(beton.deriveColor(0, 1, 1.08, 1)));
            post.setTranslateX(i * 14.6);
            post.setTranslateZ(0.9);
            post.setTranslateY(Meshes.jy(5.2));
            stand.getChildren().add(post);
        }

        place(g, stand, p.x, p.y - 0.2, p.z, heading - 90);
        return g;
    }

    // ------------------------------------------------------------------- ciel

    private static javafx.scene.shape.Sphere sky(Track track) {
        javafx.scene.shape.Sphere dome = new javafx.scene.shape.Sphere(2400, 32);
        PhongMaterial mat = new PhongMaterial(Color.WHITE);
        mat.setDiffuseMap(Textures.sky(track.def.theme));
        mat.setSelfIlluminationMap(Textures.sky(track.def.theme));
        mat.setSpecularColor(Color.TRANSPARENT);
        dome.setMaterial(mat);
        dome.setCullFace(CullFace.FRONT);
        dome.setTranslateY(Meshes.jy(track.minY + 60));
        dome.setTranslateX((track.minX + track.maxX) / 2);
        dome.setTranslateZ((track.minZ + track.maxZ) / 2);
        dome.setMouseTransparent(true);
        return dome;
    }
}
