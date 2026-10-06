package org.tuxkart.race;

import org.tuxkart.audio.Sfx;
import org.tuxkart.core.Difficulty;
import org.tuxkart.core.GameSettings;
import org.tuxkart.items.ItemType;
import org.tuxkart.items.Projectile;
import org.tuxkart.kart.Kart;
import org.tuxkart.kart.KartDef;
import org.tuxkart.kart.KartRoster;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Track;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/** Deroulement d'une course : depart, physique, objets, classement, arrivee. */
public final class Race {

    /**
     * COUNTDOWN : grille immobile. RUNNING : course. PLAYER_FINISHED : le
     * joueur a franchi la ligne, la course continue derriere lui quelques
     * secondes. OVER : plus rien a montrer, l'ecran de resultats peut prendre
     * la main.
     */
    public enum State { COUNTDOWN, RUNNING, PLAYER_FINISHED, OVER }

    /** Duree d'affichage de l'arrivee avant de basculer sur les resultats. */
    public static final double FINISH_LINGER = 5.0;

    public static final class Notice {
        public final String text;
        public double life;

        Notice(String text, double life) {
            this.text = text;
            this.life = life;
        }
    }

    public final Track track;
    public final List<Kart> karts = new ArrayList<>();
    public final List<AIDriver> ais = new ArrayList<>();
    public final List<Projectile> projectiles = new ArrayList<>();
    public final ItemField items;
    /** Le kart du joueur. Jamais nul : la grille compte toujours au moins un kart. */
    public final Kart player;
    public final int totalLaps;
    public final Difficulty difficulty;

    public State state = State.COUNTDOWN;
    /** Temps de course, negatif pendant le decompte. */
    public double time;
    public double countdown = 3.9;
    private int lastCountdownTick = 4;
    /** Temps ecoule depuis l'arrivee du joueur ; au-dela de FINISH_LINGER, on passe a OVER. */
    public double sincePlayerFinished;

    public final List<Sfx> sounds = new ArrayList<>();
    public final List<Notice> notices = new ArrayList<>();
    private final List<Kart> ranking = new ArrayList<>();
    private final Random rnd = org.tuxkart.core.Rng.stream(41);
    private final Track.Loc scratch = new Track.Loc();
    private int playerLastLap;

    public Race(GameSettings settings) {
        org.tuxkart.core.Rng.reset();
        this.track = new Track(settings.track);
        this.totalLaps = settings.laps;
        this.difficulty = settings.difficulty;

        List<KartDef> pool = new ArrayList<>(KartRoster.ALL);
        pool.remove(settings.kart);
        Collections.shuffle(pool, rnd);

        int total = Math.clamp(settings.totalKarts(), 1, KartRoster.ALL.size());
        // le joueur part en fond de grille : il y a quelque chose a faire
        int playerGrid = total - 1;

        Kart human = null;
        int poolIdx = 0;
        for (int i = 0; i < total; i++) {
            if (i == playerGrid) {
                human = new Kart(settings.kart, track, true, i);
                karts.add(human);
            } else {
                Kart ai = new Kart(pool.get(poolIdx++), track, false, i);
                karts.add(ai);
                ais.add(new AIDriver(ai, difficulty, rnd.nextLong()));
            }
        }
        this.player = human;
        this.items = new ItemField(track);
        this.time = -countdown;
        updatePositions();
    }

    /** Pilote automatique pour le kart du joueur : sert au mode demonstration. */
    private AIDriver autopilot;

    public void enableAutopilot() {
        if (player != null && autopilot == null) {
            autopilot = new AIDriver(player, difficulty, rnd.nextLong());
        }
    }

    public boolean hasAutopilot() {
        return autopilot != null;
    }

    public void notice(String text) {
        notices.add(0, new Notice(text, 2.6));
        while (notices.size() > 4) notices.remove(notices.size() - 1);
    }

    // ---------------------------------------------------------------- update

    public void update(double dt) {
        for (Notice n : notices) n.life -= dt;
        notices.removeIf(n -> n.life <= 0);

        if (state == State.COUNTDOWN) {
            countdown -= dt;
            int tick = (int) Math.ceil(countdown);
            if (tick < lastCountdownTick) {
                lastCountdownTick = tick;
                if (tick > 0) sounds.add(Sfx.COUNTDOWN);
            }
            if (countdown <= 0) {
                state = State.RUNNING;
                countdown = 0;
                time = 0;
                sounds.add(Sfx.GO);
                notice("PARTEZ !");
                for (Kart k : karts) k.lapStartTime = 0;
            } else {
                time = -countdown;
            }
        } else {
            time += dt;
        }

        if (state == State.PLAYER_FINISHED) {
            sincePlayerFinished += dt;
            if (sincePlayerFinished >= FINISH_LINGER || allFinished()) state = State.OVER;
        }

        boolean running = state != State.COUNTDOWN;

        for (AIDriver ai : ais) ai.update(dt, this);
        if (autopilot != null) autopilot.update(dt, this);

        for (Kart k : karts) {
            if (!k.human && k.finished) {
                // les IA arrivees continuent de rouler, mais sans objets
                k.item = null;
            }
            k.update(dt, time, running);
            if (k.wallHit && k.human && Math.abs(k.speed) > 6) sounds.add(Sfx.BUMP);
            if (k.justLanded && k.human) sounds.add(Sfx.JUMP);
        }

        applyMagnets(dt);
        resolveKartCollisions();
        items.update(dt, this);
        handleFiring();
        updateProjectiles(dt);
        updatePositions();
        checkLapsAndFinish();
    }

    private void applyMagnets(double dt) {
        for (Kart k : karts) {
            if (k.magnetTimer <= 0) continue;
            Kart ahead = kartAhead(k);
            if (ahead == null) continue;
            double ds = track.deltaS(k.loc.s, ahead.loc.s);
            if (ds < 0 || ds > 140) continue;
            // canal dedie : rearmer le minuteur du turbo empecherait ce dernier
            // d'expirer et prolongerait son facteur pendant tout l'aimant
            k.tow(0.1, 1.20);
            double desired = Math.atan2(ahead.pos.x - k.pos.x, ahead.pos.z - k.pos.z);
            double err = MathUtil.wrapPi(desired - k.heading);
            k.heading += Math.clamp(err, -0.9 * dt, 0.9 * dt);
        }
    }

    private void resolveKartCollisions() {
        int n = karts.size();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                Kart a = karts.get(i), b = karts.get(j);
                if (a.beingRescued() || b.beingRescued()) continue;
                double dx = b.pos.x - a.pos.x, dz = b.pos.z - a.pos.z;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > 2.5 || d < 1e-4) continue;
                double overlap = (2.5 - d) / 2;
                double ux = dx / d, uz = dz / d;
                double ma = a.def.mass, mb = b.def.mass;
                double shareA = mb / (ma + mb), shareB = ma / (ma + mb);
                a.pos = new Vec3(a.pos.x - ux * overlap * 2 * shareA, a.pos.y,
                        a.pos.z - uz * overlap * 2 * shareA);
                b.pos = new Vec3(b.pos.x + ux * overlap * 2 * shareB, b.pos.y,
                        b.pos.z + uz * overlap * 2 * shareB);
                // la poussee se projette sur le vecteur lateral de CHAQUE kart :
                // il faut le produit scalaire complet, sinon un contact oriente
                // selon Z n'ecarte personne alors que le meme, tourne de 90
                // degres, ecarte au maximum
                Vec3 sa = a.sideVec(), sb = b.sideVec();
                a.lateralVel -= (ux * sa.x + uz * sa.z) * 3 * shareA;
                b.lateralVel += (ux * sb.x + uz * sb.z) * 3 * shareB;
                double loss = 0.06;
                a.speed *= (1 - loss * shareA);
                b.speed *= (1 - loss * shareB);
                if ((a.human || b.human) && Math.abs(a.speed - b.speed) > 5) sounds.add(Sfx.BUMP);
            }
        }
    }

    private void handleFiring() {
        for (Kart k : karts) {
            if (!k.fireRequested) continue;
            k.fireRequested = false;
            k.controls.fire = false;
            if (k.item == null || k.itemCount <= 0) continue;
            fire(k, k.item);
            k.itemCount--;
            if (k.itemCount <= 0) k.item = null;
        }
    }

    private void fire(Kart k, ItemType type) {
        switch (type) {
            case ZIPPER -> {
                k.boost(3.2, 1.62);
                if (k.human) {
                    sounds.add(Sfx.BOOST);
                    notice("Turbo !");
                }
            }
            case MAGNET -> {
                k.magnetTimer = 8.0;
                if (k.human) {
                    sounds.add(Sfx.BOOST);
                    notice("Aimant actif");
                }
            }
            case HOMING -> {
                Projectile p = new Projectile(Projectile.Kind.HOMING, k,
                        k.pos.add(0, 0.7, 0), k.heading, 46, 9.0);
                p.s = k.loc.s;
                p.lateral = k.loc.lateral;
                p.target = kartAhead(k);
                projectiles.add(p);
                if (k.human) sounds.add(Sfx.FIRE);
            }
            case SPARK -> {
                Vec3 f = k.forwardVec();
                Projectile p = new Projectile(Projectile.Kind.SPARK, k,
                        k.pos.add(f.x * 2.5, 0.6, f.z * 2.5), k.heading,
                        Math.max(28, Math.abs(k.speed) + 16), 7.5);
                projectiles.add(p);
                if (k.human) sounds.add(Sfx.FIRE);
            }
        }
    }

    private void updateProjectiles(double dt) {
        for (Projectile p : projectiles) {
            p.life -= dt;
            if (p.armDelay > 0) p.armDelay -= dt;
            p.spin += dt * 12;
            if (p.life <= 0) {
                p.dead = true;
                continue;
            }
            if (p.kind == Projectile.Kind.HOMING) {
                p.s = MathUtil.mod(p.s + p.speed * dt, track.length);
                if (p.target != null) {
                    p.lateral = MathUtil.damp(p.lateral, p.target.loc.lateral, 2.2, dt);
                } else {
                    p.lateral = MathUtil.damp(p.lateral, 0, 1.2, dt);
                }
                Vec3 w = track.worldAt(p.s, p.lateral);
                p.heading = track.headingAtS(p.s);
                p.pos = new Vec3(w.x, w.y + 0.8, w.z);
            } else {
                Vec3 f = Vec3.fromHeading(p.heading);
                double nx = p.pos.x + f.x * p.speed * dt;
                double nz = p.pos.z + f.z * p.speed * dt;
                Track.Loc l = track.locate(nx, nz, p.hint, scratch);
                if (Math.abs(l.lateral) > track.halfCorridor - 0.5) {
                    // rebond sur le muret : on renvoie symetriquement
                    double trackHeading = l.heading;
                    double rel = MathUtil.wrapPi(p.heading - trackHeading);
                    p.heading = MathUtil.wrapPi(trackHeading - rel);
                    f = Vec3.fromHeading(p.heading);
                    nx = p.pos.x + f.x * p.speed * dt * 1.5;
                    nz = p.pos.z + f.z * p.speed * dt * 1.5;
                    l = track.locate(nx, nz, l.index, scratch);
                }
                p.hint = l.index;
                p.pos = new Vec3(nx, l.height + 0.6, nz);
            }

            for (Kart k : karts) {
                if (k == p.owner && p.armDelay > 0) continue;
                if (k.beingRescued()) continue;
                double d = Math.hypot(k.pos.x - p.pos.x, k.pos.z - p.pos.z);
                if (d < 1.9) {
                    k.spinOut(2.0);
                    p.dead = true;
                    if (k.human) {
                        sounds.add(Sfx.HIT);
                        notice("Touché par " + (p.owner != null ? p.owner.def.name : "?") + " !");
                    } else if (p.owner != null && p.owner.human) {
                        sounds.add(Sfx.HIT);
                        notice("Touché : " + k.def.name);
                    }
                    break;
                }
            }
        }
        projectiles.removeIf(p -> p.dead);
    }

    private void updatePositions() {
        ranking.clear();
        ranking.addAll(karts);
        ranking.sort(Comparator.comparingDouble(k -> k.finished ? -1e9 + k.finishTime : -k.totalProgress));
        for (int i = 0; i < ranking.size(); i++) {
            ranking.get(i).position = i + 1;
        }
    }

    public List<Kart> standings() {
        return ranking;
    }

    private void checkLapsAndFinish() {
        for (Kart k : karts) {
            if (!k.finished && k.lapsDone() >= totalLaps) {
                k.finished = true;
                k.finishTime = time;
                if (k.human) {
                    sounds.add(Sfx.FINISH);
                    state = State.PLAYER_FINISHED;
                    sincePlayerFinished = 0;
                }
            }
        }
        if (player != null && !player.finished) {
            int lap = Math.min(player.lapsDone() + 1, totalLaps);
            if (lap != playerLastLap) {
                if (playerLastLap > 0) {
                    sounds.add(Sfx.LAP);
                    notice(lap == totalLaps ? "Dernier tour !" : "Tour " + lap + " / " + totalLaps);
                }
                playerLastLap = lap;
            }
        }
    }

    /** Le kart immediatement devant celui-ci au classement. */
    public Kart kartAhead(Kart k) {
        Kart best = null;
        double bestGap = Double.MAX_VALUE;
        for (Kart o : karts) {
            if (o == k) continue;
            double gap = o.totalProgress - k.totalProgress;
            if (gap > 0 && gap < bestGap) {
                bestGap = gap;
                best = o;
            }
        }
        return best;
    }

    public int playerLap() {
        if (player == null) return 1;
        return Math.clamp(player.lapsDone() + 1, 1, totalLaps);
    }

    public boolean allFinished() {
        for (Kart k : karts) {
            if (!k.finished) return false;
        }
        return true;
    }
}
