package org.tuxkart.ui;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.text.TextAlignment;
import org.tuxkart.core.GameSettings;
import org.tuxkart.items.Pickup;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.race.Race;
import org.tuxkart.track.Track;

import java.util.List;

/** Affichage tete haute : classement, tours, chrono, objet, compteur, minicarte. */
public final class Hud {

    private static final Color PANEL = Color.rgb(6, 14, 26, 0.88);
    private static final Color LINE = Color.rgb(246, 167, 35, 0.55);

    /** Cadence de la boucle de jeu, lissee. */
    public double fps;
    /** Pire temps d'image de la derniere seconde : c'est lui qu'on ressent. */
    public double worstMs;
    /** Images reellement rendues, mesurees par JavaFX ; -1 si indisponible. */
    public double renderFps = -1;
    /**
     * Durees reelles des dernieres images, en millisecondes.
     * Une moyenne masque les a-coups ; un graphe de durees les montre.
     */
    public double[] history = new double[0];
    public int historyHead;

    /**
     * Avancement du chargement du paysage, de 0 a 1, ou -1 une fois pose.
     *
     * Le decompte n'a pas encore commence pendant ce temps : sans ce voyant,
     * l'ecran montrerait un circuit qui se remplit tout seul, sans rien dire.
     */
    public double loading = -1;

    public void draw(GraphicsContext g, double w, double h, Race race, GameSettings settings) {
        g.clearRect(0, 0, w, h);
        g.setTextAlign(TextAlignment.LEFT);

        if (loading >= 0) {
            drawLoading(g, w, h);
            return;
        }

        Kart player = race.player;
        drawSpeedLines(g, w, h, player);
        drawFrameGraph(g, w);
        drawPositionAndLap(g, race, player);
        drawTimers(g, w, race, player);
        drawStandings(g, h, race);
        drawItemSlot(g, h, player);
        drawSpeedometer(g, w, h, player);
        if (settings.showMinimap) drawMinimap(g, w, race);
        drawCenterMessages(g, w, h, race);
    }

    /** Trainees radiales : elles ne se voient qu'a tres haute vitesse ou en turbo. */
    private void drawSpeedLines(GraphicsContext g, double w, double h, Kart player) {
        double norm = Math.clamp(Math.abs(player.speed) / player.def.maxSpeed, 0, 1.4);
        double boost = player.boosting() ? 0.55 : 0;
        double strength = Math.clamp((norm - 0.72) / 0.4, 0, 1) * 0.5 + boost;
        if (strength <= 0.01) return;

        double cx = w / 2, cy = h * 0.52;
        double rMax = Math.hypot(w, h) * 0.55;
        g.setLineCap(StrokeLineCap.ROUND);
        for (int i = 0; i < 26; i++) {
            double a = i * Math.PI * 2 / 26 + (i % 3) * 0.11;
            double r0 = rMax * (0.52 + ((i * 37) % 17) / 60.0);
            double len = rMax * (0.16 + ((i * 53) % 11) / 40.0) * strength;
            g.setStroke(Color.color(1, 1, 1, 0.10 + 0.16 * strength));
            g.setLineWidth(2 + strength * 2);
            g.strokeLine(cx + Math.cos(a) * r0, cy + Math.sin(a) * r0,
                    cx + Math.cos(a) * (r0 + len), cy + Math.sin(a) * (r0 + len));
        }
    }

    /**
     * Courbe des temps d'image des dernieres secondes.
     *
     * C'est le seul affichage honnete : un a-coup de cent millisecondes est
     * invisible dans une moyenne, mais saute aux yeux sur une courbe. Les deux
     * traits de reference sont 60 et 30 images par seconde.
     */
    private void drawFrameGraph(GraphicsContext g, double w) {
        if (history.length == 0) return;
        double gw = 236, gh = 52, x = w / 2 - gw / 2, y = 120;
        panel(g, x, y, gw, gh);

        double maxMs = 50;
        for (int i = 0; i < 2; i++) {
            double ms = i == 0 ? 16.7 : 33.3;
            double ly = y + gh - (ms / maxMs) * gh;
            g.setStroke(Color.rgb(255, 255, 255, i == 0 ? 0.22 : 0.34));
            g.setLineWidth(1);
            g.strokeLine(x + 2, ly, x + gw - 2, ly);
        }

        double step = (gw - 6) / history.length;
        for (int i = 0; i < history.length; i++) {
            double v = history[(historyHead + i) % history.length];
            if (v <= 0) continue;
            double ms = Math.min(maxMs, v);
            double bh = Math.max(1, (ms / maxMs) * (gh - 6));
            // Le passage au rouge tombe exactement sur le trait des 30 images
            // par seconde trace juste au-dessus : arrondi a 34, une image sous
            // les 30 fps restait orange alors qu'elle depassait deja la ligne.
            g.setFill(ms < 20 ? Color.web("#5ad46a")
                    : ms < 33.3 ? Color.web("#f6a723") : Color.web("#e04a3a"));
            g.fillRect(x + 3 + i * step, y + gh - 3 - bh, Math.max(1, step - 0.5), bh);
        }
        g.setFill(Ui.DIM);
        g.setFont(Ui.mono(11));
        g.fillText("temps d'image", x + 6, y + 13);
    }

    private static void panel(GraphicsContext g, double x, double y, double w, double h) {
        g.setFill(PANEL);
        g.fillRoundRect(x, y, w, h, 10, 10);
        g.setStroke(LINE);
        g.setLineWidth(1.5);
        g.strokeRoundRect(x, y, w, h, 10, 10);
    }

    private void drawPositionAndLap(GraphicsContext g, Race race, Kart player) {
        panel(g, 18, 18, 210, 96);
        g.setFill(Ui.ACCENT);
        g.setFont(Ui.monoBold(46));
        g.fillText(ordinal(player.position), 32, 66);
        g.setFill(Ui.DIM);
        g.setFont(Ui.mono(17));
        g.fillText("/ " + race.karts.size(), 118, 66);
        g.setFill(Ui.TEXT);
        g.setFont(Ui.monoBold(20));
        g.fillText("TOUR " + race.playerLap() + " / " + race.totalLaps, 32, 98);
    }

    private void drawTimers(GraphicsContext g, double w, Race race, Kart player) {
        double x = w / 2 - 120;
        panel(g, x, 18, 240, 96);
        g.setFill(Ui.TEXT);
        g.setFont(Ui.monoBold(32));
        g.setTextAlign(TextAlignment.CENTER);
        g.fillText(MathUtil.formatTime(Math.max(0, race.time)), w / 2, 52);
        g.setFont(Ui.mono(15));
        g.setFill(Ui.DIM);
        String best = player.bestLap == Double.MAX_VALUE
                ? "meilleur tour : --:--.--"
                : "meilleur tour : " + MathUtil.formatTime(player.bestLap);
        g.fillText(best, w / 2, 78);
        g.setFont(Ui.mono(12));
        g.setFill(Color.rgb(147, 165, 181, 0.75));
        // la pire image de la seconde ecoulee est le chiffre qui correspond au
        // ressenti : une seule image a 200 ms se voit, une moyenne ne la voit pas
        String line = renderFps >= 0
                ? String.format("%.0f fps   rendu %.0f   pire %.0f ms", fps, renderFps, worstMs)
                : String.format("%.0f fps   pire %.0f ms", fps, worstMs);
        g.fillText(line, w / 2, 106);
        g.setTextAlign(TextAlignment.LEFT);
    }

    private void drawStandings(GraphicsContext g, double h, Race race) {
        List<Kart> order = race.standings();
        double rows = order.size();
        double boxH = 22 * rows + 16;
        double y = h / 2 - boxH / 2;
        panel(g, 18, y, 210, boxH);
        g.setFont(Ui.mono(15));
        for (int i = 0; i < order.size(); i++) {
            Kart k = order.get(i);
            double ty = y + 26 + i * 22;
            g.setFill(k.human ? Ui.ACCENT : Ui.TEXT);
            g.fillText((i + 1) + ".", 32, ty);
            g.fillText(k.def.name, 58, ty);
            g.setFill(k.human ? Ui.ACCENT : Ui.DIM);
            if (k.finished) {
                g.fillText(MathUtil.formatTime(k.finishTime), 130, ty);
            } else if (k != race.player) {
                double gap = (k.totalProgress - race.player.totalProgress);
                g.fillText(String.format("%+.0f m", gap), 140, ty);
            }
        }
    }

    private void drawItemSlot(GraphicsContext g, double h, Kart player) {
        double x = 18, y = h - 132;
        panel(g, x, y, 210, 114);
        g.setFont(Ui.mono(14));
        g.setFill(Ui.DIM);
        g.fillText("OBJET", x + 14, y + 22);

        double bx = x + 14, by = y + 32, bs = 62;
        if (player.item != null && player.itemCount > 0) {
            g.setFill(player.item.color.deriveColor(0, 1, 1, 0.85));
            g.fillRoundRect(bx, by, bs, bs, 8, 8);
            g.setStroke(Color.WHITE);
            g.setLineWidth(2);
            g.strokeRoundRect(bx, by, bs, bs, 8, 8);
            g.setFill(Ui.TEXT);
            g.setFont(Ui.monoBold(15));
            g.fillText(player.item.label, bx + bs + 12, by + 24);
            g.setFill(Ui.DIM);
            g.setFont(Ui.mono(14));
            g.fillText("x" + player.itemCount, bx + bs + 12, by + 46);
            g.fillText("[Espace]", bx + bs + 12, by + 64);
        } else {
            g.setStroke(Color.rgb(255, 255, 255, 0.25));
            g.setLineWidth(2);
            g.strokeRoundRect(bx, by, bs, bs, 8, 8);
            g.setFill(Ui.DIM);
            g.setFont(Ui.mono(14));
            g.fillText("aucun", bx + bs + 12, by + 30);
        }

        g.setFill(Pickup.Kind.GOLD.color);
        g.setFont(Ui.monoBold(16));
        g.fillText("Harengs : " + player.herrings, x + 14, y + 106);
    }

    private void drawSpeedometer(GraphicsContext g, double w, double h, Kart player) {
        double size = 168;
        double cx = w - size / 2 - 30, cy = h - size / 2 - 26;
        double r = size / 2;

        g.setFill(PANEL);
        g.fillOval(cx - r, cy - r, size, size);

        g.setLineCap(StrokeLineCap.BUTT);
        g.setStroke(Color.rgb(255, 255, 255, 0.16));
        g.setLineWidth(9);
        g.strokeArc(cx - r + 12, cy - r + 12, size - 24, size - 24, 225, -270, ArcType.OPEN);

        double vmax = player.def.maxSpeed * 3.6 * 1.35;
        double frac = Math.clamp(player.speedKmh() / vmax, 0, 1);
        Color arcColor = player.boosting() ? Color.web("#ffd54a") : Ui.ACCENT_2;
        g.setStroke(arcColor);
        g.setLineWidth(9);
        g.strokeArc(cx - r + 12, cy - r + 12, size - 24, size - 24, 225, -270 * frac, ArcType.OPEN);

        // graduations
        g.setStroke(Color.rgb(255, 255, 255, 0.35));
        g.setLineWidth(2);
        for (int i = 0; i <= 8; i++) {
            double a = Math.toRadians(225 - 270.0 * i / 8);
            double x1 = cx + Math.cos(a) * (r - 22), y1 = cy - Math.sin(a) * (r - 22);
            double x2 = cx + Math.cos(a) * (r - 30), y2 = cy - Math.sin(a) * (r - 30);
            g.strokeLine(x1, y1, x2, y2);
        }

        // aiguille
        double a = Math.toRadians(225 - 270 * frac);
        g.setStroke(Color.web("#ff6b52"));
        g.setLineWidth(3.5);
        g.setLineCap(StrokeLineCap.ROUND);
        g.strokeLine(cx, cy, cx + Math.cos(a) * (r - 34), cy - Math.sin(a) * (r - 34));
        g.setFill(Color.web("#ff6b52"));
        g.fillOval(cx - 5, cy - 5, 10, 10);

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Ui.TEXT);
        g.setFont(Ui.monoBold(30));
        g.fillText(String.format("%.0f", player.speedKmh()), cx, cy + 44);
        g.setFill(Ui.DIM);
        g.setFont(Ui.mono(13));
        g.fillText("km/h", cx, cy + 62);
        g.setTextAlign(TextAlignment.LEFT);
    }

    /**
     * Fond de la minicarte : le trace et la ligne de depart, dessines une fois.
     *
     * Le contour represente trois cents segments qui ne bougeront jamais de la
     * course. Les redessiner a chaque image, c'etait la moitie du cout de tout
     * l'ATH ; seules les pastilles des karts ont besoin d'etre refaites.
     */
    private javafx.scene.image.Image minimapBase;
    private Track minimapTrack;

    private javafx.scene.image.Image bakeMinimap(Track t, double size, double scale,
                                                 double ox, double oz) {
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(size, size);
        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setStroke(Color.rgb(255, 255, 255, 0.55));
        g.setLineWidth(Math.max(2.5, t.halfRoad * 2 * scale));
        g.setLineCap(StrokeLineCap.ROUND);
        g.beginPath();
        for (int i = 0; i <= t.n; i += 2) {
            Vec3 p = t.center[i % t.n];
            double px = ox + p.x * scale, pz = oz + p.z * scale;
            if (i == 0) g.moveTo(px, pz);
            else g.lineTo(px, pz);
        }
        g.closePath();
        g.stroke();

        Vec3 c = t.centerAtS(0);
        Vec3 sd = t.sideAtS(0);
        g.setStroke(Color.web("#ffd54a"));
        g.setLineWidth(2.5);
        g.strokeLine(ox + (c.x + sd.x * t.halfRoad) * scale, oz + (c.z + sd.z * t.halfRoad) * scale,
                ox + (c.x - sd.x * t.halfRoad) * scale, oz + (c.z - sd.z * t.halfRoad) * scale);

        javafx.scene.SnapshotParameters sp = new javafx.scene.SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return canvas.snapshot(sp, new javafx.scene.image.WritableImage(
                (int) Math.ceil(size), (int) Math.ceil(size)));
    }

    private void drawMinimap(GraphicsContext g, double w, Race race) {
        double size = 190;
        double x = w - size - 26, y = 18;
        panel(g, x, y, size, size);

        Track t = race.track;
        double pad = 18;
        double spanX = Math.max(1, t.maxX - t.minX), spanZ = Math.max(1, t.maxZ - t.minZ);
        double scale = Math.min((size - 2 * pad) / spanX, (size - 2 * pad) / spanZ);
        // repere local de l'image du fond, puis decale a sa place a l'ecran
        double lx = (size - spanX * scale) / 2 - t.minX * scale;
        double lz = (size - spanZ * scale) / 2 - t.minZ * scale;
        double ox = x + lx, oz = y + lz;

        if (minimapBase == null || minimapTrack != t) {
            minimapBase = bakeMinimap(t, size, scale, lx, lz);
            minimapTrack = t;
        }
        g.drawImage(minimapBase, x, y);

        for (Kart k : race.karts) {
            double px = ox + k.pos.x * scale, pz = oz + k.pos.z * scale;
            double rr = k.human ? 5.5 : 4;
            g.setFill(k.human ? Ui.ACCENT : k.def.kartColor.deriveColor(0, 1, 1.7, 1));
            g.fillOval(px - rr, pz - rr, rr * 2, rr * 2);
            if (k.human) {
                g.setStroke(Color.WHITE);
                g.setLineWidth(1.5);
                g.strokeOval(px - rr, pz - rr, rr * 2, rr * 2);
            }
        }
    }

    private void drawCenterMessages(GraphicsContext g, double w, double h, Race race) {
        g.setTextAlign(TextAlignment.CENTER);

        if (race.state == Race.State.COUNTDOWN) {
            int n = (int) Math.ceil(race.countdown);
            String txt = n > 3 ? "PRÊT ?" : String.valueOf(Math.max(1, n));
            // le chiffre nait grand et retrecit au fil de sa seconde
            double scale = 1 + (race.countdown - Math.floor(race.countdown)) * 0.5;
            g.setFill(Color.rgb(4, 10, 20, 0.62));
            g.fillRoundRect(w / 2 - 190, h / 2 - 88, 380, 158, 18, 18);
            g.setStroke(LINE);
            g.setLineWidth(1.5);
            g.strokeRoundRect(w / 2 - 190, h / 2 - 88, 380, 158, 18, 18);
            g.setFill(Ui.ACCENT);
            g.setFont(Ui.monoBold(96 * scale));
            g.fillText(txt, w / 2, h / 2 + 20);
            g.setFill(Ui.TEXT);
            g.setFont(Ui.mono(18));
            g.fillText(race.track.def.name + "  -  " + Ui.laps(race.totalLaps), w / 2, h / 2 + 58);
        }

        double y = h * 0.30;
        for (Race.Notice n : race.notices) {
            double alpha = Math.clamp(n.life / 0.6, 0, 1);
            g.setFill(Color.color(Ui.ACCENT.getRed(), Ui.ACCENT.getGreen(), Ui.ACCENT.getBlue(), alpha));
            g.setFont(Ui.monoBold(28));
            g.fillText(n.text, w / 2, y);
            y += 34;
        }

        if (race.state == Race.State.PLAYER_FINISHED || race.state == Race.State.OVER) {
            g.setFill(Color.rgb(4, 10, 20, 0.66));
            g.fillRoundRect(w / 2 - 330, h / 2 - 66, 660, 118, 18, 18);
            g.setStroke(LINE);
            g.setLineWidth(1.5);
            g.strokeRoundRect(w / 2 - 330, h / 2 - 66, 660, 118, 18, 18);
            g.setFill(Ui.ACCENT);
            g.setFont(Ui.monoBold(58));
            g.fillText("ARRIVÉE  -  " + ordinal(race.player.position), w / 2, h / 2 + 6);
            g.setFill(Ui.TEXT);
            g.setFont(Ui.mono(18));
            g.fillText("Entrée : voir les résultats", w / 2, h / 2 + 40);
        }
        g.setTextAlign(TextAlignment.LEFT);
    }

    /** Bandeau de chargement : un titre et une jauge, rien de plus. */
    private void drawLoading(GraphicsContext g, double w, double h) {
        double barW = Math.min(520, w * 0.5), barH = 14;
        double x = (w - barW) / 2, y = h * 0.62;

        g.setTextAlign(TextAlignment.CENTER);
        g.setFill(Color.web("#f2c14b"));
        g.setFont(Ui.fontBold(30));
        g.fillText("CHARGEMENT DU CIRCUIT", w / 2, y - 26);

        g.setFill(Color.web("#0b1622", 0.75));
        g.fillRoundRect(x - 3, y - 3, barW + 6, barH + 6, 8, 8);
        g.setFill(Color.web("#f2c14b"));
        g.fillRoundRect(x, y, barW * Math.clamp(loading, 0, 1), barH, 6, 6);
        g.setStroke(Color.web("#f2c14b", 0.55));
        g.setLineWidth(1.5);
        g.strokeRoundRect(x - 3, y - 3, barW + 6, barH + 6, 8, 8);
        g.setTextAlign(TextAlignment.LEFT);
    }

    public static String ordinal(int p) {
        return p == 1 ? "1er" : p + "e";
    }
}
