package org.tuxkart.core;

import java.util.Random;

/**
 * Source d'alea du jeu, rendue reproductible a la demande.
 *
 * <p>{@code -Dtuxkart.seed=<n>} fige tout ce qui est tire au sort pendant une
 * course : les objets distribues, le tremblement de la camera aux impacts, la
 * dispersion des particules. Avec un pas de temps fixe
 * ({@code -Dtuxkart.fixeddt}), deux executions rendent alors <b>exactement</b>
 * la meme suite d'images.
 *
 * <p>Sans cela, comparer deux versions du rendu est impossible : deux courses
 * de demonstration lancees a la suite divergent des les premieres secondes, et
 * une comparaison pixel a pixel donne 49 % d'ecart la ou l'on cherchait a
 * mesurer l'effet d'un changement. Mesure faite, c'est ce qui a empeche de
 * verifier a l'image le tri du decor.
 *
 * <p>Tout se passe sur le fil applicatif : un generateur partage suffit.
 */
public final class Rng {

    private static final Long SEED = readSeed();
    private static Random shared = SEED == null ? null : new Random(SEED);

    private Rng() {
    }

    private static Long readSeed() {
        String spec = System.getProperty("tuxkart.seed");
        if (spec == null || spec.isBlank()) return null;
        try {
            return Long.parseLong(spec.trim());
        } catch (NumberFormatException e) {
            System.err.println("Graine illisible : " + spec);
            return null;
        }
    }

    /** Vrai si le jeu tourne en mode reproductible. */
    public static boolean fixed() {
        return SEED != null;
    }

    /** Equivalent de {@link Math#random()}, reproductible si une graine est donnee. */
    public static double next() {
        return shared == null ? Math.random() : shared.nextDouble();
    }

    /**
     * Generateur propre a un sous-systeme. En mode reproductible il derive de la
     * graine commune, de sorte que deux sous-systemes ne partagent pas la meme
     * suite — deux courses identiques ou tout tombe pareil ne testent rien.
     */
    public static Random stream(long salt) {
        return SEED == null ? new Random() : new Random(SEED * 1_000_003L + salt);
    }

    /** Repart de la graine ; appele au debut de chaque course. */
    public static void reset() {
        if (SEED != null) shared = new Random(SEED);
    }
}
