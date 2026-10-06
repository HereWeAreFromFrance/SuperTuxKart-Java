package org.tuxkart.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;
import org.tuxkart.items.Pickup;
import org.tuxkart.items.Projectile;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.race.Race;
import org.tuxkart.render.ChaseCamera;
import org.tuxkart.render.KartNode;
import org.tuxkart.render.Effects;
import org.tuxkart.render.Meshes;
import org.tuxkart.render.TrackNode;
import org.tuxkart.render.Textures;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** L'ecran de course : monde 3D, ATH, pause. */
public final class RaceScreen extends Screen {

    private static final class PickupVis {
        Pickup pickup;
        Node node;
        Rotate spin;
        double baseY;
        double phase;
        /**
         * Altitude de repos, calculee une fois pour toutes.
         *
         * Un objet ne bouge jamais : redemander sa position monde a chaque
         * image, c'etait refaire une interpolation le long de l'axe et allouer
         * deux vecteurs pour retrouver un nombre connu d'avance. Seule
         * l'oscillation varie.
         */
        double restY;
    }

    private final StackPane root = new StackPane();
    private final Group world = new Group();
    private final Group pickupGroup = new Group();
    private final Group projectileGroup = new Group();
    private final SubScene subScene;
    private final Canvas hudCanvas = new Canvas(TuxKartApp.WIDTH, TuxKartApp.HEIGHT);
    private final Hud hud = new Hud();
    private final ChaseCamera cam = new ChaseCamera();
    private final Race race;

    private final Map<Kart, KartNode> kartNodes = new IdentityHashMap<>();
    private final Map<Projectile, Node> projectileNodes = new IdentityHashMap<>();
    /**
     * Reserve de visuels de projectiles, construite avec la scene.
     *
     * Fabriquer un maillage en pleine course oblige JavaFX a l'envoyer a la
     * carte graphique au milieu d'une image. Tout est donc pret d'avance et
     * simplement recycle.
     */
    private final java.util.ArrayDeque<Node> homingPool = new java.util.ArrayDeque<>();
    private final java.util.ArrayDeque<Node> sparkPool = new java.util.ArrayDeque<>();
    private final List<PickupVis> pickupVis = new ArrayList<>();

    private final Effects effects;
    /** Ecarte du rendu les troncons de decor hors du champ de vision. */
    private final org.tuxkart.render.DecorCulling culling;
    /** Accroche le decor par paquets, pour ne pas figer la premiere image. */
    private final org.tuxkart.render.DecorLoader loader;
    private final Vec3[][] lastSkid;
    private final double[] emitTimer;

    private final Set<KeyCode> keys = new HashSet<>();
    private boolean firePending;
    private boolean paused;
    private int cameraMode;
    private double playerSteer;
    private double clock;
    private double worstFrame;
    private double worstWindow;
    private double sampleClock;
    /** Durees reelles des dernieres images, en millisecondes. */
    private final double[] frameHistory = new double[150];
    private int historyHead;

    /** -Dtuxkart.debug=1 : trace l'etat du kart du joueur une fois par seconde. */
    private static final boolean DEBUG = System.getProperty("tuxkart.debug") != null;

    /** Interrupteurs de diagnostic : ils servent a isoler la source d'un a-coup. */
    private static final boolean NO_PARTICLES = System.getProperty("tuxkart.noparticles") != null;
    private static final boolean NO_SKID = System.getProperty("tuxkart.noskid") != null;
    private static final boolean NO_PROJECTILES = System.getProperty("tuxkart.noprojectiles") != null;
    private static final boolean NO_PICKUPS = System.getProperty("tuxkart.nopickups") != null;
    private static final boolean NO_HUD = System.getProperty("tuxkart.nohud") != null;

    /** Distance de piste au-dela de laquelle les objets ne sont plus dessines. */
    private static final double PICKUP_VIEW_RANGE = 170;

    private final VBox pauseOverlay;
    private final MenuList pauseMenu = new MenuList();

    /**
     * Prepare une course sans bloquer la fenetre.
     *
     * <p>La construction du decor coutait une seconde au plus haut niveau d'alors — 90 %
     * de la mise en place d'un ecran de course, mesure faite — et se faisait
     * dans une seule image de la boucle de jeu : fenetre figee, sans rien a
     * l'ecran pour le dire. Elle part donc sur un fil de fond, derriere un
     * ecran de chargement, et l'ecran de course n'est construit qu'une fois le
     * decor pret.
     *
     * <p>Fabriquer des noeuds hors du fil JavaFX est permis tant qu'ils ne sont
     * pas accroches a une scene affichee, ce qui est le cas ici. Les textures
     * peintes au Canvas font exception : {@link Textures} les fait peindre par
     * le fil JavaFX et attend, ce qui ne bloque pas puisque celui-ci se
     * contente d'animer l'ecran de chargement.
     */
    public static void demarrer(TuxKartApp app) {
        Race race = new Race(app.settings);
        java.util.concurrent.atomic.AtomicReference<TrackNode.Decor> pret =
                new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<Throwable> rate =
                new java.util.concurrent.atomic.AtomicReference<>();
        Thread builder = Thread.ofPlatform().name("tuxkart-decor").daemon().unstarted(() -> {
            try {
                Textures.setResolution(app.settings.textures.textureSize);
                pret.set(TrackNode.build(race.track, app.settings.polygons, race.karts.size()));
            } catch (Throwable t) {
                rate.set(t);
            }
        });
        app.show(new LoadingScreen(app, race.track.def.name,
                () -> pret.get() != null || rate.get() != null,
                () -> {
                    if (rate.get() != null) {
                        // le circuit n'a pas pu etre construit : on retourne au
                        // menu plutot que de laisser une attente sans fin
                        System.err.println("Construction du circuit impossible : " + rate.get());
                        app.show(new MainMenuScreen(app));
                        return;
                    }
                    app.show(new RaceScreen(app, race, pret.get()));
                }));
        builder.start();
    }

    /** La course en cours : sert aux captures declenchees a une abscisse donnee. */
    public Race race() {
        return race;
    }

    public RaceScreen(TuxKartApp app, Race race, TrackNode.Decor decor) {
        super(app);
        this.race = race;
        this.cameraMode = app.settings.cameraMode;

        world.getChildren().add(decor.root());
        culling = decor.culling();
        // le paysage se pose autour de la grille : l'oeil du joueur et son cap
        Vec3 depart = race.player.pos;
        Vec3 cap = race.player.forwardVec();
        loader = new org.tuxkart.render.DecorLoader(decor.root(),
                depart.x, depart.z, cap.x, cap.z);

        world.getChildren().addAll(org.tuxkart.render.Lighting.rig(race.track));

        for (Kart k : race.karts) {
            KartNode node = new KartNode(k.def);
            kartNodes.put(k, node);
            world.getChildren().add(node.root);
            node.sync(k, 0);
        }

        buildPickups();
        world.getChildren().addAll(pickupGroup, projectileGroup);

        for (int i = 0; i < 8; i++) {
            Node h = makeProjectileNode(Projectile.Kind.HOMING);
            Node s = makeProjectileNode(Projectile.Kind.SPARK);
            h.setVisible(false);
            s.setVisible(false);
            homingPool.add(h);
            sparkPool.add(s);
            projectileGroup.getChildren().addAll(h, s);
        }

        effects = new Effects(race.track.def.theme);
        lastSkid = new Vec3[race.karts.size()][2];
        emitTimer = new double[race.karts.size()];
        if (app.settings.polygons.particles) world.getChildren().add(effects.particles());
        if (app.settings.polygons.skidMarks) world.getChildren().add(effects.skidMarks());

        // -Dtuxkart.ss permet de sonder d'autres facteurs sans recompiler
        double ss = Double.parseDouble(System.getProperty("tuxkart.ss",
                String.valueOf(app.settings.effectiveRenderScale())));
        // Le multi-echantillonnage de JavaFX et le surechantillonnage font le
        // meme travail sur les silhouettes. Les cumuler, c'est demander a la
        // carte graphique plusieurs echantillons par pixel d'une cible qui en
        // compte deja quatre fois trop : on ne garde le premier que lorsque le
        // second est desactive — et que le niveau de textures le demande, car
        // a lui seul il coute la moitie de la cadence.
        boolean msaa = ss <= 1.0 && app.settings.textures.antialias;
        subScene = new SubScene(world, TuxKartApp.WIDTH, TuxKartApp.HEIGHT, true,
                msaa ? SceneAntialiasing.BALANCED : SceneAntialiasing.DISABLED);
        subScene.setCamera(cam.camera);
        subScene.setFill(race.track.def.theme.skyLow);
        // La cible de rendu est bornee : en plein ecran 4K, un facteur 2
        // demanderait 65 megapixels, bien au-dela des limites des pilotes.
        // Le facteur effectif se recalcule donc a chaque redimensionnement.
        javafx.beans.binding.DoubleBinding effective =
                javafx.beans.binding.Bindings.createDoubleBinding(
                        () -> org.tuxkart.core.RenderScale.effective(
                                ss, root.getWidth(), root.getHeight()),
                        root.widthProperty(), root.heightProperty());

        subScene.widthProperty().bind(root.widthProperty().multiply(effective));
        subScene.heightProperty().bind(root.heightProperty().multiply(effective));
        if (ss != 1.0) {
            javafx.scene.transform.Scale shrink = new javafx.scene.transform.Scale();
            shrink.xProperty().bind(javafx.beans.binding.Bindings.divide(1.0, effective));
            shrink.yProperty().bind(javafx.beans.binding.Bindings.divide(1.0, effective));
            subScene.getTransforms().add(shrink);
        }
        // un Group ne gere pas la mise en page : ses bornes sont celles de la
        // SubScene une fois reduite, donc exactement la taille de la fenetre
        Group subHolder = new Group(subScene);
        // Ni la vue 3D ni l'ATH ne participent au calcul de la mise en page.
        // Sans cela, leur taille preferee — qui est leur taille, elle-meme liee
        // a celle de la racine — la renvoyait dans la racine : celle-ci
        // grossissait d'un pixel par image, indefiniment. Mesure faite, la
        // racine passait de 1930 a 2229 pixels en cinq secondes, la 3D etait
        // rendue de plus en plus large pour n'en montrer qu'un recadrage
        // centre — un zoom lent — et le canvas de l'ATH, redessine et renvoye
        // a la carte graphique a chaque image, enflait d'autant.
        subHolder.setManaged(false);
        hudCanvas.setManaged(false);

        hudCanvas.widthProperty().bind(root.widthProperty());
        hudCanvas.heightProperty().bind(root.heightProperty());
        hudCanvas.setMouseTransparent(true);

        pauseMenu.action("Reprendre", this::resume);
        pauseMenu.action("Recommencer", () -> RaceScreen.demarrer(app));
        pauseMenu.action("Changer de caméra", this::cycleCamera);
        pauseMenu.action("Retour au menu", () -> app.show(new MainMenuScreen(app)));
        pauseMenu.setOnChange(() -> app.sfx(Sfx.MENU_MOVE));

        VBox panel = Ui.panel(6);
        panel.getChildren().addAll(
                Ui.labelBold("PAUSE", 34, Ui.ACCENT),
                new Label(" "),
                pauseMenu.node);
        panel.setMaxWidth(430);
        panel.setMaxHeight(300);
        pauseOverlay = new VBox(panel);
        pauseOverlay.setAlignment(Pos.CENTER);
        pauseOverlay.setStyle("-fx-background-color: rgba(4,10,20,0.66);");
        pauseOverlay.setVisible(false);

        javafx.scene.layout.Region vignette = new javafx.scene.layout.Region();
        vignette.setMouseTransparent(true);
        vignette.setStyle("-fx-background-color: radial-gradient(center 50% 52%, radius 72%,"
                + " transparent 52%, rgba(0,0,0,0.30) 100%);");
        // le degrade ne change jamais : on le fige en image plutot que de le
        // recomposer plein ecran a chaque battement
        vignette.setCache(true);
        vignette.setCacheHint(javafx.scene.CacheHint.SPEED);

        root.getChildren().addAll(subHolder, vignette, hudCanvas, pauseOverlay);
        if (TuxKartApp.DEMO) race.enableAutopilot();
        cam.snapTo(race.player, cameraMode);
    }

    // ------------------------------------------------------------- decor 3D

    private void buildPickups() {
        int i = 0;
        for (Pickup p : race.items.pickups) {
            PickupVis vis = new PickupVis();
            vis.pickup = p;
            vis.phase = (i++ % 7) * 0.9;
            Group g = new Group();
            Rotate spin = new Rotate(0, Rotate.Y_AXIS);
            g.getTransforms().add(spin);
            vis.spin = spin;

            if (p.kind == Pickup.Kind.BOX) {
                Box cube = new Box(1.5, 1.5, 1.5);
                cube.setMaterial(Meshes.material(Textures.itemBox()));
                Box ring = new Box(1.78, 0.16, 1.78);
                ring.setMaterial(Meshes.material(Color.web("#f6a723")));
                Box ring2 = new Box(0.16, 1.78, 1.78);
                ring2.setMaterial(Meshes.material(Color.web("#f6a723")));
                g.getChildren().addAll(cube, ring, ring2);
                vis.baseY = 1.20;
            } else {
                // hareng : corps fuselé, nageoire caudale et dorsale, oeil
                var scale = Meshes.material(p.kind.color);
                var darker = Meshes.material(p.kind.color.deriveColor(0, 1, 0.72, 1));
                Sphere body = new Sphere(0.40, 16);
                body.setMaterial(scale);
                body.setScaleX(0.55);
                body.setScaleY(0.72);
                body.setScaleZ(1.55);

                Box tailTop = new Box(0.05, 0.34, 0.30);
                tailTop.setMaterial(darker);
                tailTop.setTranslateZ(-0.70);
                tailTop.setTranslateY(Meshes.jy(0.10));
                Box tailBottom = new Box(0.05, 0.26, 0.26);
                tailBottom.setMaterial(darker);
                tailBottom.setTranslateZ(-0.68);
                tailBottom.setTranslateY(Meshes.jy(-0.09));

                Box dorsal = new Box(0.04, 0.22, 0.34);
                dorsal.setMaterial(darker);
                dorsal.setTranslateY(Meshes.jy(0.26));
                dorsal.setTranslateZ(-0.02);

                Sphere eye = new Sphere(0.065, 8);
                eye.setMaterial(Meshes.material(Color.web("#141414")));
                eye.setTranslateZ(0.42);
                eye.setTranslateX(0.14);
                eye.setTranslateY(Meshes.jy(0.09));
                Sphere eye2 = new Sphere(0.065, 8);
                eye2.setMaterial(Meshes.material(Color.web("#141414")));
                eye2.setTranslateZ(0.42);
                eye2.setTranslateX(-0.14);
                eye2.setTranslateY(Meshes.jy(0.09));

                g.getChildren().addAll(body, tailTop, tailBottom, dorsal, eye, eye2);
                vis.baseY = 0.85;
            }

            Vec3 w = race.track.worldAt(p.s, p.lateral);
            g.setTranslateX(w.x);
            g.setTranslateZ(w.z);
            g.setTranslateY(Meshes.jy(w.y + vis.baseY));
            vis.restY = w.y + vis.baseY;
            vis.node = g;
            pickupGroup.getChildren().add(g);
            pickupVis.add(vis);
        }
    }

    /**
     * Un projectile mesure quarante centimetres et passe a toute allure : ses
     * subdivisions sont donnees explicitement, comme partout ailleurs dans le
     * jeu. Laisser celles par defaut de JavaFX — soixante-quatre — coutait
     * quatre mille triangles par sphere, soit cent trente mille pour la seule
     * reserve de projectiles : plus que le decor complet du mode Fluide, pour
     * seize objets presque toujours invisibles.
     */
    private Node makeProjectileNode(Projectile.Kind kind) {
        Group g = new Group();
        if (kind == Projectile.Kind.HOMING) {
            Cylinder body = new Cylinder(0.24, 1.1, 12);
            body.setMaterial(Meshes.material(Color.web("#cfe9f7")));
            body.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            Sphere tip = new Sphere(0.26, 12);
            tip.setMaterial(Meshes.material(Color.web("#e05a4a")));
            tip.setTranslateZ(0.6);
            Sphere flame = new Sphere(0.30, 12);
            flame.setMaterial(Meshes.material(Color.web("#ffb347")));
            flame.setTranslateZ(-0.72);
            g.getChildren().addAll(body, tip, flame);
        } else {
            Sphere ball = new Sphere(0.40, 14);
            ball.setMaterial(Meshes.material(Color.web("#b06bff")));
            Sphere halo = new Sphere(0.62, 12);
            halo.setMaterial(Meshes.material(Color.rgb(200, 150, 255, 0.35)));
            g.getChildren().addAll(ball, halo);
        }
        g.setUserData(kind);
        return g;
    }

    // ---------------------------------------------------------------- boucle

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void onShow() {
        root.requestFocus();
    }

    /**
     * Relache la scene 3D.
     *
     * Un circuit represente plusieurs centaines de milliers de triangles et une
     * dizaine de textures ; « Recommencer » construit un ecran neuf, et sans ce
     * nettoyage l'ancien decor restait accroche au graphe le temps que le
     * ramasse-miettes s'en apercoive — deux circuits en memoire pour un seul
     * affiche.
     */
    @Override
    public void onHide() {
        if (app.audio != null) app.audio.engine(false, 0, 0);
        world.getChildren().clear();
        pickupGroup.getChildren().clear();
        projectileGroup.getChildren().clear();
        kartNodes.clear();
        projectileNodes.clear();
        pickupVis.clear();
        homingPool.clear();
        sparkPool.clear();
        subScene.setRoot(new Group());
    }

    @Override
    public void update(double dt) {
        clock += dt;
        // Le paysage s'accroche par paquets : tant qu'il n'est pas complet, la
        // course attend. Donner le depart sur un circuit a moitie pose serait
        // pire que l'attente, et le decompte defilerait pendant les images
        // longues de la mise en place.
        if (!loader.done()) {
            loader.step();
            app.resetPacing();
            hud.loading = loader.progress();
            drawHud();
            return;
        }
        hud.loading = -1;
        if (!paused) {
            if (!race.hasAutopilot()) applyPlayerInput(dt);
            race.update(dt);
            drainSounds();
            syncWorld(dt);
            updateEffects(dt);
            cam.update(race.player, dt, cameraMode, keys.contains(KeyCode.B));
            updateAudio();

            // la course decide elle-meme quand il n'y a plus rien a montrer
            if (race.state == Race.State.OVER) showResults();
        }
        if (DEBUG && (int) clock != (int) (clock - dt)) tracePlayer();
        // On mesure la duree REELLE de l'image, pas le pas de temps borne
        // transmis a la physique : c'est le seul chiffre qui corresponde a ce
        // que l'oeil percoit.
        double reelMs = app.lastFrameMs > 0 ? app.lastFrameMs : dt * 1000;
        if (reelMs > 0) {
            double instant = 1000 / reelMs;
            hud.fps = hud.fps <= 0 ? instant : hud.fps * 0.92 + instant * 0.08;
        }
        frameHistory[historyHead] = reelMs;
        historyHead = (historyHead + 1) % frameHistory.length;
        hud.history = frameHistory;
        hud.historyHead = historyHead;

        worstFrame = Math.max(worstFrame, reelMs);
        worstWindow += dt;
        if (worstWindow >= 1.0) {
            hud.worstMs = worstFrame;
            worstFrame = 0;
            worstWindow = 0;
        }

        sampleClock += dt;
        if (sampleClock >= 0.25 && app.renderStats != null && app.renderStats.isAvailable()) {
            sampleClock = 0;
            hud.renderFps = app.renderStats.renderFps();
            app.lastRenderFps = hud.renderFps;
        }

        drawHud();
    }

    private void drawHud() {
        if (NO_HUD) return;
        hud.draw(hudCanvas.getGraphicsContext2D(),
                hudCanvas.getWidth(), hudCanvas.getHeight(), race, app.settings);
    }

    private void applyPlayerInput(double dt) {
        Kart p = race.player;
        p.controls.reset();

        double target = 0;
        if (keys.contains(KeyCode.LEFT) || keys.contains(KeyCode.A)) target -= 1;
        if (keys.contains(KeyCode.RIGHT) || keys.contains(KeyCode.D)) target += 1;
        playerSteer = MathUtil.approach(playerSteer, target, (target == 0 ? 7.0 : 4.5) * dt);
        p.controls.steer = Math.clamp(playerSteer, -1, 1);

        p.controls.accel = keys.contains(KeyCode.UP);
        p.controls.brake = keys.contains(KeyCode.DOWN);
        p.controls.skid = keys.contains(KeyCode.CONTROL) || keys.contains(KeyCode.X);
        p.controls.wheelie = keys.contains(KeyCode.W);
        p.controls.jump = keys.contains(KeyCode.J);
        p.controls.rescue = keys.contains(KeyCode.R);
        p.controls.fire = firePending;
        firePending = false;
    }

    /** Poussiere, fumee de gomme, etincelles et traces au sol. */
    private void updateEffects(double dt) {
        boolean wantParticles = app.settings.polygons.particles && !NO_PARTICLES;
        boolean wantMarks = app.settings.polygons.skidMarks && !NO_SKID;
        if (!wantParticles && !wantMarks) return;

        for (int i = 0; i < race.karts.size(); i++) {
            Kart k = race.karts.get(i);
            if (k.beingRescued()) {
                lastSkid[i][0] = null;
                continue;
            }
            Vec3 f = k.forwardVec();
            Vec3 sd = k.sideVec();
            double bx = k.pos.x - f.x * 1.0;
            double bz = k.pos.z - f.z * 1.0;
            Vec3 left = new Vec3(bx - sd.x * 0.88, k.pos.y, bz - sd.z * 0.88);
            Vec3 right = new Vec3(bx + sd.x * 0.88, k.pos.y, bz + sd.z * 0.88);

            boolean fast = Math.abs(k.speed) > 6;
            boolean marking = wantMarks && fast && k.onRoad() && k.skidAmount > 0.35;
            if (marking && lastSkid[i][0] != null
                    && lastSkid[i][0].distanceXZ(left) > 0.3) {
                double hw = 0.16;
                effects.addSkid(
                        lastSkid[i][0].add(-sd.x * hw, 0, -sd.z * hw),
                        lastSkid[i][0].add(sd.x * hw, 0, sd.z * hw),
                        left.add(-sd.x * hw, 0, -sd.z * hw),
                        left.add(sd.x * hw, 0, sd.z * hw));
                effects.addSkid(
                        lastSkid[i][1].add(-sd.x * hw, 0, -sd.z * hw),
                        lastSkid[i][1].add(sd.x * hw, 0, sd.z * hw),
                        right.add(-sd.x * hw, 0, -sd.z * hw),
                        right.add(sd.x * hw, 0, sd.z * hw));
                lastSkid[i][0] = left;
                lastSkid[i][1] = right;
            } else if (!marking || lastSkid[i][0] == null) {
                lastSkid[i][0] = marking ? left : null;
                lastSkid[i][1] = marking ? right : null;
            }

            if (!wantParticles) continue;
            emitTimer[i] -= dt;
            if (emitTimer[i] > 0) continue;
            if (!k.onRoad() && fast) {
                effects.dust(left.x, left.y, left.z, Math.abs(k.speed), f.x, f.z);
                effects.dust(right.x, right.y, right.z, Math.abs(k.speed), f.x, f.z);
                emitTimer[i] = 0.07;
            } else if (k.skidAmount > 0.45 && fast) {
                effects.smoke(left.x, left.y, left.z);
                effects.smoke(right.x, right.y, right.z);
                emitTimer[i] = 0.09;
            }
            // pas de fumee de turbo : a pleine vitesse les bouffees s'espacaient
            // d'un metre et demi et formaient une chenille de boules blanches
            if (k.wallHit && Math.abs(k.speed) > 8) {
                effects.sparks(k.pos.x + f.x, k.pos.y, k.pos.z + f.z, f.x, f.z);
                emitTimer[i] = 0.12;
            }
        }
        effects.update(dt);
    }

    private void drainSounds() {
        for (Sfx s : race.sounds) {
            app.sfx(s);
            // les evenements marquants sont reportes dans le journal : c'est ce
            // qui permet de correler une saccade avec ce qui se passait
            if (app.perfLog != null && (s == Sfx.LAP || s == Sfx.GO || s == Sfx.HIT
                    || s == Sfx.FIRE || s == Sfx.BOX)) {
                app.perfLog.event(s.name().toLowerCase(java.util.Locale.ROOT));
            }
            if (s == Sfx.HIT) cam.addShake(0.9);
            if (s == Sfx.BUMP) cam.addShake(0.35);
        }
        race.sounds.clear();
    }

    private void updateAudio() {
        if (app.audio == null) return;
        Kart p = race.player;
        boolean on = race.state != Race.State.OVER;
        app.audio.engine(on, p.engineLoad(), p.skidAmount * (p.onRoad() ? 1 : 0.4));
    }

    private void syncWorld(double dt) {
        Vec3 eye = cam.position();
        // Pendant le decompte, tout le decor reste a l'ecran : c'est la que
        // JavaFX convertit chaque maillage dans son format interne, au premier
        // rendu. Trier des le depart repousserait cette conversion au moment ou
        // le troncon entre dans le champ, en pleine course, et chacun coute une
        // centaine de millisecondes.
        if (race.state != Race.State.COUNTDOWN) {
            Vec3 aim = cam.target();
            culling.update(eye.x, eye.z, aim.x - eye.x, aim.z - eye.z);
        }
        // on parcourt race.karts, pas la table : l'ordre d'iteration d'une
        // IdentityHashMap depend des adresses memoire et change d'une
        // execution a l'autre, ce qui suffisait a rendre deux courses de meme
        // graine visuellement differentes
        for (Kart k : race.karts) {
            KartNode node = kartNodes.get(k);
            node.sync(k, dt);
            // un kart colle a la camera masque toute la vue : on l'escamote
            boolean tooClose = k != race.player
                    && Math.hypot(k.pos.x - eye.x, k.pos.z - eye.z) < 3.6;
            node.root.setVisible(!tooClose);
        }

        Kart player = race.player;
        if (NO_PICKUPS) return;
        for (PickupVis v : pickupVis) {
            // les objets sont des centaines : hors de portee de vue, on les
            // masque plutot que de les faire dessiner
            double gap = Math.abs(race.track.deltaS(player.loc.s, v.pickup.s));
            boolean avail = v.pickup.available() && gap < PICKUP_VIEW_RANGE;
            v.node.setVisible(avail);
            if (!avail) continue;
            v.spin.setAngle((clock * 95 + v.phase * 60) % 360);
            if (v.pickup.kind == Pickup.Kind.BOX) {
                v.node.setRotationAxis(Rotate.X_AXIS);
                v.node.setRotate(Math.sin(clock * 1.3 + v.phase) * 14);
            }
            v.node.setTranslateY(Meshes.jy(
                    v.restY + Math.sin(clock * 2.4 + v.phase) * 0.14));
        }

        // projectiles : creation / suppression a la volee
        projectileNodes.keySet().removeIf(p -> {
            if (race.projectiles.contains(p)) return false;
            Node libere = projectileNodes.get(p);
            libere.setVisible(false);
            (libere.getUserData() == Projectile.Kind.HOMING ? homingPool : sparkPool).add(libere);
            return true;
        });
        if (NO_PROJECTILES) return;
        for (Projectile p : race.projectiles) {
            Node n = projectileNodes.get(p);
            if (n == null) {
                n = (p.kind == Projectile.Kind.HOMING ? homingPool : sparkPool).poll();
                if (n == null) continue;
                n.setVisible(true);
                projectileNodes.put(p, n);
            }
            n.setTranslateX(p.pos.x);
            n.setTranslateZ(p.pos.z);
            n.setTranslateY(Meshes.jy(p.pos.y));
            n.setRotationAxis(Rotate.Y_AXIS);
            n.setRotate(Math.toDegrees(p.heading));
        }
    }

    // ----------------------------------------------------------------- input

    @Override
    public void keyPressed(KeyEvent e) {
        KeyCode c = e.getCode();
        if (paused) {
            switch (c) {
                case UP -> pauseMenu.move(-1);
                case DOWN -> pauseMenu.move(1);
                case ENTER, SPACE -> pauseMenu.select();
                case ESCAPE, P -> resume();
                default -> {
                }
            }
            return;
        }

        keys.add(c);
        switch (c) {
            case SPACE -> firePending = true;
            case C -> cycleCamera();
            case M -> app.settings.showMinimap = !app.settings.showMinimap;
            case ESCAPE, P -> pause();
            case ENTER -> {
                if (race.state == Race.State.PLAYER_FINISHED
                        || race.state == Race.State.OVER) showResults();
            }
            default -> {
            }
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        keys.remove(e.getCode());
    }

    private void tracePlayer() {
        Kart p = race.player;
        System.out.printf("t=%.0f  %.0f fps  touches=%s  v=%.1f m/s  lateral=%.1f m  "
                        + "spin=%.1f  sauvetage=%.1f  muret=%s%n",
                clock, hud.fps, keys, p.speed, p.loc.lateral, p.spinTimer,
                p.rescueTimer, p.wallHit);
        System.out.printf("        decor : %d/%d troncons dessines (moyenne %.1f)%n",
                culling.visible(), culling.total(), culling.averageVisible());
    }

    private void cycleCamera() {
        cameraMode = (cameraMode + 1) % ChaseCamera.MODE_COUNT;
        app.settings.cameraMode = cameraMode;
        cam.snapTo(race.player, cameraMode);
        app.sfx(Sfx.MENU_MOVE);
    }

    private void pause() {
        paused = true;
        keys.clear();
        pauseOverlay.setVisible(true);
        pauseMenu.refresh();
        if (app.audio != null) app.audio.engine(false, 0, 0);
    }

    private void resume() {
        paused = false;
        pauseOverlay.setVisible(false);
        root.requestFocus();
    }

    private void showResults() {
        if (app.audio != null) app.audio.engine(false, 0, 0);
        app.show(new ResultsScreen(app, race));
    }
}
