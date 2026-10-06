package org.tuxkart.core;

/**
 * Limite la cadence de jeu, et la rabaisse d'elle-meme quand la chaine
 * graphique ne la tient pas.
 *
 * <p>Rendre plus vite que l'ecran n'apporte rien : cela consomme du courant et
 * peut provoquer du dechirement d'image. Comme JavaFX ne redessine que ce qui a
 * change, il suffit de ne pas toucher au graphe de scene pour que le rendu
 * s'arrete de lui-meme — d'ou ce filtre en amont de la boucle de jeu.
 *
 * <p>Le reste de budget est <b>conserve</b> d'une image a l'autre. Le remettre a
 * zero reviendrait a arrondir a un nombre entier de battements du moteur
 * d'animation : avec un moteur a 78 Hz et une cible a 60, on n'obtiendrait
 * qu'une image sur deux, soit 39 par seconde.
 *
 * <h2>Pourquoi la cadence s'abaisse toute seule</h2>
 *
 * <p>Demander a la chaine graphique plus d'images qu'elle n'en livre ne donne
 * pas une cadence plus basse mais reguliere : cela donne des <b>blocages de
 * cent a deux cent cinquante millisecondes</b>, par bouffees. Le temps ne part
 * ni dans le jeu ni dans le dessin — le journal de battements de JavaFX place
 * 3 a 5 ms dans « Painting », 0,6 ms dans la mise a jour du jeu, et 95 a 125 ms
 * dans « Presenting », c'est-a-dire dans l'echange des tampons d'ecran. Les
 * images en trop s'empilent dans la file de presentation et le retour se paie
 * d'un coup.
 *
 * <p>Mesures sur la Piste de Tux, niveau Ultra, images au-dela de 100 ms en
 * regime etabli sur une minute de course :
 *
 * <pre>
 *   decor soumis   cadence demandee   blocages
 *   1,95 M         sans limite (85)     15 a 26
 *   1,95 M         85                        1
 *   1,95 M         70                        0
 *   1,95 M         60                        0
 *   2,80 M         60                       18
 *   2,80 M         40                        0
 *   3,44 M         60                    52 a 71
 *   3,44 M         40                        0   (pire image : 41 ms)
 * </pre>
 *
 * <p>La cadence demandee est donc le seul reglage qui compte, et il n'y a pas
 * de valeur juste a graver : elle depend de la machine, du circuit et de la
 * taille de la fenetre. Le niveau de detail ne fixe donc plus qu'un
 * <b>plafond</b> ; c'est la premiere bouffee qui fixe le reste.
 */
public final class FrameLimiter {

    /**
     * Cadence visee quand le joueur n'en fixe aucune.
     *
     * <p>« Aucune limite » veut dire que le joueur n'impose pas de cadence, pas
     * que le jeu doive reclamer plus d'images que la chaine n'en livre : la
     * descente automatique s'applique donc aussi. Une scene legere n'en
     * ressent rien — mesure faite, « Fluide » rend ses cent quatre-vingt-dix
     * images par seconde sur un ecran a 60 Hz sans une seule image au-dela de
     * 100 ms, donc sans jamais declencher la descente. Depasser la frequence de
     * l'ecran n'est pas le probleme ; depasser ce que la chaine livre, si.
     */
    private static final int SANS_LIMITE = 1000;

    /** Cadence en dessous de laquelle on ne descend plus : le jeu se pilote encore. */
    public static final int PLANCHER = 20;

    /** Ce qui compte comme blocage, et non comme image lente. */
    private static final double BLOCAGE_S = 0.100;

    /**
     * Il faut deux blocages dans cette fenetre pour descendre d'un cran.
     *
     * <p>Un blocage isole arrive pour des raisons qui ne regardent pas le jeu —
     * une autre fenetre qui se redessine, une pause du ramasse-miettes. C'est
     * la <b>bouffee</b> qui signe la file de presentation pleine : les mesures
     * la donnent espacee de 0,4 a 1,2 s a l'interieur d'une grappe.
     */
    private static final double FENETRE_S = 3.0;

    /**
     * Un cran de descente, en fraction de la cadence <b>reellement obtenue</b>.
     *
     * <p>Descendre d'un quart de la cible precedente ne marchait pas quand le
     * joueur n'avait fixe aucune limite : partir de {@link #SANS_LIMITE} y
     * demandait dix crans, donc vingt blocages, avant de mordre. La cadence
     * obtenue, elle, est connue des la premiere seconde et donne le bon ordre
     * de grandeur du premier coup.
     */
    private static final double CRAN = 0.75;

    /**
     * Temps sans le moindre blocage exige avant de retenter le cran superieur.
     *
     * <p>Ne jamais remonter etait plus simple, et faux : une bouffee due a autre
     * chose que le jeu — une fenetre qui se redessine, une autre application qui
     * prend la carte graphique — verrouillait toute la course a la moitie de la
     * cadence. Vu a la mesure, une course bloquee a 31 images par seconde alors
     * qu'elle en tenait soixante.
     *
     * <p>Le delai <b>double a chaque descente</b>. Un incident isole se rattrape
     * donc en vingt secondes ; une scene reellement trop lourde cesse d'etre
     * sondee au bout de trois descentes — quatre-vingts secondes de calme
     * exigees, plus que ce qu'il reste de course.
     */
    private static final double QUARANTAINE_S = 20.0;

    /**
     * Duree pendant laquelle un blocage n'est pas retenu contre la scene, apres
     * une capture d'ecran. Couvre l'image gelee par {@code scene.snapshot} et
     * celle de rattrapage qui la suit.
     */
    private static final double GRACE_CAPTURE_S = 0.5;

    private double carry;
    private double sinceFrame;
    private double grace;

    private int plafond = -1;
    private int cible;
    private int blocages;
    private double depuisPremierBlocage;
    /** Duree moyenne des images non bloquees, en secondes. */
    private double moyenne;
    private double quarantaine;
    private double depuisLaDerniereBouffee;

    /**
     * Repart du plafond.
     *
     * <p>A appeler quand la scene change de nature : un ecran de menu ne dit
     * rien de ce que tiendra une course, et le devoilement du decor produit des
     * images longues par construction.
     */
    public void reset() {
        cible = plafond;
        blocages = 0;
        depuisPremierBlocage = 0;
        moyenne = 0;
        quarantaine = 0;
        depuisLaDerniereBouffee = 0;
        grace = 0;
    }

    /** Cadence effectivement visee, une fois les descentes appliquees. */
    public int target() {
        return cible;
    }

    /**
     * Ne pas retenir contre la scene le blocage qui suit : c'est une capture.
     *
     * <p>{@code scene.snapshot} arrete le fil d'application deux cents
     * millisecondes, et l'image de rattrapage qui suit est longue elle aussi.
     * Le limiteur y voyait deux blocages dans la meme fenetre, donc la signature
     * exacte d'une file de presentation pleine, et descendait d'un cran. La
     * quarantaine doublant a chaque descente, il ne remontait plus avant la fin
     * de la course.
     *
     * <p>Une campagne de captures mesurait donc la cadence qu'elle detruisait.
     * Releve du controle visuel sur le Donjon hante : 1958 images, moyenne 38,0
     * images par seconde mais mediane 30,9 — la moyenne plus rapide que la
     * mediane, signe qu'on a tourne plein regime puis qu'on est descendu sans
     * remonter. Sept passes ulterieures, quatre sans capture et trois avec le
     * meme protocole a six captures, donnent 60,1 a 60,4 sur le meme circuit.
     * Le decor n'y etait pour rien.
     *
     * <p>Le joueur qui appuie sur F12 payait la meme chose : la moitie de la
     * cadence pour le reste de la course, sans rien avoir change a la scene.
     */
    public void capture() {
        grace = GRACE_CAPTURE_S;
    }

    /**
     * Le pas renvoye n'est <b>pas</b> borne : apres un blocage d'une seconde,
     * c'est une seconde qui ressort. Le plafond de stabilite de la physique est
     * la responsabilite de l'appelant (voir {@code TuxKartApp}), pour que le
     * chiffre affiche au joueur reste la duree reelle de l'image.
     *
     * @param raw temps ecoule depuis le battement precedent, en secondes
     * @param cap cadence visee, ou 0 pour ne pas limiter
     * @return le pas de temps a simuler, ou 0 s'il faut sauter cette image
     */
    public double accept(double raw, int cap) {
        int demande = cap <= 0 ? SANS_LIMITE : cap;
        if (demande != plafond) {
            plafond = demande;
            reset();
        }

        double budget = 1.0 / cible;
        carry += raw;
        sinceFrame += raw;
        if (carry < budget) return 0;
        // on borne le reste : apres une longue pause, inutile de rattraper en
        // enchainant des images
        carry = Math.min(carry - budget, budget);
        double dt = sinceFrame;
        sinceFrame = 0;
        surveiller(dt);
        return dt;
    }

    /**
     * Compte les blocages et descend d'un cran a la deuxieme bouffee.
     *
     * <p>La remontee, elle, se merite : voir {@link #QUARANTAINE_S}.
     */
    private void surveiller(double dt) {
        if (grace > 0) grace -= dt;
        if (blocages > 0) {
            depuisPremierBlocage += dt;
            if (depuisPremierBlocage > FENETRE_S) blocages = 0;
        }
        if (dt < BLOCAGE_S) {
            // les images bloquees sont justement ce qu'on cherche a supprimer :
            // les compter dans la moyenne ferait descendre bien plus bas que
            // necessaire, et d'autant plus que le defaut est marque
            moyenne = moyenne <= 0 ? dt : moyenne * 0.9 + dt * 0.1;
            remonter(dt);
            return;
        }
        // un blocage cause par une capture ne dit rien de la scene
        if (grace > 0) return;
        if (blocages == 0) depuisPremierBlocage = 0;
        if (++blocages < 2) return;
        blocages = 0;
        int tenable = moyenne > 0
                ? (int) Math.round(CRAN / moyenne)
                : (int) Math.round(cible * CRAN);
        // au moins un cran, toujours : sans cela une cadence deja atteinte
        // mais mal livree bloquerait la descente
        cible = Math.max(PLANCHER, Math.min(cible - 1, tenable));
        quarantaine = quarantaine <= 0 ? QUARANTAINE_S : quarantaine * 2;
        depuisLaDerniereBouffee = 0;
    }

    /** Retente le cran superieur apres une quarantaine sans le moindre blocage. */
    private void remonter(double dt) {
        if (cible >= plafond) return;
        depuisLaDerniereBouffee += dt;
        if (depuisLaDerniereBouffee < quarantaine) return;
        depuisLaDerniereBouffee = 0;
        cible = Math.min(plafond, (int) Math.round(cible / CRAN));
    }
}
