package org.tuxkart.items;

import javafx.scene.paint.Color;

/**
 * Les quatre « collectables » historiques de TuxKart. On garde leurs noms
 * d'origine entre parentheses dans l'interface.
 */
public enum ItemType {

    ZIPPER("Turbo", "zipper", Color.web("#ffd54a"), 1),
    MAGNET("Aimant", "magnet", Color.web("#e05a4a"), 1),
    HOMING("Missile", "homing missile", Color.web("#7ec8f2"), 1),
    SPARK("Étincelle", "spark", Color.web("#b06bff"), 3);

    public final String label;
    public final String originalName;
    public final Color color;
    /** Nombre d'utilisations donnees par une boite. */
    public final int charges;

    ItemType(String label, String originalName, Color color, int charges) {
        this.label = label;
        this.originalName = originalName;
        this.color = color;
        this.charges = charges;
    }
}
