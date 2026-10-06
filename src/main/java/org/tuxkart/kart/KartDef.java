package org.tuxkart.kart;

import javafx.scene.paint.Color;

/** Fiche d'un pilote : identite, apparence et caracteristiques de conduite. */
public final class KartDef {

    /** Museau de la mascotte assise dans le kart. */
    public enum Snout { BEAK, MUZZLE, HORN_SNOUT, NONE }

    /** Silhouette du chassis : chaque pilote pilote son propre genre de machine. */
    public enum Chassis { CLASSIQUE, BUGGY, FUSEE, BULLE, ROADSTER, HOTROD, TRIKE, BLINDE }

    /** Oreilles de la mascotte. */
    public enum Ears { NONE, RONDES, LONGUES, POINTUES, NAGEOIRE, TOUFFE }

    /** Couvre-chef. */
    public enum Headgear { NONE, CASQUETTE, CASQUE, LUNETTES, COURONNE, BANDEAU }

    /** Corpulence : elle etire ou tasse toute la mascotte. */
    public enum Build { FIN, NORMAL, TRAPU }

    /** Sourcils : c'est peu de matiere, mais c'est tout le caractere. */
    public enum Brow { NONE, FACHE, DOUX }

    /**
     * Apparence d'un pilote, montee par chainage.
     *
     * Les fiches ne declarent que ce qui les distingue du reste du plateau :
     * {@code Look.colors(...).chassis(BUGGY).ears(LONGUES).horns()}.
     */
    public static final class Look {

        final Color kart;
        final Color trim;
        final Color body;
        final Color belly;
        final Color accent;

        Chassis chassis = Chassis.CLASSIQUE;
        Snout snout = Snout.NONE;
        Ears ears = Ears.NONE;
        Headgear gear = Headgear.NONE;
        Build build = Build.NORMAL;
        Brow brow = Brow.NONE;
        boolean horns;
        boolean spikes;
        boolean beard;

        private Look(Color kart, Color trim, Color body, Color belly, Color accent) {
            this.kart = kart;
            this.trim = trim;
            this.body = body;
            this.belly = belly;
            this.accent = accent;
        }

        /** Carrosserie, liseres et jantes, puis les trois teintes de la mascotte. */
        public static Look colors(Color kart, Color trim, Color body, Color belly, Color accent) {
            return new Look(kart, trim, body, belly, accent);
        }

        public Look chassis(Chassis c) {
            chassis = c;
            return this;
        }

        public Look snout(Snout s) {
            snout = s;
            return this;
        }

        public Look ears(Ears e) {
            ears = e;
            return this;
        }

        public Look gear(Headgear h) {
            gear = h;
            return this;
        }

        public Look build(Build b) {
            build = b;
            return this;
        }

        public Look brow(Brow b) {
            brow = b;
            return this;
        }

        public Look horns() {
            horns = true;
            return this;
        }

        public Look spikes() {
            spikes = true;
            return this;
        }

        public Look beard() {
            beard = true;
            return this;
        }
    }

    public final String id;
    public final String name;
    public final String tagline;

    public final Color kartColor;
    public final Color trimColor;
    public final Color bodyColor;
    public final Color bellyColor;
    public final Color accentColor;
    public final Chassis chassis;
    public final Snout snout;
    public final Ears ears;
    public final Headgear gear;
    public final Build build;
    public final Brow brow;
    public final boolean horns;
    public final boolean spikes;
    public final boolean beard;

    /** Vitesse de pointe sur bitume, en m/s. */
    public final double maxSpeed;
    /** Acceleration nominale, en m/s^2. */
    public final double accel;
    /** Vitesse de rotation maximale, en rad/s. */
    public final double turnRate;
    /** Adherence laterale : 1 = colle a la route, 0.6 = tres glissant. */
    public final double grip;
    /** Masse relative, utilisee lors des contacts entre karts. */
    public final double mass;

    public KartDef(String id, String name, String tagline, Look look,
                   double maxSpeed, double accel, double turnRate, double grip, double mass) {
        this.id = id;
        this.name = name;
        this.tagline = tagline;
        this.kartColor = look.kart;
        this.trimColor = look.trim;
        this.bodyColor = look.body;
        this.bellyColor = look.belly;
        this.accentColor = look.accent;
        this.chassis = look.chassis;
        this.snout = look.snout;
        this.ears = look.ears;
        this.gear = look.gear;
        this.build = look.build;
        this.brow = look.brow;
        this.horns = look.horns;
        this.spikes = look.spikes;
        this.beard = look.beard;
        this.maxSpeed = maxSpeed;
        this.accel = accel;
        this.turnRate = turnRate;
        this.grip = grip;
        this.mass = mass;
    }

    /** Note sur 5 pour l'affichage des jauges du menu. */
    public int speedStars() {
        return stars(maxSpeed, 30, 38);
    }

    public int accelStars() {
        return stars(accel, 9.0, 14.0);
    }

    public int handlingStars() {
        return stars(turnRate * grip, 1.5, 2.3);
    }

    private static int stars(double v, double lo, double hi) {
        double t = (v - lo) / (hi - lo);
        // Math.clamp(long, int, int) rend un int : le cast d'avant devient inutile
        return Math.clamp(Math.round(1 + t * 4), 1, 5);
    }
}
