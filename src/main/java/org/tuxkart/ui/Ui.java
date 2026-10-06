package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/** Petite bibliotheque de style pour garder tous les ecrans coherents. */
public final class Ui {

    public static final Color ACCENT = Color.web("#f6a723");
    public static final Color ACCENT_2 = Color.web("#7ec8f2");
    public static final Color TEXT = Color.web("#eaf2f8");
    public static final Color DIM = Color.web("#93a5b5");
    public static final Color PANEL = Color.rgb(9, 18, 32, 0.86);

    public static final String FAMILY = "Verdana";
    public static final String MONO = "Monospaced";

    private Ui() {
    }

    /**
     * Fontes deja demandees.
     *
     * L'ATH change de fonte une vingtaine de fois par image ; sans ce cache,
     * chaque changement repassait par la resolution de famille de JavaFX. Les
     * tailles utilisees se comptent sur les doigts des deux mains, autant les
     * garder. La table n'est lue et ecrite que depuis le fil JavaFX.
     */
    private static final java.util.Map<Long, Font> FONTS = new java.util.HashMap<>();

    private static Font cached(String family, FontWeight weight, double size) {
        // la cle tient dans un long : famille (2 valeurs), graisse, taille au 1/4 de point
        long key = (family.equals(MONO) ? 1L : 0L)
                | (weight == FontWeight.BOLD ? 2L : 0L)
                | ((long) Math.round(size * 4) << 2);
        return FONTS.computeIfAbsent(key, k -> weight == null
                ? Font.font(family, size)
                : Font.font(family, weight, size));
    }

    public static Font font(double size) {
        return cached(FAMILY, null, size);
    }

    public static Font fontBold(double size) {
        return cached(FAMILY, FontWeight.BOLD, size);
    }

    public static Font mono(double size) {
        return cached(MONO, null, size);
    }

    public static Font monoBold(double size) {
        return cached(MONO, FontWeight.BOLD, size);
    }

    public static Background background() {
        return new Background(new BackgroundFill(
                new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#0a1626")),
                        new Stop(0.55, Color.web("#16283f")),
                        new Stop(1, Color.web("#0a1626"))),
                CornerRadii.EMPTY, Insets.EMPTY));
    }

    public static Label label(String text, double size, Color color) {
        Label l = new Label(text);
        l.setFont(font(size));
        l.setTextFill(color);
        return l;
    }

    public static Label labelBold(String text, double size, Color color) {
        Label l = new Label(text);
        l.setFont(fontBold(size));
        l.setTextFill(color);
        return l;
    }

    public static Text title(String text) {
        Text t = new Text(text);
        t.setFont(Font.font(FAMILY, FontWeight.BOLD, 58));
        t.setFill(ACCENT);
        return t;
    }

    public static VBox panel(double spacing) {
        VBox box = new VBox(spacing);
        box.setBackground(new Background(new BackgroundFill(PANEL, new CornerRadii(10), Insets.EMPTY)));
        box.setStyle("-fx-border-color: #f6a723aa; -fx-border-width: 2; -fx-border-radius: 10;");
        box.setPadding(new Insets(22, 30, 22, 30));
        return box;
    }

    public static HBox row(double spacing, Node... nodes) {
        HBox h = new HBox(spacing, nodes);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, javafx.scene.layout.Priority.ALWAYS);
        return r;
    }

    /** Jauge en etoiles utilisee dans la fiche des pilotes. */
    public static HBox stars(String name, int value, Color color) {
        HBox h = new HBox(6);
        h.setAlignment(Pos.CENTER_LEFT);
        Label l = label(name, 15, DIM);
        l.setMinWidth(120);
        h.getChildren().add(l);
        for (int i = 0; i < 5; i++) {
            Region r = new Region();
            r.setPrefSize(26, 12);
            r.setBackground(new Background(new BackgroundFill(
                    i < value ? color : Color.rgb(255, 255, 255, 0.13),
                    new CornerRadii(3), Insets.EMPTY)));
            h.getChildren().add(r);
        }
        return h;
    }

    /** « 1 tour » / « 3 tours ». */
    public static String laps(int n) {
        return n + (n > 1 ? " tours" : " tour");
    }

    public static StackPane hint(String text) {
        Label l = label(text, 14, DIM);
        StackPane p = new StackPane(l);
        p.setPadding(new Insets(8));
        return p;
    }
}
