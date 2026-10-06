package org.tuxkart.race;

import org.tuxkart.core.Difficulty;
import org.tuxkart.items.ItemType;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Track;

import java.util.Random;

/**
 * Pilote automatique. Il vise un point d'anticipation sur sa trajectoire,
 * regle sa vitesse sur la courbure a venir, evite les autres karts et se sert
 * de ses objets.
 */
public final class AIDriver {

    private final Kart kart;
    private final Track track;
    private final Difficulty difficulty;
    private final Random rnd;

    /** Ecart lateral prefere par rapport a l'axe : donne du caractere a chaque IA. */
    private final double linePhase;
    private final double lineAmp;
    private final double skill;

    private double itemTimer;
    private double wanderTimer;
    private double wander;
    /** Temps restant de wheelie : la commande doit etre tenue, pas clignotee. */
    private double wheelieTime;
    /** Manoeuvre de degagement quand le kart s'est plante contre un muret. */
    private double stuckTime;
    private double reverseTime;
    private double reverseSteer;

    public AIDriver(Kart kart, Difficulty difficulty, long seed) {
        this.kart = kart;
        this.track = kart.track;
        this.difficulty = difficulty;
        this.rnd = new Random(seed);
        this.linePhase = rnd.nextDouble() * Math.PI * 2;
        this.lineAmp = 0.25 + rnd.nextDouble() * 0.35;
        this.skill = difficulty.paceFactor * (0.95 + rnd.nextDouble() * 0.09);
        this.itemTimer = 1 + rnd.nextDouble() * 2;
    }

    public void update(double dt, Race race) {
        kart.controls.reset();
        if (race.state == Race.State.COUNTDOWN) {
            // immobile jusqu'au feu vert, comme le joueur
            return;
        }
        if (kart.beingRescued()) {
            stuckTime = 0;
            reverseTime = 0;
            return;
        }

        double speed = kart.speed;

        // --- degagement en marche arriere plutot que d'attendre le sauvetage
        if (reverseTime > 0) {
            reverseTime -= dt;
            kart.controls.brake = true;
            kart.controls.steer = reverseSteer;
            return;
        }
        if (Math.abs(speed) < 1.6) stuckTime += dt;
        else stuckTime = 0;
        if (stuckTime > 1.1) {
            stuckTime = 0;
            reverseTime = 1.0;
            // on braque du cote oppose au muret le plus proche
            reverseSteer = kart.loc.lateral > 0 ? 1 : -1;
            return;
        }

        // --- trajectoire visee
        double curveNear = track.curvatureAhead(kart.loc.s, 26 + speed);
        double curveFar = track.curvatureAhead(kart.loc.s + 25, 45);
        // en epingle, viser trop loin fait couper le virage : on raccourcit
        double look = (9 + Math.abs(speed) * 0.62)
                * Math.clamp(1 - curveNear * 9, 0.5, 1.0);
        double targetS = kart.loc.s + look;

        wanderTimer -= dt;
        if (wanderTimer <= 0) {
            wanderTimer = 1.5 + rnd.nextDouble() * 2.5;
            wander = (rnd.nextDouble() - 0.5) * (1.2 - difficulty.lineQuality);
        }

        // corde : on se place a l'interieur du virage
        double bend = signedBend(kart.loc.s, 30);
        // le cap augmente quand la piste tourne du cote du vecteur lateral :
        // viser la corde revient donc a suivre le signe de "bend"
        // largeur locale : sur un circuit qui se resserre, viser la corde d'une
        // piste large ferait passer l'IA dans le mobilier
        double largeur = track.halfRoadAtS(kart.loc.s);
        double idealLat = bend * largeur * 0.62 * difficulty.lineQuality;
        idealLat += Math.sin(race.time * 0.35 + linePhase) * largeur * 0.16 * lineAmp;
        idealLat += wander * largeur;
        idealLat += avoidance(race);
        idealLat = Math.clamp(idealLat, -largeur * 0.92, largeur * 0.92);

        Vec3 target = track.worldAt(targetS, idealLat);
        double desired = Math.atan2(target.x - kart.pos.x, target.z - kart.pos.z);
        double err = MathUtil.wrapPi(desired - kart.heading);
        // gain plus fort quand l'ecart de cap est grand : indispensable en epingle
        double gain = 2.4 + Math.min(1.8, Math.abs(err) * 2.2);
        kart.controls.steer = Math.clamp(err * gain, -1, 1);

        // --- allure
        double curve = Math.max(curveNear, curveFar * 0.7);
        double cornerFactor = Math.clamp(1.0 - curve * 18.0, 0.28, 1.0);
        double targetSpeed = kart.def.maxSpeed * skill * cornerFactor;
        if (!kart.onRoad()) targetSpeed *= 0.7;

        // effet elastique : on aide les retardataires, on bride les fuyards
        double gap = race.player == null ? 0 : (kart.totalProgress - race.player.totalProgress);
        double rubber = Math.clamp(-gap / 260.0, -0.10, 0.14);
        targetSpeed *= (1 + rubber);

        if (speed < targetSpeed) {
            kart.controls.accel = true;
        } else if (speed > targetSpeed * 1.12) {
            kart.controls.brake = true;
        } else {
            kart.controls.accel = rnd.nextDouble() < 0.5;
        }

        kart.controls.skid = curve > 0.045 && Math.abs(speed) > 16 && Math.abs(err) > 0.18;

        // Le wheelie doit etre tenu pour servir a quelque chose : le kart met
        // pres d'une demi-seconde a le lever. Un tirage par image ne produirait
        // que des impulsions isolees, jamais la duree necessaire.
        boolean wheelieUtile = curve < 0.008 && speed > kart.def.maxSpeed * 0.8;
        if (wheelieTime > 0) {
            wheelieTime -= dt;
            kart.controls.wheelie = wheelieUtile;
        } else if (wheelieUtile && rnd.nextDouble() < dt * 0.6) {
            wheelieTime = 1.2 + rnd.nextDouble() * 1.8;
            kart.controls.wheelie = true;
        }

        // --- objets
        itemTimer -= dt;
        if (kart.item != null && kart.itemCount > 0 && itemTimer <= 0) {
            if (shouldFire(race)) {
                kart.controls.fire = true;
                itemTimer = 0.7 + rnd.nextDouble() * (2.5 - 2.0 * difficulty.itemSkill);
            } else {
                itemTimer = 0.4;
            }
        }
    }

    private boolean shouldFire(Race race) {
        ItemType t = kart.item;
        if (t == null) return false;
        if (rnd.nextDouble() > 0.35 + difficulty.itemSkill * 0.65) return false;
        return switch (t) {
            case ZIPPER -> track.curvatureAhead(kart.loc.s, 45) < 0.012;
            case MAGNET -> race.kartAhead(kart) != null;
            case HOMING -> {
                Kart ahead = race.kartAhead(kart);
                yield ahead != null && Math.abs(track.deltaS(kart.loc.s, ahead.loc.s)) < 90;
            }
            case SPARK -> true;
        };
    }

    /** Ecart a ajouter pour ne pas percuter le kart qui precede. */
    private double avoidance(Race race) {
        double push = 0;
        for (Kart other : race.karts) {
            if (other == kart) continue;
            double ds = track.deltaS(kart.loc.s, other.loc.s);
            if (ds < 1 || ds > 16) continue;
            double dl = kart.loc.lateral - other.loc.lateral;
            if (Math.abs(dl) > 4.5) continue;
            double strength = (16 - ds) / 16.0 * 4.5;
            push += (dl >= 0 ? 1 : -1) * strength;
        }
        return Math.clamp(push, -6, 6);
    }

    /**
     * Signe et intensite du virage a venir : positif = la piste tourne vers la
     * droite (au sens du vecteur lateral du circuit).
     */
    private double signedBend(double s, double lookMeters) {
        double h0 = track.headingAtS(s);
        double h1 = track.headingAtS(s + lookMeters);
        double d = MathUtil.wrapPi(h1 - h0);
        return Math.clamp(d * 2.2, -1, 1);
    }
}
