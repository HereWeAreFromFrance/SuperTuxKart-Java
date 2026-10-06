package org.tuxkart.core;

/**
 * Echelle de rendu de la scene 3D, en fraction de la taille de la fenetre.
 *
 * <p>Au-dessus de 1 c'est du surechantillonnage : la scene est rendue plus
 * grande puis reduite, ce qui est du vrai anticrenelage au prix du carre du
 * facteur en nombre de pixels. En dessous de 1, l'inverse : on rend moins de
 * pixels puis on agrandit, ce qui rend la machine plus rapide et l'image plus
 * floue. C'est le reglage a descendre quand le remplissage est le facteur
 * limitant — mesures a l'appui, ce n'est pas le cas de toutes les machines.
 *
 * <p>Le facteur demande n'est pas toujours celui applique : la cible de rendu
 * est bornee, faute de quoi un plein ecran 4K a facteur 2 reclamerait 33
 * megapixels — 3840x2160 double sur chaque cote — bien au-dela de ce que les
 * pilotes acceptent, et plus de six fois le plafond fixe ici.
 */
public final class RenderScale {

    /** Plafonds de la cible de rendu. */
    public static final double MAX_PIXELS = 5_000_000;
    public static final double MAX_SIDE = 6000;

    /** Facteurs proposes dans les options ; {@link #FOLLOW_QUALITY} suit le niveau. */
    public static final double FOLLOW_QUALITY = -1;
    public static final double[] STEPS = {FOLLOW_QUALITY, 0.50, 0.75, 1.00, 1.50, 2.00};

    private RenderScale() {
    }

    /**
     * Facteur reellement applicable pour une fenetre de {@code w} par {@code h}.
     *
     * <p>Les plafonds ne peuvent que *reduire* un surechantillonnage ; ils ne
     * ramenent jamais au-dessus de ce qui a ete demande. Une version precedente
     * bornait systematiquement le resultat a 1 au minimum : demander moins que
     * la resolution native ne diminuait donc rien, et levait meme une exception
     * puisque la borne basse depassait la borne haute.
     */
    public static double effective(double want, double w, double h) {
        double width = Math.max(1, w);
        double height = Math.max(1, h);
        double byArea = Math.sqrt(MAX_PIXELS / (width * height));
        double bySide = Math.min(MAX_SIDE / width, MAX_SIDE / height);
        double capped = Math.min(want, Math.min(byArea, bySide));
        // en dessous de 1 le plancher est le facteur demande lui-meme : c'est
        // un choix du joueur, pas une degradation subie qu'il faudrait rattraper
        return Math.clamp(capped, Math.min(1.0, want), want);
    }

    /** Libelle d'un facteur, tel qu'affiche dans les options. */
    public static String label(double scale) {
        return Math.round(scale * 100) + " %";
    }
}
