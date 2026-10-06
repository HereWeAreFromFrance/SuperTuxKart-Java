package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Theme;

/**
 * Effets de piste : gerbes de poussiere et traces de gomme.
 *
 * Les deux sont a budget fixe. Les particules sont un pool de spheres qu'on
 * recycle, les traces un unique maillage en tampon circulaire dont on ne
 * reecrit que les sommets : aucun objet n'est cree pendant la course.
 */
public final class Effects {

    // ------------------------------------------------------------ particules

    private static final int PARTICLES = 110;

    /** Dispersion des particules ; flux a part, voir {@link ChaseCamera}. */
    private final java.util.Random fx = org.tuxkart.core.Rng.stream(19);

    private final Group particleGroup = new Group();
    private final Sphere[] pool = new Sphere[PARTICLES];
    private final double[] px = new double[PARTICLES];
    private final double[] py = new double[PARTICLES];
    private final double[] pz = new double[PARTICLES];
    private final double[] vx = new double[PARTICLES];
    private final double[] vy = new double[PARTICLES];
    private final double[] vz = new double[PARTICLES];
    private final double[] life = new double[PARTICLES];
    private final double[] span = new double[PARTICLES];
    private final double[] size = new double[PARTICLES];
    private final double[] grow = new double[PARTICLES];
    private int next;

    private final PhongMaterial dustMat;
    private final PhongMaterial smokeMat;
    private final PhongMaterial sparkMat;

    // ---------------------------------------------------------- traces de gomme

    private static final int MAX_MARKS = 420;
    private final MeshView markView;
    private final TriangleMesh markMesh = new TriangleMesh();
    private final float[] markPts = new float[MAX_MARKS * 4 * 3];
    private int markHead;
    /**
     * Plage de sommets modifiee depuis la derniere publication, ou -1.
     *
     * Republier tout le tampon — cinq mille flottants — pour les douze qui ont
     * change a chaque trace posee, c'etait redonner a JavaFX de quoi resynchroniser
     * l'ensemble du maillage. On ne lui signale plus que ce qui bouge.
     */
    private int dirtyFrom = -1;
    private int dirtyTo = -1;

    public Effects(Theme theme) {
        Color dust = switch (theme) {
            case DESERT -> Color.web("#e0c88a");
            case SNOW -> Color.web("#f4fbff");
            case VOLCANO -> Color.web("#6a5a52");
            default -> Color.web("#9c8f6a");
        };
        dustMat = translucent(dust, 0.30);
        smokeMat = translucent(Color.web("#dcdcdc"), 0.26);
        sparkMat = new PhongMaterial(Color.web("#ffb03a"));
        sparkMat.setSelfIlluminationMap(Terrain.flat(Color.web("#ff8c1a")));

        for (int i = 0; i < PARTICLES; i++) {
            Sphere s = new Sphere(0.3, 10);
            s.setMaterial(dustMat);
            s.setVisible(false);
            s.setMouseTransparent(true);
            pool[i] = s;
            particleGroup.getChildren().add(s);
        }

        // maillage des traces : tous les quads sont degeneres au depart
        markMesh.getPoints().setAll(markPts);
        markMesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
        int[] faces = new int[MAX_MARKS * 2 * 6];
        for (int i = 0; i < MAX_MARKS; i++) {
            int b = i * 4;
            int f = i * 12;
            faces[f] = b; faces[f + 1] = 0;
            faces[f + 2] = b + 1; faces[f + 3] = 1;
            faces[f + 4] = b + 2; faces[f + 5] = 2;

            faces[f + 6] = b; faces[f + 7] = 0;
            faces[f + 8] = b + 2; faces[f + 9] = 2;
            faces[f + 10] = b + 3; faces[f + 11] = 3;
        }
        markMesh.getFaces().setAll(faces);
        markView = new MeshView(markMesh);
        markView.setMaterial(translucent(Color.web("#101012"), 0.62));
        markView.setCullFace(CullFace.NONE);
        markView.setMouseTransparent(true);
    }

    private static PhongMaterial translucent(Color c, double alpha) {
        PhongMaterial m = new PhongMaterial(Color.color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
        m.setSpecularColor(Color.TRANSPARENT);
        return m;
    }

    public Group particles() {
        return particleGroup;
    }

    public MeshView skidMarks() {
        return markView;
    }

    // ----------------------------------------------------------------- emission

    public void dust(double x, double y, double z, double speed, double dirX, double dirZ) {
        emit(dustMat, x, y + 0.15, z,
                -dirX * speed * 0.18 + rand(1.2), 1.1 + fx.nextDouble() * 1.4,
                -dirZ * speed * 0.18 + rand(1.2),
                0.75, 0.22, 1.9);
    }

    public void smoke(double x, double y, double z) {
        emit(smokeMat, x, y + 0.18, z, rand(0.9), 0.9 + fx.nextDouble(), rand(0.9),
                0.85, 0.20, 2.2);
    }

    public void sparks(double x, double y, double z, double dirX, double dirZ) {
        for (int i = 0; i < 5; i++) {
            emit(sparkMat, x, y + 0.4, z,
                    -dirX * 3 + rand(4), 2.5 + fx.nextDouble() * 3, -dirZ * 3 + rand(4),
                    0.35, 0.10, 0.4);
        }
    }

    private double rand(double amount) {
        return (fx.nextDouble() - 0.5) * 2 * amount;
    }

    private void emit(PhongMaterial mat, double x, double y, double z,
                      double dx, double dy, double dz,
                      double duration, double radius, double growth) {
        int i = next;
        next = (next + 1) % PARTICLES;
        Sphere s = pool[i];
        s.setMaterial(mat);
        px[i] = x;
        py[i] = y;
        pz[i] = z;
        vx[i] = dx;
        vy[i] = dy;
        vz[i] = dz;
        life[i] = duration;
        span[i] = duration;
        grow[i] = growth;
        size[i] = radius;
        s.setRadius(0.3);
        setScale(s, radius / 0.3);
        s.setTranslateX(x);
        s.setTranslateY(Meshes.jy(y));
        s.setTranslateZ(z);
        s.setVisible(true);
    }

    public void update(double dt) {
        for (int i = 0; i < PARTICLES; i++) {
            if (life[i] <= 0) continue;
            life[i] -= dt;
            if (life[i] <= 0) {
                pool[i].setVisible(false);
                continue;
            }
            vy[i] -= 1.6 * dt;
            px[i] += vx[i] * dt;
            py[i] += vy[i] * dt;
            pz[i] += vz[i] * dt;
            vx[i] *= 1 - 1.4 * dt;
            vz[i] *= 1 - 1.4 * dt;

            double t = 1 - life[i] / span[i];
            Sphere s = pool[i];
            s.setTranslateX(px[i]);
            s.setTranslateY(Meshes.jy(py[i]));
            s.setTranslateZ(pz[i]);
            // l'echelle se recalcule depuis la taille d'origine : la deduire de
            // l'echelle courante la faisait grossir de maniere exponentielle
            setScale(s, size[i] * (1 + t * grow[i]) / 0.3);
        }
        if (dirtyFrom >= 0) {
            markMesh.getPoints().set(dirtyFrom, markPts, dirtyFrom, dirtyTo - dirtyFrom);
            dirtyFrom = -1;
            dirtyTo = -1;
        }
    }

    private static void setScale(Sphere s, double factor) {
        s.setScaleX(factor);
        s.setScaleY(factor);
        s.setScaleZ(factor);
    }

    /** Ajoute un segment de trace entre deux positions successives d'une roue. */
    public void addSkid(Vec3 fromA, Vec3 fromB, Vec3 toA, Vec3 toB) {
        int base = markHead * 12;
        write(base, fromA);
        write(base + 3, fromB);
        write(base + 6, toB);
        write(base + 9, toA);
        markHead = (markHead + 1) % MAX_MARKS;
        // plusieurs traces peuvent tomber dans la meme image : on retient la
        // plage qui les couvre toutes, quitte a republier un peu trop
        dirtyFrom = dirtyFrom < 0 ? base : Math.min(dirtyFrom, base);
        dirtyTo = Math.max(dirtyTo, base + 12);
    }

    private void write(int offset, Vec3 p) {
        markPts[offset] = (float) p.x;
        markPts[offset + 1] = Meshes.jy(p.y + 0.035);
        markPts[offset + 2] = (float) p.z;
    }
}
