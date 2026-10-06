package org.tuxkart.core;

/**
 * Niveau de detail graphique, sur l'un des deux axes de reglage.
 *
 * <p>Le meme jeu de quatre niveaux sert deux fois : une fois pour le
 * <b>nombre de polygones</b>, une fois pour les <b>textures</b>. Les deux se
 * choisissent separement ({@link GameSettings#polygons} et
 * {@link GameSettings#textures}) parce qu'ils ne coutent pas a la meme chose :
 * les polygones coutent de la memoire et du travail de synchronisation de
 * scene, les textures coutent des pixels. Une machine limitee par l'un ne l'est
 * pas forcement par l'autre, et melanger les deux reglages en un seul curseur
 * obligeait a payer les deux pour n'en vouloir qu'un.
 *
 * <p>Les champs se rangent donc en trois groupes, et chacun n'est lu qu'a
 * travers l'axe qui le gouverne :
 *
 * <ul>
 *   <li><b>polygones</b> : {@link #terrainCell}, {@link #decorDensity},
 *       {@link #terrainMargin}, {@link #decorShadows}, la finesse des facettes
 *       ({@link #sphereDiv}, {@link #coneSides}) et les effets qui ajoutent de
 *       la geometrie ({@link #particles}, {@link #skidMarks}) ;</li>
 *   <li><b>textures</b> : {@link #textureSize}, {@link #supersample},
 *       {@link #antialias} ;</li>
 *   <li><b>commun</b> : {@link #frameCap}, qui ne depend pas d'un axe mais du
 *       plus lourd des deux.</li>
 * </ul>
 *
 * <h2>Ce que chaque axe achete, mesure</h2>
 *
 * <p>Mesures sur « Colline du Manchot », en plein ecran 1920x1080, huit karts,
 * plafond de cadence leve, graine 7, quarante secondes de course — Ultra etait
 * alors a la densite de decor 30, d'ou des totaux un peu plus bas
 * qu'aujourd'hui : les quatre
 * paires homogenes donnent 116, 105, 69 et 63 images par seconde medianes, avec
 * au plus une image au-dela de cent millisecondes — et aucune une fois le
 * plafond livre de 60 remis. En bougeant un seul axe a la
 * fois depuis Fluide, les polygones font tomber la mediane a 63 et les textures
 * a 91 : les deux axes sont du meme ordre, ce qui est exactement ce qui rend
 * leur separation utile.
 *
 * <p>Le premier palier des textures n'est pas la taille des images mais
 * {@link #antialias} : le multi-echantillonnage de JavaFX coute a lui seul la
 * moitie de la cadence — 61 images par seconde contre 106 sur la meme scene.
 * C'est ce qui interdisait a « Fluide » d'atteindre les cent images par seconde
 * visees, quel que soit l'allegement du decor.
 *
 * <h2>Ce qui plafonnait Ultra, et pourquoi ce n'etait pas la geometrie</h2>
 *
 * <p>Le plafond d'<b>Ultra</b> n'a longtemps pas ete une cadence mais un
 * <b>hoquet</b> : des images isolees de 100 a 250 ms, par bouffees, dont la
 * frequence montait avec la densite du decor alors que la mediane ne bougeait
 * pas. La densite avait donc ete arretee a 21, soit 1,73 million de triangles
 * sur la Piste de Tux.
 *
 * <p>Le journal de battements de JavaFX a designe le coupable : sur ces images,
 * la mise a jour du jeu prend 0,6 ms, le dessin (<i>Painting</i>) 3 a 5 ms, et
 * l'echange des tampons d'ecran (<i>Presenting</i>) 95 a 125 ms. Le temps ne
 * partait ni dans le jeu, ni dans la carte graphique, mais dans la file de
 * presentation : le jeu reclamait plus d'images que la chaine n'en livrait, et
 * l'excedent revenait d'un bloc. Toutes les mesures de la campagne avaient ete
 * prises <b>sans limite de cadence</b> — exactement la condition qui remplit
 * cette file.
 *
 * <p>{@link FrameLimiter} abaisse desormais la cadence visee a la premiere
 * bouffee, et {@link #frameCap} n'est plus qu'un plafond. Le hoquet disparait,
 * et avec lui le plafond de densite. Mesures sur la Piste de Tux, images
 * au-dela de 100 ms sur une minute de course en regime etabli, deux passes
 * alternees, bande semee de 90 m quelle que soit la densite :
 *
 * <pre>
 *   densite   triangles   cadence fixe a 60   cadence adaptative
 *   21        1,73 M      0 et 0              0 et 0
 *   30        2,14 M      0 et 0              0 et 0   &lt;- retenu alors
 *   45        2,80 M      18                  0
 *   60        3,44 M      71 et 52            0 et 0
 * </pre>
 *
 * <p>Du cote de la cadence, 60 passait : mediane 58,5 images par seconde et pas
 * un blocage. <b>Ce qui arretait la montee n'etait donc plus la machine, mais
 * l'oeil</b> : a 60 les silhouettes fusionnaient — la sapiniere de la Colline
 * devenait un mur vert sans un tronc ni un trou de ciel, les mesas du Desert
 * s'interpenetraient — parce que le semis resserrait l'ecart le long du ruban
 * ({@code step = 7 / densite}) sans elargir l'etalement lateral, fige a 90 m.
 * La densite avait donc ete arretee a 30.
 *
 * <h2>La bande semee s'elargit maintenant avec la densite</h2>
 *
 * <p>Ce defaut-la est corrige : la bande semee de chaque cote du couloir croit
 * proportionnellement a la densite (voir {@code TrackNode.bandeSemee}), si bien
 * qu'un semis deux fois plus nombreux couvre deux fois plus de terrain et que
 * la distance entre voisins immediats ne se resserre presque plus. Le surplus
 * part en profondeur de paysage, pas en epaisseur de haie. Verifie a l'image
 * sur la Colline et le Desert : a 45 comme a 60, les troncs se detachent
 * encore, le ciel passe entre les cones et la montagne enneigee reste visible
 * derriere la sapiniere. Elargir ne coute rien — ce que le tri par cone perd,
 * le niveau de detail par distance le rend, a 3 % pres.
 *
 * <p>L'oeil ne s'y oppose donc plus, et c'est <b>la machine qui reprend la
 * main</b>. Images au-dela de 100 ms en regime etabli
 * ({@code ./run.sh regime}), fenetre 1280x800, densites alternees A/B dans la
 * meme session :
 *
 * <pre>
 *   densite   triangles (Tux / Colline)   blocages                      pire image
 *   30        2,14 M / 2,16 M             0                             33 ms
 *   36        2,32 M / 2,37 M             0, 4 circuits et 8 passes     33 a 43 ms  &lt;- retenu
 *   40        2,45 M / 2,52 M             1 sur Tux, 2 sur le Desert    293 ms
 *   45        2,59 M / 2,69 M             2 a 5                         155 ms
 * </pre>
 *
 * <p>Le decrochage est franc et il ne se lit pas dans le total : entre 36 et 40
 * il y a six pour cent de triangles d'ecart, et l'un ne bloque jamais quand
 * l'autre sort une image de 293 ms. C'est la meme loi que plus haut — ce n'est
 * pas la geometrie qui decide, c'est l'ecart entre la cadence demandee et la
 * cadence livree, et cet ecart se creuse d'un coup des que la construction
 * d'une image passe le budget. La Piste de Tux est la premiere a lacher parce
 * qu'elle est une boucle compacte de mille metres, ou le tri par cone n'ecarte
 * presque rien : une quinzaine de troncons sur 21 restent dessines a chaque
 * image.
 *
 * <p><b>36</b> tient donc les deux bouts : pas un blocage, pire image sous
 * cinquante millisecondes, et un decor qui gagne un dixieme de triangles sur le
 * niveau precedent — 2,32 millions sur la Piste de Tux contre 2,14, releve
 * d'alors : elle est depuis retombee a 2,22, le champ degage de ses reperes
 * ayant retire du semis — pour un paysage qui s'etend maintenant sur 108 m de
 * part et d'autre du couloir au lieu de 90. C'est un plafond mesure sur cette machine et cette fenetre, pas
 * un chiffre rond : {@code -Dtuxkart.decordensity=<n>} refait la campagne sans
 * recompiler.
 *
 * <p>Restent deux observations de la campagne precedente, toujours vraies. Ce
 * ne sont ni le ramasse-miettes — trois pauses de 36, 25 et 9 ms sur une course
 * qui comptait vingt blocages — ni les textures — les memes blocages a 384
 * pixels qu'a 1536. Et <b>ce n'est pas le total qui compte mais ce qu'on en
 * voit</b> : la frequence des blocages suivait le nombre de triangles dans le
 * cone de la camera, nulle en dessous de 1,4 million, 1,4 % des images au-dela
 * de 1,8 million.
 */
public enum Quality {

    FLUIDE("Fluide", 20, 1.20, 850, false, 6, 8, true, true, 384, 1.00, false, 0),
    EQUILIBRE("Équilibré", 13, 3.50, 1150, true, 8, 10, true, true, 640, 1.25, true, 60),
    QUALITE("Qualité", 7.5, 11.00, 1500, true, 11, 14, true, true, 1024, 1.50, true, 60),
    ULTRA("Ultra", 6.0, 36.00, 1650, true, 12, 16, true, true, 1536, 2.00, true, 60);

    public final String label;

    // ------------------------------------------------------------ polygones

    /** Taille d'une maille de terrain, en metres. */
    public final double terrainCell;
    /** Multiplicateur de densite du decor. */
    public final double decorDensity;
    /** Portee du terrain autour du circuit, en metres. */
    public final double terrainMargin;
    /** Ombres portees sous les objets du decor. */
    public final boolean decorShadows;
    /**
     * Nombre de meridiens des spheres du decor — troncs, feuillages, boules.
     *
     * <p>La finesse des facettes se deduisait de {@link #decorDensity}, par des
     * seuils poses dans le maillage du decor. Les deux axes cessaient alors
     * d'etre lisibles : demander moins d'objets rendait aussi chaque objet plus
     * grossier, sans que rien ne le dise. Elle est donc declaree ici, et la
     * distance continue de la rabaisser objet par objet — un arbre a deux cents
     * metres n'a pas besoin de seize meridiens.
     */
    public final int sphereDiv;
    /** Nombre de cotes des cones du decor — sapins, agaves, torches. */
    public final int coneSides;
    public final boolean particles;
    public final boolean skidMarks;

    // ------------------------------------------------------------- textures

    /** Cote des textures de surface, en pixels. */
    public final int textureSize;
    /**
     * Facteur de surechantillonnage : la scene 3D est rendue a cette echelle
     * puis reduite a la taille de la fenetre. C'est du vrai anticrenelage, au
     * prix du carre du facteur en nombre de pixels.
     *
     * <p>Le facteur demande n'est pas toujours celui applique : la cible de
     * rendu est bornee a cinq megapixels (voir {@link RenderScale}), ce qui en
     * plein ecran 1920x1080 plafonne le facteur reel vers 1,55. Les valeurs
     * plus hautes ne prennent tout leur sens qu'en fenetre.
     */
    public final double supersample;
    /**
     * Multi-echantillonnage des silhouettes par JavaFX.
     *
     * <p>Il ne s'applique que lorsque le surechantillonnage est a 1 : cumuler
     * les deux, c'est demander plusieurs echantillons par pixel d'une cible qui
     * en compte deja quatre fois trop. Il est coupe a « Fluide » parce qu'il y
     * coute la moitie de la cadence — 61 images par seconde contre 106 — pour
     * un adoucissement de bord que ce niveau n'a pas vocation a payer.
     */
    public final boolean antialias;

    // --------------------------------------------------------------- commun

    /**
     * <b>Plafond</b> de cadence propre au niveau, ou 0 pour n'en poser aucun.
     * C'est le plus lourd des deux axes qui le donne : c'est lui qui decrit a
     * quel point la configuration est exigeante.
     *
     * <p>Ce n'est qu'un plafond : {@link FrameLimiter} descend en dessous de
     * lui-meme des qu'une bouffee de blocages montre que la chaine graphique ne
     * le tient pas. C'est ce qui rend la livraison des images reguliere —
     * reclamer plus d'images que la chaine n'en livre ne donne pas une cadence
     * plus basse mais reguliere, cela donne des blocages de 100 a 250 ms.
     *
     * <p>« Fluide », lui, ne pose aucun plafond : son objet meme est de rendre
     * plus d'images que l'ecran n'en affiche — cent trente a cent quatre-vingts
     * en mesure, contre les soixante d'un panneau ordinaire — et une limite a
     * soixante lui retirait par construction ce pour quoi on le choisit. Sa
     * scene est assez legere pour ne jamais declencher la descente : mesure
     * faite, pas une image au-dela de 100 ms.
     */
    public final int frameCap;

    Quality(String label, double terrainCell, double decorDensity, double terrainMargin,
            boolean decorShadows, int sphereDiv, int coneSides,
            boolean particles, boolean skidMarks,
            int textureSize, double supersample, boolean antialias, int frameCap) {
        this.label = label;
        this.terrainCell = terrainCell;
        this.decorDensity = decorDensity;
        this.terrainMargin = terrainMargin;
        this.decorShadows = decorShadows;
        this.sphereDiv = sphereDiv;
        this.coneSides = coneSides;
        this.particles = particles;
        this.skidMarks = skidMarks;
        this.textureSize = textureSize;
        this.supersample = supersample;
        this.antialias = antialias;
        this.frameCap = frameCap;
    }

    /** Le plus lourd de deux niveaux, au sens de l'ordre de l'enumeration. */
    public static Quality heaviest(Quality a, Quality b) {
        return a.ordinal() >= b.ordinal() ? a : b;
    }
}
