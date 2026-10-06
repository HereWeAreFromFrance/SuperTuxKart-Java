package org.tuxkart.math;

public final class MathUtil {

    private MathUtil() {
    }

    public static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /** Ramene un angle dans ]-PI, PI]. */
    public static double wrapPi(double a) {
        while (a > Math.PI) a -= 2 * Math.PI;
        while (a <= -Math.PI) a += 2 * Math.PI;
        return a;
    }

    /** Deplace {@code cur} vers {@code target} d'au plus {@code maxDelta}. */
    public static double approach(double cur, double target, double maxDelta) {
        if (cur < target) return Math.min(cur + maxDelta, target);
        return Math.max(cur - maxDelta, target);
    }

    /** Lissage exponentiel independant du pas de temps. */
    public static double damp(double cur, double target, double rate, double dt) {
        return target + (cur - target) * Math.exp(-rate * dt);
    }

    public static double smoothstep(double edge0, double edge1, double x) {
        double t = Math.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static double sign(double v) {
        return v > 0 ? 1 : (v < 0 ? -1 : 0);
    }

    /** Modulo toujours positif. */
    public static double mod(double a, double m) {
        double r = a % m;
        return r < 0 ? r + m : r;
    }

    public static int mod(int a, int m) {
        return Math.floorMod(a, m);
    }

    /** Formatage m:ss.cc utilise par le chrono et les resultats. */
    public static String formatTime(double seconds) {
        // une duree negative, absente (Double.MAX_VALUE pour « aucun tour
        // chronometre ») ou superieure a dix heures n'a pas de sens ici : on
        // affiche le tiret plutot qu'un nombre qui n'apprendrait rien
        if (seconds < 0 || seconds > 36_000 || Double.isNaN(seconds)) return "--:--.--";
        int total = (int) Math.floor(seconds * 100 + 0.5);
        int cs = total % 100;
        int s = (total / 100) % 60;
        int m = total / 6000;
        return String.format("%d:%02d.%02d", m, s, cs);
    }
}
