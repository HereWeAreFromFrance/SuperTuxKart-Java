package org.tuxkart.track;

/**
 * Revetement de la piste. Tous les circuits ne sont pas asphaltes : une piste
 * de terre battue ou de sable dame se conduit autrement et se dessine
 * autrement — pas de marquage peint, pas de vibreur, des ornieres a la place.
 */
public enum Surface {

    BITUME("Bitume", 1.00, true),
    TERRE("Terre battue", 0.84, false),
    SABLE("Sable damé", 0.76, false),
    NEIGE("Neige damée", 0.72, false),

    /** Revetements des circuits d'interieur : feutre, bois, laine, dalle. */
    FEUTRE("Tapis de billard", 0.94, false, true),
    PARQUET("Parquet ciré", 0.90, false, true),
    TAPIS("Tapis de laine", 0.78, false, true),
    DALLE("Dalle de pierre", 0.86, false, true);

    public final String label;
    /** Adherence relative du revetement. */
    public final double grip;
    /** Un vibreur peint n'a de sens que sur du bitume. */
    public final boolean kerbs;
    /** Revetement d'interieur : pas d'ornieres, un motif a la place. */
    public final boolean indoor;

    Surface(String label, double grip, boolean kerbs) {
        this(label, grip, kerbs, false);
    }

    Surface(String label, double grip, boolean kerbs, boolean indoor) {
        this.label = label;
        this.grip = grip;
        this.kerbs = kerbs;
        this.indoor = indoor;
    }
}
