package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import org.tuxkart.core.Quality;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Noise;
import org.tuxkart.track.Theme;
import org.tuxkart.track.Track;

import java.util.Arrays;

/**
 * Le paysage autour du circuit.
 *
 * Plutot qu'une jupe plate suivant la piste, le terrain est une grille
 * reguliere dont l'altitude vient d'un bruit de Perlin exprime en coordonnees
 * monde : les collines sont donc de vraies collines, orientees comme le
 * paysage et non comme la piste. Le relief s'aplatit en approchant du bitume
 * pour venir mourir sous les murets, ce qui evite tout raccord visible.
 *
 * La grille est decoupee en trois anneaux de distance auxquels on ajoute une
 * brume croissante : c'est la perspective aerienne, seul moyen de donner de la
 * profondeur puisque JavaFX n'offre pas de brouillard.
 */
public final class Terrain {

    /** Distance sur laquelle le relief se leve en s'eloignant de la piste. */
    private static final double FLATTEN = 55;
    private static final double NEAR_BAND = 150;
    private static final double MID_BAND = 380;
    private static final double TEXTURE_TILE = 19;

    private final Track track;
    private final Quality quality;
    private final Noise noise;
    private final double baseY;
    /** Repere de travail de {@link #sample} ; chaque appelant fournit le sien. */
    private final Track.Loc scratch = new Track.Loc();

    public Terrain(Track track, Quality quality) {
        this.track = track;
        this.quality = quality;
        this.noise = new Noise(track.def.id.hashCode() * 977L + 3);
        this.baseY = (track.minY + track.maxY) / 2 - 5;
    }

    /**
     * Altitude du sol et distance au couloir en un point du monde.
     *
     * Les deux resultats sortent de la meme projection : les demander
     * separement, comme le faisait le placement du decor, doublait le nombre de
     * balayages du ruban de piste pour chaque arbre pose.
     */
    public static final class Sample {
        /** Altitude du terrain. */
        public double height;
        /** Distance au bord du couloir praticable, negative sur la piste. */
        public double distance;
        /** Index de piste le plus proche, a reutiliser comme indice au prochain appel. */
        public int index;
    }

    public Sample sample(double x, double z, int hint, Sample out) {
        Sample s = out != null ? out : new Sample();
        Track.Loc loc = track.locate(x, z, hint, scratch);
        // la largeur locale, et non la largeur de reference : sur un circuit
        // dont le profil s'ouvre au milieu du tapis, le relief se serait leve
        // a l'interieur meme de la chaussee — quelques centimetres, assez pour
        // crever le bitume et pour qu'un arbre pousse sur la piste.
        double couloir = track.halfCorridorAt[loc.index];
        double d = Math.abs(loc.lateral) - couloir;
        // Hauteur du ruban interpolee entre deux echantillons, et non celle du
        // plus proche : sur un dos d'ane, le pas d'echantillonnage fait une
        // marche de vingt centimetres, et le terrain ancre dessus creve la
        // chaussee juste apres la crete.
        double centre = loc.height - loc.lateral * loc.bankSlope;
        double edgeY = centre
                + Math.signum(loc.lateral)
                * Math.min(Math.abs(loc.lateral), couloir) * loc.bankSlope;

        double rise = MathUtil.smoothstep(0, FLATTEN, d);
        double far = MathUtil.smoothstep(70, 340, d);
        double anchor = MathUtil.lerp(edgeY - 0.5, baseY, far);

        double hills = noise.fbm(x * 0.0034, z * 0.0034, 4) * 26
                + noise.fbm(x * 0.0125, z * 0.0125, 3) * 5.5
                + noise.perlin(x * 0.045, z * 0.045) * 0.9;

        s.height = anchor + rise * hills;
        s.distance = d;
        s.index = loc.index;
        return s;
    }

    /** Altitude du sol en un point du monde, hors piste. */
    public double heightAt(double x, double z) {
        return sample(x, z, -1, new Sample()).height;
    }

    /** Distance au bord du couloir praticable, negative sur la piste. */
    public double distanceToTrack(double x, double z) {
        return sample(x, z, -1, new Sample()).distance;
    }

    /**
     * Nombre de paves par cote pour la bande lointaine.
     *
     * Elle porte a elle seule 483 000 des 531 000 triangles du terrain en Ultra
     * (Colline du Manchot ; 453 000 sur 491 000 a la Piste de Tux), et
     * couvre tout le pourtour du circuit : la decouper permet d'en ecarter du
     * rendu la moitie que la camera ne regarde pas, et d'etaler sa mise en
     * place sur plusieurs images. Les deux bandes proches restent d'un seul
     * tenant : un pave a ses propres sommets, donc ses propres normales, et le
     * raccord se verrait la ou le joueur regarde de pres.
     *
     * <p>Deux et non trois : chaque pave est un appel de dessin de plus, et
     * JavaFX repose ses lumieres a chaque maillage et a chaque image. Mesure
     * faite, 2x2 tient 79 images par seconde la ou 3x3 en tient 70 — les paves
     * plus fins etalent un peu mieux le chargement, mais coutent davantage a
     * chaque image de la course.
     */
    private static final int FAR_TILES = 2;

    public Group build() {
        return build(new java.util.ArrayList<>());
    }

    /**
     * @param cullable recoit les paves de la bande lointaine, que l'ecran de
     *                 course peut ecarter du rendu
     */
    public Group build(java.util.List<javafx.scene.Node> cullable) {
        Group root = new Group();
        Theme theme = track.def.theme;

        double margin = quality.terrainMargin;
        double cell = quality.terrainCell;
        double x0 = track.minX - margin, x1 = track.maxX + margin;
        double z0 = track.minZ - margin, z1 = track.maxZ + margin;
        int cols = (int) Math.ceil((x1 - x0) / cell) + 1;
        int rows = (int) Math.ceil((z1 - z0) / cell) + 1;

        float[] pts = new float[cols * rows * 3];
        float[] tex = new float[cols * rows * 2];
        double[] dist = new double[cols * rows];

        Sample s = new Sample();
        int hint = -1;
        for (int r = 0; r < rows; r++) {
            // en fin de rangee on repart de l'autre bord de la carte : l'indice
            // de la case precedente n'y apprend plus rien
            hint = -1;
            for (int c = 0; c < cols; c++) {
                double x = x0 + c * cell;
                double z = z0 + r * cell;
                sample(x, z, hint, s);
                hint = s.index;

                int i = r * cols + c;
                pts[i * 3] = (float) x;
                pts[i * 3 + 1] = Meshes.jy(s.height);
                pts[i * 3 + 2] = (float) z;
                tex[i * 2] = (float) (x / TEXTURE_TILE);
                tex[i * 2 + 1] = (float) (z / TEXTURE_TILE);
                dist[i] = s.distance;
            }
        }

        // Deux passes, en tableaux primitifs : une grille fine represente des
        // millions d'indices, les stocker en List<Integer> couterait plusieurs
        // centaines de mega-octets rien qu'en boites.
        int[] counts = new int[3];
        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                counts[bandOf(dist, cols, r, c)]++;
            }
        }
        int[][] bands = new int[3][];
        int[] cursor = new int[3];
        for (int b = 0; b < 3; b++) bands[b] = new int[counts[b] * 12];

        for (int r = 0; r < rows - 1; r++) {
            for (int c = 0; c < cols - 1; c++) {
                int a = r * cols + c;
                int b = r * cols + c + 1;
                int cc = (r + 1) * cols + c + 1;
                int d = (r + 1) * cols + c;
                int band = bandOf(dist, cols, r, c);
                int[] f = bands[band];
                int k = cursor[band];
                f[k] = a; f[k + 1] = a; f[k + 2] = b; f[k + 3] = b; f[k + 4] = cc; f[k + 5] = cc;
                f[k + 6] = a; f[k + 7] = a; f[k + 8] = cc; f[k + 9] = cc; f[k + 10] = d; f[k + 11] = d;
                cursor[band] = k + 12;
            }
        }

        Image diffuse = Textures.ground(theme);
        Image bump = Textures.groundBump(theme);
        // sur la banquise le sol est deja clair : la brume l'effacerait
        double hazeScale = theme == Theme.SNOW ? 0.45 : 1.0;
        double[] haze = {0.0, 0.20 * hazeScale, 0.42 * hazeScale};
        for (int b = 0; b < 2; b++) {
            if (bands[b].length == 0) continue;
            TriangleMesh mesh = new TriangleMesh();
            mesh.getPoints().setAll(pts);
            mesh.getTexCoords().setAll(tex);
            int[] arr = bands[b];
            mesh.getFaces().setAll(arr);
            int[] smooth = new int[arr.length / 6];
            Arrays.fill(smooth, 1);
            mesh.getFaceSmoothingGroups().setAll(smooth);

            MeshView view = new MeshView(mesh);
            view.setMaterial(hazyMaterial(diffuse, b == 0 ? bump : null, theme, haze[b]));
            view.setCullFace(CullFace.NONE);
            view.setMouseTransparent(true);
            root.getChildren().add(view);
        }
        farTiles(root, cullable, pts, tex, dist, cols, rows,
                hazyMaterial(diffuse, null, theme, haze[2]));

        // Trois plans plutot que deux : c'est le decalage entre eux, quand la
        // camera se deplace, qui donne la profondeur. Le plus proche reste bas
        // pour ne pas ecraser le circuit, le plus lointain monte haut et se
        // noie presque dans le ciel.
        // Seul l'anneau le plus proche porte les creneaux du donjon : trois
        // rangs de dents empiles refaisaient exactement l'empilement de blocs
        // qu'on cherchait a supprimer. Un rempart devant, deux murs plats
        // derriere — c'est ce qui donne la profondeur sans redonner la dentelure.
        root.getChildren().add(mountains(980, 45, 135, 208, 0.20, false, true));
        // pas de sommets enneiges sur un mur ou une etagere
        boolean coiffe = !track.def.theme.indoor;
        root.getChildren().add(mountains(1550, 95, 250, 176, 0.34, coiffe, false));
        root.getChildren().add(mountains(2350, 165, 430, 148, 0.50, coiffe, false));
        return root;
    }

    /**
     * Bande lointaine, decoupee en paves rectangulaires de la grille.
     *
     * Chaque pave porte ses propres sommets — un maillage ne peut pas en
     * partager avec un autre — d'ou un leger recouvrement sur les bords. Le
     * cout en memoire reste inferieur a l'ancienne disposition, ou chacune des
     * trois bandes recopiait la grille entiere pour n'en utiliser qu'une part.
     */
    private void farTiles(Group root, java.util.List<javafx.scene.Node> cullable,
                          float[] pts, float[] tex, double[] dist,
                          int cols, int rows, PhongMaterial material) {
        for (int tr = 0; tr < FAR_TILES; tr++) {
            for (int tc = 0; tc < FAR_TILES; tc++) {
                int r0 = tr * (rows - 1) / FAR_TILES, r1 = (tr + 1) * (rows - 1) / FAR_TILES;
                int c0 = tc * (cols - 1) / FAR_TILES, c1 = (tc + 1) * (cols - 1) / FAR_TILES;
                if (r1 <= r0 || c1 <= c0) continue;

                int quads = 0;
                for (int r = r0; r < r1; r++) {
                    for (int c = c0; c < c1; c++) {
                        if (bandOf(dist, cols, r, c) == 2) quads++;
                    }
                }
                if (quads == 0) continue;

                int lc = c1 - c0 + 1, lr = r1 - r0 + 1;
                float[] lp = new float[lc * lr * 3];
                float[] lt = new float[lc * lr * 2];
                for (int r = 0; r < lr; r++) {
                    for (int c = 0; c < lc; c++) {
                        int g = (r0 + r) * cols + (c0 + c), l = r * lc + c;
                        lp[l * 3] = pts[g * 3];
                        lp[l * 3 + 1] = pts[g * 3 + 1];
                        lp[l * 3 + 2] = pts[g * 3 + 2];
                        lt[l * 2] = tex[g * 2];
                        lt[l * 2 + 1] = tex[g * 2 + 1];
                    }
                }

                int[] faces = new int[quads * 12];
                int k = 0;
                for (int r = r0; r < r1; r++) {
                    for (int c = c0; c < c1; c++) {
                        if (bandOf(dist, cols, r, c) != 2) continue;
                        int lr0 = r - r0, lc0 = c - c0;
                        int a = lr0 * lc + lc0;
                        int b = lr0 * lc + lc0 + 1;
                        int cc = (lr0 + 1) * lc + lc0 + 1;
                        int d = (lr0 + 1) * lc + lc0;
                        faces[k] = a; faces[k + 1] = a; faces[k + 2] = b; faces[k + 3] = b;
                        faces[k + 4] = cc; faces[k + 5] = cc;
                        faces[k + 6] = a; faces[k + 7] = a; faces[k + 8] = cc; faces[k + 9] = cc;
                        faces[k + 10] = d; faces[k + 11] = d;
                        k += 12;
                    }
                }

                TriangleMesh mesh = new TriangleMesh();
                mesh.getPoints().setAll(lp);
                mesh.getTexCoords().setAll(lt);
                mesh.getFaces().setAll(faces);
                int[] smooth = new int[faces.length / 6];
                Arrays.fill(smooth, 1);
                mesh.getFaceSmoothingGroups().setAll(smooth);

                MeshView view = new MeshView(mesh);
                view.setMaterial(material);
                view.setCullFace(CullFace.NONE);
                view.setMouseTransparent(true);
                root.getChildren().add(view);
                cullable.add(view);
            }
        }
    }

    private static int bandOf(double[] dist, int cols, int r, int c) {
        double dm = (dist[r * cols + c] + dist[r * cols + c + 1]
                + dist[(r + 1) * cols + c + 1] + dist[(r + 1) * cols + c]) / 4;
        return dm < NEAR_BAND ? 0 : (dm < MID_BAND ? 1 : 2);
    }

    /**
     * Materiau de sol auquel on ajoute une part de couleur du ciel : plus la
     * bande est loin, plus elle se noie dans l'horizon.
     */
    private static PhongMaterial hazyMaterial(Image diffuse, Image bump, Theme theme, double haze) {
        PhongMaterial m = new PhongMaterial(Color.gray(1 - haze * 0.40));
        m.setDiffuseMap(diffuse);
        if (bump != null) m.setBumpMap(bump);
        m.setSpecularColor(Color.rgb(12, 12, 12));
        m.setSpecularPower(6);
        if (haze > 0) {
            Color sky = theme.skyLow.deriveColor(0, 0.6, 1.05, 1);
            m.setSelfIlluminationMap(flat(sky.deriveColor(0, 1, haze * 0.85, 1)));
        }
        return m;
    }

    /** Petite image unie, utilisee comme voile lumineux constant. */
    static Image flat(Color c) {
        WritableImage img = new WritableImage(2, 2);
        int argb = 0xff000000
                | ((int) (Math.clamp(c.getRed(), 0, 1) * 255) << 16)
                | ((int) (Math.clamp(c.getGreen(), 0, 1) * 255) << 8)
                | (int) (Math.clamp(c.getBlue(), 0, 1) * 255);
        img.getPixelWriter().setPixels(0, 0, 2, 2, PixelFormat.getIntArgbInstance(),
                new int[]{argb, argb, argb, argb}, 0, 2);
        return img;
    }

    /**
     * Ce qui ferme l'horizon : un ruban vertical ferme, dont la crete suit un
     * bruit a aretes dehors et une horizontale dedans. Vue de si loin, la
     * silhouette suffit — et c'est bien pour cela qu'elle doit dire la bonne
     * chose.
     */
    /**
     * Silhouette de montagne, en fonction du relief brut et du theme.
     *
     * Un massif ne dit pas la meme chose partout : le desert dresse des mesas a
     * sommet plat, le volcan des cones aigus, le reste des cretes ordinaires.
     * C'est la seule chose qui distingue vraiment un horizon d'un autre a cette
     * distance — la couleur, elle, est deja mangee par la brume.
     *
     * <p>Les pieces ne passent plus par ici : voir {@link #indoorCrest}.
     */
    private double peakProfile(double ridge, double minH, double maxH) {
        return switch (track.def.theme) {
            // mesas : le relief monte par paliers et s'arrete net
            case DESERT -> MathUtil.lerp(minH, maxH, Math.round(Math.pow(ridge, 1.3) * 3) / 3.0);
            // cones : peu de sommets, mais francs
            case VOLCANO -> MathUtil.lerp(minH, maxH, Math.pow(ridge, 2.4));
            default -> MathUtil.lerp(minH, maxH, Math.pow(ridge, 1.6));
        };
    }

    /**
     * Ligne de faite d'une piece : horizontale.
     *
     * <p>Le meme bruit a aretes servait dedans et dehors, et les quatre pieces
     * heritaient donc d'un horizontal dentele. Dehors c'est un massif, et c'est
     * juste. Dedans il n'y a pas de montagnes : il y a une bande de table, un
     * comptoir, un mur, une plinthe — des objets dont la ligne de faite est
     * horizontale, et dont la hauteur ne varie pas d'un metre au suivant. Un
     * rang de blocs bruns inegaux au fond d'une salle de billard annule
     * l'echelle que toute la scene raconte, exactement comme le ferait une
     * tribune posee sur le tapis. On ne l'attenue donc pas, on le supprime.
     *
     * <p>Le donjon garde des creneaux, parce qu'un mur de pierre en a
     * legitimement — mais sur le seul anneau de premier plan, et
     * <b>reguliers</b>, deux pans de merlon pour deux de creneau, et peu
     * profonds : c'est l'irregularite qui faisait la mesa, pas le
     * decrochement. Sur l'anneau proche, un merlon fait ainsi soixante metres
     * de large pour neuf de haut, soit une douzaine de pixels : a seize metres
     * de haut, ou a un seul pan de large, il redevenait un peigne, et le fond
     * de la nef se lisait comme une rangee de tuyaux d'orgue plutot que comme
     * un mur — d'autant que la texture y pose deja ses propres refends
     * verticaux.
     *
     * <p>Les trois autres pieces prennent une hauteur unique, proche du haut de
     * l'echelle. Ce n'est pas gratuit : le relief lointain du sol monte a une
     * soixantaine de metres au-dessus du pied du ruban, et tout ce qui est
     * peint plus bas disparait derriere une bosse du tapis des que la camera
     * s'abaisse. A trois quarts d'echelle, le cadre eclaire de la table etait
     * mange dans une colonne sur cinq au depart ; a 0,88 il ne l'est plus. Les
     * trois anneaux montent du meme facteur, donc l'etagement qui les cache
     * l'un derriere l'autre est conserve.
     */
    private double indoorCrest(int i, double minH, double maxH, boolean creneaux) {
        if (creneaux && track.def.theme == Theme.DONJON) {
            return MathUtil.lerp(minH, maxH, (i / 2) % 2 == 0 ? 0.86 : 0.76);
        }
        return MathUtil.lerp(minH, maxH, 0.88);
    }

    /**
     * @param cap      coiffe les sommets d'une bande claire, la ou ils
     *                 depassent — neige pour la banquise, roche nue ailleurs
     * @param creneaux autorise la dentelure de rempart du donjon ; reserve a
     *                 l'anneau de premier plan, voir l'appelant
     */
    private javafx.scene.Node mountains(double radius, double minH, double maxH,
                                        int segments, double haze, boolean cap,
                                        boolean creneaux) {
        double cx = (track.minX + track.maxX) / 2;
        double cz = (track.minZ + track.maxZ) / 2;
        double base = track.minY - 14;

        double[] px = new double[segments];
        double[] pz = new double[segments];
        double[] ph = new double[segments];
        boolean piece = track.def.theme.indoor;
        for (int i = 0; i < segments; i++) {
            double a = i * 2 * Math.PI / segments;
            double r;
            if (piece) {
                // Dedans, le ruban est un vrai cylindre. Supprimer le relief ne
                // suffisait pas : l'ondulation de rayon de seize pour cent fait
                // varier la distance de l'anneau de mille metres a huit cents,
                // donc la hauteur apparente de la crete de deux degres pleins —
                // une cinquantaine de pixels. A elle seule, elle rendait
                // l'horizon ondulant alors que la crete etait deja plate.
                r = radius;
                ph[i] = indoorCrest(i, minH, maxH, creneaux);
            } else {
                double wobble =
                        noise.perlin(Math.cos(a) * 3 + radius * 0.01, Math.sin(a) * 3) * 0.16;
                r = radius * (1 + wobble);
                double ridge =
                        noise.ridged(Math.cos(a) * 5.5 + radius * 0.01, Math.sin(a) * 5.5, 4);
                ph[i] = peakProfile(ridge, minH, maxH);
            }
            px[i] = cx + Math.cos(a) * r;
            pz[i] = cz + Math.sin(a) * r;
        }
        // Altitude de reference du haut de la texture, commune a tout l'anneau.
        double hRef = 0;
        for (double v : ph) hRef = Math.max(hRef, v);

        // Chaque pan porte ses quatre sommets au lieu de partager ses colonnes
        // avec ses voisins. Cela ne change rien dehors, ou le relief monte et
        // descend d'un pan a l'autre — mais dedans, c'est ce qui permet de
        // tenir une <b>hauteur constante d'un bout a l'autre du pan</b>, donc
        // une marche franche au changement de niveau.
        //
        // Quantifier la hauteur ne suffisait pas : avec des colonnes partagees,
        // deux niveaux voisins restent joints par une arete oblique, et le mur
        // d'etagere reprend la silhouette d'une crete de montagne — des
        // pyramides dans la brume au fond d'un salon. Le silhouettage se joue
        // sur l'arete, pas sur la hauteur.
        //
        // Il ne reste qu'un seul changement de niveau dedans, celui des
        // creneaux du donjon, mais c'est justement celui qui doit rester franc.
        boolean blocs = piece;

        TriangleMesh mesh = new TriangleMesh();
        float[] pts = new float[segments * 4 * 3];
        float[] tex = new float[segments * 4 * 2];
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            double h0 = ph[i];
            double h1 = blocs ? ph[i] : ph[j];

            int p = i * 12;
            pts[p] = (float) px[i];
            pts[p + 1] = Meshes.jy(base);
            pts[p + 2] = (float) pz[i];
            pts[p + 3] = (float) px[i];
            pts[p + 4] = Meshes.jy(base + h0);
            pts[p + 5] = (float) pz[i];
            pts[p + 6] = (float) px[j];
            pts[p + 7] = Meshes.jy(base + h1);
            pts[p + 8] = (float) pz[j];
            pts[p + 9] = (float) px[j];
            pts[p + 10] = Meshes.jy(base);
            pts[p + 11] = (float) pz[j];

            // Dedans, le haut de la texture est ancre a une altitude commune a
            // tout l'anneau, et non a la hauteur du pan. Autrement la meme
            // fraction d'image retombe a une altitude differente d'un pan a
            // l'autre, et tout trait horizontal — la bande d'une table, la
            // plinthe d'un salon — se met en escalier au changement de niveau.
            // Un pan plus bas que la reference montre simplement moins de
            // texture : c'est un mur qu'on a arase, pas un mur qu'on a comprime.
            //
            // Dehors on garde l'etirement par sommet : c'est lui qui pose la
            // neige sur la crete de chaque montagne, grande ou petite.
            float v0 = piece ? (float) (1 - h0 / hRef) : 0f;
            float v1 = piece ? (float) (1 - h1 / hRef) : 0f;

            int t = i * 8;
            tex[t] = (float) (i * 0.25);
            tex[t + 1] = 1f;
            tex[t + 2] = (float) (i * 0.25);
            tex[t + 3] = v0;
            tex[t + 4] = (float) ((i + 1) * 0.25);
            tex[t + 5] = v1;
            tex[t + 6] = (float) ((i + 1) * 0.25);
            tex[t + 7] = 1f;
        }
        mesh.getPoints().setAll(pts);
        mesh.getTexCoords().setAll(tex);

        int[] faces = new int[segments * 2 * 6];
        for (int i = 0; i < segments; i++) {
            int b0 = i * 4, t0 = i * 4 + 1, t1 = i * 4 + 2, b1 = i * 4 + 3;
            int f = i * 12;
            faces[f] = b0; faces[f + 1] = b0;
            faces[f + 2] = t0; faces[f + 3] = t0;
            faces[f + 4] = t1; faces[f + 5] = t1;

            faces[f + 6] = b0; faces[f + 7] = b0;
            faces[f + 8] = t1; faces[f + 9] = t1;
            faces[f + 10] = b1; faces[f + 11] = b1;
        }
        mesh.getFaces().setAll(faces);
        int[] smooth = new int[segments * 2];
        // dedans, chaque pan est un bloc : un groupe de lissage par pan, sinon
        // l'ombrage arrondit l'angle qu'on vient justement de rendre franc
        for (int i = 0; i < segments; i++) {
            smooth[i * 2] = blocs ? (1 << (i % 30)) : 1;
            smooth[i * 2 + 1] = blocs ? (1 << (i % 30)) : 1;
        }
        mesh.getFaceSmoothingGroups().setAll(smooth);

        PhongMaterial m = new PhongMaterial(Color.gray(0.85));
        m.setDiffuseMap(Textures.mountain(track.def.theme));
        m.setSpecularColor(Color.TRANSPARENT);
        m.setSelfIlluminationMap(flat(track.def.theme.skyLow.deriveColor(0, 0.7, haze * 0.85, 1)));

        MeshView view = new MeshView(mesh);
        view.setMaterial(m);
        view.setCullFace(CullFace.NONE);
        view.setMouseTransparent(true);
        if (!cap) return view;
        Group pair = new Group(view, caps(radius, minH, maxH, segments, haze));
        pair.setMouseTransparent(true);
        return pair;
    }

    /**
     * Coiffe des sommets : une seconde bande, du col au sommet, la ou le relief
     * depasse. Blanche sur la banquise, roche claire ailleurs.
     *
     * <p>La bande couvre tout le tour, mais s'aplatit a zero sous le seuil : un
     * ruban degenere ne dessine rien, et cela evite d'avoir a construire un
     * maillage a trous.
     */
    private MeshView caps(double radius, double minH, double maxH, int segments, double haze) {
        double cx = (track.minX + track.maxX) / 2;
        double cz = (track.minZ + track.maxZ) / 2;
        double base = track.minY - 14;
        double seuil = MathUtil.lerp(minH, maxH, 0.55);

        TriangleMesh mesh = new TriangleMesh();
        float[] pts = new float[segments * 2 * 3];
        float[] tex = new float[segments * 2 * 2];
        for (int i = 0; i < segments; i++) {
            double a = i * 2 * Math.PI / segments;
            double wobble = noise.perlin(Math.cos(a) * 3 + radius * 0.01, Math.sin(a) * 3) * 0.16;
            double r = radius * (1 + wobble);
            double ridge = noise.ridged(Math.cos(a) * 5.5 + radius * 0.01, Math.sin(a) * 5.5, 4);
            double peak = peakProfile(ridge, minH, maxH);
            double col = peak > seuil ? MathUtil.lerp(peak, seuil, 0.35) : peak;

            double x = cx + Math.cos(a) * r * 0.999;
            double z = cz + Math.sin(a) * r * 0.999;
            int p = i * 6;
            pts[p] = (float) x;
            pts[p + 1] = Meshes.jy(base + col);
            pts[p + 2] = (float) z;
            pts[p + 3] = (float) x;
            pts[p + 4] = Meshes.jy(base + peak);
            pts[p + 5] = (float) z;
            int t = i * 4;
            tex[t] = 0f;
            tex[t + 1] = 1f;
            tex[t + 2] = 0f;
            tex[t + 3] = 0f;
        }
        mesh.getPoints().setAll(pts);
        mesh.getTexCoords().setAll(tex);
        mesh.getFaces().setAll(ringFaces(segments));
        int[] smooth = new int[segments * 2];
        Arrays.fill(smooth, 1);
        mesh.getFaceSmoothingGroups().setAll(smooth);

        // Blanche sur la banquise, la foret et la prairie ; roche claire au
        // desert. Sur le volcan, une coiffe claire donnait des sommets enneiges
        // au-dessus d'une coulee — c'est le deuxieme grief du controle visuel
        // apres la bouillie de valeurs du semis. Ce qui coiffe un cone
        // volcanique n'est pas de la neige mais une couronne incandescente, et
        // elle reste volontairement <b>sombre et saturee</b> : le voile
        // lumineux ci-dessous ajoute deja de la clarte, et une braise qui vire
        // au blanc n'est plus une braise.
        Color teinte = switch (track.def.theme) {
            case SNOW, FOREST, GRASS -> Color.web("#f4fbff");
            case VOLCANO -> Color.web("#a82f16");
            default -> Color.web("#d8cfc2");
        };
        PhongMaterial m = new PhongMaterial(teinte);
        m.setSpecularColor(Color.TRANSPARENT);
        m.setSelfIlluminationMap(flat(track.def.theme.skyLow
                .interpolate(teinte, 1 - haze * 0.75)));

        MeshView view = new MeshView(mesh);
        view.setMaterial(m);
        view.setCullFace(CullFace.NONE);
        view.setMouseTransparent(true);
        return view;
    }

    /** Faces d'un ruban ferme de {@code segments} colonnes. */
    private static int[] ringFaces(int segments) {
        int[] faces = new int[segments * 2 * 6];
        for (int i = 0; i < segments; i++) {
            int j = (i + 1) % segments;
            int b0 = i * 2, t0 = i * 2 + 1, b1 = j * 2, t1 = j * 2 + 1;
            int f = i * 12;
            faces[f] = b0; faces[f + 1] = b0;
            faces[f + 2] = t0; faces[f + 3] = t0;
            faces[f + 4] = t1; faces[f + 5] = t1;
            faces[f + 6] = b0; faces[f + 7] = b0;
            faces[f + 8] = t1; faces[f + 9] = t1;
            faces[f + 10] = b1; faces[f + 11] = b1;
        }
        return faces;
    }
}
