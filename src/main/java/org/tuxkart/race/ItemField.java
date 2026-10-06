package org.tuxkart.race;

import org.tuxkart.audio.Sfx;
import org.tuxkart.items.ItemType;
import org.tuxkart.items.Pickup;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.track.Track;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Semis d'objets sur le circuit et logique de ramassage.
 *
 * Comme dans TuxKart, on alterne des chapelets de harengs (bonus de vitesse,
 * ou piege pour les rouges) et des boites de collectables.
 */
public final class ItemField {

    private static final double PICKUP_RADIUS_S = 2.2;
    private static final double PICKUP_RADIUS_LAT = 1.9;
    /** Longueur d'un casier de l'index, en metres. Doit depasser PICKUP_RADIUS_S. */
    private static final double BUCKET = 12.0;

    public final List<Pickup> pickups = new ArrayList<>();
    private final Track track;
    private final Random rnd;

    /**
     * Objets ranges par tronçon de piste.
     *
     * Un circuit porte une centaine d'objets ; les confronter tous a chacun des
     * huit karts a chaque image, c'est un millier de tests dont trois servent.
     * L'abscisse curviligne suffit a n'examiner que le voisinage immediat.
     */
    private final List<List<Pickup>> buckets = new ArrayList<>();

    public ItemField(Track track) {
        this.track = track;
        this.rnd = new Random(track.def.id.hashCode() * 131L + 17);
        generate();
        index();
    }

    private void index() {
        // au moins trois casiers : en dessous, le balayage des voisins
        // repasserait deux fois sur le meme et doublerait les ramassages
        int count = Math.max(3, (int) Math.ceil(track.length / BUCKET));
        for (int i = 0; i < count; i++) buckets.add(new ArrayList<>());
        for (Pickup p : pickups) {
            buckets.get(bucketOf(p.s)).add(p);
        }
    }

    private int bucketOf(double s) {
        return MathUtil.mod((int) Math.floor(MathUtil.mod(s, track.length) / BUCKET),
                buckets.size());
    }

    private void generate() {
        double s = 40;
        int k = 0;
        while (s < track.length - 25) {
            // largeur locale : sur une piste qui se resserre, une rangee calee
            // sur la largeur de reference poserait ses boites dans le decor
            double h = track.halfRoadAtS(s);
            switch (k % 3) {
                case 0 -> {
                    // rangee de boites en travers de la piste
                    for (double lat : new double[]{-h * 0.58, 0, h * 0.58}) {
                        pickups.add(new Pickup(Pickup.Kind.BOX, s, lat));
                    }
                    s += 52;
                }
                case 1 -> {
                    // chapelet de harengs verts en leger zigzag
                    double base = (rnd.nextBoolean() ? 1 : -1) * h * 0.45;
                    for (int i = 0; i < 5; i++) {
                        double lat = base * Math.cos(i * 0.7);
                        pickups.add(new Pickup(Pickup.Kind.GREEN, s + i * 4.5, lat));
                    }
                    s += 44;
                }
                default -> {
                    // piege rouge sur la trajectoire, recompenses a l'exterieur
                    pickups.add(new Pickup(Pickup.Kind.RED, s, 0));
                    double side = rnd.nextBoolean() ? 1 : -1;
                    pickups.add(new Pickup(Pickup.Kind.SILVER, s + 7, side * h * 0.7));
                    pickups.add(new Pickup(Pickup.Kind.SILVER, s + 11, side * h * 0.7));
                    if (k % 6 == 2) {
                        pickups.add(new Pickup(Pickup.Kind.GOLD, s + 16, -side * h * 0.75));
                    }
                    s += 40;
                }
            }
            k++;
        }
    }

    public void update(double dt, Race race) {
        for (Pickup p : pickups) {
            if (p.respawn > 0) p.respawn -= dt;
        }
        for (Kart kart : race.karts) {
            if (kart.beingRescued()) continue;
            // le casier courant et ses deux voisins couvrent largement le rayon
            // de ramassage, quelle que soit la vitesse du kart
            int here = bucketOf(kart.loc.s);
            for (int k = -1; k <= 1; k++) {
                for (Pickup p : buckets.get(MathUtil.mod(here + k, buckets.size()))) {
                    if (!p.available()) continue;
                    double ds = track.deltaS(kart.loc.s, p.s);
                    if (Math.abs(ds) > PICKUP_RADIUS_S) continue;
                    if (Math.abs(kart.loc.lateral - p.lateral) > PICKUP_RADIUS_LAT) continue;
                    collect(kart, p, race);
                }
            }
        }
    }

    private void collect(Kart kart, Pickup p, Race race) {
        switch (p.kind) {
            case BOX -> {
                if (kart.item == null || kart.itemCount <= 0) {
                    kart.giveItem(rollItem(kart, race));
                    p.respawn = 7;
                    if (kart.human) {
                        race.sounds.add(Sfx.BOX);
                        race.notice("Objet : " + kart.item.label);
                    }
                }
            }
            case GREEN -> {
                kart.herrings += 1;
                p.respawn = 13;
                if (kart.human) race.sounds.add(Sfx.HERRING);
            }
            case SILVER -> {
                kart.herrings += 2;
                p.respawn = 15;
                if (kart.human) race.sounds.add(Sfx.HERRING);
            }
            case GOLD -> {
                kart.herrings += 5;
                p.respawn = 20;
                if (kart.human) {
                    race.sounds.add(Sfx.HERRING);
                    race.notice("Hareng doré !");
                }
            }
            case RED -> {
                kart.herrings = Math.max(0, kart.herrings - 2);
                kart.slowDown(1.6);
                p.respawn = 11;
                if (kart.human) {
                    race.sounds.add(Sfx.HIT);
                    race.notice("Hareng rouge...");
                }
            }
        }
    }

    /**
     * Tirage pondere : les derniers recoivent plus souvent du missile ou du
     * turbo, ce qui garde le peloton groupe.
     */
    private ItemType rollItem(Kart kart, Race race) {
        double behind = (kart.position - 1) / (double) Math.max(1, race.karts.size() - 1);
        double r = rnd.nextDouble();
        if (behind > 0.5) {
            if (r < 0.34) return ItemType.HOMING;
            if (r < 0.66) return ItemType.ZIPPER;
            if (r < 0.85) return ItemType.MAGNET;
            return ItemType.SPARK;
        }
        if (r < 0.30) return ItemType.SPARK;
        if (r < 0.60) return ItemType.ZIPPER;
        if (r < 0.85) return ItemType.HOMING;
        return ItemType.MAGNET;
    }
}
