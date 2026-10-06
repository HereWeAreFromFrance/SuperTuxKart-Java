package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;
import org.tuxkart.kart.KartDef;
import org.tuxkart.kart.KartRoster;

/** Choix du pilote, avec fiche de caracteristiques et apercu 3D. */
public final class KartSelectScreen extends Screen {

    private final BorderPane root = new BorderPane();
    /**
     * Une {@link javafx.scene.SubScene} n'est pas redimensionnable : sa taille
     * de construction devient la hauteur minimale de ce qui la porte, et un
     * {@code setPrefSize} plus petit sur le conteneur ne l'obtient pas. C'est
     * donc ici que la hauteur de l'apercu se decide, pas sur la boite.
     */
    private final KartPreview preview = new KartPreview(435, 360);
    private final HBox tiles = new HBox(10);
    private final Label name = Ui.labelBold("", 34, Ui.ACCENT);
    private final Label tagline = Ui.label("", 17, Ui.ACCENT_2);
    private final VBox statBox = new VBox(9);
    private int index;

    public KartSelectScreen(TuxKartApp app) {
        super(app);
        root.setBackground(Ui.background());
        index = Math.max(0, KartRoster.ALL.indexOf(app.settings.kart));

        Text title = Ui.title("PILOTES");
        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(14, 0, 4, 0));
        root.setTop(header);

        for (int i = 0; i < KartRoster.ALL.size(); i++) {
            KartDef def = KartRoster.ALL.get(i);
            final int idx = i;
            VBox tile = new VBox(4);
            tile.setAlignment(Pos.CENTER);
            tile.setPrefSize(112, 66);
            Label l = Ui.labelBold(def.name, 17, Ui.TEXT);
            StackPane chip = new StackPane();
            chip.setPrefSize(70, 22);
            chip.setBackground(new Background(new BackgroundFill(def.kartColor,
                    new CornerRadii(4), Insets.EMPTY)));
            tile.getChildren().addAll(chip, l);
            tile.setOnMouseClicked(e -> {
                index = idx;
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            });
            tiles.getChildren().add(tile);
        }
        tiles.setAlignment(Pos.CENTER);

        VBox info = Ui.panel(12);
        info.setPrefWidth(430);
        info.getChildren().addAll(name, tagline, new Label(" "), statBox);

        StackPane previewBox = new StackPane(preview.subScene);
        // Le stage descend a 600 pixels de haut. A 430, l'apercu poussait
        // l'ecran a 671 et le bas se coupait — l'entete, les tuiles et la ligne
        // d'aide ne sont pas compressibles, c'est donc l'apercu qui cede. La
        // proportion est conservee : 520/430 devient 435/360.
        previewBox.setPrefSize(435, 360);

        HBox center = new HBox(40, previewBox, info);
        center.setAlignment(Pos.CENTER);
        center.setFillHeight(false);
        VBox all = new VBox(18, center, tiles);
        all.setAlignment(Pos.CENTER);
        all.setPadding(new Insets(0, 30, 10, 30));
        root.setCenter(all);

        root.setBottom(Ui.hint("Gauche / Droite : changer de pilote     Entrée : valider     Échap : retour"));
        refresh();
    }

    private void refresh() {
        KartDef def = KartRoster.ALL.get(index);
        preview.setKart(def);
        name.setText(def.name);
        tagline.setText(def.tagline);
        statBox.getChildren().setAll(
                Ui.stars("Vitesse", def.speedStars(), Ui.ACCENT),
                Ui.stars("Accélération", def.accelStars(), Ui.ACCENT),
                Ui.stars("Maniabilité", def.handlingStars(), Ui.ACCENT),
                new Label(" "),
                Ui.label(String.format("Pointe : %.0f km/h", def.maxSpeed * 3.6), 15, Ui.DIM),
                Ui.label(String.format("Masse : %.0f kg", def.mass), 15, Ui.DIM),
                Ui.label(String.format("Adhérence : %.0f %%", def.grip * 100), 15, Ui.DIM));

        for (int i = 0; i < tiles.getChildren().size(); i++) {
            VBox tile = (VBox) tiles.getChildren().get(i);
            boolean sel = i == index;
            tile.setBackground(sel
                    ? new Background(new BackgroundFill(Color.rgb(246, 167, 35, 0.22),
                    new CornerRadii(6), Insets.EMPTY))
                    : Background.EMPTY);
            ((Label) tile.getChildren().get(1)).setTextFill(sel ? Ui.ACCENT : Ui.TEXT);
        }
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void update(double dt) {
        preview.update(dt);
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, UP -> {
                index = Math.floorMod(index - 1, KartRoster.ALL.size());
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            }
            case RIGHT, DOWN -> {
                index = Math.floorMod(index + 1, KartRoster.ALL.size());
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            }
            case ENTER, SPACE -> {
                app.settings.kart = KartRoster.ALL.get(index);
                app.sfx(Sfx.MENU_SELECT);
                app.show(new MainMenuScreen(app));
            }
            case ESCAPE -> app.show(new MainMenuScreen(app));
            default -> {
            }
        }
    }
}
