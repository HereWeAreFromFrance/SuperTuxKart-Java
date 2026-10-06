package org.tuxkart.core;

import javafx.geometry.Rectangle2D;

import java.util.List;
import java.util.Locale;

/**
 * Choix de l'ecran physique sur lequel ouvrir la fenetre.
 *
 * <p>JavaFX ne donne pas le nom des sorties video (HDMI-1, eDP-1...) : il n'y a
 * que des rectangles dans un repere commun. On designe donc un ecran par sa
 * position dans ce repere, ce qui suffit et reste vrai quand on rebranche la
 * meme configuration ailleurs. Sur un portable surmonte d'un ecran externe,
 * « bas » est l'ecran integre et « haut » le moniteur : jouer sur le premier
 * laisse le second libre pour travailler.
 *
 * <p>La selection est isolee ici, sans rien de graphique, pour etre verifiable
 * sans demarrer le moteur JavaFX.
 */
public final class Monitors {

    private Monitors() {
    }

    /**
     * Indice de l'ecran designe par {@code want} parmi {@code bounds}.
     *
     * @param bounds  rectangles des ecrans, dans le repere commun
     * @param primary indice de l'ecran principal, utilise par defaut
     * @param want    bas|haut|gauche|droite|principal, un indice, ou null
     */
    public static int pick(List<Rectangle2D> bounds, int primary, String want) {
        if (bounds.isEmpty()) throw new IllegalArgumentException("aucun ecran");
        int fallback = Math.clamp(primary, 0, bounds.size() - 1);
        if (want == null || want.isBlank()) return fallback;

        String key = want.strip().toLowerCase(Locale.ROOT);
        // un seul ecran : toutes les directions y menent, sans avertissement
        if (bounds.size() == 1) return 0;

        return switch (key) {
            case "principal", "primary" -> fallback;
            // en cas d'egalite on prend le plus a droite (resp. le plus bas) :
            // deux ecrans cote a cote ont le meme bord superieur, et « bas »
            // doit alors rester un choix stable plutot qu'un tirage au sort
            case "bas", "bottom" -> best(bounds, true, true);
            case "haut", "top" -> best(bounds, true, false);
            case "droite", "right" -> best(bounds, false, true);
            case "gauche", "left" -> best(bounds, false, false);
            default -> {
                int index = index(key);
                if (index >= 0 && index < bounds.size()) yield index;
                System.err.println("Ecran inconnu : " + want
                        + " (attendu bas, haut, gauche, droite, principal ou 0.."
                        + (bounds.size() - 1) + ") ; on garde l'ecran principal.");
                yield fallback;
            }
        };
    }

    /** Ecran extreme selon un axe : {@code vertical} et {@code max} le designent. */
    private static int best(List<Rectangle2D> bounds, boolean vertical, boolean max) {
        int best = 0;
        for (int i = 1; i < bounds.size(); i++) {
            double a = axis(bounds.get(i), vertical);
            double b = axis(bounds.get(best), vertical);
            if (a == b) {
                // egalite sur l'axe demande : on departage par l'autre, dans le
                // meme sens, pour que « bas » sur deux ecrans alignes designe
                // toujours le meme
                a = axis(bounds.get(i), !vertical);
                b = axis(bounds.get(best), !vertical);
            }
            if (max ? a > b : a < b) best = i;
        }
        return best;
    }

    private static double axis(Rectangle2D r, boolean vertical) {
        return vertical ? r.getMinY() : r.getMinX();
    }

    private static int index(String key) {
        try {
            return Integer.parseInt(key);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
