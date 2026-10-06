package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Scale;
import org.tuxkart.math.Vec3;

/**
 * Fabrique de maillages. Rappel de convention : dans le monde du jeu Y monte,
 * dans JavaFX Y descend — d'ou le {@link #jy(double)} systematique.
 */
public final class Meshes {

    private Meshes() {
    }

    /** Conversion altitude monde -> Y JavaFX. */
    public static float jy(double worldY) {
        return (float) (-worldY);
    }

    /**
     * Maillages unitaires partages.
     *
     * Chaque {@code new Sphere(...)} ou {@code new Cylinder(...)} de JavaFX
     * construit son propre maillage. Avec plusieurs milliers d'arbres, cela
     * represente des centaines de mega-octets pour des formes identiques. On
     * fabrique donc une sphere et un cylindre de taille 1 par nombre de
     * subdivisions, et chaque objet n'est qu'une vue mise a l'echelle.
     */
    private static final java.util.Map<String, TriangleMesh> SHARED = new java.util.HashMap<>();

    static TriangleMesh sharedBox() {
        return SHARED.computeIfAbsent("box", k -> {
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            float[] pts = new float[24 * 3];
            float[] nrm = new float[24 * 3];
            float[] tex = new float[24 * 2];
            int[] faces = new int[12 * 9];
            // six faces independantes : chacune a sa propre normale
            float[][] axes = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
            int v = 0, f = 0;
            for (float[] n : axes) {
                // deux vecteurs orthogonaux a la normale
                float[] u = Math.abs(n[1]) > 0.5f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
                float[] w = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2],
                        n[0] * u[1] - n[1] * u[0]};
                int base = v;
                for (int c = 0; c < 4; c++) {
                    float su = (c == 0 || c == 3) ? -0.5f : 0.5f;
                    float sw = (c < 2) ? -0.5f : 0.5f;
                    pts[v * 3] = n[0] * 0.5f + u[0] * su + w[0] * sw;
                    pts[v * 3 + 1] = n[1] * 0.5f + u[1] * su + w[1] * sw;
                    pts[v * 3 + 2] = n[2] * 0.5f + u[2] * su + w[2] * sw;
                    nrm[v * 3] = n[0];
                    nrm[v * 3 + 1] = n[1];
                    nrm[v * 3 + 2] = n[2];
                    tex[v * 2] = (c == 0 || c == 3) ? 0 : 1;
                    tex[v * 2 + 1] = (c < 2) ? 0 : 1;
                    v++;
                }
                f = tri(faces, f, base, base + 1, base + 2);
                f = tri(faces, f, base, base + 2, base + 3);
            }
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    static TriangleMesh sharedSphere(int divisions) {
        return SHARED.computeIfAbsent("sph" + divisions, k -> {
            int lon = Math.max(4, divisions);
            int lat = Math.max(2, divisions / 2);
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            int count = (lat + 1) * (lon + 1);
            float[] pts = new float[count * 3];
            float[] nrm = new float[count * 3];
            float[] tex = new float[count * 2];
            for (int i = 0; i <= lat; i++) {
                double theta = Math.PI * i / lat;
                double sy = Math.cos(theta), r = Math.sin(theta);
                for (int j = 0; j <= lon; j++) {
                    double phi = 2 * Math.PI * j / lon;
                    int v = i * (lon + 1) + j;
                    float x = (float) (r * Math.cos(phi));
                    float y = (float) sy;
                    float z = (float) (r * Math.sin(phi));
                    pts[v * 3] = x;
                    pts[v * 3 + 1] = y;
                    pts[v * 3 + 2] = z;
                    nrm[v * 3] = x;
                    nrm[v * 3 + 1] = y;
                    nrm[v * 3 + 2] = z;
                    tex[v * 2] = (float) j / lon;
                    tex[v * 2 + 1] = (float) i / lat;
                }
            }
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);

            int[] faces = new int[lat * lon * 2 * 9];
            int f = 0;
            for (int i = 0; i < lat; i++) {
                for (int j = 0; j < lon; j++) {
                    int a = i * (lon + 1) + j;
                    int b = a + 1;
                    int c = (i + 1) * (lon + 1) + j + 1;
                    int d = (i + 1) * (lon + 1) + j;
                    f = tri(faces, f, a, b, c);
                    f = tri(faces, f, a, c, d);
                }
            }
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    static TriangleMesh sharedCylinder(int divisions) {
        return SHARED.computeIfAbsent("cyl" + divisions, k -> {
            int n = Math.max(4, divisions);
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            // 2n sur le pourtour, 2n de plus pour les capuchons, 2 centres.
            // Les capuchons ont leurs propres sommets parce qu'ils ont leurs
            // propres normales : reutiliser celles du pourtour, radiales,
            // eteindrait les disques du bord vers le centre.
            int count = 4 * n + 2;
            float[] pts = new float[count * 3];
            float[] nrm = new float[count * 3];
            float[] tex = new float[count * 2];
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                float x = (float) Math.cos(a), z = (float) Math.sin(a);
                for (int half = 0; half < 2; half++) {
                    int v = i * 2 + half;
                    pts[v * 3] = x;
                    pts[v * 3 + 1] = half == 0 ? -0.5f : 0.5f;
                    pts[v * 3 + 2] = z;
                    nrm[v * 3] = x;
                    nrm[v * 3 + 1] = 0;
                    nrm[v * 3 + 2] = z;
                    tex[v * 2] = (float) i / n;
                    tex[v * 2 + 1] = half;

                    // meme point, normale du capuchon, texture en disque
                    int c = 2 * n + i * 2 + half;
                    pts[c * 3] = x;
                    pts[c * 3 + 1] = pts[v * 3 + 1];
                    pts[c * 3 + 2] = z;
                    nrm[c * 3 + 1] = half == 0 ? -1 : 1;
                    tex[c * 2] = (float) (0.5 + 0.5 * Math.cos(a));
                    tex[c * 2 + 1] = (float) (0.5 + 0.5 * Math.sin(a));
                }
            }
            // rappel : Y descend en repere JavaFX, le capuchon « du haut » est
            // donc celui d'ordonnee negative
            int top = 4 * n, bottom = 4 * n + 1;
            pts[top * 3 + 1] = -0.5f;
            nrm[top * 3 + 1] = -1;
            tex[top * 2] = 0.5f;
            tex[top * 2 + 1] = 0.5f;
            pts[bottom * 3 + 1] = 0.5f;
            nrm[bottom * 3 + 1] = 1;
            tex[bottom * 2] = 0.5f;
            tex[bottom * 2 + 1] = 0.5f;
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);

            int[] faces = new int[n * 4 * 9];
            int f = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int a = i * 2, b = i * 2 + 1, c = j * 2 + 1, d = j * 2;
                f = tri(faces, f, a, b, c);
                f = tri(faces, f, a, c, d);
                // les capuchons s'enroulent dans l'autre sens que le pourtour,
                // sinon ils sont elimines comme faces arriere et l'on voit
                // l'interieur du tube
                int ta = 2 * n + i * 2, td = 2 * n + j * 2;
                int bb = ta + 1, bc = td + 1;
                f = tri(faces, f, top, ta, td);
                f = tri(faces, f, bottom, bc, bb);
            }
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    /**
     * Disque unitaire horizontal, face tournee vers le ciel.
     *
     * Les ombres portees du decor etaient des cylindres ecrases : trente-deux
     * triangles, dont un fond et un pourtour que personne ne verra jamais, la
     * ou une seule face en demande huit.
     */
    static TriangleMesh sharedDisc(int sides) {
        return SHARED.computeIfAbsent("disc" + sides, k -> {
            int n = Math.max(3, sides);
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            // 0 = centre, 1.. = couronne
            float[] pts = new float[(n + 1) * 3];
            float[] nrm = new float[(n + 1) * 3];
            float[] tex = new float[(n + 1) * 2];
            // rappel : Y descend en repere JavaFX, le ciel est donc en -Y
            nrm[1] = -1;
            tex[0] = 0.5f;
            tex[1] = 0.5f;
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int v = i + 1;
                pts[v * 3] = (float) Math.cos(a);
                pts[v * 3 + 2] = (float) Math.sin(a);
                nrm[v * 3 + 1] = -1;
                tex[v * 2] = (float) (0.5 + 0.5 * Math.cos(a));
                tex[v * 2 + 1] = (float) (0.5 + 0.5 * Math.sin(a));
            }
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);

            int[] faces = new int[n * 9];
            int f = 0;
            for (int i = 0; i < n; i++) {
                f = tri(faces, f, 0, 1 + i, 1 + (i + 1) % n);
            }
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    /** Sphere partagee, mise a l'echelle. */
    public static MeshView sphere(double radius, int divisions, PhongMaterial material) {
        MeshView v = new MeshView(sharedSphere(divisions));
        v.setMaterial(material);
        v.setCullFace(CullFace.BACK);
        v.setScaleX(radius);
        v.setScaleY(radius);
        v.setScaleZ(radius);
        return v;
    }

    /** Cylindre partage, axe Y, centre sur l'origine. */
    public static MeshView cylinder(double radius, double height, int divisions,
                                    PhongMaterial material) {
        MeshView v = new MeshView(sharedCylinder(divisions));
        v.setMaterial(material);
        v.setCullFace(CullFace.BACK);
        v.setScaleX(radius);
        v.setScaleZ(radius);
        v.setScaleY(height);
        return v;
    }

    public static PhongMaterial material(Image diffuse) {
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(diffuse);
        m.setSpecularColor(Color.rgb(30, 30, 30));
        m.setSpecularPower(12);
        return m;
    }

    /**
     * Materiau texture avec carte de normales : c'est elle qui donne le relief
     * du bitume, des vibreurs et des murets sans polygone supplementaire.
     */
    public static PhongMaterial material(Image diffuse, Image bump, Color specular, double power) {
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(diffuse);
        if (bump != null) m.setBumpMap(bump);
        m.setSpecularColor(specular);
        m.setSpecularPower(power);
        return m;
    }

    public static PhongMaterial material(Color color) {
        PhongMaterial m = new PhongMaterial(color);
        m.setSpecularColor(color.deriveColor(0, 1, 1.4, 1));
        m.setSpecularPower(18);
        return m;
    }

    /**
     * Distance a laquelle on eleve l'axe de reference pour orienter un ruban.
     *
     * @see #retourne
     */
    private static final double REGARD_EN_L_AIR = 50;

    /**
     * Ce ruban est-il enroule a l'envers, c'est-a-dire eclaire par sa mauvaise
     * face ?
     *
     * <p>JavaFX deduit la normale d'un triangle de son enroulement quand le
     * maillage n'en porte pas — et l'enroulement d'un ruban depend du sens dans
     * lequel on l'a pose. Un ruban pose du bord droit vers le bord gauche sort
     * exactement a l'envers du meme ruban pose dans l'autre sens : la normale
     * vaut {@code avant x (exterieur - interieur)}, elle change donc de signe
     * avec le second facteur.
     *
     * <p>Mesure faite sur la Banquise, maillage par maillage : la chaussee
     * ({@code ribbon(roadL, roadR)}) avait <b>100 % de ses normales tournees
     * vers le bas</b>, ses bas-cotes de gauche vers le haut et ceux de droite
     * vers le bas, son muret de gauche tourne vers l'exterieur du circuit. La
     * chaussee etait donc eclairee par la lumiere d'appoint posee <b>sous</b>
     * le circuit et pas du tout par le soleil : 137 de clarte avec l'appoint,
     * 71 sans lui. C'est aussi pourquoi la piste et le terrain, eclaires en
     * opposition, voyaient leurs clartes se croiser trois fois par tour.
     *
     * <p>On choisit donc l'enroulement au lieu de le subir. Le retourner ne
     * coute <b>rien</b> — ni sommet, ni normale explicite, ni triangle — et ne
     * change pas ce qu'on voit : les rubans de piste sont tous dessines en
     * {@link CullFace#NONE}, leurs deux faces sont la.
     *
     * <p>La regle tient en une phrase : <b>la face eclairee regarde l'axe de la
     * piste, pose cinquante metres en l'air</b>. Un ruban horizontal regarde
     * alors le ciel, une paroi verticale regarde la piste — et c'est bien ce
     * qu'on veut du muret, dont on ne voit jamais que la face interieure. Le
     * choix est fait une fois pour tout le ruban, a la majorite de ses rangees,
     * pour qu'un groupe de vibreurs ne puisse pas s'enrouler autrement que le
     * bas-cote avec lequel il partage ses sommets.
     *
     * @param axe polyligne de reference, ou {@code null} pour la seule regle
     *            « la normale monte »
     */
    static boolean retourne(Vec3[] inner, Vec3[] outer, Vec3[] axe) {
        int n = inner.length;
        double score = 0;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            Vec3 mid = inner[i].add(outer[i]).mul(0.5);
            Vec3 avant = inner[j].add(outer[j]).mul(0.5).sub(mid);
            Vec3 travers = outer[i].sub(inner[i]);
            Vec3 nrm = avant.cross(travers);
            if (nrm.length() < 1e-9) continue;
            Vec3 cible = (axe == null ? mid : axe[i]).add(0, REGARD_EN_L_AIR, 0).sub(mid);
            if (cible.length() < 1e-9) continue;
            score += nrm.normalize().dot(cible.normalize());
        }
        return score < 0;
    }

    /**
     * Ruban ferme entre deux polylignes de meme longueur.
     *
     * @param inner    bord "gauche" (u = 0)
     * @param outer    bord "droit" (u = 1)
     * @param arc      abscisse curviligne de chaque rangee, pour l'echelle de texture
     * @param metersPerTile longueur de piste couverte par une repetition de texture
     */
    public static MeshView ribbon(Vec3[] inner, Vec3[] outer, double[] arc,
                                  double metersPerTile, Image texture) {
        return ribbon(inner, outer, arc, metersPerTile, material(texture), 1);
    }

    /**
     * @param uRepeat nombre de repetitions de la texture en travers du ruban
     */
    public static MeshView ribbon(Vec3[] inner, Vec3[] outer, double[] arc,
                                  double metersPerTile, PhongMaterial material, double uRepeat) {
        return ribbon(inner, outer, arc, metersPerTile, material, uRepeat, null, null);
    }

    /**
     * Meme ruban, mais interrompu la ou {@code saute} est vrai.
     *
     * <p>Sert aux ouvrages traverses : un ouvrage porte ses propres murs, le
     * muret de la piste doit donc s'arreter a l'entree. Les sommets sont tous
     * poses quand meme — seules les faces manquent — parce que l'echelle de
     * texture se lit dans l'abscisse curviligne : recouper les tableaux
     * decalerait le motif de chaque troncon restant.
     *
     * @param saute un drapeau par rangee, ou {@code null} pour un ruban continu
     * @param axe   axe de reference vers lequel la face eclairee doit regarder
     *              ({@link #retourne})
     */
    public static MeshView ribbon(Vec3[] inner, Vec3[] outer, double[] arc,
                                  double metersPerTile, PhongMaterial material, double uRepeat,
                                  boolean[] saute, Vec3[] axe) {
        int n = inner.length;
        TriangleMesh mesh = new TriangleMesh();

        float[] pts = new float[n * 2 * 3];
        float[] tex = new float[n * 2 * 2];
        for (int i = 0; i < n; i++) {
            Vec3 a = inner[i], b = outer[i];
            int p = i * 6;
            pts[p] = (float) a.x;
            pts[p + 1] = jy(a.y);
            pts[p + 2] = (float) a.z;
            pts[p + 3] = (float) b.x;
            pts[p + 4] = jy(b.y);
            pts[p + 5] = (float) b.z;

            float v = (float) (arc[i] / metersPerTile);
            int t = i * 4;
            tex[t] = 0f;
            tex[t + 1] = v;
            tex[t + 2] = (float) uRepeat;
            tex[t + 3] = v;
        }
        mesh.getPoints().setAll(pts);
        mesh.getTexCoords().setAll(tex);

        int segments = n;
        if (saute != null) {
            segments = 0;
            for (int i = 0; i < n; i++) {
                if (!saute[i]) segments++;
            }
        }
        boolean envers = retourne(inner, outer, axe);
        int[] faces = new int[segments * 2 * 6];
        int f = 0;
        for (int i = 0; i < n; i++) {
            if (saute != null && saute[i]) continue;
            int j = (i + 1) % n;
            int ai = i * 2, bi = i * 2 + 1, aj = j * 2, bj = j * 2 + 1;
            int p1 = envers ? aj : bi, q1 = envers ? bi : aj;
            int p2 = envers ? aj : bj, q2 = envers ? bj : aj;
            faces[f] = ai;      faces[f + 1] = ai;
            faces[f + 2] = p1;  faces[f + 3] = p1;
            faces[f + 4] = q1;  faces[f + 5] = q1;

            faces[f + 6] = bi;  faces[f + 7] = bi;
            faces[f + 8] = p2;  faces[f + 9] = p2;
            faces[f + 10] = q2; faces[f + 11] = q2;
            f += 12;
        }
        mesh.getFaces().setAll(faces);
        // toutes les faces dans le meme groupe de lissage : le ruban est lisse
        int[] smooth = new int[segments * 2];
        java.util.Arrays.fill(smooth, 1);
        mesh.getFaceSmoothingGroups().setAll(smooth);

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        return view;
    }

    /**
     * Meme ruban, mais chaque segment est verse dans l'un ou l'autre de deux
     * maillages selon un masque. Sert a ne poser des vibreurs qu'en virage :
     * les deux maillages partagent leurs sommets, il n'y a donc aucun raccord.
     *
     * @return {maillage pour masque faux, maillage pour masque vrai}
     */
    public static MeshView[] ribbonSplit(Vec3[] inner, Vec3[] outer, double[] arc,
                                         double metersPerTile, double uRepeat,
                                         PhongMaterial matFalse, PhongMaterial matTrue,
                                         boolean[] mask, Vec3[] axe) {
        int[] groups = new int[mask.length];
        for (int i = 0; i < mask.length; i++) groups[i] = mask[i] ? 1 : 0;
        return ribbonGroups(inner, outer, arc, metersPerTile, uRepeat,
                new PhongMaterial[]{matFalse, matTrue}, groups, null, axe);
    }

    /**
     * Ruban dont chaque segment est verse dans l'un de plusieurs maillages,
     * selon un indice de groupe. Sert a alterner les revetements le long de la
     * piste — vibreur rouge ici, bleu la, degagement vert ailleurs — sans le
     * moindre raccord, puisque tous les maillages partagent leurs sommets.
     */
    public static MeshView[] ribbonGroups(Vec3[] inner, Vec3[] outer, double[] arc,
                                          double metersPerTile, double uRepeat,
                                          PhongMaterial[] materials, int[] groupOf, Vec3[] axe) {
        return ribbonGroups(inner, outer, arc, metersPerTile, uRepeat, materials, groupOf,
                null, axe);
    }

    /**
     * Le meme ruban, interrompu la ou {@code saute} est vrai.
     *
     * <p>Sert aux ouvrages traverses. Le sol d'une grange y a longtemps ete
     * verse dans les rubans de la piste comme un revetement de plus, un groupe
     * a cote des vibreurs : c'etait faux sur deux points. L'echelle de texture
     * d'un ruban vaut pour toute sa largeur, si bien que la bordure posait sa
     * terre battue en tuiles de deux metres quarante quand le bas-cote, large
     * de cinq metres, la posait en tuiles de six — une couture de un metre
     * soixante-dix le long de la chaussee. Et un ouvrage sans sol propre — six
     * sur huit — ne basculait rien du tout : ni la bordure ni le bas-cote,
     * qui pouvait donc ouvrir un bac a gravier a l'interieur du temple.
     *
     * <p>Les deux rubans s'interrompent donc, et un ruban unique les remplace
     * sous l'emprise, de la ligne de vibreur au pied du mur.
     *
     * @param saute un drapeau par rangee, ou {@code null} pour un ruban continu
     * @param axe   axe de reference vers lequel la face eclairee doit regarder
     *              ({@link #retourne})
     */
    public static MeshView[] ribbonGroups(Vec3[] inner, Vec3[] outer, double[] arc,
                                          double metersPerTile, double uRepeat,
                                          PhongMaterial[] materials, int[] groupOf,
                                          boolean[] saute, Vec3[] axe) {
        int n = inner.length;
        float[] pts = new float[n * 2 * 3];
        float[] tex = new float[n * 2 * 2];
        for (int i = 0; i < n; i++) {
            Vec3 a = inner[i], b = outer[i];
            int p = i * 6;
            pts[p] = (float) a.x;
            pts[p + 1] = jy(a.y);
            pts[p + 2] = (float) a.z;
            pts[p + 3] = (float) b.x;
            pts[p + 4] = jy(b.y);
            pts[p + 5] = (float) b.z;

            float v = (float) (arc[i] / metersPerTile);
            int t = i * 4;
            tex[t] = 0f;
            tex[t + 1] = v;
            tex[t + 2] = (float) uRepeat;
            tex[t + 3] = v;
        }

        int groups = materials.length;
        int[] counts = new int[groups];
        for (int i = 0; i < n; i++) {
            if (saute != null && saute[i]) continue;
            counts[Math.floorMod(groupOf[i], groups)]++;
        }
        int[][] faces = new int[groups][];
        int[] cursor = new int[groups];
        for (int g = 0; g < groups; g++) faces[g] = new int[counts[g] * 12];

        // le sens est choisi une fois pour tout le ruban : les groupes
        // partagent leurs sommets, deux enroulements les separeraient
        boolean envers = retourne(inner, outer, axe);
        for (int i = 0; i < n; i++) {
            if (saute != null && saute[i]) continue;
            int j = (i + 1) % n;
            int ai = i * 2, bi = i * 2 + 1, aj = j * 2, bj = j * 2 + 1;
            int p1 = envers ? aj : bi, q1 = envers ? bi : aj;
            int p2 = envers ? aj : bj, q2 = envers ? bj : aj;
            int g = Math.floorMod(groupOf[i], groups);
            int[] f = faces[g];
            int k = cursor[g];
            f[k] = ai; f[k + 1] = ai; f[k + 2] = p1; f[k + 3] = p1; f[k + 4] = q1; f[k + 5] = q1;
            f[k + 6] = bi; f[k + 7] = bi; f[k + 8] = p2; f[k + 9] = p2; f[k + 10] = q2; f[k + 11] = q2;
            cursor[g] = k + 12;
        }

        MeshView[] out = new MeshView[groups];
        for (int g = 0; g < groups; g++) {
            TriangleMesh mesh = new TriangleMesh();
            mesh.getPoints().setAll(pts);
            mesh.getTexCoords().setAll(tex);
            mesh.getFaces().setAll(faces[g]);
            if (faces[g].length > 0) {
                int[] smooth = new int[faces[g].length / 6];
                java.util.Arrays.fill(smooth, 1);
                mesh.getFaceSmoothingGroups().setAll(smooth);
            }
            MeshView view = new MeshView(mesh);
            view.setMaterial(materials[g]);
            view.setCullFace(CullFace.NONE);
            out[g] = view;
        }
        return out;
    }

    /**
     * Cone plein, base a l'altitude 0 et pointe vers le haut du monde. JavaFX
     * ne fournit pas cette primitive, or elle est partout dans un jeu de kart :
     * sapins, cactus, ogives, flammes de turbo.
     *
     * <p>L'enveloppe dans un groupe est indispensable : JavaFX applique les
     * proprietes d'echelle d'un noeud APRES sa liste de transformations. Sans
     * elle, une rotation ajoutee par l'appelant serait appliquee au cone
     * unitaire puis deformee par une echelle non uniforme — un bec de manchot
     * finirait pointe en l'air.
     *
     * <p>La mise a l'echelle passe par une {@link Scale} et non par
     * {@code setScaleY} : {@code setScale*} prend pour pivot le centre des
     * bornes du noeud, or le cone unitaire n'est pas centre sur son origine —
     * sa base y est, sa pointe est un metre au-dessus. Reduire sa hauteur a
     * {@code h} le decollait donc de {@code (1 - h) / 2} de son point
     * d'ancrage : le bec de Tux, haut de 0,30 m, flottait a trente-cinq
     * centimetres devant sa tete, et toutes les cornes, oreilles pointues,
     * huppes et derives du plateau avec lui.
     */
    public static Group cone(double radius, double height, int sides, PhongMaterial material) {
        MeshView v = new MeshView(sharedCone(sides));
        v.setMaterial(material);
        v.setCullFace(CullFace.NONE);
        v.getTransforms().add(new Scale(radius, height, radius));
        return new Group(v);
    }

    /** Cone unitaire partage : pointe en haut, base de rayon 1 a l'altitude 0. */
    static TriangleMesh sharedCone(int sides) {
        return SHARED.computeIfAbsent("cone" + sides, k -> {
            int n = Math.max(3, sides);
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            // 0 = pointe, 1 = centre de la base, 2.. = couronne
            int count = n + 2;
            float[] pts = new float[count * 3];
            float[] nrm = new float[count * 3];
            float[] tex = new float[count * 2];
            pts[1] = jy(1);
            nrm[1] = -1;
            tex[0] = 0.5f;
            nrm[3 + 1] = 1;
            tex[2] = 0.5f;
            tex[3] = 1f;
            // la pente d'un cone de rayon 1 et de hauteur 1 donne une normale
            // laterale a 45 degres
            double s = Math.sqrt(0.5);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                int v = i + 2;
                float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
                pts[v * 3] = cx;
                pts[v * 3 + 1] = 0;
                pts[v * 3 + 2] = cz;
                nrm[v * 3] = (float) (cx * s);
                nrm[v * 3 + 1] = (float) -s;
                nrm[v * 3 + 2] = (float) (cz * s);
                tex[v * 2] = (float) i / n;
                tex[v * 2 + 1] = 1f;
            }
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);

            int[] faces = new int[n * 2 * 9];
            int f = 0;
            for (int i = 0; i < n; i++) {
                int a = 2 + i, b = 2 + (i + 1) % n;
                f = tri(faces, f, 0, a, b);
                f = tri(faces, f, 1, b, a);
            }
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    /**
     * Tronc de cone unitaire partage : base de rayon 1 a l'altitude 0, dessus
     * de rayon {@code topRatio} a l'altitude 1, capuchon superieur compris.
     *
     * <p>Un cone simple ne suffisait pas au volcan du Petit Volcan : un cone
     * de stratovolcan a un sommet <b>tronque</b> — le cratere — et un flanc
     * <b>concave</b>, et le seul moyen d'obtenir ce profil avec des formes
     * partagees est d'empiler deux ou trois troncs de pente croissante. Un
     * empilement de cylindres, lui, donnait des terrasses de riziere.
     *
     * <p>Le capuchon du bas est omis : le pied est toujours enterre, et le
     * decor est dessine en {@code CullFace.BACK}, donc une face qu'on ne voit
     * jamais de l'exterieur ne coute que des triangles.
     */
    static TriangleMesh sharedFrustum(int sides, double topRatio) {
        int n = Math.max(3, sides);
        double k = Math.clamp(topRatio, 0.01, 0.99);
        return SHARED.computeIfAbsent("frustum" + n + "-" + Math.round(k * 1000), key -> {
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            // 0 = centre du dessus, puis n du bas, puis n du haut
            int count = 1 + 2 * n;
            float[] pts = new float[count * 3];
            float[] nrm = new float[count * 3];
            float[] tex = new float[count * 2];

            pts[1] = jy(1);
            nrm[1] = -1;
            tex[0] = 0.5f;
            tex[1] = 0.5f;

            // la generatrice va de (1, 0) a (k, 1) : sa normale dans le plan
            // (rayon, altitude) vaut (1, 1 - k), une fois normalisee
            double inv = 1 / Math.hypot(1, 1 - k);
            for (int i = 0; i < n; i++) {
                double a = 2 * Math.PI * i / n;
                float cx = (float) Math.cos(a), cz = (float) Math.sin(a);
                for (int haut = 0; haut < 2; haut++) {
                    int v = 1 + haut * n + i;
                    double r = haut == 0 ? 1 : k;
                    pts[v * 3] = (float) (cx * r);
                    pts[v * 3 + 1] = haut == 0 ? 0 : jy(1);
                    pts[v * 3 + 2] = (float) (cz * r);
                    nrm[v * 3] = (float) (cx * inv);
                    nrm[v * 3 + 1] = (float) (-(1 - k) * inv);
                    nrm[v * 3 + 2] = (float) (cz * inv);
                    tex[v * 2] = (float) i / n;
                    tex[v * 2 + 1] = haut;
                }
            }
            mesh.getPoints().setAll(pts);
            mesh.getNormals().setAll(nrm);
            mesh.getTexCoords().setAll(tex);

            int[] faces = new int[n * 3 * 9];
            int f = 0;
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                int b0 = 1 + i, b1 = 1 + j, t0 = 1 + n + i, t1 = 1 + n + j;
                f = tri(faces, f, t0, b0, b1);
                f = tri(faces, f, t0, b1, t1);
                f = tri(faces, f, 0, t0, t1);
            }
            mesh.getFaces().setAll(faces);
            return mesh;
        });
    }

    /**
     * Carrosserie obtenue en tendant une peau entre des sections transversales.
     * Chaque section est une super-ellipse (un rectangle aux angles arrondis),
     * ce qui donne un galbe impossible a obtenir avec des boites.
     *
     * Les normales sont fournies explicitement : c'est le seul moyen d'etre sur
     * de l'eclairage sans dependre du sens d'enroulement des triangles.
     *
     * @param sections {z, demi-largeur, bas, haut, exposant} du nez vers l'arriere
     * @param ring     nombre de points par section
     */
    public static MeshView hull(double[][] sections, int ring, PhongMaterial material) {
        int s = sections.length;
        int vertices = s * ring + 2;
        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);

        float[] pts = new float[vertices * 3];
        float[] nrm = new float[vertices * 3];
        float[] tex = new float[vertices * 2];

        for (int i = 0; i < s; i++) {
            double z = sections[i][0];
            double w = sections[i][1];
            double cy = (sections[i][2] + sections[i][3]) / 2;
            double hh = (sections[i][3] - sections[i][2]) / 2;
            double e = sections[i][4];
            for (int k = 0; k < ring; k++) {
                double th = k * 2 * Math.PI / ring;
                double ct = Math.cos(th), st = Math.sin(th);
                double px = w * Math.signum(ct) * Math.pow(Math.abs(ct), 2.0 / e);
                double py = cy + hh * Math.signum(st) * Math.pow(Math.abs(st), 2.0 / e);

                double nx = ct / Math.max(1e-3, w);
                double ny = st / Math.max(1e-3, hh);
                double len = Math.hypot(nx, ny);

                int v = i * ring + k;
                pts[v * 3] = (float) px;
                pts[v * 3 + 1] = jy(py);
                pts[v * 3 + 2] = (float) z;
                nrm[v * 3] = (float) (nx / len);
                nrm[v * 3 + 1] = (float) (-ny / len);
                nrm[v * 3 + 2] = 0f;
                tex[v * 2] = (float) k / ring;
                tex[v * 2 + 1] = (float) i / (s - 1);
            }
        }

        // deux sommets de fermeture, au centre de la premiere et de la derniere section
        int front = s * ring, back = s * ring + 1;
        addCap(pts, nrm, tex, front, sections[0], 1);
        addCap(pts, nrm, tex, back, sections[s - 1], -1);

        mesh.getPoints().setAll(pts);
        mesh.getNormals().setAll(nrm);
        mesh.getTexCoords().setAll(tex);

        int quadFaces = (s - 1) * ring * 2;
        int capFaces = ring * 2;
        int[] faces = new int[(quadFaces + capFaces) * 9];
        int f = 0;
        for (int i = 0; i < s - 1; i++) {
            for (int k = 0; k < ring; k++) {
                int k2 = (k + 1) % ring;
                int a = i * ring + k, b = i * ring + k2;
                int c = (i + 1) * ring + k2, d = (i + 1) * ring + k;
                f = tri(faces, f, a, b, c);
                f = tri(faces, f, a, c, d);
            }
        }
        for (int k = 0; k < ring; k++) {
            int k2 = (k + 1) % ring;
            f = tri(faces, f, front, k2, k);
            f = tri(faces, f, back, (s - 1) * ring + k, (s - 1) * ring + k2);
        }
        mesh.getFaces().setAll(faces);

        MeshView view = new MeshView(mesh);
        view.setMaterial(material);
        view.setCullFace(CullFace.NONE);
        return view;
    }

    private static void addCap(float[] pts, float[] nrm, float[] tex, int v,
                               double[] section, int dir) {
        double cy = (section[2] + section[3]) / 2;
        pts[v * 3] = 0f;
        pts[v * 3 + 1] = jy(cy);
        pts[v * 3 + 2] = (float) section[0];
        nrm[v * 3] = 0f;
        nrm[v * 3 + 1] = 0f;
        nrm[v * 3 + 2] = dir;
        tex[v * 2] = 0.5f;
        tex[v * 2 + 1] = dir > 0 ? 0f : 1f;
    }

    private static int tri(int[] faces, int f, int a, int b, int c) {
        faces[f] = a; faces[f + 1] = a; faces[f + 2] = a;
        faces[f + 3] = b; faces[f + 4] = b; faces[f + 5] = b;
        faces[f + 6] = c; faces[f + 7] = c; faces[f + 8] = c;
        return f + 9;
    }

    /**
     * Quadrilatere simple (dans l'ordre a, b, c, d).
     *
     * <p>Comme pour les rubans, l'enroulement decide de la normale et donc de
     * la lumiere qui eclaire la plaque. Tous les quadrilateres du jeu sont des
     * marquages poses au sol, et deux d'entre eux etaient donnes dans l'autre
     * sens : la ligne damier des neuf circuits sortait tournee vers le bas,
     * eclairee par le seul appoint pose sous le circuit. On retourne donc
     * l'enroulement quand la normale descend. Une plaque verticale, elle, garde
     * le sien : le critere est nul, et rien ne dit de quel cote on la regarde.
     */
    public static MeshView quad(Vec3 a, Vec3 b, Vec3 c, Vec3 d, double tile, Image texture) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(
                (float) a.x, jy(a.y), (float) a.z,
                (float) b.x, jy(b.y), (float) b.z,
                (float) c.x, jy(c.y), (float) c.z,
                (float) d.x, jy(d.y), (float) d.z);
        float t = (float) tile;
        mesh.getTexCoords().setAll(0, 0, t, 0, t, t, 0, t);
        // normale monde = -(ab x ac) : le monde a Y qui monte, JavaFX Y qui
        // descend, et ce miroir change le signe du produit vectoriel
        boolean envers = b.sub(a).cross(c.sub(a)).dot(Vec3.UP) > 0;
        mesh.getFaces().setAll(envers
                ? new int[]{0, 0, 2, 2, 1, 1, 0, 0, 3, 3, 2, 2}
                : new int[]{0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3});
        MeshView view = new MeshView(mesh);
        view.setMaterial(material(texture));
        view.setCullFace(CullFace.NONE);
        return view;
    }
}
