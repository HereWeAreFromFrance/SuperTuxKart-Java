package org.tuxkart.render;

import javafx.scene.Node;

import java.util.List;

/**
 * Ecarte du rendu les troncons de decor que la camera ne regarde pas.
 *
 * <p>JavaFX ne trie pas la scene 3D par le champ de vision : tout noeud visible
 * part a la carte graphique, devant comme derriere. Sur la Piste de Tux en
 * niveau Ultra, le semis represente 1,47 des 2,22 millions de triangles du
 * circuit (mesure : « ./run.sh chiffres triangles », puis la meme avec
 * -Dtuxkart.nodecor), et l'oter entierement faisait passer la cadence moyenne de
 * 55 a 80 images par seconde — c'est donc bien la geometrie qui limite.
 *
 * <p>Le tri se fait par cone et non par distance. Un circuit est une boucle
 * compacte : sur la Piste de Tux, longue de 1070 m, deux points ne sont jamais
 * distants de plus de 400 m, si bien qu'une portee de plusieurs centaines de
 * metres ne retire jamais rien — mesure faite, elle ne changeait pas la cadence
 * d'une image. Ce qui est hors du cone, en revanche, represente en permanence un
 * tiers des troncons — 14 sur 21 restent dessines sur la Piste de Tux, releve
 * avec -Dtuxkart.debug=1 — et le retirer ne peut rien changer a l'image.
 *
 * <p>Le demi-angle est volontairement large : le champ horizontal reel va de 60
 * a 75 degres selon la vitesse et le turbo, la camera tremble aux impacts et le
 * joueur peut regarder en arriere. Rogner au plus juste ferait apparaitre un bosquet en bord d'ecran.
 */
public final class DecorCulling {

    /**
     * Demi-angle du cone conserve, en degres : le cone couvre donc 124 degres,
     * contre 60 a 75 pour le champ reel.
     *
     * <p>La comparaison est directe et ne demande aucune conversion vers un
     * format d'image : {@code ChaseCamera} appelle
     * {@code setVerticalFieldOfView(false)}, si bien que l'angle donne a la
     * camera est deja horizontal. Reste donc une vingtaine de degres de marge
     * de chaque cote quand le champ est au plus large, et une trentaine a
     * l'arret — c'est cette marge que le paragraphe de classe justifie.
     */
    public static final double DEFAULT_HALF_ANGLE = 62;

    private final Node[] nodes;
    private final double[] cx;
    private final double[] cz;
    private final double[] radius;
    private final double tanHalf;
    private final boolean enabled;

    private int visible;
    private long visibleSum;
    private long updates;

    public DecorCulling(List<Node> chunks, double halfAngleDeg) {
        // au-dela de 89 degres la tangente diverge et le cone couvre de toute
        // facon tout ce qui est devant : le tri n'a plus d'objet
        this.enabled = halfAngleDeg > 0 && halfAngleDeg < 89;
        this.tanHalf = Math.tan(Math.toRadians(Math.clamp(halfAngleDeg, 1, 89)));
        int n = chunks.size();
        nodes = new Node[n];
        cx = new double[n];
        cz = new double[n];
        radius = new double[n];
        for (int i = 0; i < n; i++) {
            Node node = chunks.get(i);
            javafx.geometry.Bounds b = node.getBoundsInParent();
            nodes[i] = node;
            cx[i] = (b.getMinX() + b.getMaxX()) / 2;
            cz[i] = (b.getMinZ() + b.getMaxZ()) / 2;
            radius[i] = Math.hypot(b.getWidth(), b.getDepth()) / 2;
        }
        visible = n;
    }

    public int total() {
        return nodes.length;
    }

    /** Troncons dessines a la derniere mise a jour, et moyenne depuis le depart. */
    public int visible() {
        return visible;
    }

    public double averageVisible() {
        return updates == 0 ? total() : (double) visibleSum / updates;
    }

    /**
     * Met a jour la visibilite pour une camera placee en {@code (camX, camZ)} et
     * regardant vers {@code (dirX, dirZ)}, non normalise.
     */
    public void update(double camX, double camZ, double dirX, double dirZ) {
        if (!enabled) return;
        double len = Math.hypot(dirX, dirZ);
        if (len < 1e-6) return;
        double fx = dirX / len, fz = dirZ / len;

        int shown = 0;
        for (int i = 0; i < nodes.length; i++) {
            double dx = cx[i] - camX, dz = cz[i] - camZ;
            double along = dx * fx + dz * fz;
            double lateral = Math.abs(dx * fz - dz * fx);
            // le rayon du troncon elargit le cone a la fois en profondeur et en
            // travers : sans cela, un troncon dont le centre sort du champ
            // disparaitrait alors qu'il en occupe encore la moitie
            boolean seen = along >= -radius[i]
                    && lateral <= radius[i] + (along + radius[i]) * tanHalf;
            if (nodes[i].isVisible() != seen) nodes[i].setVisible(seen);
            if (seen) shown++;
        }
        visible = shown;
        visibleSum += shown;
        updates++;
    }
}
