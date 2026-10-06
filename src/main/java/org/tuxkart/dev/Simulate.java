package org.tuxkart.dev;

import org.tuxkart.core.Difficulty;
import org.tuxkart.core.GameSettings;
import org.tuxkart.items.ItemType;
import org.tuxkart.items.Pickup;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.Vec3;
import org.tuxkart.math.MathUtil;
import org.tuxkart.race.Race;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

/**
 * Banc d'essai sans interface : la simulation (piste, physique, IA, objets,
 * classement) ne depend pas du rendu, on peut donc la faire tourner a pleine
 * vitesse pour verifier qu'un trace est praticable et que l'IA boucle ses
 * tours sans se faire secourir.
 *
 * Usage : java -cp ... org.tuxkart.dev.Simulate [difficulte]
 */
public final class Simulate {

    private static final double DT = 1.0 / 60;
    private static final boolean VERBOSE = Boolean.getBoolean("tuxkart.verbose");

    private static boolean okAll = true;

    private static void check(String label, boolean condition) {
        okAll &= condition;
        System.out.printf("  [%s] %s%n", condition ? "OK " : "KO ", label);
    }

    /** Verifie les regles du jeu sans passer par l'interface. */
    private static boolean rulesSelfTest() {
        System.out.println("Regles du jeu :");
        GameSettings s = new GameSettings();
        s.opponents = 3;
        s.laps = 1;
        Race race = new Race(s);
        Kart p = race.player;

        while (race.state == Race.State.COUNTDOWN) race.update(DT);
        check("le decompte laisse la place a la course", race.state == Race.State.RUNNING);

        p.giveItem(ItemType.ZIPPER);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;
        check("le turbo est consomme et accelere", p.item == null && p.boostTimer > 0);

        p.giveItem(ItemType.SPARK);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;
        check("l'etincelle part et laisse 2 tirs",
                p.itemCount == 2 && race.projectiles.size() == 1);

        p.giveItem(ItemType.HOMING);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;
        check("le missile part", race.projectiles.size() == 2);

        // ramassage : on pose le kart pile sur un hareng vert disponible
        Pickup green = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.GREEN && x.available())
                .findFirst().orElse(null);
        int before = p.herrings;
        if (green != null) {
            Vec3 w = race.track.worldAt(green.s, green.lateral);
            p.pos = new Vec3(w.x, w.y, w.z);
            race.track.locate(p.pos.x, p.pos.z, -1, p.loc);
            race.update(DT);
        }
        check("un hareng vert se ramasse", green != null && p.herrings > before);

        // sauvetage : depuis le bord du couloir, le kart doit revenir sur l'axe
        Vec3 borde = race.track.worldAt(p.loc.s, race.track.halfCorridor - 1);
        p.pos = new Vec3(borde.x, borde.y, borde.z);
        race.track.locate(p.pos.x, p.pos.z, -1, p.loc);
        p.requestRescue();
        for (int i = 0; i < 200; i++) race.update(DT);
        check("le sauvetage repose le kart sur la piste",
                !p.beingRescued() && Math.abs(p.loc.lateral) < 1.5);

        // comptage des tours et arrivee
        int lapBefore = p.lapsDone();
        p.totalProgress += race.track.length;
        race.update(DT);
        check("un tour de plus est compte", p.lapsDone() == lapBefore + 1);
        check("l'arrivee est detectee", p.finished && p.finishTime > 0);

        // classement coherent
        race.update(DT);
        check("le classement est complet", race.standings().size() == race.karts.size());
        return okAll;
    }

    /**
     * Suit un kart en l'air, d'un decollage a sa reception.
     *
     * <p>Un tremplin ne se regle pas au jugement : la hauteur et la
     * demi-longueur d'une bosse decident de la vitesse a partir de laquelle le
     * sol se derobe, et le reste — trois secondes de vol ou un frisson —
     * n'apparait qu'a la mesure. C'est ce que ce petit suiveur donne, sur toute
     * une course et pour les huit karts.
     */
    private static final class Vol {
        double longueur, hauteur, duree;
        private boolean enVol;
        private Vec3 depart = Vec3.ZERO;
        private double sol, courant, duree0, hauteur0;

        /** Rend le vol qui vient de s'achever, ou {@code null}. */
        Vol suivre(Kart k, double dt) {
            if (k.airborne) {
                if (!enVol) {
                    enVol = true;
                    depart = k.pos;
                    sol = k.pos.y;
                    duree0 = 0;
                    hauteur0 = 0;
                }
                duree0 += dt;
                hauteur0 = Math.max(hauteur0, k.pos.y - sol);
                courant = k.pos.distanceXZ(depart);
                return null;
            }
            if (!enVol) return null;
            enVol = false;
            // le petit ressaut d'un dos d'ane n'est pas un saut : on ne retient
            // que ce qui quitte vraiment le sol
            if (courant < 4 || duree0 < 0.25) return null;
            Vol fini = new Vol();
            fini.longueur = courant;
            fini.hauteur = hauteur0;
            fini.duree = duree0;
            return fini;
        }

        String resume() {
            return longueur < 1 ? "aucun envol      "
                    : String.format("plus long saut %4.0f m, %3.1f m de haut, %3.1f s",
                    longueur, hauteur, duree);
        }
    }

    public static void main(String[] args) {
        Difficulty diff = args.length > 0
                ? Difficulty.valueOf(args[0].toUpperCase())
                : Difficulty.CHAMPION;

        System.out.printf("Banc d'essai - difficulte %s%n%n", diff.label);
        boolean allOk = rulesSelfTest();
        System.out.println();

        for (TrackDef def : Tracks.ALL) {
            GameSettings s = new GameSettings();
            s.track = def;
            s.difficulty = diff;
            s.laps = 3;
            s.opponents = 7;
            Race race = new Race(s);
            race.enableAutopilot();

            int[] seen = new int[race.karts.size()];
            // mesure des envols : un tremplin ne se regle pas au jugement, il
            // se mesure — longueur, hauteur et temps de vol du plus grand saut
            Vol[] vols = new Vol[race.karts.size()];
            for (int i = 0; i < vols.length; i++) vols[i] = new Vol();
            Vol record = new Vol();
            double simulated = 0;
            double limit = 600;
            while (!race.allFinished() && simulated < limit) {
                race.update(DT);
                race.sounds.clear();
                simulated += DT;
                for (int i = 0; i < race.karts.size(); i++) {
                    Vol v = vols[i].suivre(race.karts.get(i), DT);
                    if (v != null && v.longueur > record.longueur) record = v;
                }
                if (VERBOSE) {
                    for (int i = 0; i < race.karts.size(); i++) {
                        Kart k = race.karts.get(i);
                        if (k.rescueCount > seen[i]) {
                            seen[i] = k.rescueCount;
                            System.out.printf("    ! %s bloque a s=%.0f m (%.0f %% du tour), "
                                            + "lateral=%.1f m%n",
                                    k.def.name, k.loc.s,
                                    100 * k.loc.s / race.track.length, k.loc.lateral);
                        }
                    }
                }
            }

            int rescues = 0;
            double bestLap = Double.MAX_VALUE;
            double worstFinish = 0;
            int finished = 0;
            for (Kart k : race.karts) {
                rescues += k.rescueCount;
                bestLap = Math.min(bestLap, k.bestLap);
                if (k.finished) {
                    finished++;
                    worstFinish = Math.max(worstFinish, k.finishTime);
                }
            }
            boolean ok = finished == race.karts.size() && rescues == 0;
            allOk &= ok;

            System.out.printf("%-22s %7.0f m  |  %d/%d a l'arrivee  |  meilleur tour %s  "
                            + "|  dernier %s  |  sauvetages %d  |  %s  %s%n",
                    def.name, race.track.length, finished, race.karts.size(),
                    MathUtil.formatTime(bestLap), MathUtil.formatTime(worstFinish),
                    rescues, record.resume(), ok ? "OK" : "<-- a revoir");
        }

        System.out.println();
        System.out.println(allOk ? "Tous les circuits sont praticables."
                : "Certains circuits demandent un reglage.");
    }
}
