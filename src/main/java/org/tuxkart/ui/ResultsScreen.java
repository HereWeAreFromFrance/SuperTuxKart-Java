package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.race.Race;

import java.util.List;

/** Classement final de la course. */
public final class ResultsScreen extends Screen {

    private final BorderPane root = new BorderPane();
    private final MenuList menu = new MenuList();

    public ResultsScreen(TuxKartApp app, Race race) {
        super(app);
        root.setBackground(Ui.background());

        Kart player = race.player;
        String headline = player != null && player.position == 1
                ? "VICTOIRE !"
                : "ARRIVÉE";
        Text title = Ui.title(headline);
        Label sub = Ui.label(race.track.def.name + "  -  " + Ui.laps(race.totalLaps)
                + "  -  difficulté " + race.difficulty.label, 18, Ui.ACCENT_2);
        VBox header = new VBox(2, title, sub);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(34, 0, 8, 0));
        root.setTop(header);

        GridPane table = new GridPane();
        table.setHgap(30);
        table.setVgap(8);
        String[] cols = {"", "Pilote", "Temps", "Meilleur tour", "Harengs"};
        for (int c = 0; c < cols.length; c++) {
            table.add(Ui.labelBold(cols[c], 16, Ui.DIM), c, 0);
        }

        List<Kart> order = race.standings();
        for (int i = 0; i < order.size(); i++) {
            Kart k = order.get(i);
            Color col = k.human ? Ui.ACCENT : Ui.TEXT;
            table.add(Ui.labelBold(Hud.ordinal(i + 1), 19, col), 0, i + 1);
            table.add(Ui.labelBold(k.def.name, 19, col), 1, i + 1);
            // les concurrents encore en piste ne sont pas des abandons : la
            // course bascule sur les resultats quelques secondes apres
            // l'arrivee du joueur, sans les attendre
            table.add(Ui.label(k.finished ? MathUtil.formatTime(k.finishTime) : "en course",
                    18, col), 2, i + 1);
            table.add(Ui.label(k.bestLap == Double.MAX_VALUE ? "--:--.--"
                    : MathUtil.formatTime(k.bestLap), 18, col), 3, i + 1);
            table.add(Ui.label(String.valueOf(k.herrings), 18, col), 4, i + 1);
        }

        VBox panel = Ui.panel(16);
        panel.getChildren().add(table);
        panel.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);

        menu.action("Rejouer ce circuit", () -> {
            app.sfx(Sfx.MENU_SELECT);
            RaceScreen.demarrer(app);
        });
        menu.action("Changer de circuit", () -> {
            app.sfx(Sfx.MENU_SELECT);
            app.show(new TrackSelectScreen(app));
        });
        menu.action("Menu principal", () -> app.show(new MainMenuScreen(app)));
        menu.setOnChange(() -> app.sfx(Sfx.MENU_MOVE));

        menu.node.setMaxWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        VBox center = new VBox(24, panel, menu.node);
        center.setAlignment(Pos.CENTER);
        center.setPadding(new Insets(4, 20, 10, 20));
        root.setCenter(center);

        root.setBottom(Ui.hint("Flèches : naviguer     Entrée : valider     Échap : menu principal"));
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
            case ENTER, SPACE -> menu.select();
            case ESCAPE -> app.show(new MainMenuScreen(app));
            default -> {
            }
        }
    }
}
