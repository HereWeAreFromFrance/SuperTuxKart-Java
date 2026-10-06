package org.tuxkart.track;

/**
 * Ce qui delimite la piste. Le rail rouge et blanc est la solution des
 * circuits permanents ; ailleurs on trouve des barrieres de bois, des talus
 * de terre, des murs de pneus ou de simples congeres.
 */
public enum Barrier {

    RAIL("Glissière métallique", 2.5),
    BOIS("Barrière de bois", 1.9),
    TALUS("Talus de terre", 2.2),
    PNEUS("Mur de pneus", 1.6),
    CONGERE("Congère", 2.0);

    public final String label;
    /** Hauteur, en metres. */
    public final double height;

    Barrier(String label, double height) {
        this.label = label;
        this.height = height;
    }
}
