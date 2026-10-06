package org.tuxkart.audio;

import java.util.Arrays;

/**
 * Outillage de mesure partage par les tests audio : transformee de Fourier,
 * bandes de tiers d'octave, ponderation A.
 *
 * <p>Il vit dans les tests et non dans le moteur : rien de tout cela ne tourne
 * pendant une partie. C'est le seul moyen de dire ou se place un son, donc de
 * verifier qu'il ne se confond pas avec un autre ni ne disparait sous le
 * moteur — deux defauts qu'une simple mesure de niveau laisse passer.
 */
final class Signal {

    static final int RATE = 44100;
    static final int CHUNK = 512;

    private Signal() {
    }

    /** Rend n secondes et retourne le melange mono des deux canaux. */
    static double[] mono(Audio audio, double seconds) {
        return mono(audio, seconds, null);
    }

    static double[] mono(Audio audio, double seconds, java.util.function.DoubleConsumer hook) {
        int blocks = (int) (seconds * RATE / CHUNK);
        byte[] buf = new byte[CHUNK * 4];
        double[] out = new double[blocks * CHUNK];
        int p = 0;
        for (int b = 0; b < blocks; b++) {
            if (hook != null) hook.accept(b * (double) CHUNK / RATE);
            audio.renderBlock(buf);
            for (int i = 0; i < CHUNK; i++) {
                double l = (short) ((buf[i * 4] & 0xff) | (buf[i * 4 + 1] << 8)) / 32768.0;
                double r = (short) ((buf[i * 4 + 2] & 0xff) | (buf[i * 4 + 3] << 8)) / 32768.0;
                out[p++] = (l + r) * 0.5;
            }
        }
        return out;
    }

    /** Transformee de Fourier en place, radix 2. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double t = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = t;
                }
            }
        }
    }

    static final int N = 8192;

    /**
     * Puissance par raie, moyenne de Welch (Hann, recouvrement 1/2). Les
     * fenetres debordent d'une demi-longueur de chaque cote du segment : sans
     * cela l'attaque d'un bruitage tombe dans le flanc montant de la premiere
     * fenetre et se trouve sous-comptee de dix decibels. La somme des raies
     * vaut la puissance moyenne du segment.
     */
    static double[] power(double[] m, int from, int to) {
        to = Math.min(to, m.length);
        int len = Math.max(1, to - from);
        double[] acc = new double[N / 2];
        double[] re = new double[N], im = new double[N];
        for (int p = from - N / 2; p < to; p += N / 2) {
            Arrays.fill(im, 0);
            for (int i = 0; i < N; i++) {
                int j = p + i;
                re[i] = (j >= from && j < to ? m[j] : 0) * (0.5 - 0.5 * Math.cos(2 * Math.PI * i / N));
            }
            fft(re, im);
            for (int k = 0; k < N / 2; k++) acc[k] += re[k] * re[k] + im[k] * im[k];
        }
        double g = 2.0 / (N * 0.75 * (double) len);
        for (int k = 0; k < acc.length; k++) acc[k] *= g;
        return acc;
    }

    /** Valeur efficace dans une plage de frequences. */
    static double rmsIn(double[] pow, double f0, double f1) {
        double df = RATE / (double) N, s = 0;
        for (int k = 1; k < pow.length; k++) {
            double f = k * df;
            if (f >= f0 && f < f1) s += pow[k];
        }
        return Math.sqrt(s);
    }

    /** Centres de tiers d'octave, de 31 Hz a 16 kHz. */
    static final double[] THIRDS = new double[28];

    static {
        for (int i = 0; i < THIRDS.length; i++) THIRDS[i] = 31.25 * Math.pow(2, i / 3.0);
    }

    /** Profil par tiers d'octave : c'est lui qui sert a comparer deux timbres. */
    static double[] thirds(double[] pow) {
        double[] b = new double[THIRDS.length];
        double df = RATE / (double) N;
        for (int k = 1; k < pow.length; k++) {
            double f = k * df;
            for (int i = 0; i < THIRDS.length; i++) {
                if (f >= THIRDS[i] / 1.1225 && f < THIRDS[i] * 1.1225) {
                    b[i] += pow[k];
                    break;
                }
            }
        }
        for (int i = 0; i < b.length; i++) b[i] = Math.sqrt(b[i]);
        return b;
    }

    /**
     * Valeur efficace ponderee A. Le niveau brut ne dit pas ce qu'on entend :
     * le turbo etait le deuxieme bruitage le plus fort en valeur efficace et
     * l'avant-dernier une fois pondere, parce qu'il depensait sa reserve dans
     * un infra-grave qu'aucune enceinte de portable ne restitue.
     */
    static double aWeighted(double[] pow) {
        double df = RATE / (double) N, s = 0;
        for (int k = 1; k < pow.length; k++) {
            double f = k * df, f2 = f * f;
            double ra = (12194.0 * 12194 * f2 * f2)
                    / ((f2 + 20.6 * 20.6)
                    * Math.sqrt((f2 + 107.7 * 107.7) * (f2 + 737.9 * 737.9))
                    * (f2 + 12194.0 * 12194));
            double a = ra * 1.2589254;                 // +2 dB : normalisation a 1 kHz
            s += pow[k] * a * a;
        }
        return Math.sqrt(s);
    }

    /** Niveau pondere A et crete d'un extrait, entre deux instants en secondes. */
    static double[] aWeightedEt(double[] m, double from, double to) {
        int a = (int) (from * RATE), b = (int) (to * RATE);
        double peak = 0;
        for (int i = a; i < b && i < m.length; i++) peak = Math.max(peak, Math.abs(m[i]));
        return new double[]{aWeighted(power(m, a, b)), peak};
    }

    /** Cosinus de deux profils spectraux : 1 = memes bandes dans les memes proportions. */
    static double cosine(double[] a, double[] b) {
        double d = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            d += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return d / Math.sqrt(na * nb);
    }

    /** Valeur efficace glissante sur 10 ms : detecteur d'enveloppe. */
    static double[] envelope(double[] m) {
        int w = RATE / 100;
        double[] e = new double[m.length];
        double acc = 0;
        for (int i = 0; i < m.length; i++) {
            acc += m[i] * m[i];
            if (i >= w) acc -= m[i - w] * m[i - w];
            e[i] = Math.sqrt(acc / Math.min(i + 1, w));
        }
        return e;
    }

    /** Dernier instant, en secondes, ou l'enveloppe depasse encore -70 dBFS. */
    static double tail(double[] m) {
        double[] e = envelope(m);
        int i = m.length - 1;
        while (i > 0 && e[i] < 3e-4) i--;
        return (i + 1) / (double) RATE;
    }
}
