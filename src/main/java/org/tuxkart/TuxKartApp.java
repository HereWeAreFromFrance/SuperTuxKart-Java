package org.tuxkart;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.tuxkart.audio.Audio;
import org.tuxkart.audio.Sfx;
import org.tuxkart.core.GameSettings;
import org.tuxkart.ui.MainMenuScreen;
import org.tuxkart.ui.Screen;

/**
 * Point d'entree JavaFX. L'application heberge un seul {@link Scene} dont on
 * remplace le contenu suivant l'ecran courant, et une unique boucle de jeu.
 *
 * <p>La cadence des battements est laissee a JavaFX. Une version precedente
 * forcait soixante battements par seconde pour corriger un defilement saccade ;
 * c'etait un mauvais remede — la saccade venait de la charge geometrique, et le
 * plafond bridait les ecrans a taux de rafraichissement plus eleve jusqu'a
 * devenir le facteur limitant. Au besoin, {@code -Djavafx.animation.pulse}
 * reste disponible.
 */
public class TuxKartApp extends Application {

    public static final int WIDTH = 1280;
    public static final int HEIGHT = 800;

    /**
     * Ce que les ecrans de menu exigent de la <b>scene</b>, mesure sur eux :
     * 1276 x 595 au pire, arrondi. Le choix du circuit fixe la largeur avec ses
     * neuf vignettes en rang, les commandes la suivent de pres a 1235 ; la
     * hauteur vient des commandes, a 595. En dessous, ca se coupe.
     */
    public static final int UI_MIN_LARGEUR = 1280;
    public static final int UI_MIN_HAUTEUR = 620;

    public final GameSettings settings = new GameSettings();
    public Audio audio;
    /** Mesure fiable des images reellement rendues. */
    public org.tuxkart.core.RenderStats renderStats;
    /** Journal des temps d'image, pour analyse apres coup. */
    public org.tuxkart.core.PerfLog perfLog;

    private Stage stage;
    private Scene scene;
    private final StackPane root = new StackPane();
    private Screen current;
    private long lastNanos;
    private AnimationTimer timer;

    /** Mode demonstration : la course demarre seule et le kart du joueur est pilote par l'IA. */
    public static final boolean DEMO = Boolean.getBoolean("tuxkart.demo");
    /**
     * -Dtuxkart.bench=40 : mesure les temps d'image pendant 40 s (apres une
     * phase de chauffe), affiche des statistiques puis quitte. Contrairement au
     * compteur de l'ATH, qui est lisse et donc trompeur sur les transitoires,
     * on garde ici chaque image pour pouvoir en tirer des centiles.
     */
    private static final double BENCH = Double.parseDouble(
            System.getProperty("tuxkart.bench", "0"));
    private static final double BENCH_WARMUP = 8.0;
    /**
     * -Dtuxkart.fixeddt=0.0167 : chaque image avance la simulation d'un pas
     * constant, quelle que soit sa duree reelle.
     *
     * Avec {@code -Dtuxkart.seed}, deux executions rendent alors exactement la
     * meme suite d'images, et deux captures prises au meme instant de jeu se
     * comparent pixel a pixel. Le compteur d'images reste, lui, mesure sur le
     * temps reel : c'est bien la cadence qu'on veut lire, pas celle du monde
     * simule.
     */
    private static final double FIXED_DT = Double.parseDouble(
            System.getProperty("tuxkart.fixeddt", "0"));
    private static final int BENCH_CAPACITY = 400_000;
    /**
     * Temps d'image et temps de mise a jour du banc d'essai.
     *
     * Alloues seulement quand {@code -Dtuxkart.bench} est demande : six
     * megaoctets reserves pour une mesure qui n'a lieu presque jamais, c'est du
     * gaspillage pur.
     */
    private double[] frameMs;
    private double[] updateMs;
    private int frameCount;
    private double benchWindow;
    /** Derniere cadence rendue connue, echantillonnee par l'ecran de course. */
    public double lastRenderFps = -1;
    /**
     * Duree reelle de la derniere image, en millisecondes.
     *
     * Le pas de temps transmis au jeu est borne a 50 ms pour que la physique
     * reste stable apres un blocage. L'affichage ne doit surtout pas se baser
     * dessus : un a-coup de 300 ms s'y lirait 50, et le compteur mentirait
     * exactement la ou on a besoin de lui.
     */
    public double lastFrameMs;
    private double renderSum;
    private int renderSamples;
    private double renderWorst = -1;

    private double[] shotTimes = new double[0];
    private int shotIndex;
    private double elapsed;
    private final org.tuxkart.core.FrameLimiter limiter = new org.tuxkart.core.FrameLimiter();

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        audio = new Audio();
        audio.setEnabled(settings.sound);
        audio.setMusicEnabled(settings.music);
        parseShotSchedule();
        String t = System.getProperty("tuxkart.track");
        if (t != null) settings.track = org.tuxkart.track.Tracks.byId(t);
        String k = System.getProperty("tuxkart.kart");
        if (k != null) settings.kart = org.tuxkart.kart.KartRoster.byId(k);
        String o = System.getProperty("tuxkart.opponents");
        if (o != null) settings.opponents = Integer.parseInt(o);
        // -Dtuxkart.quality regle les deux axes a la fois : les commandes de
        // capture s'en servent, et elles veulent une scene homogene
        org.tuxkart.core.Quality both = niveau("tuxkart.quality", null);
        if (both != null) {
            settings.polygons = both;
            settings.textures = both;
        }
        settings.polygons = niveau("tuxkart.polygons", settings.polygons);
        settings.textures = niveau("tuxkart.textures", settings.textures);
        String cap = System.getProperty("tuxkart.framecap");
        if (cap != null) settings.frameCap = Integer.parseInt(cap);
        String l = System.getProperty("tuxkart.laps");
        if (l != null) settings.laps = Integer.parseInt(l);

        scene = new Scene(root, WIDTH, HEIGHT, Color.BLACK);
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F11) {
                stage.setFullScreen(!stage.isFullScreen());
                e.consume();
                return;
            }
            if (e.getCode() == KeyCode.F12) {
                java.nio.file.Path p = org.tuxkart.core.Screenshot.capture(scene, null);
                limiter.capture();
                if (p != null) System.out.println("Capture enregistrée : " + p);
                e.consume();
                return;
            }
            if (current != null) current.keyPressed(e);
        });
        scene.setOnKeyReleased(e -> {
            if (current != null) current.keyReleased(e);
        });

        stage.setTitle("TuxKart - Java 26 / JavaFX");
        stage.setScene(scene);
        // Les bornes portent sur la SCENE, pas sur la fenetre — et c'est tout
        // le piege. Elles etaient posees a 900 x 600 sur le stage, decorations
        // comprises : la barre de titre en prend une trentaine, si bien que la
        // scene tombait a 563 et que deux ecrans y perdaient leur ligne d'aide.
        // Pire, le 900 de large etait une affirmation sans fondement : mesure
        // faite, le choix du circuit demande 1276 pixels — neuf vignettes en
        // rang — et les commandes 1235. En dessous, les textes se coupent.
        //
        // Le stage annonce donc ce dont l'interface a besoin, augmente de ce
        // que le gestionnaire de fenetres ajoute autour, mesure a l'affichage.
        stage.setMinWidth(UI_MIN_LARGEUR);
        stage.setMinHeight(UI_MIN_HAUTEUR);
        stage.setOnShown(e -> {
            double cadreL = stage.getWidth() - scene.getWidth();
            double cadreH = stage.getHeight() - scene.getHeight();
            stage.setMinWidth(UI_MIN_LARGEUR + Math.max(0, cadreL));
            stage.setMinHeight(UI_MIN_HAUTEUR + Math.max(0, cadreH));
        });
        stage.setFullScreenExitHint("F11 : plein écran / fenêtre");
        // Sans cela, JavaFX intercepte Echap pour sortir du plein ecran et
        // l'ecran courant ne le voit jamais : en course, la touche de pause
        // renvoyait donc en mode fenetre au lieu de mettre en pause.
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);
        stage.setOnCloseRequest(e -> quit());
        setIcon();
        placeWindow();
        stage.show();
        // Le plein ecran ne peut etre demande qu'une fois la fenetre apparue :
        // pose avant show(), le gestionnaire de fenetres l'ignore purement et
        // simplement (verifie sous Wayland/mutter). La position, elle, est
        // bien prise en compte avant, ce qui suffit a designer le moniteur.
        if (fullScreenWanted()) stage.setFullScreen(true);
        renderStats = new org.tuxkart.core.RenderStats(scene);
        perfLog = org.tuxkart.core.PerfLog.open(
                "pipeline=" + org.tuxkart.core.RenderStats.pipelineName()
                        + " polygones=" + settings.polygons.label
                        + " textures=" + settings.textures.label
                        + " fenetre=" + WIDTH + "x" + HEIGHT);
        System.out.println("Pipeline de rendu : " + org.tuxkart.core.RenderStats.pipelineName()
                + (org.tuxkart.core.RenderStats.isSoftwarePipeline()
                ? "  <-- RENDU LOGICIEL : la 3D sera tres lente, voir le README" : ""));

        showInitialScreen();

        timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                    return;
                }
                double raw = (now - lastNanos) / 1_000_000_000.0;
                lastNanos = now;

                double avance = limiter.accept(raw, settings.effectiveFrameCap());
                if (avance <= 0) return;
                raw = avance;

                // on borne le pas pour que la physique reste stable apres un lag
                double dt = FIXED_DT > 0 ? FIXED_DT : Math.min(raw, 0.05);
                elapsed += dt;
                injectKeys();
                lastFrameMs = raw * 1000;
                long t0 = System.nanoTime();
                if (current != null) current.update(dt);
                injectStall(dt);
                recordFrame(raw, (System.nanoTime() - t0) / 1_000_000.0);
                if (perfLog != null) perfLog.frame(raw, lastRenderFps);
                takeScheduledShot();
                takeShotAtAbscissa();
            }
        };
        timer.start();
    }

    /** -Dtuxkart.stall=250 : bloque le fil applicatif toutes les deux secondes. */
    private static final long STALL_MS = Long.getLong("tuxkart.stall", 0);
    private double stallClock;

    private void injectStall(double dt) {
        if (STALL_MS <= 0) return;
        stallClock += dt;
        if (stallClock < 2.0) return;
        stallClock = 0;
        try {
            Thread.sleep(STALL_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordFrame(double dt, double update) {
        if (BENCH <= 0) return;
        if (elapsed < BENCH_WARMUP) return;
        if (frameMs == null) {
            frameMs = new double[BENCH_CAPACITY];
            updateMs = new double[BENCH_CAPACITY];
        }
        benchWindow += dt;
        if (benchWindow >= 1.0) {
            benchWindow = 0;
            if (renderStats != null && renderStats.isAvailable()) {
                double r = renderStats.renderFps();
                renderSum += r;
                renderSamples++;
                renderWorst = renderWorst < 0 ? r : Math.min(renderWorst, r);
            }
        }
        if (frameCount < frameMs.length) {
            updateMs[frameCount] = update;
            frameMs[frameCount++] = dt * 1000;
        }
        if (elapsed >= BENCH_WARMUP + BENCH) {
            reportBench();
            quit();
        }
    }

    /**
     * Detail des images les plus longues, avec la part passee dans la mise a
     * jour du jeu.
     *
     * <p>C'est ce qui separe les deux causes possibles d'un a-coup : si la mise
     * a jour reste a une milliseconde sur une image de cent cinquante, le temps
     * est parti dans JavaFX — synchronisation de la scene ou rendu — et non
     * dans la simulation. Sans ce detail, la moyenne noie l'evenement : vingt
     * saccades sur deux mille images ne deplacent le total que de deux
     * millisecondes.
     */
    private void reportWorstFrames() {
        Integer[] order = new Integer[frameCount];
        for (int i = 0; i < frameCount; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, b) -> Double.compare(frameMs[b], frameMs[a]));
        int show = Math.min(6, frameCount);
        StringBuilder sb = new StringBuilder("   pires images :");
        for (int i = 0; i < show; i++) {
            int k = order[i];
            sb.append(String.format(java.util.Locale.ROOT, "  %.0f ms (maj %.1f)",
                    frameMs[k], updateMs[k]));
        }
        System.out.println(sb);
    }

    private void reportBench() {
        if (frameMs == null || frameCount < 10) {
            System.out.println("Banc : pas assez d'images.");
            return;
        }
        double[] ms = java.util.Arrays.copyOf(frameMs, frameCount);
        double total = 0, upd = 0;
        for (int i = 0; i < frameCount; i++) {
            total += frameMs[i];
            upd += updateMs[i];
        }
        java.util.Arrays.sort(ms);
        int slow = 0;
        for (double v : ms) {
            if (v > 1000.0 / 30) slow++;
        }
        System.out.printf(java.util.Locale.ROOT,
                "%d images | moyenne %.1f fps | median %.1f fps | 1%% bas %.1f fps"
                        + " | pire image %.1f ms | images sous 30 fps : %d (%.2f %%)%n",
                frameCount, frameCount / (total / 1000.0),
                1000.0 / ms[frameCount / 2],
                1000.0 / ms[(int) (frameCount * 0.99)],
                ms[frameCount - 1], slow, 100.0 * slow / frameCount);
        System.out.printf(java.util.Locale.ROOT,
                "   dont mise a jour (fil applicatif) : %.2f ms/image, soit %.0f %% du temps%n",
                upd / frameCount, 100.0 * upd / total);
        reportWorstFrames();
        if (renderSamples > 0) {
            System.out.printf(java.util.Locale.ROOT,
                    "   IMAGES REELLEMENT RENDUES : %.1f fps en moyenne, %.1f au pire%n",
                    renderSum / renderSamples, renderWorst);
        } else {
            System.out.println("   mesure de rendu indisponible");
        }
    }

    /**
     * -Dtuxkart.monitor=bas|haut|gauche|droite|principal|&lt;n&gt; : choisit l'ecran
     * physique. -Dtuxkart.fullscreen=true : l'occupe entierement.
     *
     * <p>A ne pas confondre avec {@code -Dtuxkart.screen}, qui designe l'ecran
     * *du jeu* (menu, course, options).
     *
     * <p>La position doit etre posee avant {@code show()} : le plein ecran
     * s'applique a l'ecran ou se trouve la fenetre, et la regler apres coup
     * ferait apparaitre le jeu une fraction de seconde sur le mauvais moniteur.
     */
    private void placeWindow() {
        var screens = javafx.stage.Screen.getScreens();
        var bounds = screens.stream().map(javafx.stage.Screen::getVisualBounds).toList();
        int primary = Math.max(0, screens.indexOf(javafx.stage.Screen.getPrimary()));
        int chosen = org.tuxkart.core.Monitors.pick(
                bounds, primary, System.getProperty("tuxkart.monitor"));
        javafx.geometry.Rectangle2D b = bounds.get(chosen);

        stage.setX(b.getMinX() + (b.getWidth() - WIDTH) / 2);
        stage.setY(b.getMinY() + (b.getHeight() - HEIGHT) / 2);
        if (screens.size() > 1) {
            System.out.println("Ecran " + chosen + " : " + (int) b.getWidth() + "x"
                    + (int) b.getHeight() + " en " + (int) b.getMinX() + "," + (int) b.getMinY()
                    + (fullScreenWanted() ? " (plein écran)" : ""));
        }
    }

    private static boolean fullScreenWanted() {
        return Boolean.parseBoolean(System.getProperty("tuxkart.fullscreen", "false"));
    }

    /** -Dtuxkart.screen=kart|track|options|help|race : ouvre directement un ecran. */
    private void showInitialScreen() {
        String want = System.getProperty("tuxkart.screen", DEMO ? "race" : "menu");
        // la course passe par l'ecran de chargement, comme depuis le menu
        if (want.equals("race")) {
            org.tuxkart.ui.RaceScreen.demarrer(this);
            return;
        }
        show(switch (want) {
            case "kart" -> new org.tuxkart.ui.KartSelectScreen(this);
            case "track" -> new org.tuxkart.ui.TrackSelectScreen(this);
            case "options" -> new org.tuxkart.ui.OptionsScreen(this);
            case "help" -> new org.tuxkart.ui.HelpScreen(this);
            default -> new MainMenuScreen(this);
        });
    }

    /**
     * -Dtuxkart.polygons=ULTRA, -Dtuxkart.textures=FLUIDE : un axe de detail.
     * Un nom inconnu ne doit pas faire echouer la partie, seulement se dire.
     */
    private static org.tuxkart.core.Quality niveau(String propriete,
                                                   org.tuxkart.core.Quality defaut) {
        String v = System.getProperty(propriete);
        if (v == null || v.isBlank()) return defaut;
        try {
            return org.tuxkart.core.Quality.valueOf(v.toUpperCase());
        } catch (IllegalArgumentException e) {
            System.err.println("Niveau inconnu : " + v + " (garde "
                    + (defaut == null ? "le reglage courant" : defaut.label) + ")");
            return defaut;
        }
    }

    /** -Dtuxkart.shots=2,6,12 -Dtuxkart.shotDir=/tmp/x : captures automatiques puis sortie. */
    private void parseShotSchedule() {
        String spec = System.getProperty("tuxkart.shots");
        if (spec != null && !spec.isBlank()) {
            String[] parts = spec.split(",");
            shotTimes = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                shotTimes[i] = Double.parseDouble(parts[i].trim());
            }
        }
        String parAbscisse = System.getProperty("tuxkart.shotAtS");
        if (parAbscisse != null && !parAbscisse.isBlank()) {
            String[] parts = parAbscisse.split(",");
            shotAbscissas = new double[parts.length];
            for (int i = 0; i < parts.length; i++) {
                shotAbscissas[i] = Double.parseDouble(parts[i].trim());
            }
        }
    }

    /**
     * -Dtuxkart.shotAtS=495 : capture quand le kart du joueur passe cette
     * abscisse curviligne, plutot qu'a une seconde donnee.
     *
     * <p>Photographier un endroit du circuit — l'interieur d'une grange, la
     * reception d'un tremplin — se faisait jusqu'ici en integrant a la main la
     * vitesse relevee par {@code -Dtuxkart.debug}, ce qui laissait trente
     * metres d'erreur et demandait plusieurs courses de rattrapage. L'abscisse,
     * elle, ne se discute pas : c'est la meme que celle qui pose les ouvrages
     * et les bosses dans la definition du circuit.
     */
    private double[] shotAbscissas = new double[0];
    private int abscissaIndex;
    private double lastPlayerS = -1;

    private void takeShotAtAbscissa() {
        if (abscissaIndex >= shotAbscissas.length) return;
        if (!(current instanceof org.tuxkart.ui.RaceScreen ecran)) return;
        double s = ecran.race().player.loc.s;
        double cible = shotAbscissas[abscissaIndex];
        // on declenche au franchissement, pas a l'approche : sans cela un kart
        // arrete devant la cible photographierait a chaque image
        boolean franchi = lastPlayerS >= 0 && lastPlayerS < cible && s >= cible;
        lastPlayerS = s;
        if (!franchi) return;
        String dir = System.getProperty("tuxkart.shotDir", System.getProperty("java.io.tmpdir"));
        java.nio.file.Path out = java.nio.file.Paths.get(dir,
                "shot-s" + Math.round(cible) + ".png");
        org.tuxkart.core.Screenshot.capture(scene, out);
        limiter.capture();
        System.out.printf("Capture a s=%.0f m -> %s%n", s, out);
        abscissaIndex++;
        if (abscissaIndex >= shotAbscissas.length && shotIndex >= shotTimes.length) quit();
    }

    /**
     * -Dtuxkart.keys=UP,RIGHT : maintient ces touches enfoncees des le depart.
     * Sert a verifier la chaine complete clavier -> commandes -> physique sans
     * avoir a piloter a la main.
     */
    private boolean keysInjected;

    private void injectKeys() {
        String spec = System.getProperty("tuxkart.keys");
        if (spec == null || keysInjected || elapsed < 2.5) return;
        keysInjected = true;
        for (String s : spec.split(",")) {
            KeyCode code = KeyCode.valueOf(s.trim().toUpperCase());
            javafx.event.Event.fireEvent(scene, new javafx.scene.input.KeyEvent(
                    javafx.scene.input.KeyEvent.KEY_PRESSED, "", "", code,
                    false, false, false, false));
        }
        System.out.println("Touches maintenues : " + spec);
    }

    private void takeScheduledShot() {
        if (shotIndex >= shotTimes.length) return;
        if (elapsed < shotTimes[shotIndex]) return;
        String dir = System.getProperty("tuxkart.shotDir", System.getProperty("java.io.tmpdir"));
        java.nio.file.Path out = java.nio.file.Paths.get(dir, "shot-" + shotIndex + ".png");
        org.tuxkart.core.Screenshot.capture(scene, out);
        limiter.capture();
        System.out.println("Capture " + shotIndex + " -> " + out);
        shotIndex++;
        if (shotIndex >= shotTimes.length && abscissaIndex >= shotAbscissas.length) quit();
    }

    private void setIcon() {
        WritableImage icon = new WritableImage(32, 32);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                double dx = (x - 16) / 12.0, dy = (y - 17) / 14.0;
                double d = dx * dx + dy * dy;
                Color c;
                if (d > 1) c = Color.TRANSPARENT;
                else if (dy > 0.15 && Math.abs(dx) < 0.55) c = Color.web("#f6f6f6");
                else if (y > 26 && Math.abs(dx) < 0.7) c = Color.web("#f6a723");
                else c = Color.web("#16161c");
                icon.getPixelWriter().setColor(x, y, c);
            }
        }
        stage.getIcons().add(icon);
    }

    public Stage stage() {
        return stage;
    }

    public void show(Screen next) {
        if (current != null) current.onHide();
        current = next;
        // seul point de passage entre ecrans : c'est ici que le morceau change,
        // et le fondu du moteur audio evite que la bascule claque
        if (audio != null) {
            audio.music(next instanceof org.tuxkart.ui.RaceScreen
                    ? org.tuxkart.audio.Music.RACE : org.tuxkart.audio.Music.MENU);
        }
        // un ecran ne dit rien de ce que tiendra le suivant : la cadence visee
        // repart du plafond du niveau de detail
        limiter.reset();
        root.getChildren().setAll(next.node());
        next.onShow();
        next.node().requestFocus();
    }

    /**
     * Repart du plafond de cadence.
     *
     * <p>Sert pendant le devoilement du decor : ces images-la sont longues par
     * construction et ne disent rien de la cadence que la course tiendra.
     */
    public void resetPacing() {
        limiter.reset();
    }

    public Screen currentScreen() {
        return current;
    }

    public void sfx(Sfx sfx) {
        if (audio != null) audio.play(sfx);
    }

    public void quit() {
        closePerfLog();
        if (timer != null) timer.stop();
        if (audio != null) audio.shutdown();
        javafx.application.Platform.exit();
    }

    private void closePerfLog() {
        if (perfLog != null) {
            System.out.println("Performances : " + perfLog.summary());
            System.out.println("Journal : " + perfLog.path().toAbsolutePath());
            perfLog.close();
            perfLog = null;
        }
    }

    @Override
    public void stop() {
        closePerfLog();
        if (audio != null) audio.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
