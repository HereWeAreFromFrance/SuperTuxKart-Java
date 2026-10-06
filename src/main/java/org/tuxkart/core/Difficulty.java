package org.tuxkart.core;

public enum Difficulty {

    NOVICE("Novice", 0.86, 0.55, 0.35),
    PILOTE("Pilote", 0.94, 0.80, 0.65),
    CHAMPION("Champion", 1.00, 1.00, 0.95);

    public final String label;
    /** Fraction de la vitesse de pointe que l'IA ose exploiter. */
    public final double paceFactor;
    /** Qualite du placement sur la trajectoire ideale. */
    public final double lineQuality;
    /** Frequence et pertinence d'utilisation des objets. */
    public final double itemSkill;

    Difficulty(String label, double paceFactor, double lineQuality, double itemSkill) {
        this.label = label;
        this.paceFactor = paceFactor;
        this.lineQuality = lineQuality;
        this.itemSkill = itemSkill;
    }
}
