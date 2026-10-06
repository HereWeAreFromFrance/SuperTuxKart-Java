package org.tuxkart.math;

import java.util.Random;

/**
 * Bruit de Perlin 2D et ses derives (fbm, ridged). Sert a la fois au relief du
 * terrain et a la fabrication des textures : c'est la meme fonction qui donne
 * les collines a l'horizon et le grain du bitume.
 */
public final class Noise {

    private final int[] p = new int[512];

    public Noise(long seed) {
        int[] src = new int[256];
        for (int i = 0; i < 256; i++) src[i] = i;
        Random r = new Random(seed);
        for (int i = 255; i > 0; i--) {
            int j = r.nextInt(i + 1);
            int t = src[i];
            src[i] = src[j];
            src[j] = t;
        }
        for (int i = 0; i < 512; i++) p[i] = src[i & 255];
    }

    private static double fade(double t) {
        return t * t * t * (t * (t * 6 - 15) + 10);
    }

    private static double grad(int hash, double x, double y) {
        return switch (hash & 3) {
            case 0 -> x + y;
            case 1 -> -x + y;
            case 2 -> x - y;
            default -> -x - y;
        };
    }

    /** Bruit de Perlin, resultat dans [-1, 1]. */
    public double perlin(double x, double y) {
        // les deux planchers sont calcules une seule fois : cette fonction est
        // appelee des dizaines de millions de fois a la cuisson des textures,
        // et Math.floor n'est pas gratuit
        double fx = Math.floor(x), fy = Math.floor(y);
        int xi = (int) fx & 255;
        int yi = (int) fy & 255;
        double xf = x - fx;
        double yf = y - fy;
        double u = fade(xf), v = fade(yf);

        int aa = p[p[xi] + yi];
        int ab = p[p[xi] + yi + 1];
        int ba = p[p[xi + 1] + yi];
        int bb = p[p[xi + 1] + yi + 1];

        double x1 = MathUtil.lerp(grad(aa, xf, yf), grad(ba, xf - 1, yf), u);
        double x2 = MathUtil.lerp(grad(ab, xf, yf - 1), grad(bb, xf - 1, yf - 1), u);
        return MathUtil.lerp(x1, x2, v);
    }

    /** Somme d'octaves : le relief « naturel ». Resultat approximativement dans [-1, 1]. */
    public double fbm(double x, double y, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += perlin(x * freq, y * freq) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.03;
        }
        return sum / norm;
    }

    /** Bruit a cretes, pour les cretes de montagne. Resultat dans [0, 1]. */
    public double ridged(double x, double y, int octaves) {
        double sum = 0, amp = 1, freq = 1, norm = 0;
        for (int i = 0; i < octaves; i++) {
            sum += (1 - Math.abs(perlin(x * freq, y * freq))) * amp;
            norm += amp;
            amp *= 0.5;
            freq *= 2.07;
        }
        return sum / norm;
    }
}
