package org.tuxkart.dev;

import javafx.application.Platform;
import org.tuxkart.core.Quality;
import org.tuxkart.math.Vec3;
import org.tuxkart.render.TrackNode;
import org.tuxkart.render.Textures;
import org.tuxkart.track.Ouvrage;
import org.tuxkart.track.Track;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

import java.util.concurrent.CountDownLatch;

/**
 * D'ou sortent les chiffres du README.
 *
 * <p>La documentation de ce projet affirme des nombres : neuf circuits, telle
 * longueur, tant d'objets poses a la main, tant de triangles en niveau Ultra.
 * Ces nombres se periment a chaque changement, et un README qui ment est pire
 * qu'un README absent — on le croit. Plutot que de les recopier a la main,
 * cette classe les <b>mesure</b>, et c'est elle qu'on relance avant de publier
 * une affirmation chiffree.
 *
 * <p>Deux tableaux, parce que les deux ne coutent pas la meme chose :
 *
 * <ul>
 *   <li>la <b>fiche</b> de chaque circuit sort de la simulation seule — piste
 *       rebattue, largeurs, bosses, denivele, rayon du virage le plus serre —
 *       et se calcule en une seconde, sans carte graphique ;</li>
 *   <li>le <b>budget de triangles</b> demande de batir le decor pour de vrai,
 *       donc le toolkit JavaFX et quelques minutes. Il n'est calcule que si on
 *       le demande.</li>
 * </ul>
 *
 * Usage : {@code ./run.sh chiffres} pour la fiche seule,
 * {@code ./run.sh chiffres triangles} pour la fiche et le budget.
 */
public final class Chiffres {

    private Chiffres() {
    }

    public static void main(String[] args) throws Exception {
        fiches();
        boolean triangles = args.length > 0 && args[0].startsWith("tri");
        if (triangles) {
            triangles();
        } else {
            System.out.println();
            System.out.println("Le budget de triangles demande la carte graphique et quelques");
            System.out.println("minutes : « ./run.sh chiffres triangles » pour l'obtenir.");
        }
        System.exit(0);
    }

    // ------------------------------------------------------------- la fiche

    private static void fiches() {
        System.out.printf("%-10s %8s %12s %6s %7s %7s %8s %9s %8s%n",
                "circuit", "longueur", "largeur", "tours", "objets", "bosses",
                "denivele", "rayon min", "ouvrage");
        for (TrackDef def : Tracks.ALL) {
            Track track = new Track(def);
            // « objets » compte tout ce que le circuit pose a la main,
            // l'ouvrage traverse compris : c'est la colonne du README
            String ouvrage = "—";
            for (String kind : def.propKinds) {
                Ouvrage o = Ouvrage.parNom(kind);
                if (o != null) ouvrage = o.nom;
            }
            System.out.printf("%-10s %6.0f m %5.0f a %2.0f m %6d %7d %7d %6.0f m %7.0f m %8s%n",
                    def.id, track.length,
                    2 * largeur(def, true), 2 * largeur(def, false),
                    def.defaultLaps, def.props.length,
                    def.bumps == null ? 0 : def.bumps.length,
                    denivele(track), rayonMini(track), ouvrage);
        }
    }

    /** Demi-largeur de piste minimale ou maximale declaree par le circuit. */
    private static double largeur(TrackDef def, boolean mini) {
        if (def.widths == null || def.widths.length == 0) return def.roadHalfWidth;
        double best = def.widths[0][1];
        for (double[] w : def.widths) best = mini ? Math.min(best, w[1]) : Math.max(best, w[1]);
        return best;
    }

    private static double denivele(Track track) {
        double bas = Double.MAX_VALUE, haut = -Double.MAX_VALUE;
        for (double s = 0; s < track.length; s += 1) {
            double y = track.heightAtS(s, 0);
            bas = Math.min(bas, y);
            haut = Math.max(haut, y);
        }
        return haut - bas;
    }

    /** Rayon du virage le plus serre de tout le circuit. */
    private static double rayonMini(Track track) {
        return rayonMini(track, 0, track.length);
    }

    /**
     * Rayon du virage le plus serre entre deux abscisses, bornes comprises.
     *
     * <p>Publique parce que les tests s'en servent : c'est cette mesure, et pas
     * une autre, qui decide si un tremplin se pose sur du droit ou si la grille
     * de depart est alignee. Deux facons de mesurer une courbure ne donnent pas
     * le meme nombre ; une seule dans le depot evite qu'un chiffre du README
     * soit vrai selon la fiche et faux selon le test.
     */
    public static double rayonMini(Track track, double s0, double s1) {
        double mini = Double.MAX_VALUE;
        for (double s = s0; s <= s1; s += 1) {
            mini = Math.min(mini, rayon(track, s));
        }
        return mini;
    }

    /**
     * Rayon du cercle passant par trois points du ruban espaces de dix metres.
     *
     * <p>La corde compte : prise trop courte, elle mesure le bruit de
     * l'echantillonnage plutot que le virage. Dix metres, c'est l'ordre de
     * grandeur d'un kart lance, donc ce que le pilote ressent.
     */
    public static double rayon(Track track, double s) {
        Vec3 a = track.worldAt(s - 10, 0);
        Vec3 b = track.worldAt(s, 0);
        Vec3 c = track.worldAt(s + 10, 0);
        double ab = Math.hypot(b.x - a.x, b.z - a.z);
        double bc = Math.hypot(c.x - b.x, c.z - b.z);
        double ca = Math.hypot(a.x - c.x, a.z - c.z);
        double aire = Math.abs((b.x - a.x) * (c.z - a.z) - (c.x - a.x) * (b.z - a.z)) / 2;
        if (aire < 1e-9) return Double.MAX_VALUE;
        return ab * bc * ca / (4 * aire);
    }

    // ------------------------------------------------- le budget de triangles

    private static void triangles() throws InterruptedException {
        CountDownLatch demarre = new CountDownLatch(1);
        Platform.startup(demarre::countDown);
        demarre.await();
        Platform.setImplicitExit(false);

        System.out.println();
        StringBuilder entete = new StringBuilder(String.format("%-10s", "circuit"));
        for (Quality quality : Quality.values()) {
            entete.append(String.format("%10s  ", sansAccents(quality.label)));
        }
        System.out.println(entete);
        double[] totaux = new double[Quality.values().length];
        for (TrackDef def : Tracks.ALL) {
            StringBuilder ligne = new StringBuilder(String.format("%-10s", def.id));
            for (Quality quality : Quality.values()) {
                long tri = compte(def, quality);
                totaux[quality.ordinal()] += tri;
                ligne.append(String.format("%8.2f M", tri / 1e6));
                ligne.append("  ");
            }
            System.out.println(ligne);
        }
        StringBuilder moyennes = new StringBuilder(String.format("%-10s", "moyenne"));
        for (Quality quality : Quality.values()) {
            moyennes.append(String.format("%8.2f M  ",
                    totaux[quality.ordinal()] / Tracks.ALL.size() / 1e6));
        }
        System.out.println(moyennes);
        Platform.exit();
    }

    /** La console du terminal n'affiche pas toujours les accents des libelles. */
    private static String sansAccents(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
    }

    /** Batit le decor complet d'un circuit sur le fil JavaFX et compte ses triangles. */
    private static long compte(TrackDef def, Quality quality) throws InterruptedException {
        CountDownLatch fini = new CountDownLatch(1);
        long[] triangles = new long[1];
        Platform.runLater(() -> {
            try {
                Textures.setResolution(quality.textureSize);
                TrackNode.build(new Track(def), quality, 8);
                triangles[0] = TrackNode.lastTriangleCount;
            } catch (Throwable t) {
                System.out.println(def.id + " : " + t);
            } finally {
                fini.countDown();
            }
        });
        fini.await();
        // le decor d'un circuit en Ultra pese quelques centaines de mega-octets :
        // sans cela, les neuf s'accumulent et la mesure finit en manque de memoire
        System.gc();
        return triangles[0];
    }
}
