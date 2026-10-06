package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;

/** Menu principal. */
public final class MainMenuScreen extends Screen {

    private final BorderPane root = new BorderPane();
    private final MenuList menu = new MenuList();
    private final KartPreview preview = new KartPreview(430, 380);
    private final Label summary = Ui.label("", 16, Ui.DIM);

    public MainMenuScreen(TuxKartApp app) {
        super(app);
        root.setBackground(Ui.background());

        Text title = Ui.title("T U X   K A R T");
        Label sub = Ui.label("Clone Java 26 / JavaFX du jeu de kart de Steve Baker", 17, Ui.ACCENT_2);
        VBox header = new VBox(4, title, sub);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(36, 0, 8, 0));
        root.setTop(header);

        menu.action("Course rapide", () -> {
            app.sfx(Sfx.MENU_SELECT);
            RaceScreen.demarrer(app);
        });
        menu.action("Choisir le pilote", () -> {
            app.sfx(Sfx.MENU_SELECT);
            app.show(new KartSelectScreen(app));
        });
        menu.action("Choisir le circuit", () -> {
            app.sfx(Sfx.MENU_SELECT);
            app.show(new TrackSelectScreen(app));
        });
        menu.action("Options", () -> {
            app.sfx(Sfx.MENU_SELECT);
            app.show(new OptionsScreen(app));
        });
        menu.action("Commandes", () -> {
            app.sfx(Sfx.MENU_SELECT);
            app.show(new HelpScreen(app));
        });
        menu.action("Quitter", app::quit);
        menu.setOnChange(() -> app.sfx(Sfx.MENU_MOVE));

        VBox left = Ui.panel(4);
        left.getChildren().addAll(menu.node, new Label(" "), summary);
        left.setAlignment(Pos.CENTER_LEFT);

        StackPane previewBox = new StackPane(preview.subScene);
        previewBox.setPrefSize(430, 380);

        HBox center = new HBox(50, left, previewBox);
        center.setAlignment(Pos.CENTER);
        center.setFillHeight(false);
        center.setPadding(new Insets(10, 40, 10, 40));
        root.setCenter(center);

        root.setBottom(Ui.hint("Flèches : naviguer     Entrée : valider     F11 : plein écran     Échap : quitter"));
        BorderPane.setAlignment(root.getBottom(), Pos.CENTER);

        refresh();
    }

    private void refresh() {
        preview.setKart(app.settings.kart);
        summary.setText(String.format("Pilote : %s     Circuit : %s     %s     %s",
                app.settings.kart.name, app.settings.track.name,
                Ui.laps(app.settings.laps), app.settings.difficulty.label));
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void onShow() {
        refresh();
    }

    @Override
    public void update(double dt) {
        preview.update(dt);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case UP, W -> menu.move(-1);
            case DOWN, S -> menu.move(1);
            case ENTER, SPACE -> menu.select();
            case ESCAPE -> app.quit();
            default -> {
            }
        }
    }
}
