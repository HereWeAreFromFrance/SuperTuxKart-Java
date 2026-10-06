package org.tuxkart.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.text.Text;
import org.tuxkart.TuxKartApp;
import org.tuxkart.audio.Sfx;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Ouvrage;
import org.tuxkart.track.Track;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

/** Choix du circuit, avec plan vu du dessus. */
public final class TrackSelectScreen extends Screen {

    private final BorderPane root = new BorderPane();
    /**
     * Plan du circuit. Le stage descend a 600 pixels de haut : a 460 le plan
     * poussait l'ecran a 689 et lui coupait le bas. La proportion est conservee,
     * 560/460 devient 450/370.
     */
    private final Canvas map = new Canvas(450, 370);
    private final HBox tiles = new HBox(8);
    private final Label name = Ui.labelBold("", 34, Ui.ACCENT);
    private final Label subtitle = Ui.label("", 17, Ui.ACCENT_2);
    private final VBox infoBox = new VBox(8);
    private int index;
    private Track cached;
    /**
     * Geometries deja construites.
     *
     * Un {@link Track} echantillonne sa spline sur plusieurs milliers de points
     * puis lisse deux fois la courbure : le refaire a chaque appui sur une
     * fleche se sentait sous les doigts, alors que seul le plan a l'ecran en a
     * besoin.
     */
    private final java.util.Map<TrackDef, Track> geometries = new java.util.HashMap<>();

    public TrackSelectScreen(TuxKartApp app) {
        super(app);
        root.setBackground(Ui.background());
        index = Math.max(0, Tracks.ALL.indexOf(app.settings.track));

        Text title = Ui.title("CIRCUITS");
        VBox header = new VBox(title);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(14, 0, 4, 0));
        root.setTop(header);

        for (int i = 0; i < Tracks.ALL.size(); i++) {
            TrackDef def = Tracks.ALL.get(i);
            final int idx = i;
            VBox tile = new VBox(2);
            tile.setAlignment(Pos.CENTER);
            // Neuf tuiles dans une fenetre de 1280 : la place par tuile est de
            // cent vingt-huit pixels, pas un de plus. Reclamer davantage ne
            // l'obtenait pas — la HBox rabotait, et « Colline du Manchot »
            // s'affichait « Colline du … ». Le nom est donc pose sur deux
            // lignes plutot que tronque.
            tile.setPrefSize(128, 58);
            javafx.scene.control.Label nom = Ui.labelBold(def.name, 15, Ui.TEXT);
            nom.setWrapText(true);
            nom.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            tile.getChildren().add(nom);
            // Sur cinq circuits le theme porte le nom du circuit — Salon,
            // Comptoir, Banquise… — et la tuile se repetait mot pour mot.
            if (!def.theme.label.equals(def.name)) {
                tile.getChildren().add(Ui.label(def.theme.label, 12, Ui.DIM));
            }
            tile.setOnMouseClicked(e -> {
                index = idx;
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            });
            tiles.getChildren().add(tile);
        }
        tiles.setAlignment(Pos.CENTER);

        VBox info = Ui.panel(6);
        info.setPrefWidth(400);
        info.getChildren().addAll(name, subtitle, new Label(" "), infoBox);

        // Le plan et la fiche s'alignent par le haut. Centres, ils ne
        // s'alignaient par aucun bout — la fiche fait dix-neuf pixels de plus
        // que le plan, donc dix de trop en haut et dix en bas. Les egaliser
        // n'aurait pas de sens ici : le plan est une toile de taille fixe, pas
        // une carte encadree comme sur l'ecran des commandes.
        HBox center = new HBox(40, map, info);
        center.setAlignment(Pos.TOP_CENTER);
        center.setFillHeight(false);
        VBox all = new VBox(12, center, tiles);
        all.setAlignment(Pos.CENTER);
        all.setPadding(new Insets(0, 30, 4, 30));
        root.setCenter(all);

        root.setBottom(Ui.hint("Gauche / Droite : changer de circuit     Entrée : valider     Échap : retour"));
        refresh();
    }

    private void refresh() {
        TrackDef def = Tracks.ALL.get(index);
        cached = geometries.computeIfAbsent(def, Track::new);
        name.setText(def.name);
        subtitle.setText(def.subtitle);

        double denivele = cached.maxY - cached.minY;
        infoBox.getChildren().setAll(
                Ui.stars("Difficulté", def.difficulty, Ui.ACCENT),
                new Label(" "),
                Ui.label(String.format("Longueur : %.0f m  (%d tours)",
                        cached.length, def.defaultLaps), 16, Ui.TEXT),
                Ui.label("Largeur de piste : " + largeurs(cached), 16, Ui.TEXT),
                Ui.label(String.format("Dénivelé : %.0f m", denivele), 16, Ui.TEXT),
                Ui.label("Revêtement : " + def.surface.label, 16, Ui.TEXT),
                Ui.label("Bordures : " + def.barrier.label, 16, Ui.TEXT),
                Ui.label(String.format("Adhérence : %.0f %%", def.surface.grip * 100), 16, Ui.TEXT),
                Ui.label("On y traverse : " + ouvrages(def), 16, Ui.TEXT));

        for (int i = 0; i < tiles.getChildren().size(); i++) {
            VBox tile = (VBox) tiles.getChildren().get(i);
            boolean sel = i == index;
            tile.setBackground(sel
                    ? new Background(new BackgroundFill(Color.rgb(246, 167, 35, 0.22),
                    new CornerRadii(6), Insets.EMPTY))
                    : Background.EMPTY);
            ((Label) tile.getChildren().getFirst()).setTextFill(sel ? Ui.ACCENT : Ui.TEXT);
        }
        drawMap();
    }

    /**
     * Largeur de piste : un chiffre si elle ne bouge pas, une fourchette
     * sinon. Sur sept circuits sur neuf la piste se resserre et s'ouvre, et
     * c'est justement ce qu'il faut savoir avant de choisir.
     */
    private static String largeurs(Track track) {
        double mini = Double.MAX_VALUE, maxi = 0;
        for (double demi : track.halfRoadAt) {
            mini = Math.min(mini, demi);
            maxi = Math.max(maxi, demi);
        }
        return maxi - mini < 0.2
                ? String.format("%.0f m", 2 * maxi)
                : String.format("%.0f à %.0f m", 2 * mini, 2 * maxi);
    }

    /** Les grands volumes que le trace traverse, ou « rien » s'il n'y en a pas. */
    private static String ouvrages(TrackDef def) {
        StringBuilder sb = new StringBuilder();
        for (String kind : def.propKinds) {
            Ouvrage o = Ouvrage.parNom(kind);
            if (o == null) continue;
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(o.label);
        }
        return sb.isEmpty() ? "rien, la piste et le paysage" : sb.toString();
    }

    private void drawMap() {
        GraphicsContext g = map.getGraphicsContext2D();
        double w = map.getWidth(), h = map.getHeight();
        g.clearRect(0, 0, w, h);
        g.setFill(Color.rgb(9, 18, 32, 0.86));
        g.fillRoundRect(0, 0, w, h, 12, 12);
        g.setStroke(Color.web("#f6a723aa"));
        g.setLineWidth(2);
        g.strokeRoundRect(1, 1, w - 2, h - 2, 12, 12);

        Track t = cached;
        double pad = 40;
        // un trace parfaitement rectiligne sur un axe donnerait une etendue
        // nulle, donc une echelle infinie
        double spanX = Math.max(1, t.maxX - t.minX), spanZ = Math.max(1, t.maxZ - t.minZ);
        double scale = Math.min((w - 2 * pad) / spanX, (h - 2 * pad) / spanZ);
        double ox = (w - spanX * scale) / 2 - t.minX * scale;
        double oz = (h - spanZ * scale) / 2 - t.minZ * scale;

        g.setLineCap(StrokeLineCap.ROUND);
        g.setLineJoin(StrokeLineJoin.ROUND);

        // bande de piste
        g.setStroke(Color.rgb(255, 255, 255, 0.10));
        g.setLineWidth(t.halfCorridor * 2 * scale);
        strokePath(g, t, ox, oz, scale);
        g.setStroke(t.def.theme.roadA.brighter());
        g.setLineWidth(Math.max(3, t.halfRoad * 2 * scale));
        strokePath(g, t, ox, oz, scale);

        // ligne de depart
        Vec3 c = t.centerAtS(0);
        Vec3 sd = t.sideAtS(0);
        g.setStroke(Color.WHITE);
        g.setLineWidth(3);
        g.strokeLine(
                ox + (c.x + sd.x * t.halfRoad) * scale, oz + (c.z + sd.z * t.halfRoad) * scale,
                ox + (c.x - sd.x * t.halfRoad) * scale, oz + (c.z - sd.z * t.halfRoad) * scale);

        // sens de la course
        Vec3 f = t.forwardAtS(0);
        double ax = ox + (c.x + f.x * 24) * scale, az = oz + (c.z + f.z * 24) * scale;
        g.setFill(Ui.ACCENT);
        g.fillOval(ax - 5, az - 5, 10, 10);
        g.setFont(Ui.font(13));
        g.fillText("départ", ax + 8, az + 4);
    }

    private void strokePath(GraphicsContext g, Track t, double ox, double oz, double scale) {
        g.beginPath();
        for (int i = 0; i <= t.n; i++) {
            Vec3 p = t.center[i % t.n];
            double x = ox + p.x * scale, y = oz + p.z * scale;
            if (i == 0) g.moveTo(x, y);
            else g.lineTo(x, y);
        }
        g.closePath();
        g.stroke();
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void keyPressed(KeyEvent e) {
        switch (e.getCode()) {
            case LEFT, UP -> {
                index = Math.floorMod(index - 1, Tracks.ALL.size());
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            }
            case RIGHT, DOWN -> {
                index = Math.floorMod(index + 1, Tracks.ALL.size());
                refresh();
                app.sfx(Sfx.MENU_MOVE);
            }
            case ENTER, SPACE -> {
                app.settings.track = Tracks.ALL.get(index);
                app.sfx(Sfx.MENU_SELECT);
                app.show(new MainMenuScreen(app));
            }
            case ESCAPE -> app.show(new MainMenuScreen(app));
            default -> {
            }
        }
    }
}
