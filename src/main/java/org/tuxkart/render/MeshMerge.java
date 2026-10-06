package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.paint.Material;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.CullFace;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Shape3D;
import javafx.scene.shape.Sphere;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import javafx.scene.transform.Transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fusionne les pieces immobiles d'un sous-arbre en un maillage par materiau.
 *
 * Meme constat que pour {@link DecorBatch} : JavaFX parcourt et soumet chaque
 * {@code Shape3D} separement. Un kart assemble a la main pese une centaine de
 * pieces — coque, pontons, arceau, boulons, pilote — dont aucune ne bouge par
 * rapport a la caisse. Les recopier une fois pour toutes dans un maillage
 * commun ne change rien a l'image et divise par trois le nombre de soumissions
 * (799 formes pour huit karts, 276 apres fusion).
 *
 * Contrairement a {@code DecorBatch}, la couleur n'est pas versee dans une
 * palette : on regroupe par materiau, et chaque materiau garde le sien. Le
 * chrome reste donc du chrome et la peinture de la peinture, ce qu'une palette
 * unique ne saurait rendre (elle n'a qu'un seul reflet speculaire).
 *
 * Les pieces texturees — plaque numerotee, gomme des pneus — restent a l'ecart :
 * leurs coordonnees de texture dependent de la forme d'origine de JavaFX, que
 * les maillages partages de {@link Meshes} ne reproduisent pas a l'identique.
 */
final class MeshMerge {

    private MeshMerge() {
    }

    /** Une piece a fusionner, avec sa transformation dans le repere de la racine. */
    private record Piece(Shape3D shape, Transform toRoot) {
    }

    /** Un maillage fusionne par materiau et par mode d'elimination des faces. */
    private record Key(Material material, CullFace cull) {
    }

    /**
     * Remplace, dans {@code root} et ses descendants, toutes les pieces
     * fusionnables par un maillage unique par materiau. Le sous-arbre doit etre
     * rigide : tout ce qui s'anime (roues, direction, flammes) doit vivre
     * ailleurs, sinon son mouvement serait fige dans la fusion.
     */
    static void merge(Group root) {
        List<Piece> pieces = new ArrayList<>();
        collect(root, null, pieces);
        if (pieces.size() < 2) return;

        Map<TriangleMesh, Source> cache = new IdentityHashMap<>();
        Map<Key, Batch> batches = new LinkedHashMap<>();
        for (Piece p : pieces) {
            Key key = new Key(p.shape().getMaterial(), p.shape().getCullFace());
            batches.computeIfAbsent(key, k -> new Batch()).add(p, cache);
        }

        for (Piece p : pieces) {
            Parent parent = p.shape().getParent();
            if (parent instanceof Group g) g.getChildren().remove(p.shape());
        }
        prune(root);

        for (Map.Entry<Key, Batch> e : batches.entrySet()) {
            MeshView view = e.getValue().build();
            view.setMaterial(e.getKey().material());
            view.setCullFace(e.getKey().cull());
            view.setMouseTransparent(true);
            root.getChildren().add(view);
        }
    }

    /** Recense les pieces fusionnables, transformation accumulee a l'appui. */
    private static void collect(Parent parent, Transform toParent, List<Piece> out) {
        for (Node child : parent.getChildrenUnmodifiable()) {
            Transform local = child.getLocalToParentTransform();
            Transform toRoot = toParent == null ? local : toParent.createConcatenation(local);
            if (child instanceof Shape3D s) {
                if (mergeable(s)) out.add(new Piece(s, toRoot));
            } else if (child instanceof Parent p) {
                collect(p, toRoot, out);
            }
        }
    }

    /**
     * Une piece se fusionne si sa forme se ramene a un maillage indexe de la
     * meme facon pour les sommets, les normales et la texture — ce que
     * garantissent les maillages de {@link Meshes} — et si son materiau ne
     * depend pas de la disposition des coordonnees de texture.
     */
    private static boolean mergeable(Shape3D s) {
        if (!(s.getMaterial() instanceof PhongMaterial m)) return false;
        // une carte diffuse ou de relief se lit a travers les coordonnees de
        // texture de la forme d'origine : la fusion les remplacerait
        if (m.getDiffuseMap() != null || m.getBumpMap() != null) return false;
        if (!s.isVisible()) return false;
        return sourceOf(s) != null;
    }

    /** Maillage unitaire correspondant a une forme, ou {@code null} si inconnu. */
    private static TriangleMesh sourceOf(Shape3D s) {
        return switch (s) {
            case Box _ -> Meshes.sharedBox();
            case Sphere sp -> Meshes.sharedSphere(sp.getDivisions());
            case Cylinder c -> Meshes.sharedCylinder(c.getDivisions());
            case MeshView v when v.getMesh() instanceof TriangleMesh m
                    && m.getVertexFormat() == VertexFormat.POINT_NORMAL_TEXCOORD -> m;
            default -> null;
        };
    }

    /**
     * Tableaux d'une forme source, lus une seule fois.
     *
     * {@code getPoints().toArray(null)} recopie tout le maillage : sans ce
     * cache, chaque piece d'un kart — et il y en a une centaine par kart —
     * rappellerait la copie sur les memes maillages partages.
     */
    private record Source(float[] points, float[] normals, float[] tex, int[] faces,
                          boolean aligned) {
    }

    private static Source read(Map<TriangleMesh, Source> cache, TriangleMesh m) {
        return cache.computeIfAbsent(m, k -> {
            int[] faces = k.getFaces().toArray(null);
            // les maillages de Meshes donnent le meme indice au sommet, a la
            // normale et a la texture : le dedoublonnage se resume alors a un
            // tableau de correspondance
            boolean aligned = true;
            for (int i = 0; i < faces.length && aligned; i += 3) {
                aligned = faces[i] == faces[i + 1] && faces[i] == faces[i + 2];
            }
            return new Source(k.getPoints().toArray(null), k.getNormals().toArray(null),
                    k.getTexCoords().toArray(null), faces, aligned);
        });
    }

    /** Echelle a appliquer au maillage unitaire pour retrouver les cotes de la forme. */
    private static double[] sizeOf(Shape3D s) {
        return switch (s) {
            case Box b -> new double[]{b.getWidth(), b.getHeight(), b.getDepth()};
            case Sphere sp -> new double[]{sp.getRadius(), sp.getRadius(), sp.getRadius()};
            case Cylinder c -> new double[]{c.getRadius(), c.getHeight(), c.getRadius()};
            default -> new double[]{1, 1, 1};
        };
    }

    /** Supprime les groupes devenus vides apres la fusion. */
    private static void prune(Group root) {
        root.getChildren().removeIf(child -> {
            if (child instanceof Group g) {
                prune(g);
                return g.getChildren().isEmpty();
            }
            return false;
        });
    }

    /**
     * Accumulateur d'un maillage fusionne.
     *
     * Les sommets sont dedoublonnes par triplet (sommet, normale, texture) de la
     * forme source : les maillages partages sont indexes de la meme facon pour
     * les trois, la fusion garde donc leur economie de sommets.
     */
    private static final class Batch {
        private float[] points = new float[512];
        private int pointCount;
        private float[] normals = new float[512];
        private int normalCount;
        private float[] tex = new float[512];
        private int texCount;
        private int[] faces = new int[1024];
        private int faceCount;
        private int vertices;

        void add(Piece piece, Map<TriangleMesh, Source> cache) {
            Shape3D shape = piece.shape();
            Source src = read(cache, sourceOf(shape));
            double[] size = sizeOf(shape);
            Transform t = piece.toRoot();

            float[] srcPts = src.points();
            float[] srcNrm = src.normals();
            float[] srcTex = src.tex();
            int[] srcFaces = src.faces();

            // matrice des cofacteurs : c'est elle qui transporte les normales
            // quand l'echelle n'est pas uniforme (une sphere aplatie n'a pas les
            // normales de la sphere d'origine)
            double[] m = {
                    t.getMxx() * size[0], t.getMxy() * size[1], t.getMxz() * size[2],
                    t.getMyx() * size[0], t.getMyy() * size[1], t.getMyz() * size[2],
                    t.getMzx() * size[0], t.getMzy() * size[1], t.getMzz() * size[2]};
            double[] cof = cofactors(m);
            double det = m[0] * cof[0] + m[1] * cof[1] + m[2] * cof[2];
            // une transformation qui retourne l'espace inverse aussi le sens
            // d'enroulement des triangles
            boolean flip = det < 0;

            // correspondance ancien sommet -> nouveau, remise a zero pour
            // chaque piece puisque chacune a sa propre transformation
            int[] direct = src.aligned() ? new int[srcPts.length / 3] : null;
            if (direct != null) java.util.Arrays.fill(direct, -1);
            Map<Long, Integer> seen = direct == null ? new HashMap<>() : null;

            int stride = 3;
            for (int f = 0; f < srcFaces.length; f += stride * 3) {
                int a = vertex(direct, seen, srcFaces, f, srcPts, srcNrm, srcTex, m, cof, t);
                int b = vertex(direct, seen, srcFaces, f + stride, srcPts, srcNrm, srcTex, m, cof, t);
                int c = vertex(direct, seen, srcFaces, f + 2 * stride, srcPts, srcNrm, srcTex, m, cof, t);
                if (flip) {
                    pushFace(a, c, b);
                } else {
                    pushFace(a, b, c);
                }
            }
        }

        /** Indice du sommet fusionne correspondant au triplet lu a {@code f}. */
        private int vertex(int[] direct, Map<Long, Integer> seen, int[] srcFaces, int f,
                           float[] srcPts, float[] srcNrm, float[] srcTex,
                           double[] m, double[] cof, Transform t) {
            int p = srcFaces[f], n = srcFaces[f + 1], u = srcFaces[f + 2];
            long key = direct != null ? 0 : ((long) p << 42) ^ ((long) n << 21) ^ u;
            if (direct != null) {
                if (direct[p] >= 0) return direct[p];
            } else {
                Integer known = seen.get(key);
                if (known != null) return known;
            }

            double px = srcPts[p * 3], py = srcPts[p * 3 + 1], pz = srcPts[p * 3 + 2];
            pushPoint(
                    (float) (m[0] * px + m[1] * py + m[2] * pz + t.getTx()),
                    (float) (m[3] * px + m[4] * py + m[5] * pz + t.getTy()),
                    (float) (m[6] * px + m[7] * py + m[8] * pz + t.getTz()));

            double nx = srcNrm[n * 3], ny = srcNrm[n * 3 + 1], nz = srcNrm[n * 3 + 2];
            double tx = cof[0] * nx + cof[1] * ny + cof[2] * nz;
            double ty = cof[3] * nx + cof[4] * ny + cof[5] * nz;
            double tz = cof[6] * nx + cof[7] * ny + cof[8] * nz;
            double len = Math.sqrt(tx * tx + ty * ty + tz * tz);
            if (len < 1e-12) {
                tx = 0; ty = 1; tz = 0; len = 1;
            }
            pushNormal((float) (tx / len), (float) (ty / len), (float) (tz / len));
            pushTex(srcTex[u * 2], srcTex[u * 2 + 1]);

            int index = vertices++;
            if (direct != null) {
                direct[p] = index;
            } else {
                seen.put(key, index);
            }
            return index;
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

        private void pushTex(float u, float v) {
            if (texCount + 2 > tex.length) {
                tex = java.util.Arrays.copyOf(tex, tex.length * 2);
            }
            tex[texCount++] = u;
            tex[texCount++] = v;
        }

        private void pushFace(int a, int b, int c) {
            if (faceCount + 9 > faces.length) {
                faces = java.util.Arrays.copyOf(faces, faces.length * 2);
            }
            // sommet, normale et texture partagent l'indice : la fusion
            // dedoublonne par triplet, ils sont donc alignes
            faces[faceCount++] = a; faces[faceCount++] = a; faces[faceCount++] = a;
            faces[faceCount++] = b; faces[faceCount++] = b; faces[faceCount++] = b;
            faces[faceCount++] = c; faces[faceCount++] = c; faces[faceCount++] = c;
        }

        MeshView build() {
            TriangleMesh mesh = new TriangleMesh(VertexFormat.POINT_NORMAL_TEXCOORD);
            mesh.getPoints().setAll(java.util.Arrays.copyOf(points, pointCount));
            mesh.getNormals().setAll(java.util.Arrays.copyOf(normals, normalCount));
            mesh.getTexCoords().setAll(java.util.Arrays.copyOf(tex, texCount));
            mesh.getFaces().setAll(java.util.Arrays.copyOf(faces, faceCount));
            return new MeshView(mesh);
        }
    }

    /** Comatrice d'une matrice 3x3 donnee par lignes. */
    private static double[] cofactors(double[] m) {
        return new double[]{
                m[4] * m[8] - m[5] * m[7], m[5] * m[6] - m[3] * m[8], m[3] * m[7] - m[4] * m[6],
                m[2] * m[7] - m[1] * m[8], m[0] * m[8] - m[2] * m[6], m[1] * m[6] - m[0] * m[7],
                m[1] * m[5] - m[2] * m[4], m[2] * m[3] - m[0] * m[5], m[0] * m[4] - m[1] * m[3]};
    }
}
