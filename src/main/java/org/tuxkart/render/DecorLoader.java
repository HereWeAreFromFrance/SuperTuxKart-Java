package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.Node;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Devoile le decor par petits paquets, sur plusieurs images.
 *
 * <p>Ce n'est pas la construction du decor qui bloque au depart d'une course :
 * elle ne prend que 390 ms a froid, textures comprises. C'est la <b>premiere
 * image</b>. JavaFX convertit chaque maillage dans son format interne au moment
 * ou il le dessine pour la premiere fois — positions, normales encodees en
 * quaternions, tangentes — et le fait sur son fil de rendu, en une fois : 1,8 s
 * d'un bloc au plus haut niveau d'alors, fenetre figee, decompte immobile.
 *
 * <p>Le travail total ne diminue pas ; il est reparti. Le decor est construit
 * en entier, mais ses feuilles sont d'abord masquees, puis rendues visibles par
 * paquets d'un budget de triangles. C'est bien la visibilite qu'on manipule et
 * non l'appartenance a la scene : un noeud masque n'est jamais dessine, donc
 * jamais converti, et le devoiler ne change ni la structure ni l'ordre de
 * dessin — la ou detacher puis rattacher les noeuds obligeait a des groupes
 * intermediaires pour leur retrouver leur place.
 *
 * <p>Le decompte, lui, n'est lance qu'une fois le paysage complet : donner le
 * depart sur un circuit a moitie pose serait pire que l'attente.
 *
 * <p>L'ordre de devoilement suit le regard du joueur, pas l'ordre de
 * construction : ce qui est pres de la grille et devant elle arrive d'abord,
 * l'autre bout du circuit en dernier. Le total ne change pas — le paysage se
 * pose en trois secondes dans les deux cas — mais il se pose la ou l'on ne
 * regarde pas.
 */
public final class DecorLoader {

    /**
     * Triangles devoiles par image.
     *
     * Mesure a l'appui, ce budget donnait au plus haut niveau d'alors une quinzaine
     * d'images de 60 a 260 ms la ou il n'y en avait qu'une de 1800. Le
     * descendre davantage ne gagne plus rien : le plancher n'est plus le budget
     * mais les maillages eux-memes, qu'on ne peut pas couper — un maillage se
     * dessine entier ou pas du tout.
     */
    private static final long BUDGET = 60_000;

    private record Step(Node node, long triangles) {
    }

    private final Deque<Step> pending = new ArrayDeque<>();
    private final long total;
    private long done;

    /**
     * Prepare le devoilement de {@code decor}, dont les feuilles sont masquees.
     *
     * @param px position de l'oeil au depart, en coordonnees monde
     * @param fx direction du regard au depart, non normalisee
     */
    public DecorLoader(Group decor, double px, double pz, double fx, double fz) {
        List<Node> leaves = new ArrayList<>();
        collect(decor, leaves);

        double len = Math.hypot(fx, fz);
        double dx0 = len > 1e-6 ? fx / len : 0, dz0 = len > 1e-6 ? fz / len : 1;
        // Les groupes du decor ne portent aucune transformation : les bornes
        // dans le parent sont donc deja des coordonnees monde. Une seule
        // exception ferait apparaitre un morceau au mauvais moment, pas au
        // mauvais endroit — le tri est un confort, pas une correction.
        leaves.sort(java.util.Comparator.comparingDouble(n -> {
            javafx.geometry.Bounds b = n.getBoundsInParent();
            // un groupe vide n'a pas de bornes : ses coordonnees sont alors
            // degenerees et le classement ne veut rien dire. Il ne coute rien a
            // dessiner, autant le devoiler d'emblee.
            if (b.isEmpty()) return 0;
            // distance au rectangle et non a son centre : le ruban de piste, le
            // terrain et le ciel entourent le joueur, et les classer par leur
            // centre les aurait poses au milieu du devoilement — le kart aurait
            // attendu sa route
            double dx = Math.clamp(px, b.getMinX(), b.getMaxX()) - px;
            double dz = Math.clamp(pz, b.getMinZ(), b.getMaxZ()) - pz;
            double dist = Math.hypot(dx, dz);
            if (dist < 1e-6) return 0;
            // ce qui est droit devant compte pour deux tiers de sa distance,
            // ce qui est dans le dos pour une fois et demie
            double devant = (dx * dx0 + dz * dz0) / dist;
            return dist * (1 - 0.35 * devant);
        }));

        long sum = 0;
        for (Node leaf : leaves) {
            long t = TrackNode.triangles(leaf);
            leaf.setVisible(false);
            pending.add(new Step(leaf, t));
            sum += t;
        }
        total = sum;
    }

    /**
     * Feuilles a devoiler : on descend dans les groupes pour obtenir des
     * morceaux assez fins, mais pas plus loin — un maillage est indivisible.
     */
    private static void collect(Node node, List<Node> out) {
        if (node instanceof Group g && !g.getChildren().isEmpty()) {
            for (Node child : g.getChildren()) collect(child, out);
        } else {
            out.add(node);
        }
    }

    public boolean done() {
        return pending.isEmpty();
    }

    /** Part deja devoilee, de 0 a 1. */
    public double progress() {
        return total == 0 ? 1 : (double) done / total;
    }

    /**
     * Devoile le paquet suivant. Le budget est depasse d'au plus un noeud : un
     * maillage de deux cent mille triangles doit bien passer entier, sans quoi
     * rien n'avancerait plus.
     */
    public void step() {
        long budget = 0;
        while (!pending.isEmpty() && budget < BUDGET) {
            Step s = pending.poll();
            s.node().setVisible(true);
            budget += s.triangles();
            done += s.triangles();
        }
    }
}
