package org.tuxkart.items;

import org.tuxkart.kart.Kart;
import org.tuxkart.math.Vec3;

/** Projectile en vol : missile a tete chercheuse ou etincelle rebondissante. */
public final class Projectile {

    public enum Kind { HOMING, SPARK }

    public final Kind kind;
    public final Kart owner;
    public Kart target;

    public Vec3 pos;
    public double heading;
    public double speed;
    /** Coordonnees curvilignes, utilisees par le missile qui suit la piste. */
    public double s;
    public double lateral;
    public double life;
    /**
     * Delai avant que le projectile puisse toucher son propre tireur.
     *
     * Se deduisait auparavant de {@code life}, ce qui liait la duree
     * d'immunite a la duree de vie de chaque type : l'etincelle n'etait
     * protegee que six dixiemes de seconde, le missile deux secondes.
     */
    public double armDelay = 1.2;
    public double spin;
    public boolean dead;
    /** Dernier index de piste connu : evite un balayage complet a chaque image. */
    public int hint = -1;

    public Projectile(Kind kind, Kart owner, Vec3 pos, double heading, double speed, double life) {
        this.kind = kind;
        this.owner = owner;
        this.pos = pos;
        this.heading = heading;
        this.speed = speed;
        this.life = life;
    }
}
