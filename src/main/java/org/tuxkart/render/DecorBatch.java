package org.tuxkart.render;

import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fusionne des milliers d'objets de decor en un seul maillage.
 *
 * JavaFX dessine et parcourt chaque {@code Shape3D} separement : quelques
 * milliers d'arbres saturent le fil d'application bien avant de gener la carte
 * graphique. On recopie donc ici les sommets de formes unitaires, transformes a
 * la main, dans un maillage unique.
 *
 * Le probleme d'une fusion, c'est la couleur : un maillage n'a qu'un materiau.
 * On construit donc une <b>palette</b> — une image d'une rangee de pastilles —
 * et chaque instance pointe ses coordonnees de texture vers sa pastille. La
 * variete de teintes est conservee avec un seul appel de dessin.
 */
public final class DecorBatch {

    /** Largeur d'une pastille de palette, en pixels : evite tout debordement au filtrage. */
    private static final int SWATCH = 8;
    /**
     * Teintes distinctes par troncon.
     *
     * Au-dela, une couleur inconnue retombait silencieusement sur la premiere
     * de la palette : en montant la densite du decor, les arbres proches sont
     * devenus creme d'un coup, sans autre indice. La limite est doublee et les
     * teintes sont quantifiees a cinq bits par canal — un pas de 3 %, invisible
     * sur des aplats, mais qui divise par plusieurs le nombre de nuances a
     * ranger, la plupart ne differant que par une lueur de hasard.
     */
    private static final int MAX_COLORS = 1024;
    /** Pas de quantification des teintes, par canal. */
    private static final int COLOR_STEP = 8;
    private static boolean overflowWarned;

    // Tableaux primitifs a croissance manuelle : une fusion represente des
    // millions de valeurs, et les emballer en List<Float> couterait plusieurs
    // centaines de mega-octets rien qu'en objets.
    private float[] points = new float[4096];
    private int pointCount;
    private float[] normals = new float[4096];
    private int normalCount;
    private int[] faces = new int[8192];
    private int faceCount;

    private final Map<Integer, Integer> palette = new LinkedHashMap<>();

    /**
     * Facteur d'echelle applique aux pieces suivantes, et son point fixe.
     *
     * Un salon n'est pas peuple d'objets de meme taille : il y a le gros ours
     * pose contre le mur et le de a jouer perdu dans le tapis. Plutot que de
     * parametrer chaque piece, on met l'echelle ici — la piece se construit
     * dans ses proportions naturelles, et le lot entier est agrandi ou reduit
     * autour de son point d'ancrage au sol.
     */
    private double scale = 1;
    private double pivotX, pivotY, pivotZ;

    /** Fixe l'echelle des pieces suivantes, autour d'un point d'ancrage. */
    public void setScale(double factor, double x, double y, double z) {
        this.scale = factor;
        this.pivotX = x;
        this.pivotY = y;
        this.pivotZ = z;
    }

    /** Revient a l'echelle naturelle. */
    public void resetScale() {
        this.scale = 1;
    }
    private int vertexCount;

    /**
     * Les tableaux des formes unitaires, lus une seule fois. Sans ce cache,
     * chaque instance rappellerait {@code toArray} et allouerait une copie
     * complete du maillage source — des centaines de mega-octets de dechets
     * pour quelques milliers d'arbres.
     */
    private static final Map<TriangleMesh, float[][]> SRC_FLOATS = new java.util.IdentityHashMap<>();
    private static final Map<TriangleMesh, int[]> SRC_FACES = new java.util.IdentityHashMap<>();

    private static float[][] sourceVertices(TriangleMesh m) {
        return SRC_FLOATS.computeIfAbsent(m,
                k -> new float[][]{k.getPoints().toArray(null), k.getNormals().toArray(null)});
    }

    private static int[] sourceFaces(TriangleMesh m) {
        return SRC_FACES.computeIfAbsent(m, k -> k.getFaces().toArray(null));
    }

    /** Vrai tant que rien n'a ete verse : {@link #build()} rendrait alors {@code null}. */
    public boolean isEmpty() {
        return faceCount == 0;
    }

    private void pushPoint(float x, float y, float z) {
        if (pointCount + 3 > points.length) {
            points = java.util.Arrays.copyOf(points, points.length * 2);
        }
        points[pointCount++] = x;
        points[pointCount++] = y;
        points[pointCount++] = z;
    }

    private void pushNormal(float x, float y, float z) {
        if (normalCount + 3 > normals.length) {
            normals = java.util.Arrays.copyOf(normals, normals.length * 2);
        }
        normals[normalCount++] = x;
        normals[normalCount++] = y;
        normals[normalCount++] = z;
    }

    private void pushFace(int p, int t) {
        if (faceCount + 3 > faces.length) {
            faces = java.util.Arrays.copyOf(faces, faces.length * 2);
        }
        faces[faceCount++] = p;
        faces[faceCount++] = p;
        faces[faceCount++] = t;
    }

    /**
     * Ajoute une copie transformee d'une forme unitaire.
     *
     * @param tiltDeg inclinaison autour de Z, appliquee avant le lacet
     * @param yawDeg  rotation autour de Y
     */
    public void add(TriangleMesh source,
                    double sx, double sy, double sz,
                    double tiltDeg, double yawDeg,
                    double tx, double ty, double tz,
                    Color color) {
        float[][] srcVerts = sourceVertices(source);
        float[] srcPts = srcVerts[0];
        float[] srcNrm = srcVerts[1];
        int[] srcFaces = sourceFaces(source);

        if (scale != 1) {
            sx *= scale;
            sy *= scale;
            sz *= scale;
            tx = pivotX + (tx - pivotX) * scale;
            ty = pivotY + (ty - pivotY) * scale;
            tz = pivotZ + (tz - pivotZ) * scale;
        }

        double ct = Math.cos(Math.toRadians(tiltDeg)), st = Math.sin(Math.toRadians(tiltDeg));
        double cy = Math.cos(Math.toRadians(yawDeg)), sy2 = Math.sin(Math.toRadians(yawDeg));

        int base = vertexCount;
        int count = srcPts.length / 3;
        for (int i = 0; i < count; i++) {
            double px = srcPts[i * 3] * sx;
            double py = srcPts[i * 3 + 1] * sy;
            double pz = srcPts[i * 3 + 2] * sz;
            // rotation Z puis rotation Y
            double x1 = px * ct - py * st, y1 = px * st + py * ct;
            double x2 = x1 * cy + pz * sy2, z2 = -x1 * sy2 + pz * cy;
            pushPoint((float) (x2 + tx), (float) (y1 + ty), (float) (z2 + tz));

            // la normale se transforme par l'inverse de l'echelle
            double nx = srcNrm[i * 3] / sx;
            double ny = srcNrm[i * 3 + 1] / sy;
            double nz = srcNrm[i * 3 + 2] / sz;
            double m1x = nx * ct - ny * st, m1y = nx * st + ny * ct;
            double m2x = m1x * cy + nz * sy2, m2z = -m1x * sy2 + nz * cy;
            double len = Math.sqrt(m2x * m2x + m1y * m1y + m2z * m2z);
            if (len < 1e-9) len = 1;
            pushNormal((float) (m2x / len), (float) (m1y / len), (float) (m2z / len));
        }
        vertexCount += count;

        int swatch = paletteIndex(color);
        // les formes unitaires ont un indice commun pour point et normale :
        // le remappage se resume donc a un decalage
        for (int i = 0; i < srcFaces.length; i += 3) {
            pushFace(base + srcFaces[i], swatch);
        }
    }

    private int paletteIndex(Color c) {
        int key = (quantize(c.getRed()) << 16) | (quantize(c.getGreen()) << 8) | quantize(c.getBlue());
        Integer existing = palette.get(key);
        if (existing != null) return existing;
        if (palette.size() >= MAX_COLORS) {
            if (!overflowWarned) {
                overflowWarned = true;
                System.err.println("Decor : plus de " + MAX_COLORS + " teintes dans un troncon,"
                        + " les suivantes reprennent la premiere. Baisser la densite ou"
                        + " elargir la palette.");
            }
            return 0;
        }
        int index = palette.size();
        palette.put(key, index);
        return index;
    }

    private static int quantize(double channel) {
        int v = (int) (channel * 255);
        return Math.min(255, v / COLOR_STEP * COLOR_STEP + COLOR_STEP / 2);
    }

    /** Construit le maillage fusionne, ou {@code null} si rien n'a ete ajoute. */
    public MeshView build() {
        if (faceCount == 0) return null;

        TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
        float[] pts = java.util.Arrays.copyOf(points, pointCount);
        float[] nrm = java.util.Arrays.copyOf(normals, normalCount);
        int[] fcs = java.util.Arrays.copyOf(faces, faceCount);

        int colors = Math.max(1, palette.size());
        float[] tex = new float[colors * 2];
        for (int i = 0; i < colors; i++) {
            tex[i * 2] = (i * SWATCH + SWATCH / 2f) / (colors * SWATCH);
            tex[i * 2 + 1] = 0.5f;
        }

        // les tableaux d'accumulation ne servent plus : on les relache avant
        // que JavaFX ne fasse ses propres copies, sinon le pic memoire cumule
        // l'accumulateur, la copie compactee et le stockage du maillage
        points = null;
        normals = null;
        faces = null;

        mesh.getPoints().setAll(pts);
        mesh.getNormals().setAll(nrm);
        mesh.getTexCoords().setAll(tex);
        mesh.getFaces().setAll(fcs);

        MeshView view = new MeshView(mesh);
        view.setMaterial(paletteMaterial(colors));
        palette.clear();
        view.setCullFace(CullFace.BACK);
        view.setMouseTransparent(true);
        return view;
    }

    private PhongMaterial paletteMaterial(int colors) {
        int w = colors * SWATCH, h = SWATCH;
        int[] argb = new int[w * h];
        int i = 0;
        for (int key : palette.keySet()) {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < SWATCH; x++) {
                    argb[y * w + i * SWATCH + x] = 0xff000000 | key;
                }
            }
            i++;
        }
        if (palette.isEmpty()) java.util.Arrays.fill(argb, 0xffffffff);

        WritableImage img = new WritableImage(w, h);
        img.getPixelWriter().setPixels(0, 0, w, h, PixelFormat.getIntArgbInstance(), argb, 0, w);
        PhongMaterial m = new PhongMaterial(Color.WHITE);
        m.setDiffuseMap(img);
        m.setSpecularColor(Color.rgb(18, 18, 18));
        m.setSpecularPower(10);
        return m;
    }
}
