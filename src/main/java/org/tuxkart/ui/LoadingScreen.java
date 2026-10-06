package org.tuxkart.ui;

import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.TextAlignment;
import org.tuxkart.TuxKartApp;

import java.util.function.Supplier;

/**
 * Ecran d'attente pendant la construction d'un circuit.
 *
 * <p>Construire le decor coutait une seconde au plus haut niveau d'alors : maillages,
 * textures, modeles des karts. C'etait une seconde de fenetre figee, dans une
 * seule image de la boucle de jeu. Le travail se fait desormais sur un fil de
 * fond et cet ecran occupe l'attente — la barre qui balaie n'indique pas une
 * progression, elle prouve que la fenetre repond.
 *
 * <p>Rien n'est construit ici : l'ecran se contente de surveiller la tache et
 * d'appeler {@code onReady} quand elle a fini. Il ne l'attend jamais, sans quoi
 * le fil JavaFX se bloquerait sur la tache qui, elle, lui demande parfois de
 * peindre une texture.
 */
public final class LoadingScreen extends Screen {

    private final StackPane root = new StackPane();
    private final Canvas canvas = new Canvas(TuxKartApp.WIDTH, TuxKartApp.HEIGHT);
    private final Supplier<Boolean> ready;
    private final Runnable onReady;
    private final String titre;
    private double clock;
    private boolean fini;

    /**
     * @param ready   consulte a chaque image ; vrai quand la tache de fond a fini
     * @param onReady execute une fois, sur le fil JavaFX, des que {@code ready} l'est
     */
    public LoadingScreen(TuxKartApp app, String titre, Supplier<Boolean> ready, Runnable onReady) {
        super(app);
        this.titre = titre;
        this.ready = ready;
        this.onReady = onReady;

        root.setBackground(Ui.background());
        canvas.widthProperty().bind(root.widthProperty());
        canvas.heightProperty().bind(root.heightProperty());
        // meme precaution que sur l'ecran de course : un canvas lie a la taille
        // de la racine et pris en compte dans la mise en page la fait grandir
        canvas.setManaged(false);
        canvas.setMouseTransparent(true);

        // tout est peint sur le canvas : un noeud de mise en page dans un
        // StackPane est etire a toute la surface, et son titre se retrouvait
        // centre par-dessus le nom du circuit
        root.getChildren().add(canvas);
    }

    @Override
    public Parent node() {
        return root;
    }

    @Override
    public void update(double dt) {
        clock += dt;
        draw();
        // le passage a l'ecran suivant se fait ici, donc sur le fil JavaFX,
        // et une seule fois
        if (!fini && Boolean.TRUE.equals(ready.get())) {
            fini = true;
            onReady.run();
        }
    }

    private void draw() {
        GraphicsContext g = canvas.getGraphicsContext2D();
        double w = canvas.getWidth(), h = canvas.getHeight();
        g.clearRect(0, 0, w, h);

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Ui.ACCENT);
        g.setFont(Ui.fontBold(52));
        g.fillText("CHARGEMENT", w / 2, h * 0.44);
        g.setFill(Ui.TEXT);
        g.setFont(Ui.font(26));
        g.fillText(titre, w / 2, h * 0.50);

        double barW = Math.min(560, w * 0.5), barH = 12;
        double x = (w - barW) / 2, y = h * 0.55;
        g.setFill(Color.web("#0b1622", 0.75));
        g.fillRoundRect(x - 3, y - 3, barW + 6, barH + 6, 8, 8);

        // une navette qui va et vient : la construction ne se decoupe pas en
        // etapes mesurables, annoncer un pourcentage serait mentir
        double span = barW * 0.28;
        double t = (Math.sin(clock * 2.2) + 1) / 2;
        g.setFill(Ui.ACCENT);
        g.fillRoundRect(x + t * (barW - span), y, span, barH, 6, 6);

        g.setStroke(Color.web("#f2c14b", 0.5));
        g.setLineWidth(1.5);
        g.strokeRoundRect(x - 3, y - 3, barW + 6, barH + 6, 8, 8);
        g.setTextAlign(TextAlignment.LEFT);
    }
}
