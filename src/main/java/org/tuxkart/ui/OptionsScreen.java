package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;
import org.tuxkart.core.Difficulty;
import org.tuxkart.core.Quality;
import org.tuxkart.core.RenderScale;
import org.tuxkart.render.ChaseCamera;

/** Reglages de la course et du confort de jeu. */
public final class OptionsScreen extends Screen {

    private final BorderPane root = new BorderPane();
    private final MenuList menu = new MenuList();

    public OptionsScreen(TuxKartApp app) {
        super(app);
        root.setBackground(Ui.background());

        Text title = Ui.title("OPTIONS");
        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(40, 0, 10, 0));
        root.setTop(header);

        menu.option("Difficulté", () -> app.settings.difficulty.label, d -> {
            Difficulty[] all = Difficulty.values();
            int i = Math.floorMod(app.settings.difficulty.ordinal() + d, all.length);
            app.settings.difficulty = all[i];
        });
        menu.option("Nombre de tours", () -> String.valueOf(app.settings.laps),
                d -> app.settings.laps = Math.clamp(app.settings.laps + d, 1, 9));
        menu.option("Adversaires", () -> String.valueOf(app.settings.opponents),
                d -> app.settings.opponents = Math.clamp(app.settings.opponents + d, 0, 7));
        menu.option("Caméra par défaut",
                () -> ChaseCamera.MODE_LABELS[app.settings.cameraMode],
                d -> app.settings.cameraMode =
                        Math.floorMod(app.settings.cameraMode + d, ChaseCamera.MODE_COUNT));
        // deux curseurs et non un : le decor et les pixels ne coutent pas a la
        // meme chose, et une machine limitee par l'un ne l'est pas par l'autre
        menu.option("Polygones", () -> app.settings.polygons.label,
                d -> app.settings.polygons = decale(app.settings.polygons, d));
        menu.option("Textures", () -> app.settings.textures.label,
                d -> app.settings.textures = decale(app.settings.textures, d));
        // libellé court : « selon le niveau (200 %) » déborde de la colonne des
        // valeurs, qui tronque alors le pourcentage — la seule information utile
        menu.option("Résolution 3D", () -> app.settings.renderScale < 0
                ? "auto (" + RenderScale.label(app.settings.textures.supersample) + ")"
                : RenderScale.label(app.settings.renderScale), d -> {
            double[] paliers = RenderScale.STEPS;
            int i = 0;
            while (i < paliers.length && paliers[i] != app.settings.renderScale) i++;
            // meme precaution que pour la limite d'images : une valeur venue de
            // -Dtuxkart.ss n'est pas forcement un palier
            if (i == paliers.length) i = 0;
            app.settings.renderScale = paliers[Math.floorMod(i + d, paliers.length)];
        });
        // « selon le niveau (illimitée) » faisait vingt-sept caracteres, la plus
        // longue valeur du menu, et c'est elle qui fixait la largeur d'une
        // colonne. Meme abreviation que la resolution 3D juste au-dessus, pour
        // la meme raison.
        menu.option("Limite d'images", () -> switch (app.settings.frameCap) {
            case -1 -> "auto (" + (app.settings.heaviestQuality().frameCap == 0
                    ? "illimitée" : app.settings.heaviestQuality().frameCap) + ")";
            case 0 -> "illimitée";
            default -> app.settings.frameCap + " / s";
        }, d -> {
            int[] paliers = {-1, 30, 60, 120, 0};
            int i = 0;
            while (i < paliers.length && paliers[i] != app.settings.frameCap) i++;
            // une valeur hors palier (-Dtuxkart.framecap=90) ne doit pas faire
            // partir le curseur d'un cran imprevisible : on repart du debut
            if (i == paliers.length) i = 0;
            app.settings.frameCap = paliers[Math.floorMod(i + d, paliers.length)];
        });
        menu.option("Minicarte", () -> app.settings.showMinimap ? "affichée" : "masquée",
                d -> app.settings.showMinimap = !app.settings.showMinimap);
        menu.option("Son", () -> {
            if (app.audio != null && !app.audio.isAvailable()) return "indisponible";
            return app.settings.sound ? "activé" : "coupé";
        }, d -> {
            app.settings.sound = !app.settings.sound;
            if (app.audio != null) app.audio.setEnabled(app.settings.sound);
        });
        menu.option("Musique", () -> {
            if (app.audio != null && !app.audio.isAvailable()) return "indisponible";
            if (!app.settings.sound) return "coupée (son)";
            return app.settings.music ? "activée" : "coupée";
        }, d -> {
            app.settings.music = !app.settings.music;
            if (app.audio != null) app.audio.setMusicEnabled(app.settings.music);
        });
        menu.action("Retour", () -> app.show(new MainMenuScreen(app)));
        menu.setOnChange(() -> app.sfx(Sfx.MENU_MOVE));

        // Deux colonnes de six. Le menu a grandi jusqu'a douze lignes, soit 869
        // pixels : il debordait de la fenetre de 800 et coupait sa propre ligne
        // d'aide, et le stage descend jusqu'a 600. La coupure tombe entre le
        // dernier reglage de course et le premier d'affichage, ce qui donne
        // deux colonnes qui se lisent.
        VBox panel = Ui.panel(6);
        panel.getChildren().addAll(menu.colonnes(6, 34), new Label(" "),
                Ui.label("Le joueur part toujours en fond de grille.", 14, Ui.DIM));
        // sa largeur naturelle, 864 pixels mesures : le panneau ne doit jamais
        // avoir a rogner ses libelles, et il entre ainsi dans la fenetre la plus
        // etroite que le stage accepte, 900
        panel.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        VBox center = new VBox(panel);
        center.setAlignment(Pos.CENTER);
        root.setCenter(center);

        root.setBottom(Ui.hint("Gauche / Droite : modifier     Entrée : valider     Échap : retour"));
    }

    private static Quality decale(Quality niveau, int d) {
        Quality[] all = Quality.values();
        return all[Math.floorMod(niveau.ordinal() + d, all.length)];
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case UP -> menu.move(-1);
            case DOWN -> menu.move(1);
            case LEFT -> menu.adjust(-1);
            case RIGHT -> menu.adjust(1);
            case ENTER, SPACE -> menu.select();
            case ESCAPE -> app.show(new MainMenuScreen(app));
            default -> {
            }
        }
    }
}
