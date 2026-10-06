package org.tuxkart.kart;

import org.tuxkart.items.ItemType;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Track;

/**
 * Etat et physique d'un kart. Le modele est volontairement « arcade », dans
 * l'esprit de TuxKart : pas de simulation de pneus, mais une vitesse le long
 * du cap, un derapage lateral amorti par l'adherence, et de la gravite pour
 * les sauts.
 */
public final class Kart {

    private static final double GRAVITY = 22.0;
    private static final double BRAKE_DECEL = 26.0;
    private static final double REVERSE_MAX = 9.0;
    private static final double ROLL_FRICTION = 4.0;
    private static final double JUMP_SPEED = 7.2;

    public final KartDef def;
    public final Track track;
    public final boolean human;
    public final int startRank;

    public final Controls controls = new Controls();

    // --- etat physique -----------------------------------------------------
    public Vec3 pos;
    public double heading;
    public double speed;
    public double lateralVel;
    public double vy;
    public boolean airborne;
    public double skidAmount;
    public double wheelie;
    private double jumpCooldown;

    // --- effets ------------------------------------------------------------
    public double spinTimer;
    public double slowTimer;
    public double boostTimer;
    public double boostFactor = 1.0;
    /**
     * Aspiration de l'aimant : deuxieme canal de poussee, distinct du turbo.
     *
     * Les deux ne peuvent pas partager {@code boostTimer} : l'aimant rearme son
     * minuteur a chaque image, ce qui empechait le turbo d'expirer et laissait
     * son facteur — bien plus fort — actif pendant toute la duree de l'aimant.
     */
    public double towTimer;
    public double towFactor = 1.0;
    public double magnetTimer;
    public double rescueTimer;
    private boolean rescuePlaced;
    public double rescueLift;
    private double stuckTimer;

    // --- rendu -------------------------------------------------------------
    public double visualPitch;
    public double visualRoll;
    public boolean wallHit;
    public boolean justLanded;

    // --- course ------------------------------------------------------------
    public Track.Loc loc = new Track.Loc();
    public double totalProgress;
    private double prevS;
    public int position = 1;
    public boolean finished;
    public double finishTime;
    public double lapStartTime;
    public double bestLap = Double.MAX_VALUE;
    private int lastCountedLap;

    // --- objets ------------------------------------------------------------
    public ItemType item;
    public int itemCount;
    public int herrings;
    public boolean fireRequested;

    public Kart(KartDef def, Track track, boolean human, int startRank) {
        this.def = def;
        this.track = track;
        this.human = human;
        this.startRank = startRank;
        Vec3 grid = track.gridPosition(startRank);
        this.pos = grid;
        this.heading = track.gridHeading(startRank);
        track.locate(pos.x, pos.z, -1, loc);
        this.pos = new Vec3(pos.x, loc.height, pos.z);
        this.prevS = loc.s;
        this.totalProgress = loc.s - track.length;
    }

    public Vec3 forwardVec() {
        return Vec3.fromHeading(heading);
    }

    /** Vecteur unitaire vers la droite du kart. */
    public Vec3 sideVec() {
        return new Vec3(Math.cos(heading), 0, -Math.sin(heading));
    }

    public int lapsDone() {
        return Math.max(0, (int) Math.floor(totalProgress / track.length));
    }

    public double speedKmh() {
        return Math.abs(speed) * 3.6;
    }

    /** Altitude d'affichage (le sauvetage souleve le kart dans les airs). */
    public double visualY() {
        return pos.y + rescueLift;
    }

    public boolean onRoad() {
        return Math.abs(loc.lateral) <= track.halfRoadAt[loc.index] + 0.4;
    }

    public boolean beingRescued() {
        return rescueTimer > 0;
    }

    public boolean controllable() {
        return rescueTimer <= 0 && spinTimer <= 0;
    }

    // ---------------------------------------------------------------- effets

    public void spinOut(double seconds) {
        if (rescueTimer > 0) return;
        spinTimer = Math.max(spinTimer, seconds);
        speed *= 0.35;
        lateralVel = 0;
        item = null;
        itemCount = 0;
        herrings = Math.max(0, herrings - 3);
    }

    public void boost(double seconds, double factor) {
        boostTimer = Math.max(boostTimer, seconds);
        boostFactor = Math.max(boostFactor, factor);
        speed = Math.max(speed, def.maxSpeed * 0.55);
    }

    /** Aspiration derriere le kart de devant : poussee douce, rearmee en continu. */
    public void tow(double seconds, double factor) {
        towTimer = Math.max(towTimer, seconds);
        towFactor = Math.max(towFactor, factor);
    }

    /** Poussee effective, le meilleur des deux canaux. */
    public double speedFactor() {
        return Math.max(boostTimer > 0 ? boostFactor : 1.0,
                towTimer > 0 ? towFactor : 1.0);
    }

    /** Vrai si une poussee notable est en cours : sert aux flammes et a la camera. */
    public boolean boosting() {
        return speedFactor() > 1.05;
    }

    public void slowDown(double seconds) {
        slowTimer = Math.max(slowTimer, seconds);
    }

    public void giveItem(ItemType type) {
        item = type;
        itemCount = type.charges;
    }

    /** Nombre de sauvetages, utile pour valider un trace ou regler l'IA. */
    public int rescueCount;

    public void requestRescue() {
        if (rescueTimer <= 0) {
            rescueCount++;
            rescueTimer = 2.2;
            rescuePlaced = false;
            speed = 0;
            lateralVel = 0;
            vy = 0;
            spinTimer = 0;
            stuckTimer = 0;
        }
    }

    // ---------------------------------------------------------------- update

    public void update(double dt, double raceTime, boolean controlsEnabled) {
        wallHit = false;
        justLanded = false;

        if (rescueTimer > 0) {
            updateRescue(dt);
            return;
        }

        if (spinTimer > 0) spinTimer -= dt;
        if (boostTimer > 0) {
            boostTimer -= dt;
            if (boostTimer <= 0) boostFactor = 1.0;
        }
        if (towTimer > 0) {
            towTimer -= dt;
            if (towTimer <= 0) towFactor = 1.0;
        }
        if (slowTimer > 0) slowTimer -= dt;
        if (magnetTimer > 0) magnetTimer -= dt;
        if (jumpCooldown > 0) jumpCooldown -= dt;

        boolean active = controlsEnabled && controllable();
        double throttle = active && controls.accel ? 1 : 0;
        double braking = active && controls.brake ? 1 : 0;
        double steerIn = active ? Math.clamp(controls.steer, -1, 1) : 0;

        boolean road = onRoad();
        double surfaceSpeed = road ? 1.0 : 0.58;
        double surfaceGrip = road ? 1.0 : 0.55;

        // --- vitesse longitudinale
        double maxSp = def.maxSpeed * surfaceSpeed * speedFactor()
                * (1 + Math.min(herrings, 20) * 0.005);
        if (slowTimer > 0) maxSp *= 0.55;

        boolean wantWheelie = active && controls.wheelie && speed > def.maxSpeed * 0.5 && !airborne;
        wheelie = MathUtil.damp(wheelie, wantWheelie ? 1 : 0, 5.0, dt);
        if (wheelie > 0.5) maxSp *= 1.08;

        if (throttle > 0) {
            double a = def.accel * (1 - Math.clamp(speed / Math.max(1.0, maxSp), 0, 1));
            speed += a * dt;
        }
        if (braking > 0) {
            if (speed > 0.3) speed -= BRAKE_DECEL * dt;
            else speed = Math.max(-REVERSE_MAX, speed - def.accel * 0.55 * dt);
        }
        if (throttle == 0 && braking == 0) {
            speed = MathUtil.approach(speed, 0, ROLL_FRICTION * dt);
        }
        if (!road) {
            // hors piste : une trainee proportionnelle a la vitesse, plus un
            // frottement constant qui finit d'arreter le kart au pas
            speed -= speed * 0.55 * dt;
            speed = MathUtil.approach(speed, 0, 1.5 * dt);
        }
        if (speed > maxSp) speed = MathUtil.approach(speed, maxSp, 16 * dt);
        speed = Math.clamp(speed, -REVERSE_MAX, def.maxSpeed * 1.9);

        // --- direction
        double grip = def.grip * surfaceGrip * track.def.surface.grip;
        double speedNorm = Math.clamp(Math.abs(speed) / def.maxSpeed, 0, 1);
        double turn = def.turnRate * steerIn
                * Math.clamp(Math.abs(speed) / 5.0, 0, 1)
                * (1 - 0.42 * speedNorm)
                * (wheelie > 0.3 ? 0.55 : 1.0);
        if (airborne) turn *= 0.25;
        heading += turn * dt * MathUtil.sign(speed);
        if (spinTimer > 0) heading += 11.0 * dt;
        heading = MathUtil.wrapPi(heading);

        // --- derapage
        boolean skidding = active && controls.skid && Math.abs(speed) > 7
                && Math.abs(steerIn) > 0.15 && !airborne;
        skidAmount = MathUtil.damp(skidAmount, skidding ? 1 : 0, 6, dt);
        double slipGain = (1 - grip) * 2.2 + skidAmount * 0.9;
        lateralVel += -turn * speed * slipGain * dt;
        lateralVel = MathUtil.damp(lateralVel, 0, (skidding ? 2.0 : 6.5) * grip, dt);
        lateralVel = Math.clamp(lateralVel, -16, 16);

        // --- deplacement
        Vec3 f = forwardVec();
        Vec3 sd = sideVec();
        double nx = pos.x + (f.x * speed + sd.x * lateralVel) * dt;
        double nz = pos.z + (f.z * speed + sd.z * lateralVel) * dt;

        track.locate(nx, nz, loc.index, loc);
        double limit = track.halfCorridorAt[loc.index] - 0.9;
        if (track.def.open) {
            // Pas de muret : on sort du ruban et on roule a cote. Au-dela du
            // couloir, le sol freine — moquette, gravier, dalles — d'autant
            // plus qu'on s'en eloigne, ce qui suffit a dissuader les
            // raccourcis sans jamais bloquer le passage.
            double dehors = Math.abs(loc.lateral) - track.halfCorridorAt[loc.index];
            if (dehors > 0) {
                double frein = Math.clamp(dehors / 40, 0, 1);
                speed *= 1 - 0.9 * frein * dt;
                lateralVel *= 1 - 1.2 * frein * dt;
            }
        } else if (Math.abs(loc.lateral) > limit) {
            double over = Math.abs(loc.lateral) - limit;
            double sgn = Math.signum(loc.lateral);
            nx -= loc.side.x * sgn * over;
            nz -= loc.side.z * sgn * over;
            // on ne perd de la vitesse qu'en proportion de l'angle d'impact :
            // frotter le long du muret coute peu, le percuter de face coute
            // cher. 0 = tangent au muret, 1 = perpendiculaire.
            double frontal = Math.abs(f.x * loc.side.x + f.z * loc.side.z);
            speed *= 1 - 0.55 * frontal;
            lateralVel = -lateralVel * 0.25;
            wallHit = true;
            track.locate(nx, nz, loc.index, loc);
        }

        // --- altitude
        double ground = loc.height;

        // Courbure verticale du ruban sous les roues. Sur une crete, le sol se
        // derobe en v²·|courbure| ; des que cela depasse la pesanteur, plus
        // rien ne retient le kart et il part avec la vitesse verticale que la
        // montee lui a donnee. C'est la condition exacte du saut de bosse, et
        // elle rend la longueur du vol proportionnelle a la vitesse d'arrivee.
        if (!airborne && track.def.bumps.length > 0) {
            double d = 2.0;
            double avant = track.heightAtS(loc.s + d, loc.lateral);
            double arriere = track.heightAtS(loc.s - d, loc.lateral);
            double courbure = (avant - 2 * ground + arriere) / (d * d);
            if (courbure < 0 && speed * speed * -courbure > GRAVITY) {
                airborne = true;
                vy = (avant - arriere) / (2 * d) * speed;
                pos = new Vec3(pos.x, ground + 0.02, pos.z);
            }
        }

        if (airborne) {
            vy -= GRAVITY * dt;
            double ny = pos.y + vy * dt;
            if (ny <= ground) {
                ny = ground;
                vy = 0;
                airborne = false;
                justLanded = true;
            }
            pos = new Vec3(nx, ny, nz);
        } else {
            if (active && controls.jump && jumpCooldown <= 0) {
                vy = JUMP_SPEED;
                airborne = true;
                jumpCooldown = 0.75;
                pos = new Vec3(nx, ground + 0.05, nz);
            } else {
                pos = new Vec3(nx, MathUtil.damp(pos.y, ground, 16, dt), nz);
            }
        }

        // --- rendu
        double targetPitch = wheelie * 17 + (airborne ? 4 : 0);
        visualPitch = MathUtil.damp(visualPitch, targetPitch, 8, dt);
        double targetRoll = Math.clamp(-lateralVel * 1.3, -16, 16) - steerIn * 4 * speedNorm;
        visualRoll = MathUtil.damp(visualRoll, targetRoll, 8, dt);

        // --- progression
        double ds = track.deltaS(prevS, loc.s);
        if (Math.abs(ds) < track.length * 0.5) totalProgress += ds;
        prevS = loc.s;

        int laps = lapsDone();
        if (laps > lastCountedLap) {
            double lapTime = raceTime - lapStartTime;
            // un « tour » de moins de trois secondes ne peut venir que d'un
            // saut de progression (sauvetage, replacement de test)
            if (lapTime > 3) bestLap = Math.min(bestLap, lapTime);
            lapStartTime = raceTime;
            lastCountedLap = laps;
        }

        // --- blocage : sauvetage automatique
        // le compteur ne tourne que quand le kart a la main : sinon la grille
        // entiere se ferait secourir pendant un decompte un peu long
        if (active && Math.abs(speed) < 1.2 && !airborne) stuckTimer += dt;
        else stuckTimer = 0;
        if ((active && controls.rescue) || stuckTimer > 5) requestRescue();

        if (controls.fire && item != null && itemCount > 0) fireRequested = true;
    }

    private void updateRescue(double dt) {
        rescueTimer -= dt;
        if (rescueTimer > 1.1) {
            rescueLift = Math.min(12, rescueLift + 16 * dt);
        } else {
            if (!rescuePlaced) {
                double s = loc.s + 4;
                Vec3 c = track.centerAtS(s);
                pos = c;
                heading = track.headingAtS(s);
                track.locate(pos.x, pos.z, loc.index, loc);
                prevS = loc.s;
                rescuePlaced = true;
            }
            rescueLift = Math.max(0, rescueLift - 16 * dt);
        }
        speed = 0;
        lateralVel = 0;
        vy = 0;
        visualPitch = 0;
        visualRoll = 0;
        if (rescueTimer <= 0) {
            rescueTimer = 0;
            rescueLift = 0;
            rescuePlaced = false;
            airborne = false;
            stuckTimer = 0;
        }
    }

    /** Regime moteur normalise, pour le son et le compte-tours. */
    public double engineLoad() {
        double base = Math.clamp(Math.abs(speed) / def.maxSpeed, 0, 1);
        return 0.18 + base * 0.82 + (controls.accel ? 0.08 : 0);
    }
}
