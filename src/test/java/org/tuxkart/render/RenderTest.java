package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import javafx.scene.shape.VertexFormat;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuxkart.FxToolkit;
import org.tuxkart.core.Quality;
import org.tuxkart.math.Vec3;
import org.tuxkart.kart.KartDef;
import org.tuxkart.kart.KartRoster;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;
import org.tuxkart.track.Track;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tests de rendu : ils exigent le toolkit JavaFX et un contexte graphique.
 *
 * Un maillage mal forme ne se voit pas a la compilation — il produit un ecran
 * noir, un objet retourne, ou une exception au moment du dessin. Ces tests
 * verifient donc la structure des maillages produits, la validite des textures
 * cuites, et vont jusqu'a construire les neuf circuits en entier.
 */
class RenderTest {

    @BeforeAll
    static void demarrerToolkit() {
        assumeTrue(FxToolkit.start(), "toolkit JavaFX indisponible : tests de rendu ignores");
    }

    @org.junit.jupiter.api.AfterAll
    static void rendreLaResolutionParDefaut() {
        // la resolution des textures est un reglage global partage par toute la
        // JVM : la laisser a 256 ferait dependre les tests suivants de l'ordre
        // d'execution
        Textures.setResolution(512);
    }

    static List<TrackDef> circuits() {
        return Tracks.ALL;
    }

    static List<KartDef> pilotes() {
        return KartRoster.ALL;
    }

    // ------------------------------------------------------------- textures

    @ParameterizedTest(name = "{0}")
    @EnumSource(Theme.class)
    @DisplayName("les textures de surface sont cuites et contrastees")
    void texturesDeSurface(Theme theme) {
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            for (Image img : List.of(Textures.road(theme), Textures.ground(theme),
                    Textures.wall(theme), Textures.kerb(), Textures.verge(theme),
                    Textures.gravel(Theme.GRASS), Textures.runoff(), Textures.tyre())) {
                assertNotNull(img);
                assertTrue(img.getWidth() >= 64 && img.getHeight() >= 64);
                assertTrue(couleursDistinctes(img) > 12,
                        "texture trop uniforme, elle serait invisible en jeu");
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Theme.class)
    @DisplayName("les cartes de normales sont valides et orientees vers le haut")
    void cartesDeNormales(Theme theme) {
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            for (Image bump : List.of(Textures.roadBump(theme), Textures.groundBump(theme),
                    Textures.kerbBump(), Textures.wallBump(theme))) {
                assertNotNull(bump, "carte de normales manquante");
                PixelReader r = bump.getPixelReader();
                int haut = 0, total = 0;
                for (int y = 0; y < bump.getHeight(); y += 7) {
                    for (int x = 0; x < bump.getWidth(); x += 7) {
                        Color c = r.getColor(x, y);
                        // encodage tangentiel : le canal bleu porte la composante
                        // normale a la surface, elle doit rester positive
                        if (c.getBlue() > 0.5) haut++;
                        total++;
                    }
                }
                assertTrue(haut > total * 0.9,
                        "des normales pointent vers l'interieur de la surface");
            }
        });
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Surface.class)
    @DisplayName("chaque revetement produit une texture distincte")
    void revetementsDistincts(Surface surface) {
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            Image img = Textures.surface(Theme.DESERT, surface);
            assertNotNull(img);
            assertTrue(couleursDistinctes(img) > 12);
            if (surface != Surface.BITUME) {
                assertNotNull(Textures.surfaceBump(Theme.DESERT, surface));
            }
        });
    }

    // -------------------------------------------------------------- maillages

    @Test
    @DisplayName("les formes unitaires sont partagees, pas dupliquees")
    void formesPartagees() {
        FxToolkit.run(() -> {
            var a = Meshes.sphere(1, 10, Meshes.material(Color.RED));
            var b = Meshes.sphere(5, 10, Meshes.material(Color.BLUE));
            assertSame(a.getMesh(), b.getMesh(),
                    "deux spheres de meme finesse doivent partager leur maillage");
            var c = Meshes.sphere(1, 16, Meshes.material(Color.RED));
            assertTrue(a.getMesh() != c.getMesh(),
                    "des finesses differentes donnent des maillages differents");
            assertEquals(5.0, b.getScaleX(), 1e-9, "le rayon passe par l'echelle");
        });
    }

    @Test
    @DisplayName("la coque de kart est un maillage complet et coherent")
    void coqueDeKart() {
        FxToolkit.run(() -> {
            double[][] sections = {
                    {-1.0, 0.5, 0.2, 0.6, 3.0},
                    {0.0, 0.8, 0.2, 0.8, 3.0},
                    {1.0, 0.4, 0.3, 0.5, 3.0},
            };
            MeshView view = Meshes.hull(sections, 12, Meshes.material(Color.RED));
            TriangleMesh mesh = (TriangleMesh) view.getMesh();
            assertSame(VertexFormat.POINT_NORMAL_TEXCOORD, mesh.getVertexFormat());
            verifierMaillage(mesh, "coque");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pilotes")
    @DisplayName("chaque kart se construit sans maillage invalide")
    void modelesDeKarts(KartDef def) {
        FxToolkit.run(() -> {
            KartNode node = new KartNode(def);
            assertNotNull(node.root);
            int maillages = parcourir(node.root, "kart " + def.id);
            assertTrue(maillages > 0, "le kart n'a aucun maillage");
        });
    }

    // ----------------------------------------------------------- le decor 3D

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("le decor complet se construit et tient dans son budget")
    void decorComplet(TrackDef def) {
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            Track track = new Track(def);
            Group root = TrackNode.build(track, Quality.FLUIDE, 8).root();
            assertNotNull(root);
            parcourir(root, def.id);
            long triangles = TrackNode.lastTriangleCount;
            assertTrue(triangles > 10_000, def.id + " : decor suspicieusement vide");
            assertTrue(triangles < 3_000_000,
                    def.id + " : " + triangles + " triangles, budget depasse");
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("aucune surface du circuit n'est eclairee par en dessous")
    void lesFacesEclaireesRegardentLaPiste(TrackDef def) {
        // JavaFX deduit la normale d'un triangle de son enroulement quand le
        // maillage n'en porte pas, et l'enroulement d'un ruban depend du sens
        // dans lequel on l'a pose : le ruban de chaussee, pose du bord gauche
        // vers le bord droit, sortait avec 100 % de ses normales tournees vers
        // le bas. La chaussee, la bordure, le bas-cote d'un cote et le muret de
        // l'autre etaient donc eclaires par la lumiere d'appoint posee sous le
        // circuit, et pas par le soleil — sur la Banquise, 137 de clarte avec
        // l'appoint contre 71 sans lui. La piste et le terrain, eclaires en
        // opposition, croisaient leurs clartes trois fois par tour et on ne
        // lisait plus la trajectoire.
        //
        // La regle est celle de Meshes.retourne : la face eclairee regarde
        // l'axe de la piste pose en l'air. Un ruban horizontal regarde donc le
        // ciel, une paroi verticale regarde la piste.
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            Track track = new Track(def);
            verifierFaces(TrackNode.build(track, Quality.FLUIDE, 8).root(), track, def.id);
        });
    }

    /**
     * Toute surface a normales deduites doit avoir sa face eclairee tournee
     * vers le haut, ou vers l'axe de la piste si elle est verticale.
     */
    private static void verifierFaces(javafx.scene.Node node, Track track, String nom) {
        if (node instanceof Group group) {
            for (javafx.scene.Node child : group.getChildren()) verifierFaces(child, track, nom);
            return;
        }
        if (!(node instanceof MeshView view) || !(view.getMesh() instanceof TriangleMesh mesh)) {
            return;
        }
        // les maillages a normales explicites disent eux-memes ou ils regardent
        if (mesh.getVertexFormat() == VertexFormat.POINT_NORMAL_TEXCOORD) return;
        float[] pts = mesh.getPoints().toArray(null);
        int[] faces = mesh.getFaces().toArray(null);
        if (faces.length == 0) return;
        double haut = 0, versAxe = 0, distance = 0;
        int n = 0;
        for (int i = 0; i + 6 <= faces.length; i += 6) {
            int a = faces[i] * 3, b = faces[i + 2] * 3, c = faces[i + 4] * 3;
            double ux = pts[b] - pts[a], uy = pts[b + 1] - pts[a + 1], uz = pts[b + 2] - pts[a + 2];
            double vx = pts[c] - pts[a], vy = pts[c + 1] - pts[a + 1], vz = pts[c + 2] - pts[a + 2];
            // le monde a Y qui monte, JavaFX Y qui descend : ce miroir change
            // le signe de la composante verticale du produit vectoriel
            double nx = uy * vz - uz * vy;
            double ny = -(uz * vx - ux * vz);
            double nz = ux * vy - uy * vx;
            double len = Math.sqrt(nx * nx + ny * ny + nz * nz);
            if (len < 1e-9) continue;
            nx /= len; ny /= len; nz /= len;
            double cx = (pts[a] + pts[b] + pts[c]) / 3, cz = (pts[a + 2] + pts[b + 2] + pts[c + 2]) / 3;
            Vec3 axe = track.center[plusProche(track, cx, cz)];
            double wx = axe.x - cx, wz = axe.z - cz;
            double wl = Math.sqrt(wx * wx + wz * wz);
            haut += ny;
            distance += wl;
            n++;
            if (wl > 1e-6) versAxe += (nx * wx + nz * wz) / wl;
        }
        if (n == 0) return;
        haut /= n;
        versAxe /= n;
        distance /= n;
        assertTrue(haut > -0.05, nom + " : un maillage a normales deduites regarde le sol ("
                + haut + ") — il sera eclaire par la lumiere d'appoint et pas par le soleil");
        // Une paroi verticale ne peut pas regarder le ciel : on lui demande de
        // regarder la piste, seule face qu'on voie jamais. Le critere ne vaut
        // que pres du ruban — l'anneau de montagnes, a un kilometre, regarde
        // bien le circuit mais la rangee d'axe la plus proche de l'un de ses
        // pans n'est pas dans cette direction-la.
        if (haut < 0.2 && distance < 60) {
            assertTrue(versAxe > 0, nom + " : une paroi verticale tourne le dos a la piste ("
                    + versAxe + ")");
        }
    }

    /** Rangee de l'axe la plus proche d'un point, en force brute. */
    private static int plusProche(Track track, double x, double z) {
        int best = 0;
        double meilleure = Double.MAX_VALUE;
        for (int i = 0; i < track.center.length; i++) {
            double dx = track.center[i].x - x, dz = track.center[i].z - z;
            double d = dx * dx + dz * dz;
            if (d < meilleure) {
                meilleure = d;
                best = i;
            }
        }
        return best;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("sous un ouvrage, le sol couvre tout du bitume au pied du mur")
    void leTablierNeLaissePasDeTerrainNu(TrackDef def) {
        // Le tablier a d'abord ete pose de la ligne de vibreur au mur, la
        // bordure etant supprimee juste au-dessus : personne ne couvrait plus
        // le metre soixante-dix qui les separe, et un lisere de gazon courait
        // le long du bitume sur toute la traversee du tunnel. Un trou de ce
        // genre ne se voit qu'a l'image, et seulement sur les deux circuits
        // dont le sol d'ouvrage differe du sol du theme — d'ou ce test, qui
        // demande la couverture et non le mecanisme.
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            Track track = new Track(def);
            Group root = TrackNode.build(track, Quality.FLUIDE, 8).root();
            for (int p = 0; p < def.props.length; p++) {
                org.tuxkart.track.Ouvrage o = org.tuxkart.track.Ouvrage.parNom(def.propKinds[p]);
                if (o == null) continue;
                double s0 = def.props[p][0];
                // Le mur n'est pas a un ecart curviligne constant : l'ouvrage
                // est une boite droite et la piste tourne sous lui. On le
                // retrouve donc dans son propre repere, comme le batisseur le
                // pose, puis on projette sur le ruban.
                var profil = o.profil(track, s0);
                org.tuxkart.math.Vec3 ancre = track.worldAt(s0, def.props[p][1]);
                double cap = track.headingAtS(s0) + Math.toRadians(def.props[p][2]);
                for (double d = -o.demiLongueur + 3; d <= o.demiLongueur - 3; d += 8) {
                    double s = org.tuxkart.math.MathUtil.mod(s0 + d, track.length);
                    int i = (int) Math.round(s / track.spacing) % track.n;
                    double along = track.deltaS(s0, track.arc[i]);
                    double demi = profil.demi(along);
                    for (int cote = -1; cote <= 1; cote += 2) {
                        double x = ancre.x + Math.cos(cap) * cote * demi + Math.sin(cap) * along;
                        double z = ancre.z - Math.sin(cap) * cote * demi + Math.cos(cap) * along;
                        double mur = Math.abs((x - track.center[i].x) * track.side[i].x
                                + (z - track.center[i].z) * track.side[i].z);
                        double atteint = couvertureLaterale(root, track, i, cote);
                        assertTrue(atteint >= mur - 0.05, def.id + " : sous " + o.label
                                + " a s=" + Math.round(track.arc[i]) + ", le sol s'arrete a "
                                + Math.round(atteint * 100) / 100.0 + " m alors que le mur est a "
                                + Math.round(mur * 100) / 100.0 + " m");
                    }
                }
            }
        });
    }

    /**
     * Jusqu'ou le sol est couvert, en partant de l'axe vers {@code cote}, a la
     * rangee {@code i} du ruban. Les intervalles se recollent tant qu'ils se
     * touchent a quinze centimetres pres.
     */
    private double couvertureLaterale(javafx.scene.Node root, Track track, int i, int cote) {
        List<double[]> intervalles = new java.util.ArrayList<>();
        collecterTravees(root, track, i, cote, intervalles);
        intervalles.sort((a, b) -> Double.compare(a[0], b[0]));
        double atteint = 0;
        for (double[] iv : intervalles) {
            if (iv[0] > atteint + 0.15) break;
            atteint = Math.max(atteint, iv[1]);
        }
        return atteint;
    }

    private void collecterTravees(javafx.scene.Node node, Track track, int rangee, int cote,
                                  List<double[]> out) {
        if (node instanceof Group g) {
            for (javafx.scene.Node child : g.getChildren()) {
                collecterTravees(child, track, rangee, cote, out);
            }
            return;
        }
        if (!(node instanceof MeshView v) || !(v.getMesh() instanceof TriangleMesh mesh)) return;
        // seuls les rubans de piste posent deux sommets par rangee du ruban
        if (mesh.getPoints().size() != track.n * 2 * 3) return;
        boolean porteUneFace = false;
        var faces = mesh.getFaces();
        for (int k = 0; k < faces.size() && !porteUneFace; k += 2) {
            porteUneFace = faces.get(k) / 2 == rangee;
        }
        if (!porteUneFace) return;
        double a = lateral(mesh, track, rangee * 2) * cote;
        double b = lateral(mesh, track, rangee * 2 + 1) * cote;
        // la chaussee est un seul ruban qui enjambe l'axe : de ce cote-ci, elle
        // couvre de zero au bord, pas du bord au bord
        if (a < 0 && b < 0) return;
        out.add(new double[]{Math.max(0, Math.min(a, b)), Math.max(a, b)});
    }

    /** Ecart lateral d'un sommet du ruban, lu dans le repere de la rangee. */
    private double lateral(TriangleMesh mesh, Track track, int point) {
        var pts = mesh.getPoints();
        double x = pts.get(point * 3), z = pts.get(point * 3 + 2);
        var centre = track.center[point / 2];
        var cote = track.side[point / 2];
        return (x - centre.x) * cote.x + (z - centre.z) * cote.z;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("aucun vibreur ni caniveau pave sous un ouvrage traverse")
    void pasDeMobilierDeCircuitSousUnOuvrage(TrackDef def) {
        // Le sol d'un ouvrage a longtemps ete verse dans les rubans de la piste
        // comme un revetement de plus. Un ouvrage sans sol propre — six sur
        // huit — ne basculait donc rien : le caniveau de beton et le vibreur
        // peint traversaient le temple et le dome de glace de bout en bout.
        // C'est un ruban unique qui les remplace maintenant sous l'emprise, et
        // ce test verrouille le resultat plutot que le mecanisme : aucune face
        // portant l'une des quatre textures de bordure ne doit tomber sous un
        // ouvrage.
        FxToolkit.run(() -> {
            Textures.setResolution(256);
            Track track = new Track(def);
            Set<Image> bordures = Set.of(Textures.kerb(), Textures.kerbAlt(),
                    Textures.runoff(), Textures.verge(def.theme));
            Group root = TrackNode.build(track, Quality.FLUIDE, 8).root();
            for (int i = 0; i < def.props.length; i++) {
                org.tuxkart.track.Ouvrage o = org.tuxkart.track.Ouvrage.parNom(def.propKinds[i]);
                if (o == null) continue;
                double s0 = def.props[i][0];
                for (double s : abscissesPortantUneBordure(root, track, bordures)) {
                    assertTrue(Math.abs(track.deltaS(s0, s)) >= o.demiLongueur,
                            def.id + " : bordure de circuit a s=" + Math.round(s)
                                    + ", sous " + o.label);
                }
            }
        });
    }

    /** Les abscisses du ruban ou une face porte l'une des textures de bordure. */
    private List<Double> abscissesPortantUneBordure(javafx.scene.Node node, Track track,
                                                    Set<Image> bordures) {
        List<Double> out = new java.util.ArrayList<>();
        collecterBordures(node, track, bordures, out);
        return out;
    }

    private void collecterBordures(javafx.scene.Node node, Track track,
                                   Set<Image> bordures, List<Double> out) {
        if (node instanceof Group g) {
            for (javafx.scene.Node child : g.getChildren()) {
                collecterBordures(child, track, bordures, out);
            }
            return;
        }
        if (!(node instanceof MeshView v) || !(v.getMesh() instanceof TriangleMesh mesh)) return;
        if (!(v.getMaterial() instanceof javafx.scene.paint.PhongMaterial m)) return;
        if (m.getDiffuseMap() == null || !bordures.contains(m.getDiffuseMap())) return;
        // un ruban pose deux sommets par rangee : l'indice de point donne la
        // rangee, donc l'abscisse curviligne de la travee
        var faces = mesh.getFaces();
        for (int k = 0; k < faces.size(); k += 2) {
            int rangee = faces.get(k) / 2;
            if (rangee >= 0 && rangee < track.n) out.add(track.arc[rangee]);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("le terrain passe sous la piste, jamais au travers")
    void leTerrainResteSousLaPiste(TrackDef def) {
        // invariant essentiel : si le relief remonte au-dessus du bitume, il
        // creve la chaussee et le circuit devient injouable
        Track track = new Track(def);
        Terrain terrain = new Terrain(track, Quality.FLUIDE);
        for (double s = 0; s < track.length; s += 7) {
            // la largeur locale, et non la largeur de reference : au-dela du
            // couloir praticable, le relief a le droit de monter — c'est meme
            // ce qu'on lui demande. Un circuit dont le profil se resserre a
            // moins que sa largeur nominale ferait echouer le test pour un
            // point qui n'est deja plus de la chaussee.
            double couloir = track.halfCorridorAtS(s);
            for (double lat : new double[]{-couloir, 0, couloir}) {
                var p = track.worldAt(s, lat);
                double sol = terrain.heightAt(p.x, p.z);
                assertTrue(Double.isFinite(sol), "altitude de terrain non finie");
                assertTrue(sol < p.y + 0.01,
                        def.id + " : le terrain remonte a " + sol
                                + " alors que la piste est a " + p.y);
            }
        }
    }

    @Test
    @DisplayName("la fusion du decor conserve les couleurs et reste dans les bornes")
    void fusionDuDecor() {
        FxToolkit.run(() -> {
            DecorBatch batch = new DecorBatch();
            assertTrue(batch.isEmpty());
            Color[] teintes = {Color.RED, Color.GREEN, Color.BLUE, Color.RED};
            for (int i = 0; i < teintes.length; i++) {
                batch.add(Meshes.sharedSphere(8), 1, 1, 1, 0, i * 30,
                        i * 5, 0, 0, teintes[i]);
            }
            MeshView view = batch.build();
            assertNotNull(view);
            TriangleMesh mesh = (TriangleMesh) view.getMesh();
            verifierMaillage(mesh, "fusion");
            // trois teintes distinctes, la quatriere reutilise la premiere
            assertEquals(3 * 2, mesh.getTexCoords().size(),
                    "la palette doit contenir exactement trois couleurs");
        });
    }

    // ------------------------------------------------------------- outillage

    /** Verifie qu'un maillage est structurellement dessinable. */
    private static void verifierMaillage(TriangleMesh mesh, String nom) {
        int points = mesh.getPoints().size() / 3;
        int normales = mesh.getNormals().size() / 3;
        int texCoords = mesh.getTexCoords().size() / 2;
        assertTrue(points > 0, nom + " : aucun sommet");
        assertTrue(texCoords > 0, nom + " : aucune coordonnee de texture");

        float[] pts = mesh.getPoints().toArray(null);
        for (int i = 0; i < pts.length; i++) {
            assertTrue(Float.isFinite(pts[i]), nom + " : sommet non fini a l'indice " + i);
            assertTrue(Math.abs(pts[i]) < 1e6, nom + " : sommet aberrant " + pts[i]);
        }

        boolean avecNormales = mesh.getVertexFormat() == VertexFormat.POINT_NORMAL_TEXCOORD;
        if (avecNormales) {
            float[] nrm = mesh.getNormals().toArray(null);
            for (int i = 0; i + 2 < nrm.length; i += 3) {
                double len = Math.sqrt(nrm[i] * nrm[i] + nrm[i + 1] * nrm[i + 1]
                        + nrm[i + 2] * nrm[i + 2]);
                assertEquals(1.0, len, 1e-3, nom + " : normale non unitaire a l'indice " + i);
            }
        }

        int stride = mesh.getVertexFormat().getVertexIndexSize();
        int[] faces = mesh.getFaces().toArray(null);
        assertEquals(0, faces.length % (stride * 3), nom + " : faces incompletes");
        for (int i = 0; i < faces.length; i += stride) {
            assertTrue(faces[i] >= 0 && faces[i] < points,
                    nom + " : indice de sommet hors bornes (" + faces[i] + "/" + points + ")");
            if (avecNormales) {
                assertTrue(faces[i + 1] >= 0 && faces[i + 1] < normales,
                        nom + " : indice de normale hors bornes");
                assertTrue(faces[i + 2] >= 0 && faces[i + 2] < texCoords,
                        nom + " : indice de texture hors bornes");
            } else {
                assertTrue(faces[i + 1] >= 0 && faces[i + 1] < texCoords,
                        nom + " : indice de texture hors bornes");
            }
        }
    }

    /** Verifie recursivement tous les maillages d'un sous-arbre. */
    private static int parcourir(javafx.scene.Node node, String nom) {
        int total = 0;
        if (node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            verifierMaillage(mesh, nom);
            total++;
        }
        if (node instanceof Group group) {
            for (javafx.scene.Node child : group.getChildren()) total += parcourir(child, nom);
        }
        return total;
    }

    private static int couleursDistinctes(Image img) {
        PixelReader r = img.getPixelReader();
        Set<Integer> vues = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 3) {
            for (int x = 0; x < img.getWidth(); x += 3) {
                vues.add(r.getArgb(x, y));
                if (vues.size() > 64) return vues.size();
            }
        }
        return vues.size();
    }
}
