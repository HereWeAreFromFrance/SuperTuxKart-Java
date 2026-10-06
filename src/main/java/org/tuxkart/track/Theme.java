package org.tuxkart.track;

import javafx.scene.paint.Color;

/**
 * Ambiance visuelle d'un circuit : couleurs du sol, du ciel et type de decor.
 * TuxKart utilisait des themes tres satures et peu de nuances ; on reprend
 * cette direction artistique volontairement « cartoon ».
 */
public enum Theme {

    GRASS("Prairie",
            Color.web("#4f9b34"), Color.web("#3d7a28"),
            Color.web("#45494f"), Color.web("#2e3237"),
            Color.web("#7ec8f2"), Color.web("#dff2fb"),
            1.10),

    FOREST("Forêt",
            Color.web("#37702a"), Color.web("#28551f"),
            Color.web("#3c4045"), Color.web("#282c30"),
            Color.web("#69b7e8"), Color.web("#cfe9f7"),
            2.45),

    SNOW("Banquise",
            Color.web("#e8f2f8"), Color.web("#cddfeb"),
            Color.web("#565b61"), Color.web("#3b4045"),
            Color.web("#74b6e6"), Color.web("#bfe2f7"),
            2.00),

    /**
     * Le sable du hors-piste est passe du beige a l'ocre orange.
     *
     * <p>Un controle visuel a releve qu'on ne voyait pas ou finissait la
     * chaussee. Mesure faite sur les trois surfaces qui se touchent : le sable
     * dame du revetement pesait 191 de clarte, le sol du theme 179, le
     * bas-cote et la bordure etant tous deux tires de ce meme sol — cinq pour
     * cent d'ecart entre la piste et ce qui n'est plus la piste, sur un
     * circuit a deux epingles seches. Ce n'est pas un defaut de gout mais un
     * defaut de jeu : on ne lit pas la trajectoire.
     *
     * <p>Le sol descend donc a 117 de clarte moyenne quand le revetement monte
     * a 198, soit une marche de trente pour cent. Ce sont ces deux valeurs-la
     * qui portent la lecture, pas un lisere peint : le desert n'a pas de
     * vibreur, {@link Surface#SABLE} n'en pose pas, et le bac a gravier ne
     * borde qu'un virage sur trois. Le talus de bord de couloir, tire de
     * {@code groundB}, fonce d'autant et souligne la limite exterieure.
     */
    DESERT("Désert",
            Color.web("#b06a2e"), Color.web("#8e5222"),
            Color.web("#6b5d47"), Color.web("#4e4335"),
            Color.web("#f0c470"), Color.web("#fdf2cf"),
            2.05),

    /**
     * Le sol du volcan est passe du brun au basalte.
     *
     * <p>Un controle visuel a releve « un terrain brun » la ou le titre
     * promet une coulee : a #4a3b36 le hors-piste avait la valeur d'une terre
     * de labour, et surtout il n'etait pas assez sombre pour que les fissures
     * incandescentes de sa texture se voient. Le noir de cendre les fait
     * ressortir et, accessoirement, detache la piste de terre battue — qui,
     * elle, ne depend pas du theme — au lieu de s'y fondre.
     */
    VOLCANO("Volcan",
            Color.web("#39302b"), Color.web("#241d1a"),
            Color.web("#322c2a"), Color.web("#221e1d"),
            Color.web("#8a3b22"), Color.web("#e0803a"),
            2.70),

    /**
     * Les quatre decors « a l'echelle du jouet » : on ne court plus dans un
     * paysage mais dans une piece. Le ciel y devient un plafond, l'horizon un
     * mur, et le decor du mobilier — d'ou le drapeau {@link #indoor}, qui
     * remplace le soleil et les nuages par une voute et sa penombre.
     */
    DONJON("Donjon hanté",
            Color.web("#7b7b88"), Color.web("#565663"),
            Color.web("#6a6058"), Color.web("#4a423c"),
            Color.web("#1d2230"), Color.web("#0e1119"),
            0.10, true),

    BILLARD("Table de billard",
            Color.web("#1f7a46"), Color.web("#176036"),
            Color.web("#2b8a52"), Color.web("#1f6b3f"),
            Color.web("#241a12"), Color.web("#120c08"),
            0.08, true),

    BAR("Comptoir",
            Color.web("#a4703f"), Color.web("#835429"),
            Color.web("#98653a"), Color.web("#6d4522"),
            Color.web("#1c1a24"), Color.web("#0d0c12"),
            0.09, true),

    SALON("Salon",
            Color.web("#c8b7a0"), Color.web("#a8977f"),
            Color.web("#b8a68e"), Color.web("#94836d"),
            Color.web("#e8e2d6"), Color.web("#cfc6b6"),
            0.10, true);

    public final String label;
    public final Color groundA, groundB;
    public final Color roadA, roadB;
    public final Color skyLow, skyHigh;
    /**
     * Multiplicateur de densite du semis de decor.
     *
     * Chaque theme a ses propres especes, et elles n'ont pas le meme prix : un
     * feuillu est une pile de spheres, une pierre dressee une simple boite. Sans
     * ce facteur, la prairie croulait sous les triangles pendant que le volcan
     * restait clairseme. Les valeurs sont calees a la mesure pour que les
     * circuits demandent un travail comparable a la carte graphique.
     *
     * <p>Les decors d'interieur ont une valeur presque nulle : leur mobilier
     * est pose piece par piece par le circuit, et il ne reste du semis qu'une
     * litiere — la miette au sol qu'on ne remarque pas mais dont l'absence
     * ferait une piece de musee.
     */
    public final double decorDensity;
    /**
     * Vrai pour un decor d'interieur : pas de soleil ni de nuages, une voute a
     * la place du ciel, et des silhouettes lointaines qui sont des murs et du
     * mobilier plutot que des cretes.
     */
    public final boolean indoor;

    // L'adherence n'appartient pas au theme mais au revetement : voir
    // Surface.grip, seule source consultee par la physique.

    Theme(String label, Color groundA, Color groundB, Color roadA, Color roadB,
          Color skyLow, Color skyHigh, double decorDensity) {
        this(label, groundA, groundB, roadA, roadB, skyLow, skyHigh, decorDensity, false);
    }

    Theme(String label, Color groundA, Color groundB, Color roadA, Color roadB,
          Color skyLow, Color skyHigh, double decorDensity, boolean indoor) {
        this.label = label;
        this.groundA = groundA;
        this.groundB = groundB;
        this.roadA = roadA;
        this.roadB = roadB;
        this.skyLow = skyLow;
        this.skyHigh = skyHigh;
        this.decorDensity = decorDensity;
        this.indoor = indoor;
    }
}
