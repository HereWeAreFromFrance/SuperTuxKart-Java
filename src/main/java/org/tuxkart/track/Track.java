package org.tuxkart.track;

import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Spline;
import org.tuxkart.math.Vec3;

/**
 * Geometrie exploitable d'un circuit : la spline est re-echantillonnee a pas
 * constant (~1,6 m) pour donner un « ruban » de reference. Tout le jeu
 * (physique, IA, classement, objets, minicarte) travaille ensuite dans le
 * repere curviligne (s le long de la piste, lateral en travers).
 */
public final class Track {

    /** Pas d'echantillonnage vise, en metres. */
    private static final double TARGET_SPACING = 1.6;
    /** Devers maximal en virage, en radians (~7,5 degres). */
    private static final double MAX_BANK = 0.13;

    public final TrackDef def;
    public final int n;
    public final double spacing;
    public final double length;
    public final double halfRoad;
    public final double shoulder;
    /** Demi-largeur totale praticable (bitume + bas-cote), au-dela c'est le mur. */
    public final double halfCorridor;

    public final Vec3[] center;
    /** Direction de la piste, normalisee, pente comprise. */
    public final Vec3[] fwd;
    /** Direction horizontale de la piste (pente ignoree). */
    public final Vec3[] fwdFlat;
    /** Vecteur lateral horizontal unitaire (positif = un cote, negatif = l'autre). */
    public final Vec3[] side;
    /** Cap de la piste au point i, en radians. Precalcule : c'est une constante geometrique. */
    public final double[] heading;
    /** Abscisse curviligne cumulee au point i. */
    public final double[] arc;
    /**
     * Devers : variation d'altitude par metre d'ecart lateral. Negatif dans un
     * virage a droite, ou l'exterieur (cote -side) est releve.
     */
    public final double[] bankSlope;

    /**
     * Demi-largeur de piste et de couloir, echantillon par echantillon.
     *
     * Les champs {@link #halfRoad} et {@link #halfCorridor} restent la largeur
     * de reference — celle de la grille de depart, de la minicarte et de tout
     * ce qui a besoin d'un seul chiffre. Ces tableaux-la sont la verite locale.
     */
    public final double[] halfRoadAt;
    public final double[] halfCorridorAt;

    public final double minY, maxY;
    public final double minX, maxX, minZ, maxZ;

    public Track(TrackDef def) {
        this.def = def;
        this.halfRoad = def.roadHalfWidth;
        this.shoulder = def.shoulderWidth;
        this.halfCorridor = halfRoad + shoulder;

        Spline spline = new Spline(def.controlPoints);

        // 1) echantillonnage dense pour mesurer la longueur reelle
        int segs = spline.segmentCount();
        int dense = segs * 96;
        Vec3[] densePts = new Vec3[dense + 1];
        for (int i = 0; i <= dense; i++) {
            densePts[i] = spline.point(i * (double) segs / dense);
        }
        double total = 0;
        double[] denseArc = new double[dense + 1];
        for (int i = 1; i <= dense; i++) {
            total += densePts[i].distance(densePts[i - 1]);
            denseArc[i] = total;
        }

        this.n = Math.max(16, (int) Math.round(total / TARGET_SPACING));
        this.spacing = total / n;
        this.length = total;

        // 2) re-echantillonnage a pas constant
        center = new Vec3[n];
        int cursor = 0;
        for (int i = 0; i < n; i++) {
            double target = i * spacing;
            while (cursor < dense - 1 && denseArc[cursor + 1] < target) cursor++;
            double segLen = denseArc[cursor + 1] - denseArc[cursor];
            double t = segLen < 1e-9 ? 0 : (target - denseArc[cursor]) / segLen;
            center[i] = Vec3.lerp(densePts[cursor], densePts[cursor + 1], t);
        }

        // 2 bis) dos d'ane : ils sont ajoutes a l'altitude du ruban une fois
        // celui-ci re-echantillonne, donc avant tout le reste. Le maillage de
        // la piste, la physique, l'IA et la camera lisent tous cette meme
        // altitude : il n'y a rien d'autre a synchroniser.
        for (double[] bump : def.bumps) {
            double s0 = bump[0], hauteur = bump[1], demi = Math.max(1e-3, bump[2]);
            for (int i = 0; i < n; i++) {
                double d = i * spacing - s0;
                while (d > total / 2) d -= total;
                while (d < -total / 2) d += total;
                if (Math.abs(d) >= demi) continue;
                double u = Math.PI * d / demi;
                center[i] = center[i].add(0, hauteur * 0.5 * (1 + Math.cos(u)), 0);
            }
        }

        // 2 ter) profil de largeur : interpole d'un point de controle au
        // suivant par un cosinus, qui raccorde les pentes — un raccord lineaire
        // donnerait un pli visible a chaque changement de largeur.
        halfRoadAt = new double[n];
        halfCorridorAt = new double[n];
        double ratio = shoulder / Math.max(1e-6, halfRoad);
        for (int i = 0; i < n; i++) {
            halfRoadAt[i] = largeurEn(def.widths, i * spacing, total, halfRoad);
            halfCorridorAt[i] = halfRoadAt[i] * (1 + ratio);
        }

        // 3) reperes locaux
        fwd = new Vec3[n];
        fwdFlat = new Vec3[n];
        side = new Vec3[n];
        heading = new double[n];
        arc = new double[n];
        for (int i = 0; i < n; i++) {
            Vec3 a = center[MathUtil.mod(i - 1, n)];
            Vec3 b = center[MathUtil.mod(i + 1, n)];
            Vec3 d = b.sub(a);
            fwd[i] = d.normalize();
            fwdFlat[i] = d.normalizeXZ();
            side[i] = new Vec3(fwdFlat[i].z, 0, -fwdFlat[i].x);
            heading[i] = fwdFlat[i].heading();
            arc[i] = i * spacing;
        }

        // 4) devers : la courbure signee, lissee sur une trentaine de metres
        double[] curvature = new double[n];
        int span = Math.max(2, (int) Math.round(4.0 / spacing));
        for (int i = 0; i < n; i++) {
            int a = MathUtil.mod(i - span, n);
            int b = MathUtil.mod(i + span, n);
            double d = MathUtil.wrapPi(heading[b] - heading[a]);
            curvature[i] = d / (2 * span * spacing);
        }
        bankSlope = new double[n];
        int win = Math.max(2, (int) Math.round(16.0 / spacing));
        for (int i = 0; i < n; i++) {
            double sum = 0;
            for (int k = -win; k <= win; k++) sum += curvature[MathUtil.mod(i + k, n)];
            double angle = Math.clamp(sum / (2 * win + 1) * 16.0, -MAX_BANK, MAX_BANK);
            bankSlope[i] = -Math.tan(angle);
        }

        double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
        double lx = Double.MAX_VALUE, hx = -Double.MAX_VALUE;
        double lz = Double.MAX_VALUE, hz = -Double.MAX_VALUE;
        for (Vec3 c : center) {
            lo = Math.min(lo, c.y);
            hi = Math.max(hi, c.y);
            lx = Math.min(lx, c.x);
            hx = Math.max(hx, c.x);
            lz = Math.min(lz, c.z);
            hz = Math.max(hz, c.z);
        }
        minY = lo;
        maxY = hi;
        minX = lx;
        maxX = hx;
        minZ = lz;
        maxZ = hz;
    }

    /** Resultat d'une projection d'un point du monde sur le ruban de piste. */
    public static final class Loc {
        public int index;
        /** Abscisse curviligne dans [0, length[. */
        public double s;
        /** Ecart lateral signe par rapport a l'axe, en metres. */
        public double lateral;
        /** Altitude du bitume sous le point. */
        public double height;
        public Vec3 center = Vec3.ZERO;
        public Vec3 fwdFlat = new Vec3(0, 0, 1);
        public Vec3 side = new Vec3(1, 0, 0);
        /** Cap de la piste a cet endroit. */
        public double heading;
        /** Devers local (voir {@link Track#bankSlope}). */
        public double bankSlope;
    }

    public int indexOfS(double s) {
        return MathUtil.mod((int) Math.floor(MathUtil.mod(s, length) / spacing), n);
    }

    public Vec3 centerAtS(double s) {
        double t = MathUtil.mod(s, length) / spacing;
        int i = MathUtil.mod((int) Math.floor(t), n);
        int j = MathUtil.mod(i + 1, n);
        return Vec3.lerp(center[i], center[j], t - Math.floor(t));
    }

    public Vec3 forwardAtS(double s) {
        return fwdFlat[indexOfS(s)];
    }

    public Vec3 sideAtS(double s) {
        return side[indexOfS(s)];
    }

    /**
     * Devers a l'abscisse {@code s}, interpole entre les deux echantillons
     * voisins — exactement comme {@link #heightAtS} et comme {@link #locate}.
     *
     * <p>Le prendre en escalier faisait diverger deux calculs de la meme
     * altitude : {@link #worldAt} sautait d'un devers a l'autre tous les
     * 1,6 m quand la projection, elle, passait progressivement de l'un a
     * l'autre. A vingt metres de l'axe, l'ecart atteignait vingt centimetres —
     * assez pour que le terrain, ancre sur le bord de chaussee, remonte au
     * travers du bitume.
     */
    public double bankAtS(double s) {
        double t = MathUtil.mod(s, length) / spacing;
        int i = (int) Math.floor(t);
        int j = MathUtil.mod(i + 1, n);
        return MathUtil.lerp(bankSlope[MathUtil.mod(i, n)], bankSlope[j], t - Math.floor(t));
    }

    /** Position monde a partir des coordonnees curvilignes, devers compris. */
    public Vec3 worldAt(double s, double lateral) {
        Vec3 c = centerAtS(s);
        Vec3 sd = sideAtS(s);
        return new Vec3(c.x + sd.x * lateral,
                c.y + lateral * bankAtS(s),
                c.z + sd.z * lateral);
    }

    // ------------------------------------------------------- grille d'amorce

    /**
     * Index de l'axe le plus proche du centre de chaque case d'une grille
     * reguliere. C'est ce qui remplace le balayage complet du ruban quand on
     * n'a pas d'indice de depart.
     *
     * <p>Sans elle, {@link #locate} retombait sur une recherche en O(n) des que
     * le point sortait de la fenetre locale — c'est-a-dire au-dela d'une
     * quarantaine de metres de la piste. Le cas etait rare en course mais
     * systematique a la construction du terrain, dont la grille s'etend a plus
     * d'un kilometre : un quart de million de projections, chacune balayant les
     * sept cents points de l'axe.
     *
     * <p>Les cases sont plus larges que le pas d'echantillonnage : l'amorce
     * n'est qu'un point de depart, la fenetre locale qui suit la corrige.
     */
    private final Object seedLock = new Object();
    private volatile int[] seedGrid;
    private double seedCell;
    private double seedX0, seedZ0;
    private int seedCols, seedRows;

    /** Marge autour du circuit encore couverte par la grille, en metres. */
    private static final double SEED_MARGIN = 400;

    private int[] seeds() {
        int[] grid = seedGrid;
        if (grid != null) return grid;
        synchronized (seedLock) {
            if (seedGrid != null) return seedGrid;
            seedCell = Math.max(12.0, spacing * 8);
            seedX0 = minX - SEED_MARGIN;
            seedZ0 = minZ - SEED_MARGIN;
            seedCols = (int) Math.ceil((maxX - minX + 2 * SEED_MARGIN) / seedCell) + 1;
            seedRows = (int) Math.ceil((maxZ - minZ + 2 * SEED_MARGIN) / seedCell) + 1;
            int[] built = new int[seedCols * seedRows];
            for (int r = 0; r < seedRows; r++) {
                double cz = seedZ0 + (r + 0.5) * seedCell;
                for (int c = 0; c < seedCols; c++) {
                    double cx = seedX0 + (c + 0.5) * seedCell;
                    built[r * seedCols + c] = nearestBrut(cx, cz);
                }
            }
            seedGrid = built;
            return built;
        }
    }

    /** Balayage complet du ruban : reserve a la construction de la grille d'amorce. */
    private int nearestBrut(double x, double z) {
        int best = 0;
        double bestD2 = Double.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            double dx = x - center[i].x, dz = z - center[i].z;
            double d2 = dx * dx + dz * dz;
            if (d2 < bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    /**
     * Index de depart pour un point quelconque du monde. Hors grille, on se
     * rabat sur la case de bord la plus proche : a cette distance de la piste,
     * l'index exact n'a plus d'influence visible sur le resultat.
     */
    private int seedFor(double x, double z) {
        int[] grid = seeds();
        int c = Math.clamp((int) ((x - seedX0) / seedCell), 0, seedCols - 1);
        int r = Math.clamp((int) ((z - seedZ0) / seedCell), 0, seedRows - 1);
        return grid[r * seedCols + c];
    }

    /** Demi-largeur de la fenetre d'index exploree autour de l'amorce. */
    private static final int WINDOW = 40;

    private double dist2(double x, double z, int i) {
        double dx = x - center[i].x, dz = z - center[i].z;
        return dx * dx + dz * dz;
    }

    /**
     * Fenetre etroite essayee d'abord : quand l'amorce est bonne — et elle
     * l'est presque toujours, qu'elle vienne de l'image precedente ou de la
     * grille — l'optimum est a quelques index de la. On n'ouvre la grande
     * fenetre que si le minimum trouve touche le bord de la petite, signe
     * qu'il est peut-etre plus loin.
     */
    private static final int NEAR_WINDOW = 8;

    /** Index de l'axe le plus proche dans une fenetre centree sur {@code from}. */
    private int scan(double x, double z, int from) {
        int best = scanWindow(x, z, from, NEAR_WINDOW);
        int gap = Math.abs(MathUtil.mod(best - from + n / 2, n) - n / 2);
        return gap >= NEAR_WINDOW ? scanWindow(x, z, from, WINDOW) : best;
    }

    private int scanWindow(double x, double z, int from, int window) {
        int best = from;
        double bestD2 = Double.MAX_VALUE;
        for (int k = -window; k <= window; k++) {
            int i = MathUtil.mod(from + k, n);
            double d2 = dist2(x, z, i);
            if (d2 < bestD2) {
                bestD2 = d2;
                best = i;
            }
        }
        return best;
    }

    /**
     * Projette (x, z) sur la piste. {@code hint} est l'index trouve a l'appel
     * precedent : la recherche reste alors locale, donc O(1). Sans indice —
     * ou si le point s'est trop eloigne de la fenetre exploree — l'amorce vient
     * de la grille ci-dessus, jamais d'un balayage complet.
     */
    public Loc locate(double x, double z, int hint, Loc out) {
        Loc loc = out != null ? out : new Loc();
        int best = scan(x, z, hint >= 0 ? hint : seedFor(x, z));
        // Loin de la fenetre exploree, l'indice de depart n'apprenait peut-etre
        // rien. On ne rebalaye pas pour autant : on demande son avis a la
        // grille, et on ne recommence que si elle propose mieux — un point de
        // comparaison coute une soustraction, un balayage en coute quatre-vingt.
        if (hint >= 0 && dist2(x, z, best) > (WINDOW * spacing) * (WINDOW * spacing) * 0.5) {
            int seed = seedFor(x, z);
            if (dist2(x, z, seed) < dist2(x, z, best)) best = scan(x, z, seed);
        }

        int i = best;
        Vec3 c = center[i];
        double dx = x - c.x, dz = z - c.z;
        double along = dx * fwdFlat[i].x + dz * fwdFlat[i].z;
        double lat = dx * side[i].x + dz * side[i].z;

        // altitude interpolee vers le voisin dans le sens de "along"
        int j = MathUtil.mod(i + (along >= 0 ? 1 : -1), n);
        double t = Math.clamp(Math.abs(along) / spacing, 0, 1);
        double h = MathUtil.lerp(c.y, center[j].y, t);

        // Le devers est interpole comme l'altitude, et entre les deux memes
        // voisins : le prendre en escalier donnait, a dix-sept metres de l'axe,
        // une hauteur differente de celle que worldAt calcule au meme endroit,
        // et le terrain finissait par crever la chaussee sur un devers.
        double bank = MathUtil.lerp(bankSlope[i], bankSlope[j], t);
        loc.index = i;
        loc.s = MathUtil.mod(arc[i] + along, length);
        loc.lateral = lat;
        loc.bankSlope = bank;
        loc.height = h + lat * bank;
        loc.center = c;
        loc.fwdFlat = fwdFlat[i];
        loc.side = side[i];
        loc.heading = heading[i];
        return loc;
    }

    /**
     * Courbure <b>maximale</b> (rad/m) rencontree sur {@code lookMeters} metres
     * a partir de s. C'est bien le pire virage a venir qui interesse l'IA, pas
     * la moyenne : une epingle noyee dans une ligne droite doit se voir.
     */
    public double curvatureAhead(double s, double lookMeters) {
        int steps = Math.max(2, (int) (lookMeters / spacing));
        int gap = Math.max(2, (int) Math.round(10.0 / spacing));
        int i0 = indexOfS(s);
        double worst = 0;
        for (int k = 0; k < steps; k += 2) {
            int a = MathUtil.mod(i0 + k, n);
            int b = MathUtil.mod(i0 + k + gap, n);
            double d = Math.abs(MathUtil.wrapPi(heading[b] - heading[a]));
            worst = Math.max(worst, d / (gap * spacing));
        }
        return worst;
    }

    /** Cap de la piste a l'abscisse s, sans repasser par un arc tangente. */
    /** Demi-largeur de piste a l'abscisse {@code s}. */
    public double halfRoadAtS(double s) {
        return halfRoadAt[indexOfS(s)];
    }

    /** Demi-largeur de couloir a l'abscisse {@code s}. */
    public double halfCorridorAtS(double s) {
        return halfCorridorAt[indexOfS(s)];
    }

    /**
     * Largeur au point {@code s}, interpolee entre les points de controle qui
     * l'encadrent. Le profil boucle avec le circuit.
     */
    private static double largeurEn(double[][] profil, double s, double total, double defaut) {
        if (profil.length == 0) return defaut;
        if (profil.length == 1) return profil[0][1];
        int apres = -1;
        for (int k = 0; k < profil.length; k++) {
            if (profil[k][0] > s) {
                apres = k;
                break;
            }
        }
        int b = apres < 0 ? 0 : apres;
        int a = (b - 1 + profil.length) % profil.length;
        double s0 = profil[a][0], s1 = profil[b][0];
        double d = s1 - s0;
        while (d <= 0) d += total;
        double u = s - s0;
        while (u < 0) u += total;
        while (u > total) u -= total;
        double t = Math.clamp(u / d, 0, 1);
        // cosinus : pente nulle aux deux extremites, donc pas de pli
        double f = (1 - Math.cos(Math.PI * t)) / 2;
        return MathUtil.lerp(profil[a][1], profil[b][1], f);
    }

    public double headingAtS(double s) {
        return heading[indexOfS(s)];
    }

    /**
     * Altitude du ruban a l'abscisse {@code s}, devers compris.
     *
     * Interpolee entre les deux echantillons voisins : sonder la pente ou la
     * courbure verticale a partir des seuls points d'echantillonnage donnerait
     * un profil en escalier, et le kart decollerait a chaque marche.
     */
    public double heightAtS(double s, double lateral) {
        double t = MathUtil.mod(s, length) / spacing;
        int i = (int) Math.floor(t);
        int j = MathUtil.mod(i + 1, n);
        double f = t - i;
        i = MathUtil.mod(i, n);
        return MathUtil.lerp(center[i].y, center[j].y, f)
                + lateral * MathUtil.lerp(bankSlope[i], bankSlope[j], f);
    }

    /** Difference signee s1 - s0 en tenant compte du bouclage. */
    public double deltaS(double s0, double s1) {
        double d = s1 - s0;
        while (d > length / 2) d -= length;
        while (d < -length / 2) d += length;
        return d;
    }

    /**
     * Grille de depart : deux colonnes decalees, juste avant la ligne s = 0.
     * L'index 0 est la pole position.
     *
     * <p>Ces deux accesseurs sont la seule definition de la grille : le decor
     * peint ses emplacements a partir d'eux, pour qu'un reglage du trace ne
     * puisse pas desaligner les karts et la peinture.
     */
    public double gridS(int rank) {
        return length - 8 - (rank / 2) * 7.0;
    }

    /** Ecart lateral de l'emplacement : les rangs pairs a gauche, impairs a droite. */
    public double gridLateral(int rank) {
        return (rank % 2 == 0 ? -1 : 1) * halfRoad * 0.45;
    }

    public Vec3 gridPosition(int rank) {
        return worldAt(gridS(rank), gridLateral(rank));
    }

    public double gridHeading(int rank) {
        return headingAtS(gridS(rank));
    }
}
