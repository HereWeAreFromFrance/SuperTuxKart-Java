package org.tuxkart.ui;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuxkart.FxToolkit;
import org.tuxkart.TuxKartApp;
import org.tuxkart.core.GameSettings;
import org.tuxkart.core.Quality;
import org.tuxkart.math.Vec3;
import org.tuxkart.race.Race;
import org.tuxkart.render.ChaseCamera;
import org.tuxkart.render.TrackNode;
import org.tuxkart.render.Textures;
import org.tuxkart.track.Track;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rendu reel : on dessine vraiment des images et on regarde ce qu'elles
 * contiennent.
 *
 * C'est la seule facon d'attraper les pannes silencieuses du rendu 3D — une
 * camera mal orientee, un ciel a l'envers, un maillage qu'aucune lumiere
 * n'atteint. Toutes ces erreurs compilent parfaitement et produisent une image
 * noire.
 */
class ScreenRenderTest {

    /**
     * La plus petite <b>scene</b> que le jeu accepte. Ce n'est pas la taille de
     * la fenetre : le gestionnaire ajoute ses decorations autour, et c'est
     * {@code TuxKartApp} qui les mesure a l'affichage pour en tenir compte.
     */
    private static final int MIN_LARGEUR = TuxKartApp.UI_MIN_LARGEUR;
    private static final int MIN_HAUTEUR = TuxKartApp.UI_MIN_HAUTEUR;

    private static final int W = 400;
    private static final int H = 260;

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

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("le circuit rend une image, avec le ciel en haut et la piste en bas")
    void leCircuitSeVoit(TrackDef def) {
        Image[] vues = FxToolkit.call(() -> {
            Textures.setResolution(256);
            Track track = new Track(def);

            // Le vrai luminaire du jeu, et non plus une ambiante et un soleil
            // montes pour le test. C'est la meme scene qui doit tenir : le
            // faux luminaire n'avait pas de lumiere d'appoint, si bien que la
            // chaussee y etait eclairee par la seule ambiante et que ce test
            // mesurait une piste que personne ne voit.
            Group world = new Group(TrackNode.build(track, Quality.FLUIDE, 8).root());
            world.getChildren().addAll(org.tuxkart.render.Lighting.rig(track));

            // Une seule scene, deux points de vue : un Group ne peut pas etre
            // la racine de deux sous-scenes a la fois.
            ChaseCamera cam = new ChaseCamera();
            SubScene sub = new SubScene(world, W, H, true, SceneAntialiasing.DISABLED);
            sub.setCamera(cam.camera);
            // fond volontairement criard : il n'apparait nulle part dans le jeu,
            // donc tout pixel magenta est un trou dans la scene. Avec la vraie
            // couleur de ciel, un monde retourne passait inapercu.
            sub.setFill(Color.MAGENTA);
            Scene scene = new Scene(new Group(sub), W, H);
            return new Image[]{vue(track, scene, cam, track.length), vue(track, scene, cam, 300)};
        });

        Image depart = vues[0];
        assertNotNull(depart);
        assertTrue(couleursDistinctes(depart) > 40,
                def.id + " : image quasi uniforme, rien n'a ete rendu");

        double trous = proportionDeFond(depart);
        assertTrue(trous < 0.12,
                def.id + " : " + Math.round(trous * 100) + " %% de l'image est du vide."
                        + " Le ciel ou le terrain ne couvre pas le champ de vision");

        assertTrue(moyenne(depart, (int) (H * 0.72), H).getBrightness() > 0.03,
                def.id + " : le bas de l'image est noir, aucune lumiere n'atteint la piste");

        // Des objets doivent se decouper sur le fond : barrieres, arbres,
        // portique, mobilier. Sans cette verification, un monde bascule sous le
        // sol passait le test — le ciel et la piste restaient a leur place.
        // On mesure un contraste et non une part de pixels sombres : sur une
        // voute sombre, tout le haut serait sombre et le test ne dirait rien.
        double silhouettes = contrasteEnHaut(depart);
        assertTrue(silhouettes > 0.004,
                def.id + " : rien ne se detache sur le fond, le decor est-il sous le sol ?");

        // Le ciel se mesure en piste, pas sur la ligne : au depart, le haut de
        // l'image est le portique et sa banderole, pas le ciel. Le test le
        // prenait pourtant pour du ciel, et n'y voyait rien tant que la piste
        // etait trop sombre — la chaussee avait ses normales tournees vers le
        // bas et le soleil ne l'atteignait pas. Une fois remise a l'endroit,
        // trois circuits sur cinq avaient leur bitume plus clair que leur
        // « ciel » : Banquise 0,60 contre 0,56, Desert 0,59 contre 0,51,
        // Petit Volcan 0,348 contre 0,348. A trois cents metres de la ligne,
        // le portique est hors champ et le plus faible des cinq ecarts
        // redevient large — 0,77 de ciel contre 0,32 de piste au volcan.
        Image piste = vues[1];
        Color haut = moyenne(piste, 0, (int) (H * 0.18));
        Color bas = moyenne(piste, (int) (H * 0.72), H);
        double ecart = Math.abs(haut.getRed() - bas.getRed())
                + Math.abs(haut.getGreen() - bas.getGreen())
                + Math.abs(haut.getBlue() - bas.getBlue());
        assertTrue(ecart > 0.15,
                def.id + " : le haut et le bas de l'image se ressemblent trop (" + ecart + ")");
        // Dehors, le ciel est toujours plus clair que le sol qu'il eclaire :
        // l'inverse signalerait un monde a l'envers. Dedans, les deux sens sont
        // legitimes — voute sombre d'un donjon, plafond clair d'un salon — et
        // seule l'ampleur de l'ecart, verifiee juste au-dessus, fait invariant.
        if (!def.theme.indoor) {
            assertTrue(haut.getBrightness() > bas.getBrightness(),
                    def.id + " : le ciel devrait etre plus clair que la piste");
        }
    }

    /**
     * Une image du circuit, prise a l'abscisse donnee, camera posee derriere le
     * point comme au depart d'une course.
     */
    private Image vue(Track track, Scene scene, ChaseCamera cam, double s) {
        Vec3 oeil = track.worldAt(s - 14, 0).add(0, 3.2, 0);
        Vec3 cible = track.worldAt(s + 6, 0).add(0, 1.2, 0);
        ChaseCamera.aim(cam.camera, oeil, cible);
        return scene.snapshot(new WritableImage(W, H));
    }

    @Test
    @DisplayName("l'apercu de kart des menus affiche bien un kart")
    void apercuDeKart() {
        Image image = FxToolkit.call(() -> {
            KartPreview preview = new KartPreview(W, H);
            preview.setKart(org.tuxkart.kart.KartRoster.TUX);
            preview.update(0.1);
            Scene scene = new Scene(new Group(preview.subScene), W, H, Color.BLACK);
            return scene.snapshot(new WritableImage(W, H));
        });
        assertTrue(couleursDistinctes(image) > 15, "l'apercu ne montre aucun kart");
        Color centre = moyenne(image, (int) (H * 0.35), (int) (H * 0.75));
        assertTrue(centre.getBrightness() > 0.05, "le kart n'est pas eclaire");
    }

    @Test
    @DisplayName("l'affichage tete haute dessine ses panneaux")
    void athDessine() {
        Image image = FxToolkit.call(() -> {
            GameSettings settings = new GameSettings();
            settings.opponents = 3;
            Race race = new Race(settings);
            for (int i = 0; i < 400; i++) race.update(1.0 / 60);

            Canvas canvas = new Canvas(W, H);
            Hud hud = new Hud();
            hud.renderFps = 60;
            hud.history = new double[]{60, 58, 30, 61};
            hud.draw(canvas.getGraphicsContext2D(), W, H, race, settings);

            var params = new javafx.scene.SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            return canvas.snapshot(params, new WritableImage(W, H));
        });
        assertTrue(couleursDistinctes(image) > 10, "l'ATH n'a rien dessine");
        // le compteur de position occupe le coin superieur gauche
        Color coin = moyenne(image, 5, 40);
        assertTrue(coin.getOpacity() > 0.2, "le panneau de position est absent");
    }

    @Test
    @DisplayName("chaque ecran de menu se construit et se dessine")
    void lesMenusSeConstruisent() {
        FxToolkit.run(() -> {
            TuxKartApp app = new TuxKartApp();
            List<Screen> ecrans = List.of(
                    new MainMenuScreen(app),
                    new KartSelectScreen(app),
                    new TrackSelectScreen(app),
                    new OptionsScreen(app),
                    new HelpScreen(app));
            for (Screen ecran : ecrans) {
                assertNotNull(ecran.node(), ecran.getClass().getSimpleName() + " : pas de racine");
                Scene scene = new Scene(new Group(ecran.node()), 900, 620);
                Image img = scene.snapshot(new WritableImage(900, 620));
                assertTrue(couleursDistinctes(img) > 8,
                        ecran.getClass().getSimpleName() + " : ecran vide");
                ecran.update(1.0 / 60);
            }
        });
    }

    @Test
    @DisplayName("chaque ecran de menu tient dans la fenetre")
    void lesMenusTiennentDansLaFenetre() {
        // L'ecran des options a grandi option par option jusqu'a demander 869
        // pixels de haut pour une fenetre qui en fait 800 : il coupait sa
        // propre ligne d'aide, et personne ne l'avait vu parce qu'on regarde le
        // haut d'un menu, pas son pied. Un menu qui deborde ne se voit pas a la
        // lecture et ne fait echouer aucun test de rendu — celui-ci mesure la
        // hauteur demandee plutot que de regarder l'image.
        //
        // La barre est la plus petite <b>scene</b> que le jeu accepte, et non
        // celle qu'il ouvre : un joueur qui reduit sa fenetre ne merite pas un
        // menu coupe. Mesure faite a cette barre, quatre ecrans sur cinq la
        // franchissaient — 671 pour la table des karts, 689 pour le choix du
        // circuit — parce que l'apercu de kart et le plan du circuit sont poses
        // a une taille fixe. Ce sont eux qui ont cede.
        FxToolkit.run(() -> {
            TuxKartApp app = new TuxKartApp();
            List<Screen> ecrans = List.of(
                    new MainMenuScreen(app),
                    new KartSelectScreen(app),
                    new TrackSelectScreen(app),
                    new OptionsScreen(app),
                    new HelpScreen(app));
            for (Screen ecran : ecrans) {
                javafx.scene.layout.Region racine = (javafx.scene.layout.Region) ecran.node();
                Scene scene = new Scene(new Group(racine), MIN_LARGEUR, MIN_HAUTEUR);
                racine.resize(MIN_LARGEUR, MIN_HAUTEUR);
                racine.applyCss();
                racine.layout();
                double large = racine.prefWidth(-1);
                assertTrue(large <= MIN_LARGEUR,
                        ecran.getClass().getSimpleName() + " : il faut " + Math.round(large)
                                + " px de large pour une scene qui descend a " + MIN_LARGEUR);
                double besoin = racine.prefHeight(MIN_LARGEUR);
                assertTrue(besoin <= MIN_HAUTEUR,
                        ecran.getClass().getSimpleName() + " : il faut " + Math.round(besoin)
                                + " px de haut pour une fenetre qui descend a " + MIN_HAUTEUR);
                assertNotNull(scene);
            }
        });
    }

    @Test
    @DisplayName("aucun libelle de menu n'est tronque")
    void aucunLibelleTronque() {
        // Passer l'ecran des options en deux colonnes a fige la largeur des
        // libelles a 210 pixels pour tout le menu, alors que « Camera par
        // defaut » en demande 230 : les onze valeurs se sont retrouvees coupees
        // par une ellipse, « Resolution 3D au... », « Minicarte affi... ». Rien
        // ne l'a signale — un texte tronque se dessine sans erreur, l'ecran
        // n'est pas vide, et sa hauteur est meme meilleure qu'avant. Il faut
        // donc comparer la largeur accordee a chaque etiquette a celle qu'elle
        // demande.
        FxToolkit.run(() -> {
            TuxKartApp app = new TuxKartApp();
            List<Screen> ecrans = List.of(
                    new MainMenuScreen(app),
                    new KartSelectScreen(app),
                    new TrackSelectScreen(app),
                    new OptionsScreen(app),
                    new HelpScreen(app));
            for (Screen ecran : ecrans) {
                javafx.scene.layout.Region racine = (javafx.scene.layout.Region) ecran.node();
                new Scene(new Group(racine), TuxKartApp.WIDTH, TuxKartApp.HEIGHT);
                racine.resize(TuxKartApp.WIDTH, TuxKartApp.HEIGHT);
                racine.applyCss();
                racine.layout();
                verifierLesEtiquettes(racine, ecran.getClass().getSimpleName());
            }
        });
    }

    private void verifierLesEtiquettes(javafx.scene.Node node, String ecran) {
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node enfant : parent.getChildrenUnmodifiable()) {
                verifierLesEtiquettes(enfant, ecran);
            }
        }
        if (!(node instanceof javafx.scene.control.Label etiquette)) return;
        if (etiquette.getText() == null || etiquette.getText().isBlank()) return;
        // Une etiquette qui s'enroule a le droit d'etre plus etroite que son
        // texte — c'est meme la reponse retenue pour les tuiles du choix de
        // circuit, ou « Colline du Manchot » tient sur deux lignes dans les
        // cent vingt-huit pixels d'une tuile. Ce qu'elle n'a pas le droit
        // d'etre, c'est trop courte : la verification passe donc en hauteur.
        // Une demi-unite de tolerance : la mise en page arrondit au pixel.
        if (etiquette.isWrapText()) {
            assertTrue(etiquette.getHeight()
                            >= etiquette.prefHeight(etiquette.getWidth()) - 0.5,
                    ecran + " : « " + etiquette.getText() + " » enroule mais rogne en"
                            + " hauteur, " + Math.round(etiquette.getHeight()) + " px pour "
                            + Math.round(etiquette.prefHeight(etiquette.getWidth()))
                            + " demandes");
            return;
        }
        assertTrue(etiquette.getWidth() >= etiquette.prefWidth(-1) - 0.5,
                ecran + " : « " + etiquette.getText() + " » tronque, "
                        + Math.round(etiquette.getWidth()) + " px accordes pour "
                        + Math.round(etiquette.prefWidth(-1)) + " demandes");
    }

    @Test
    @DisplayName("l'ecran de course se construit et rend une image")
    void lEcranDeCourseSeRend() {
        Image image = FxToolkit.call(() -> {
            TuxKartApp app = new TuxKartApp();
            app.settings.polygons = Quality.FLUIDE;
            app.settings.textures = Quality.FLUIDE;
            app.settings.opponents = 3;
            // le decor se construit desormais hors du fil JavaFX, derriere un
            // ecran de chargement : le test le fabrique lui-meme et le passe
            org.tuxkart.race.Race race = new org.tuxkart.race.Race(app.settings);
            org.tuxkart.render.Textures.setResolution(app.settings.textures.textureSize);
            RaceScreen ecran = new RaceScreen(app, race,
                    org.tuxkart.render.TrackNode.build(race.track, app.settings.polygons,
                            race.karts.size()));
            Scene scene = new Scene(new Group(ecran.node()), 640, 400);
            // une poignee d'images pour que la camera se place et que l'ATH s'affiche
            for (int i = 0; i < 30; i++) ecran.update(1.0 / 60);
            return scene.snapshot(new WritableImage(640, 400));
        });
        assertNotNull(image);
        assertTrue(couleursDistinctes(image) > 40, "l'ecran de course ne rend rien");
    }

    // ------------------------------------------------------------- outillage

    /** Part de pixels sombres dans la moitie haute : le decor qui depasse l'horizon. */
    /**
     * Part des pixels du haut de l'image qui tranchent nettement sur la moyenne
     * de cette zone : ce qui se decoupe sur le fond, clair sur sombre comme
     * sombre sur clair.
     */
    private static double contrasteEnHaut(Image img) {
        PixelReader r = img.getPixelReader();
        int limite = (int) (img.getHeight() * 0.45);
        double somme = 0;
        int total = 0;
        for (int y = 0; y < limite; y++) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                somme += r.getColor(x, y).getBrightness();
                total++;
            }
        }
        double moyenne = somme / total;
        int tranchent = 0;
        for (int y = 0; y < limite; y++) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                if (Math.abs(r.getColor(x, y).getBrightness() - moyenne) > 0.18) tranchent++;
            }
        }
        return tranchent / (double) total;
    }

    /** Part de l'image restee a la couleur de fond, donc non couverte par la scene. */
    private static double proportionDeFond(Image img) {
        PixelReader r = img.getPixelReader();
        int fond = 0, total = 0;
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                Color c = r.getColor(x, y);
                if (c.getRed() > 0.85 && c.getBlue() > 0.85 && c.getGreen() < 0.25) fond++;
                total++;
            }
        }
        return fond / (double) total;
    }

    private static Color moyenne(Image img, int y0, int y1) {
        PixelReader r = img.getPixelReader();
        double rouge = 0, vert = 0, bleu = 0, alpha = 0;
        int n = 0;
        for (int y = y0; y < y1; y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                Color c = r.getColor(x, y);
                rouge += c.getRed();
                vert += c.getGreen();
                bleu += c.getBlue();
                alpha += c.getOpacity();
                n++;
            }
        }
        return Color.color(rouge / n, vert / n, bleu / n, alpha / n);
    }

    private static int couleursDistinctes(Image img) {
        PixelReader r = img.getPixelReader();
        Set<Integer> vues = new HashSet<>();
        for (int y = 0; y < img.getHeight(); y += 2) {
            for (int x = 0; x < img.getWidth(); x += 2) {
                vues.add(r.getArgb(x, y));
                if (vues.size() > 200) return vues.size();
            }
        }
        return vues.size();
    }
}
