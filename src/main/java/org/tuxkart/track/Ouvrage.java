package org.tuxkart.track;

/**
 * Les grands volumes que la piste <b>traverse</b> : on n'y passe pas a cote,
 * on passe dedans. Une grange, un tube de lave, une nef voutee, un tonneau
 * couche, une boite en carton posee en travers du tapis.
 *
 * <p>Un ouvrage n'est pas une piece de decor de plus. Une piece se pose au bord
 * du ruban et se contourne ; un ouvrage enjambe la piste entiere, et trois
 * parties du moteur ont besoin de le savoir sans rien construire :
 *
 * <ul>
 *   <li>le semis de decor, qui doit s'arreter a l'entree — un bosquet pousse
 *       mal dans une grange ;</li>
 *   <li>le mobilier de bord de piste (fanions, panneaux, piles de pneus), qui
 *       se planterait dans les murs ;</li>
 *   <li>les tests, qui verifient que la piste passe vraiment dedans — un
 *       ouvrage pose dans un virage barre le circuit.</li>
 * </ul>
 *
 * <p>Cette enumeration ne porte donc que la trace au sol : la longueur, et le
 * {@link Profil} de la paroi le long de cette longueur. Les trois consommateurs
 * lisent tous ce meme profil, ce qui est le seul moyen de garantir qu'ils
 * s'accordent — une enveloppe unique et une paroi variable ne peuvent que
 * diverger. La geometrie, elle, est batie par {@code render.OuvrageNode}, qui
 * seul connait les poutres et les douelles.
 */
public enum Ouvrage {

    /** Grange de ferme : deux murs, une charpente, un toit a deux pentes. */
    GRANGE("grange", "la grange", 32, 9),
    /** Tube de lave ou tunnel perce : une voute continue et ses deux portails. */
    TUNNEL("tunnel", "le tunnel", 36, 5),
    /** Dome de glace : la meme voute, batie en blocs decales. */
    IGLOU("iglou", "le dôme de glace", 32, 6),
    /** Salle hypostyle : deux rangees de colonnes et des dalles a claire-voie. */
    TEMPLE("temple", "le temple", 38, 10),
    /**
     * Nef : piliers, arcs doubleaux et voute de pierre.
     *
     * <p>C'est le plus long des ouvrages, donc celui dont la queue aveugle
     * coutait le plus cher : d'ou cinq metres huit la ou les autres en ont
     * cinq a dix.
     *
     * <p>Le seul aussi a demander plus que le degagement ordinaire : l'arcade
     * n'est pas sa peau. Derriere elle courent le bas-cote et son mur, batis a
     * trois metres quatre au-dela, epais d'un metre six — quatre metres deux de
     * la paroi a la face exterieure, mesures sur le maillage. Trois metres de
     * degagement planteraient un bosquet dans ce mur-la.
     */
    NEF("nef", "la nef", 46, 5.8, 5.5),
    /** Tonneau couche : on entre par la bonde et on ressort par le fond. */
    TONNEAU("tonneaugeant", "le tonneau géant", 32, 5),
    /**
     * Boite en carton renversee, rabats ouverts aux deux bouts.
     *
     * <p>Cinq metres : ici c'est le flanc lui-meme qui borne le resserrement,
     * rien ne se tient en retrait de lui.
     */
    BOITE("boitegeante", "la boîte en carton", 34, 5.0),
    /** Poche de billard : les deux bandes, la gueule de bois et le sac de cuir. */
    POCHE("pochegeante", "la poche de coin", 28, 7);

    /** Nom employe dans {@link TrackDef#propKinds}. */
    public final String nom;
    /** Nom affiche au joueur, dans la fiche du circuit. */
    public final String label;
    /** Demi-longueur le long de la piste, en metres. */
    public final double demiLongueur;
    /**
     * Marge laterale a laquelle la paroi est batie, au-dela du couloir
     * praticable, en metres.
     *
     * <p>La largeur d'un ouvrage n'est pas un chiffre d'auteur mais une
     * consequence : elle suit le couloir de piste qu'il enjambe, travee par
     * travee ({@link #profil}). La ou la piste s'ouvre le mur recule, la ou
     * elle se resserre il revient — c'est bien le mur qui cede, pas la piste.
     * La marge est donc tout ce que l'auteur choisit : la distance, constante,
     * a laquelle la paroi longe le bord du couloir.
     *
     * <p>Elle veut rester etroite, parce qu'un mur trop ecarte sort du champ de
     * la camera. Un mur a {@code R} metres de l'axe quitte le cadre des qu'il
     * reste moins de {@code 1,4 x R} metres devant soi : tout ouvrage a donc une
     * <b>queue aveugle</b> ou on ne le voit plus, et elle croit avec sa largeur.
     * La queue vaut {@code 1,4 x (demi-couloir a l'ancrage + marge)} : la
     * reappliquer, et non recopier les chiffres, des qu'un trace bouge. La nef,
     * batie a onze metres de marge, s'effacait sur ses trente-huit derniers
     * metres — plus de mur, plus de voute, plus de pilier, alors que la
     * geometrie etait bien la. A cinq metres huit sa queue tombe a trente et un
     * metres deux, et elle se lit sept metres de plus.
     *
     * <p>La queue aveugle a une <b>jumelle verticale</b>, et c'est elle qui a
     * failli couter le dome de glace. La camera est reglee en champ horizontal
     * — 60 a 69 degres — donc sa demi-ouverture verticale vaut environ 22
     * degres sur une image en 16/10 : un point situe {@code H} metres au-dessus
     * de l'oeil n'entre dans le cadre qu'a {@code 2,4 x H} devant. La clef du
     * dome, en plein cintre, montait a 27,3 m au-dessus d'un couloir de 18,76 m
     * de demi-largeur : il aurait fallu soixante metres de recul pour la voir,
     * et l'ouvrage n'en fait que soixante-quatre. On entrait donc sous un
     * demi-dome et on n'en voyait plus rien passe le quart. Ce n'est pas une
     * anomalie de cet ouvrage-la — le tunnel de la Colline s'efface de la meme
     * facon sur ses vingt-cinq derniers metres, capture a l'appui — mais le
     * dome est le plus large des huit et le seul dont la clef egale la
     * demi-portee. La reponse est dans {@code OuvrageNode} : sa voute est
     * surbaissee a 0,62, ce qui ramene la clef a 17,9 m sans toucher ni a
     * l'emprise, ni a la marge, ni donc au semis.
     *
     * <p>Six ouvrages sur huit ne peuvent pas se resserrer davantage : le temple
     * est tenu par sa colonnade, qui se dresse trois metres deux en retrait du
     * mur ; la grange par les bottes de son fenil, l'iglou et le tonneau par
     * leur coque, qui rejoint le plancher en deca du bord du couloir. Les
     * resserrer poserait de la pierre sur la trajectoire.
     *
     * <p>Il y a eu ici <b>deux</b> marges, celle-ci pour batir et une seconde,
     * plus large, pour ecarter le semis. Toutes deux se comptaient depuis l'axe
     * de la piste ; la seconde etait un seul nombre par ouvrage, donc pris sur
     * le couloir le plus large de l'emprise, et il fallait la choisir genereuse
     * pour qu'aucun bosquet ne pousse dans l'angle mort d'une grange qui se
     * resserre. La nef la declarait a onze metres pour une paroi batie a cinq
     * metres huit, et attrapait par un bout l'elargissement de la grille de
     * depart : treize metres de dallage nu entre la paroi et le premier meuble,
     * sur cent metres de long, dans une salle qui se lisait alors comme une
     * plaine. C'est {@link #degagement} qui l'a remplacee — meme role, mais
     * compte depuis la paroi, donc local et sans angle mort.
     */
    public final double marge;

    /**
     * Bande laissee libre <b>au-dela de la paroi</b>, en metres.
     *
     * <p>C'est ce qui reste de la seconde marge, et le changement de nature vaut
     * d'etre dit : l'ancienne etait une demi-largeur comptee depuis l'axe de la
     * piste, donc un seul nombre pour tout l'ouvrage, donc prise sur la travee
     * la plus large de l'emprise. Celle-ci se compte depuis la paroi, qui suit
     * le couloir : elle est locale par construction, et le degagement vaut la
     * meme chose sous chaque travee.
     *
     * <p>Trois metres suffisent a sept ouvrages sur huit. Le huitieme, la nef,
     * n'a pas sa peau la ou est sa paroi.
     *
     * <p>Cette valeur ne borne <b>pas</b> le debord reel du bati : mesure sur le
     * maillage de chaque ouvrage, au ras du sol, la matiere depasse la paroi de
     * un metre sous la grange, zero sous le tonneau, deux metres neuf sous le
     * tunnel, quatre metres deux sous la nef, mais de sept metres sous le temple
     * et de plus de huit sous le dome de glace et la poche de billard — coque
     * epaisse, stylobate, bourrelet de bande. Trois de ces ouvrages ont donc du
     * decor seme dans leur enveloppe, et l'avaient deja : l'ancienne exclusion
     * ne leur laissait pas trois metres non plus. Les elargir se deciderait sur
     * ce qu'on voit, et on ne voit rien — la coque est opaque, le buisson enfoui
     * n'apparait ni du dedans ni du dehors. Ce qui a impose le cas de la nef,
     * c'est qu'elle, on voit au travers : son bas-cote se lit sous l'arcade.
     */
    public final double degagement;

    Ouvrage(String nom, String label, double demiLongueur, double marge) {
        this(nom, label, demiLongueur, marge, 3);
    }

    Ouvrage(String nom, String label, double demiLongueur, double marge, double degagement) {
        this.nom = nom;
        this.label = label;
        this.demiLongueur = demiLongueur;
        this.marge = marge;
        this.degagement = degagement;
    }

    /** L'ouvrage de ce nom, ou {@code null} si le nom designe une piece ordinaire. */
    public static Ouvrage parNom(String nom) {
        for (Ouvrage o : values()) {
            if (o.nom.equals(nom)) return o;
        }
        return null;
    }

    /**
     * Le profil de demi-largeur de l'ouvrage pose a l'abscisse {@code s0}.
     *
     * <p>Un ouvrage a longtemps eu une demi-largeur constante, prise sur le
     * couloir le plus large de son emprise. La nef du donjon en payait le prix :
     * son emprise attrapait l'elargissement de la grille de depart, et les
     * quatre-vingt-douze metres de nef etaient batis a la largeur de ce seul
     * point — soixante-cinq metres de large, une voute a quarante-sept metres de
     * clef. Dedans, plus rien n'entrait dans le champ de la camera : on roulait
     * quatre-vingt-dix metres dans une nef qu'on ne voyait pas.
     *
     * <p>Le mur suit donc le couloir travee par travee. A {@code along} metres de
     * l'ancrage il se tient a {@code halfCorridorAtS(s0 + along) + marge}, ce qui
     * conserve la garantie qui a impose le maximum — en tout point de l'emprise
     * le mur reste a plus d'un metre et demi du bord — tout en rendant a
     * l'ouvrage l'echelle de la piste qu'il enjambe.
     *
     * <p>C'est aussi ce profil, et non plus une enveloppe unique, qui arrete le
     * semis et le mobilier de bord de piste : voir {@code TrackNode.ouvrageSur}.
     */
    public Profil profil(Track track, double s0) {
        int n = (int) Math.ceil(2 * demiLongueur / Profil.PAS) + 1;
        double[] demi = new double[n];
        for (int i = 0; i < n; i++) {
            demi[i] = track.halfCorridorAtS(s0 - demiLongueur + i * Profil.PAS) + marge;
        }
        return new Profil(demi, demiLongueur);
    }

    /**
     * La demi-largeur libre d'un ouvrage, le long de son emprise.
     *
     * <p>Un ouvrage est une boite rigide : il est bati une fois, dans son propre
     * repere, autour de l'axe de la piste au point d'ancrage. Le couloir, lui,
     * s'ouvre et se resserre sous lui. Le profil est la fonction qui reconcilie
     * les deux — l'ecart auquel poser le mur a chaque travee.
     *
     * <p>Il est echantillonne plutot que calcule a la demande : un batisseur
     * interroge son profil quelques centaines de fois par ouvrage, et
     * {@code halfCorridorAtS} fait a chaque appel une recherche dans le ruban.
     */
    public static final class Profil {

        /** Pas d'echantillonnage, en metres. */
        private static final double PAS = 1.0;

        private final double[] demi;
        private final double demiLongueur;
        private final double enveloppe;

        private Profil(double[] demi, double demiLongueur) {
            this.demi = demi;
            this.demiLongueur = demiLongueur;
            double max = 0;
            for (double d : demi) max = Math.max(max, d);
            this.enveloppe = max;
        }

        /**
         * Demi-largeur libre a {@code along} metres de l'ancrage, positif vers
         * l'avant.
         *
         * <p>Au-dela de l'emprise la valeur du bout est prolongee : les portails,
         * rabats et pylones se posent un a six metres devant l'entree, et doivent
         * s'aligner sur la premiere travee, pas sur un couloir extrapole.
         */
        public double demi(double along) {
            double t = (along + demiLongueur) / PAS;
            if (t <= 0) return demi[0];
            if (t >= demi.length - 1) return demi[demi.length - 1];
            int i = (int) t;
            double f = t - i;
            return demi[i] * (1 - f) + demi[i + 1] * f;
        }

        /** Demi-longueur de l'emprise, en metres. */
        public double demiLongueur() {
            return demiLongueur;
        }

        /** La plus grande demi-largeur du profil. */
        public double enveloppe() {
            return enveloppe;
        }
    }
}
