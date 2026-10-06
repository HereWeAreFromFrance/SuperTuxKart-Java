package org.tuxkart.items;

import javafx.scene.paint.Color;

/**
 * Objet pose sur la piste. TuxKart melangeait des « harengs » (bonus/malus
 * ramasses directement) et des boites de collectables ; on garde les deux.
 */
public final class Pickup {

    public enum Kind {
        BOX("Boite", Color.web("#f3f3f3")),
        GREEN("Hareng vert", Color.web("#4fd166")),
        RED("Hareng rouge", Color.web("#e0473a")),
        SILVER("Hareng argenté", Color.web("#c9d2d8")),
        GOLD("Hareng doré", Color.web("#f5c542"));

        public final String label;
        public final Color color;

        Kind(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    public final Kind kind;
    /** Position curviligne fixe sur la piste. */
    public final double s;
    public final double lateral;
    /** Temps restant avant reapparition ; 0 = disponible. */
    public double respawn;

    public Pickup(Kind kind, double s, double lateral) {
        this.kind = kind;
        this.s = s;
        this.lateral = lateral;
    }

    public boolean available() {
        return respawn <= 0;
    }
}
