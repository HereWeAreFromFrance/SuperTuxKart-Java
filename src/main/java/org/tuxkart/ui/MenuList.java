package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Liste de menu navigable au clavier ou a la souris. */
public final class MenuList {

    public static final class Entry {
        final String label;
        final Supplier<String> value;
        final Runnable onSelect;
        final IntConsumer onAdjust;

        Entry(String label, Supplier<String> value, Runnable onSelect, IntConsumer onAdjust) {
            this.label = label;
            this.value = value;
            this.onSelect = onSelect;
            this.onAdjust = onAdjust;
        }
    }

    public final VBox node = new VBox(6);
    private final List<Entry> entries = new ArrayList<>();
    private final List<HBox> rows = new ArrayList<>();
    private int index;
    private Runnable onChange = () -> {
    };

    public MenuList() {
        node.setAlignment(Pos.CENTER_LEFT);
    }

    public void setOnChange(Runnable r) {
        this.onChange = r;
    }

    public MenuList action(String label, Runnable onSelect) {
        return add(new Entry(label, null, onSelect, null));
    }

    public MenuList option(String label, Supplier<String> value, IntConsumer onAdjust) {
        return add(new Entry(label, value, () -> onAdjust.accept(1), onAdjust));
    }

    private MenuList add(Entry e) {
        entries.add(e);
        Label name = new Label(e.label);
        name.setFont(Ui.fontBold(22));
        name.setMinWidth(300);
        Label val = new Label();
        val.setFont(Ui.font(22));
        HBox row = new HBox(18, name, val);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(9, 18, 9, 18));
        int myIndex = entries.size() - 1;
        row.setOnMouseEntered(ev -> {
            if (index != myIndex) {
                index = myIndex;
                refresh();
                onChange.run();
            }
        });
        row.setOnMouseClicked(ev -> {
            index = myIndex;
            refresh();
            select();
        });
        rows.add(row);
        node.getChildren().add(row);
        refresh();
        return this;
    }

    /**
     * Les memes lignes, reparties en colonnes.
     *
     * <p>L'ecran des options a onze reglages et un retour. Empiles, ils
     * demandaient 869 pixels de haut — mesure faite — alors que la fenetre en
     * fait 800 et que le stage accepte de descendre a 600 : la ligne d'aide du
     * bas etait deja coupee, et personne ne s'en etait apercu parce qu'on
     * regarde le haut d'un menu, pas son pied.
     *
     * <p>La navigation ne change pas d'un pouce. Haut et Bas parcourent la
     * liste dans son ordre de lecture, colonne apres colonne ; Gauche et Droite
     * restent ce qu'elles sont, le reglage de la valeur, et ne peuvent donc pas
     * servir a changer de colonne. C'est aussi pour cela que la coupure se fait
     * en hauteur et non en alternance : une colonne se lit de haut en bas.
     *
     * <p>A n'appeler qu'une fois, apres la derniere entree : les lignes
     * changent de parent.
     */
    public HBox colonnes(int parColonne, double ecart) {
        HBox colonnes = new HBox(ecart);
        colonnes.setAlignment(Pos.TOP_LEFT);
        node.getChildren().clear();
        VBox colonne = null;
        for (int i = 0; i < rows.size(); i++) {
            if (i % parColonne == 0) {
                colonne = new VBox(node.getSpacing());
                colonne.setAlignment(Pos.CENTER_LEFT);
                colonnes.getChildren().add(colonne);
            }
            colonne.getChildren().add(rows.get(i));
        }
        for (javafx.scene.Node c : colonnes.getChildren()) alignerLesValeurs((VBox) c);
        return colonnes;
    }

    /**
     * Aligne les valeurs d'une colonne sur son libelle le plus long.
     *
     * <p><b>Par colonne, et non pour tout le menu</b> : c'est la difference qui
     * fait tenir l'ecran. Une largeur unique doit valoir pour le pire libelle
     * des deux colonnes — « Camera par defaut », 230 pixels — et la fait payer
     * a l'autre, dont le plus long en fait 196. Mesure faite, le panneau des
     * options passe de 962 pixels a 881, ce qui le fait entrer dans la fenetre
     * la plus etroite que le stage accepte, 900.
     *
     * <p>Une largeur unique <i>trop petite</i> est pire encore, et c'est la
     * faute qui a ete commise : figee a 210 pixels, elle rognait le libelle de
     * 230 et laissait 33 pixels aux valeurs, qui se sont toutes retrouvees
     * coupees par une ellipse. Un libelle tronque ne fait echouer aucun test de
     * rendu et ne se voit qu'a l'image.
     */
    private static void alignerLesValeurs(VBox colonne) {
        double large = 0;
        for (javafx.scene.Node n : colonne.getChildren()) {
            large = Math.max(large, ((Label) ((HBox) n).getChildren().get(0)).prefWidth(-1));
        }
        for (javafx.scene.Node n : colonne.getChildren()) {
            ((Label) ((HBox) n).getChildren().get(0)).setMinWidth(Math.ceil(large));
        }
    }

    public int index() {
        return index;
    }

    public void move(int delta) {
        if (entries.isEmpty()) return;
        index = Math.floorMod(index + delta, entries.size());
        refresh();
        onChange.run();
    }

    public void adjust(int delta) {
        if (entries.isEmpty()) return;
        Entry e = entries.get(index);
        if (e.onAdjust != null) {
            e.onAdjust.accept(delta);
            refresh();
            onChange.run();
        }
    }

    public void select() {
        if (entries.isEmpty()) return;
        Entry e = entries.get(index);
        if (e.onSelect != null) e.onSelect.run();
        refresh();
    }

    public void refresh() {
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            HBox row = rows.get(i);
            Label name = (Label) row.getChildren().get(0);
            Label val = (Label) row.getChildren().get(1);
            boolean sel = i == index;
            name.setTextFill(sel ? Ui.ACCENT : Ui.TEXT);
            val.setTextFill(sel ? Ui.ACCENT_2 : Ui.DIM);
            val.setText(e.value == null ? "" : e.value.get());
            row.setBackground(sel
                    ? new Background(new BackgroundFill(Color.rgb(246, 167, 35, 0.14),
                    new CornerRadii(6), Insets.EMPTY))
                    : Background.EMPTY);
        }
    }
}
