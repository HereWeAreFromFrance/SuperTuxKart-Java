package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.items.ItemType;
import org.tuxkart.items.Pickup;

/** Rappel des commandes et description des objets. */
public final class HelpScreen extends Screen {

    private static final String[][] KEYS = {
            {"Flèche haut", "Accélérer"},
            {"Flèche bas", "Freiner puis reculer"},
            {"Flèches gauche / droite", "Tourner"},
            {"Ctrl gauche  ou  X", "Déraper (skid)"},
            {"Espace", "Utiliser l'objet"},
            {"W", "Wheelie : plus vite, moins maniable"},
            {"J", "Sauter"},
            {"R", "Sauvetage : replace le kart sur la piste"},
            {"B", "Regarder derrière"},
            {"C", "Changer de caméra"},
            {"M", "Afficher / masquer la minicarte"},
            {"P  ou  Échap", "Pause"},
            {"F11", "Plein écran"},
    };

    private final BorderPane root = new BorderPane();

    public HelpScreen(TuxKartApp app) {
        super(app);
        root.setBackground(Ui.background());

        Text title = Ui.title("COMMANDES");
        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(16, 0, 4, 0));
        root.setTop(header);

        GridPane grid = new GridPane();
        grid.setHgap(28);
        grid.setVgap(7);
        for (int i = 0; i < KEYS.length; i++) {
            Label k = Ui.labelBold(KEYS[i][0], 17, Ui.ACCENT);
            k.setMinWidth(230);
            grid.add(k, 0, i);
            grid.add(Ui.label(KEYS[i][1], 17, Ui.TEXT), 1, i);
        }
        VBox left = Ui.panel(10);
        left.getChildren().addAll(Ui.labelBold("Au volant", 22, Ui.ACCENT_2), grid);

        VBox right = Ui.panel(10);
        right.getChildren().add(Ui.labelBold("Objets", 22, Ui.ACCENT_2));
        for (ItemType t : ItemType.values()) {
            Label l = Ui.labelBold(t.label, 18, t.color);
            Label d = Ui.label(describe(t) + "   (« " + t.originalName + " »)", 14, Ui.DIM);
            VBox v = new VBox(1, l, d);
            right.getChildren().add(v);
        }
        right.getChildren().add(new Label(" "));
        right.getChildren().add(Ui.labelBold("Harengs", 22, Ui.ACCENT_2));
        for (Pickup.Kind k : Pickup.Kind.values()) {
            if (k == Pickup.Kind.BOX) continue;
            right.getChildren().add(Ui.label(k.label + " : " + describe(k), 15, k.color));
        }

        // Les deux cartes s'alignent par le haut. Centrees, elles ne
        // s'alignaient par aucun bout, la droite depassant d'une dizaine de
        // pixels en haut comme en bas. Les etirer a la meme hauteur serait
        // pire : a huit cents pixels de fenetre elles feraient six cent
        // soixante-dix de haut pour quatre cent soixante-six de contenu, soit
        // deux cadres a moitie vides. Qu'elles finissent a des hauteurs
        // differentes est honnete — la droite en dit plus que la gauche.
        HBox center = new HBox(34, left, right);
        center.setAlignment(Pos.TOP_CENTER);
        center.setFillHeight(false);
        center.setPadding(new Insets(2, 24, 2, 24));
        // Le TOP_CENTER ci-dessus aligne les deux cartes entre elles, mais il
        // colle aussi le bloc en haut de la zone centrale : a huit cents pixels
        // de fenetre, il laissait deux cent vingt-cinq pixels de fond vide
        // au-dessus de la ligne d'aide. Les deux reglages ne sont pas le meme,
        // et il en faut donc deux : les cartes s'alignent par le haut a
        // l'interieur d'un bloc qui, lui, reste centre.
        VBox bloc = new VBox(center);
        bloc.setAlignment(Pos.CENTER);
        root.setCenter(bloc);

        root.setBottom(Ui.hint("Échap ou Entrée : retour au menu"));
    }

    private static String describe(ItemType t) {
        return switch (t) {
            case ZIPPER -> "Poussée de vitesse pendant 3 secondes";
            case MAGNET -> "Attire votre kart vers celui qui vous précède";
            case HOMING -> "Missile qui suit la piste jusqu'à sa cible";
            case SPARK -> "Projectile qui rebondit sur les murets (3 tirs)";
        };
    }

    private static String describe(Pickup.Kind k) {
        return switch (k) {
            case GREEN -> "+1 point de vitesse de pointe";
            case SILVER -> "+2 points";
            case GOLD -> "+5 points";
            case RED -> "piège : ralentit et fait perdre des points";
            default -> "";
        };
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case ESCAPE, ENTER, SPACE -> app.show(new MainMenuScreen(app));
            default -> {
            }
        }
    }
}
