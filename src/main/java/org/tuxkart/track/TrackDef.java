package org.tuxkart.track;

import org.tuxkart.math.Vec3;

import java.util.ArrayList;
import java.util.List;

/** Description statique d'un circuit (donnees d'auteur, sans geometrie). */
public final class TrackDef {

    public final String id;
    public final String name;
    public final String subtitle;
    public final Theme theme;
    public final Surface surface;
    public final Barrier barrier;
    /** Demi-largeur de bitume, en metres (la piste fait donc 2x cette valeur). */
    public final double roadHalfWidth;
    /** Largeur de bas-cote praticable mais lent, de chaque cote. */
    public final double shoulderWidth;
    public final int defaultLaps;
    public final List<Vec3> controlPoints;
    /** Difficulte indicative affichee dans le menu, de 1 a 5. */
    public final int difficulty;

    /**
     * Dos d'ane du circuit : {abscisse curviligne, hauteur, demi-longueur}, en
     * metres. Le profil est un cosinus releve, donc raccorde en pente au sol de
     * part et d'autre — une marche lancerait le kart n'importe comment.
     *
     * <p>La hauteur et la demi-longueur decident de la vitesse a partir de
     * laquelle on decolle : sur une bosse de hauteur H et de demi-longueur L,
     * la crete se derobe des que v² > 2 g L² / (H π²).
     */
    public final double[][] bumps;

    /**
     * Piste ouverte : ni glissiere ni muret, on sort du ruban et on roule a
     * cote. Les circuits d'interieur en vivent — une petite voiture dans un
     * salon n'a pas de rambarde, elle a de la moquette et des jouets.
     */
    public final boolean open;

    /**
     * Profil de largeur : {abscisse curviligne, demi-largeur de piste}, en
     * metres, interpole d'un point au suivant. Vide, la piste garde partout la
     * largeur declaree.
     *
     * <p>Une piste de largeur constante est une piste de circuit. Dans une
     * piece, le passage se resserre entre le canape et la table basse et
     * s'ouvre au milieu du tapis : c'est le mobilier qui dessine la trajectoire,
     * pas une rambarde.
     */
    public final double[][] widths;

    /**
     * Objets poses un a un : {abscisse, ecart lateral, cap relatif, echelle,
     * indice de piece}. Une piece habitee compte quelques dizaines d'objets
     * places, pas des milliers semes — et chacun se voit, donc chacun se
     * choisit.
     */
    public final double[][] props;
    /** Nom de la piece a construire pour chaque {@link #props}. */
    public final String[] propKinds;

    public TrackDef(String id, String name, String subtitle, Theme theme,
                    Surface surface, Barrier barrier,
                    double roadHalfWidth, double shoulderWidth, int defaultLaps,
                    int difficulty, double[][] pts) {
        this(id, name, subtitle, theme, surface, barrier, roadHalfWidth, shoulderWidth,
                defaultLaps, difficulty, pts, new double[0][], false,
                new double[0][], new double[0][], new String[0]);
    }

    public TrackDef(String id, String name, String subtitle, Theme theme,
                    Surface surface, Barrier barrier,
                    double roadHalfWidth, double shoulderWidth, int defaultLaps,
                    int difficulty, double[][] pts, double[][] bumps, boolean open) {
        this(id, name, subtitle, theme, surface, barrier, roadHalfWidth, shoulderWidth,
                defaultLaps, difficulty, pts, bumps, open,
                new double[0][], new double[0][], new String[0]);
    }

    public TrackDef(String id, String name, String subtitle, Theme theme,
                    Surface surface, Barrier barrier,
                    double roadHalfWidth, double shoulderWidth, int defaultLaps,
                    int difficulty, double[][] pts, double[][] bumps, boolean open,
                    double[][] widths, double[][] props, String[] propKinds) {
        this.id = id;
        this.name = name;
        this.subtitle = subtitle;
        this.theme = theme;
        this.surface = surface;
        this.barrier = barrier;
        this.roadHalfWidth = roadHalfWidth;
        this.shoulderWidth = shoulderWidth;
        this.defaultLaps = defaultLaps;
        this.difficulty = difficulty;
        List<Vec3> cps = new ArrayList<>(pts.length);
        for (double[] p : pts) {
            cps.add(new Vec3(p[0], p.length > 2 ? p[2] : 0, p[1]));
        }
        this.controlPoints = List.copyOf(cps);
        this.bumps = bumps;
        this.open = open;
        this.widths = widths;
        this.props = props;
        this.propKinds = propKinds;
    }
}
