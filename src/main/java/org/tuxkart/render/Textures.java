package org.tuxkart.render;

import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Noise;
import org.tuxkart.track.Barrier;
import org.tuxkart.track.Surface;
import org.tuxkart.track.Theme;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Toutes les textures sont fabriquees a l'execution : le jeu n'a aucun fichier
 * de donnees a installer.
 *
 * Les surfaces (bitume, vibreur, sol, muret, pneu) sont calculees pixel par
 * pixel a partir d'un champ de hauteur. Ce champ sert deux fois : une fois pour
 * la couleur, une fois pour produire une <b>carte de normales</b> que JavaFX
 * applique en relief. C'est ce qui donne au bitume son grain et aux vibreurs
 * leurs cannelures sans ajouter un seul polygone.
 */
public final class Textures {

    /** Suffixe des cartes de normales dans le cache. */
    private static final String BUMP = "#n";

    /** Textures dessinees au Canvas : leur taille est fixee par leur contenu. */
    private static final Cache DRAWN = new Cache(32L << 20);
    /** Textures calculees pixel par pixel : leur taille suit {@link #resolution}. */
    private static final Cache BAKED = new Cache(64L << 20);
    private static final Noise N = new Noise(20260814L);

    private static int resolution = 512;

    private Textures() {
    }

    /**
     * Resolution des textures calculees. Seules celles-ci sont jetees : les
     * textures dessinees (panneaux, ciel, foule) ne dependent pas du reglage et
     * leur re-fabrication coute une seconde d'attente pour rien.
     */
    /**
     * Verrou des deux caches.
     *
     * Le decor d'une course est desormais construit en tache de fond pendant
     * que le fil JavaFX anime l'ecran de chargement : les deux peuvent demander
     * une texture en meme temps, et les caches sont des tables a ordre d'acces,
     * qui se corrompent silencieusement sous deux fils.
     */
    private static final Object LOCK = new Object();

    public static void setResolution(int px) {
        synchronized (LOCK) {
        if (px != resolution) {
            resolution = px;
            BAKED.clear();
        }
        }
    }

    /** Resolution courante des textures calculees. */
    public static int resolution() {
        return resolution;
    }

    /**
     * Cache de textures borne en memoire, les plus anciennement utilisees
     * partant les premieres.
     *
     * Sans borne, une session qui enchaine les circuits garde tout : chaque
     * theme ajoute son ciel, son sol, ses murets et leurs cartes de normales,
     * soit quelques dizaines de mega-octets par circuit que rien ne relache
     * jamais. La borne ne coute qu'une re-fabrication le jour ou l'on revient
     * sur un theme quitte depuis longtemps.
     */
    private static final class Cache {

        private final long budget;
        // ordre d'acces : le premier de la table est le moins recemment lu
        private final Map<String, Image> map = new LinkedHashMap<>(16, 0.75f, true);
        private long bytes;

        Cache(long budget) {
            this.budget = budget;
        }

        Image get(String key) {
            return map.get(key);
        }

        void put(String key, Image img) {
            Image old = map.put(key, img);
            if (old != null) bytes -= weight(old);
            bytes += weight(img);
            while (bytes > budget && map.size() > 2) {
                String oldest = map.keySet().iterator().next();
                drop(oldest);
                // couleur et relief vont par paire : garder l'une sans l'autre
                // donnerait une surface texturee mais plate
                drop(oldest.endsWith(BUMP)
                        ? oldest.substring(0, oldest.length() - BUMP.length())
                        : oldest + BUMP);
            }
        }

        void clear() {
            map.clear();
            bytes = 0;
        }

        private void drop(String key) {
            Image gone = map.remove(key);
            if (gone != null) bytes -= weight(gone);
        }

        private static long weight(Image img) {
            return (long) img.getWidth() * (long) img.getHeight() * 4L;
        }
    }

    // ------------------------------------------------------------- outillage

    private interface Baker {
        /**
         * Remplit la couleur et, en parallele, le champ de hauteur du relief,
         * pour les rangees {@code [y0, y1[} seulement.
         *
         * Le decoupage en bandes est ce qui rend la cuisson parallelisable :
         * chaque bande ecrit dans des cases distinctes des deux tableaux, il
         * n'y a donc rien a synchroniser. Une recette doit pour cela ne
         * dependre que de ses coordonnees — pas d'un tirage aleatoire qui
         * avancerait au fil du balayage, sans quoi le motif dependrait du
         * decoupage (voir {@link #dither}).
         */
        void bake(int size, int y0, int y1, int[] argb, double[] height);
    }

    /** Bandes cuites en parallele : au-dela, le decoupage coute plus qu'il ne rapporte. */
    private static final int BANDS = Math.min(8, Runtime.getRuntime().availableProcessors());

    /**
     * Cuit une texture, au plus a deux fois sa taille de reference.
     *
     * @param authoredSize   taille a laquelle la recette a ete reglee a l'oeil.
     *                       Le motif est exprime en unites de cette taille :
     *                       le cuire quatre fois plus fin ne revele aucun
     *                       detail supplementaire, il adoucit seulement les
     *                       transitions. On plafonne donc a deux fois la
     *                       taille de reference — assez pour que les aretes
     *                       nettes (bord de vibreur, joint de planche, pave de
     *                       sculpture) restent propres de pres, sans payer
     *                       seize fois les pixels pour la meme image.
     *                       Le relief, lui, se lit sur l'ecart entre pixels
     *                       voisins et a bien besoin d'etre remis a l'echelle :
     *                       a resolution doublee cet ecart est deux fois plus
     *                       faible pour un motif identique.
     */
    private static Image bake(String key, int authoredSize, double bumpStrength, Baker baker) {
        synchronized (LOCK) {
        Image cached = BAKED.get(key);
        if (cached != null) return cached;
        int size = Math.min(resolution, authoredSize * 2);
        int[] argb = new int[size * size];
        double[] height = new double[size * size];
        bands(size, (y0, y1) -> baker.bake(size, y0, y1, argb, height));
        Image diffuse = toImage(size, argb);
        BAKED.put(key, diffuse);
        if (bumpStrength > 0) {
            double strength = bumpStrength * size / (double) authoredSize;
            BAKED.put(key + BUMP, toImage(size, normalMap(size, height, strength)));
        }
        return diffuse;
        }
    }

    private interface Band {
        void rows(int y0, int y1);
    }

    /** Repartit les rangees sur les coeurs disponibles. */
    private static void bands(int size, Band work) {
        if (BANDS <= 1 || size < 128) {
            work.rows(0, size);
            return;
        }
        java.util.stream.IntStream.range(0, BANDS).parallel().forEach(b ->
                work.rows(size * b / BANDS, size * (b + 1) / BANDS));
    }

    /**
     * Tirage reproductible a partir des seules coordonnees.
     *
     * Un {@code Random} partage donnerait un motif different selon le
     * decoupage en bandes — et donc selon le nombre de coeurs de la machine.
     */
    private static double dither(int x, int y, long seed) {
        long h = x * 0x9E3779B97F4A7C15L ^ y * 0xC2B2AE3D27D4EB4FL ^ seed;
        h ^= h >>> 33;
        h *= 0xff51afd7ed558ccdL;
        h ^= h >>> 33;
        return (h >>> 11) * 0x1.0p-53;
    }

    private static Image bumpOf(String key) {
        return BAKED.get(key + BUMP);
    }

    private static Image toImage(int size, int[] argb) {
        WritableImage img = new WritableImage(size, size);
        img.getPixelWriter().setPixels(0, 0, size, size,
                PixelFormat.getIntArgbInstance(), argb, 0, size);
        return img;
    }

    /** Derive une carte de normales tangentielles du champ de hauteur. */
    private static int[] normalMap(int size, double[] h, double strength) {
        int[] out = new int[size * size];
        bands(size, (y0, y1) -> normalRows(size, h, strength, out, y0, y1));
        return out;
    }

    private static void normalRows(int size, double[] h, double strength, int[] out,
                                   int y0, int y1) {
        for (int y = y0; y < y1; y++) {
            for (int x = 0; x < size; x++) {
                double hl = h[wrap(size, x - 1, y)], hr = h[wrap(size, x + 1, y)];
                double hd = h[wrap(size, x, y + 1)], hu = h[wrap(size, x, y - 1)];
                double nx = (hl - hr) * strength;
                double ny = (hd - hu) * strength;
                double len = Math.sqrt(nx * nx + ny * ny + 1);
                int r = (int) ((nx / len * 0.5 + 0.5) * 255);
                int g = (int) ((ny / len * 0.5 + 0.5) * 255);
                int b = (int) ((1 / len * 0.5 + 0.5) * 255);
                out[y * size + x] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
    }

    private static int wrap(int size, int x, int y) {
        return ((y + size) % size) * size + (x + size) % size;
    }

    private static int argb(double r, double g, double b) {
        int ri = (int) Math.clamp(r * 255, 0, 255);
        int gi = (int) Math.clamp(g * 255, 0, 255);
        int bi = (int) Math.clamp(b * 255, 0, 255);
        return 0xff000000 | (ri << 16) | (gi << 8) | bi;
    }

    private static int shade(Color c, double factor) {
        return argb(c.getRed() * factor, c.getGreen() * factor, c.getBlue() * factor);
    }

    /** Eclaircit ou assombrit une couleur deja encodee, sans repasser par un objet Color. */
    private static int shade(int c, double factor) {
        return argb(((c >> 16) & 0xff) / 255.0 * factor,
                ((c >> 8) & 0xff) / 255.0 * factor,
                (c & 0xff) / 255.0 * factor);
    }

    private static int mix(Color a, Color b, double t) {
        return argb(MathUtil.lerp(a.getRed(), b.getRed(), t),
                MathUtil.lerp(a.getGreen(), b.getGreen(), t),
                MathUtil.lerp(a.getBlue(), b.getBlue(), t));
    }

    /** Melange deux couleurs deja encodees, sans allouer d'objet Color. */
    private static int mix(int a, Color b, double t) {
        double ar = ((a >> 16) & 0xff) / 255.0;
        double ag = ((a >> 8) & 0xff) / 255.0;
        double ab = (a & 0xff) / 255.0;
        return argb(MathUtil.lerp(ar, b.getRed(), t),
                MathUtil.lerp(ag, b.getGreen(), t),
                MathUtil.lerp(ab, b.getBlue(), t));
    }

    // ------------------------------------------------------------------ route

    /**
     * Revetement non asphalte : terre battue, sable ou neige damee. Pas de
     * marquage peint — la piste se lit aux ornieres laissees par les passages,
     * qui sont plus lisses et plus sombres que le reste.
     */
    public static Image surface(Theme theme, Surface surface) {
        if (surface == Surface.BITUME) return road(theme);
        if (surface.indoor) return revetementInterieur(surface);
        String key = "surf-" + surface.name();
        return bake(key, 512, 3.0, (size, y0, y1, argb, height) -> {
            double sc = 128.0 / size;
            Color base = switch (surface) {
                case TERRE -> Color.web("#8a6a4a");
                // Sable dame : un cran plus clair que le sable meuble du
                // desert, parce que c'est l'ecart de clarte entre les deux qui
                // dit ou finit la chaussee. Voir Theme.DESERT — a #d6be8c sur
                // un sol a #d8b06a, les deux pesaient la meme valeur et le
                // bord de piste n'existait plus.
                case SABLE -> Color.web("#e6cf90");
                default -> Color.web("#e6eef5");
            };
            Color dark = base.deriveColor(0, 1, 0.72, 1);
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double fx = x * sc, fy = y * sc;

                    double coarse = N.fbm(fx * 0.09, fy * 0.09, 4);
                    double grit = N.perlin(fx * 1.4, fy * 1.4);
                    double h = 0.5 + coarse * 0.30 + grit * 0.14;

                    // deux ornieres, la ou passent les roues
                    double rut = Math.min(Math.abs(u - 0.34), Math.abs(u - 0.66));
                    double packed = Math.clamp(1 - rut / 0.09, 0, 1);
                    h -= packed * 0.28;

                    double light = 0.72 + h * 0.46 - packed * 0.10;
                    int color = shade(mix(base, dark, packed * 0.55), light);

                    // cailloux ou plaques de glace selon le revetement
                    if (surface == Surface.NEIGE) {
                        if (grit > 0.62) color = argb(0.98, 0.99, 1.0);
                    } else if (grit > 0.58) {
                        color = mix(color, base.deriveColor(0, 1, 1.35, 1), (grit - 0.58) * 1.4);
                    }
                    // bords plus meubles : la piste s'effrite sur les cotes
                    double edge = Math.min(u, 1 - u);
                    if (edge < 0.09) {
                        double f = 1 - edge / 0.09;
                        color = mix(color, base.deriveColor(0, 1, 1.12, 1), f * 0.5);
                        h += f * 0.12;
                    }

                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    /**
     * Revetements d'interieur : feutre, parquet, laine, dalle.
     *
     * Aucun n'a d'ornieres — on ne creuse pas un tapis de billard — et chacun
     * tient a son motif : le sens du poil, la longueur des lames, la trame du
     * tissage, le joint entre deux pierres. C'est ce motif, plus que la
     * couleur, qui dit l'echelle du decor : on voit tout de suite que le kart
     * est minuscule.
     */
    private static Image revetementInterieur(Surface surface) {
        return bake("surf-" + surface.name(), 512, 2.0, (size, y0, y1, argb, height) -> {
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double fx = x * sc, fy = y * sc;
                    double grain = N.fbm(fx * 0.30, fy * 0.30, 3);
                    int color;
                    double h;
                    switch (surface) {
                        case FEUTRE -> {
                            // feutre : un poil fin, oriente, sans relief marque
                            double poil = N.perlin(fx * 2.6, fy * 0.7);
                            double light = 0.88 + poil * 0.16 + grain * 0.10;
                            color = shade(Color.web("#1f8a4c"), light);
                            h = 0.5 + poil * 0.06;
                        }
                        case PARQUET -> {
                            // lames de 40 cm, veinees, jointoyees de sombre
                            double lame = Math.floor(fy / 40) + Math.floor(fx / 160) * 0.5;
                            double veine = N.fbm(fx * 0.5, fy * 3.2 + lame * 17, 3);
                            double joint = Math.min(fy / 40 - Math.floor(fy / 40),
                                    1 - (fy / 40 - Math.floor(fy / 40)));
                            double light = 0.80 + veine * 0.30
                                    + (Math.abs(Math.sin(lame * 12.9898)) - 0.5) * 0.18;
                            color = shade(Color.web("#9a6a38"), light);
                            h = 0.55 + veine * 0.10;
                            if (joint < 0.035) {
                                color = shade(Color.web("#5a3a1e"), 0.9);
                                h -= 0.22;
                            }
                        }
                        case TAPIS -> {
                            // laine : une trame carree, molle
                            double trame = Math.sin(fx * 0.9) * Math.sin(fy * 0.9);
                            double light = 0.85 + trame * 0.10 + grain * 0.22;
                            color = shade(Color.web("#b6a389"), light);
                            h = 0.5 + trame * 0.14 + grain * 0.10;
                        }
                        default -> {
                            // dalles : de grands carreaux irreguliers, joints creuses
                            double cx = fx / 64, cz = fy / 64;
                            double jx = Math.min(cx - Math.floor(cx), 1 - (cx - Math.floor(cx)));
                            double jz = Math.min(cz - Math.floor(cz), 1 - (cz - Math.floor(cz)));
                            double joint = Math.min(jx, jz);
                            double teinte = N.perlin(Math.floor(cx) * 3.1, Math.floor(cz) * 3.1);
                            double light = 0.72 + teinte * 0.30 + grain * 0.16;
                            color = shade(Color.web("#6e6a66"), light);
                            h = 0.6 + grain * 0.10;
                            if (joint < 0.05) {
                                color = shade(Color.web("#3a3734"), 0.85);
                                h -= 0.35;
                            }
                        }
                    }
                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image surfaceBump(Theme theme, Surface surface) {
        surface(theme, surface);
        return surface == Surface.BITUME ? roadBump(theme) : bumpOf("surf-" + surface.name());
    }

    /** Habillage de la limite de piste, selon le type de barriere. */
    public static Image barrier(Barrier barrier, Theme theme) {
        if (barrier == Barrier.RAIL) return wall(theme);
        return bake("bar-" + barrier.name(), 256, 2.6, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double up = x / (double) size;
                    double along = y / (double) size;
                    double grain = N.fbm(x * sc * 0.10, y * sc * 0.10, 3);
                    int color;
                    double h;
                    switch (barrier) {
                        case BOIS -> {
                            // planches horizontales et poteaux verticaux
                            double plank = MathUtil.mod(up * 4, 1.0);
                            boolean gap = plank > 0.86;
                            Color wood = Color.web("#8a6034")
                                    .deriveColor(0, 1, 0.85 + grain * 0.3, 1);
                            double post = MathUtil.mod(along * 6, 1.0);
                            if (post < 0.10) wood = Color.web("#6b4a28");
                            color = shade(gap ? Color.web("#2a2a26") : wood,
                                    0.9 + grain * 0.2);
                            h = gap ? 0.1 : (post < 0.10 ? 0.95 : 0.7);
                        }
                        case TALUS -> {
                            Color earth = theme.groundB.deriveColor(0, 1, 0.9 + grain * 0.35, 1);
                            color = shade(earth, 0.78 + up * 0.4);
                            h = 0.45 + grain * 0.4;
                            // touffes seches sur la crete
                            if (up < 0.18 && grain > 0.25) {
                                color = shade(Color.web("#9a8f52"), 0.9 + grain * 0.3);
                            }
                        }
                        case PNEUS -> {
                            // rangees de pneus empiles
                            double col = MathUtil.mod(along * 8, 1.0) - 0.5;
                            double row = MathUtil.mod(up * 4, 1.0) - 0.5;
                            double r = Math.sqrt(col * col + row * row);
                            boolean tyre = r < 0.42;
                            boolean hole = r < 0.16;
                            color = shade(hole ? Color.web("#0e0e10")
                                    : tyre ? Color.web("#23232a") : Color.web("#17171b"),
                                    0.85 + grain * 0.25);
                            h = hole ? 0.15 : (tyre ? 0.9 - r : 0.3);
                        }
                        default -> {
                            Color snow = Color.web("#eef5fb")
                                    .deriveColor(0, 1, 0.92 + grain * 0.22, 1);
                            color = shade(snow, 0.80 + up * 0.32);
                            h = 0.5 + grain * 0.45;
                            if (up > 0.82) color = shade(Color.web("#b9cfe0"), 0.9);
                        }
                    }
                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image barrierBump(Barrier barrier, Theme theme) {
        barrier(barrier, theme);
        return barrier == Barrier.RAIL ? wallBump(theme) : bumpOf("bar-" + barrier.name());
    }

    public static Image road(Theme theme) {
        return bake("road-" + theme.name(), 512, 2.4, (size, y0, y1, argb, height) -> {
            double sc = 128.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double fx = x * sc, fy = y * sc;

                    // grain du bitume : deux echelles + gravillons
                    double coarse = N.fbm(fx * 0.10, fy * 0.10, 3);
                    double fine = N.perlin(fx * 0.85, fy * 0.85);
                    double gravel = N.perlin(fx * 2.6 + 40, fy * 2.6 + 17);
                    double h = 0.50 + coarse * 0.16 + fine * 0.10;
                    if (gravel > 0.55) h += (gravel - 0.55) * 0.9;

                    double light = 0.66 + h * 0.36;
                    int color = shade(theme.roadA, light);

                    // gravillons plus clairs, comme dans un enrobe use
                    if (gravel > 0.66) {
                        color = mix(color, Color.web("#b9bcc0"), (gravel - 0.66) * 1.1);
                    }

                    // quelques fissures, rares et discretes : a haute frequence
                    // elles formaient un filet visible sur toute la piste
                    double crack = 1 - Math.abs(N.perlin(fx * 0.055 + 90, fy * 0.055 + 5));
                    if (crack > 0.992) {
                        double deep = (crack - 0.992) / 0.008;
                        color = mix(color, theme.roadB, deep * 0.55);
                        h -= deep * 0.35;
                    }

                    // marquages : lignes de rive et axe median discontinu
                    double edge = Math.min(u, 1 - u);
                    double paint = 0;
                    if (edge < 0.030) paint = 1;
                    else if (edge < 0.038) paint = (0.038 - edge) / 0.008;
                    double dash = (y % (size / 2)) / (double) (size / 2);
                    if (Math.abs(u - 0.5) < 0.011 && dash < 0.42) paint = Math.max(paint, 1);

                    if (paint > 0) {
                        double wear = 0.86 + fine * 0.10;
                        color = mix(color, Color.web("#f4f4ee"),
                                Math.clamp(paint * wear, 0, 1));
                        h += paint * 0.22;
                    }

                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image roadBump(Theme theme) {
        road(theme);
        return bumpOf("road-" + theme.name());
    }

    // ---------------------------------------------------------------- vibreur

    /**
     * Vibreur rouge et blanc. Le champ de hauteur dessine une cannelure : le
     * relief se voit vraiment quand on frotte dessus.
     */
    public static Image kerb() {
        return bake("kerb", 256, 3.2, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double v = y / (double) size;
                    boolean red = v < 0.5;
                    Color base = red ? Color.web("#d8382a") : Color.web("#f2f2ee");

                    // profil bombe en travers + usure
                    double dome = Math.sin(Math.PI * Math.clamp(u, 0, 1));
                    double grit = N.perlin(x * sc * 0.12, y * sc * 0.12) * 0.045;
                    double h = dome * 0.75 + grit;

                    double light = 0.76 + dome * 0.34 + grit;
                    int color = shade(base, light);
                    // bord interieur sali
                    if (u < 0.10) color = shade(base, light * 0.72);
                    // frontiere adoucie entre les deux bandes
                    double band = Math.abs(v - 0.5) < 0.012 ? 0.6 : 1.0;
                    if (band < 1) color = shade(base, light * band);

                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    /** Vibreur bleu et blanc : l'autre grand classique des circuits. */
    public static Image kerbAlt() {
        return bake("kerb-alt", 256, 3.2, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double v = y / (double) size;
                    Color base = v < 0.5 ? Color.web("#2f5fa8") : Color.web("#f2f2ee");
                    double dome = Math.sin(Math.PI * Math.clamp(u, 0, 1));
                    double grit = N.perlin(x * sc * 0.12, y * sc * 0.12) * 0.045;
                    double light = 0.76 + dome * 0.34 + grit;
                    if (u < 0.10) light *= 0.80;
                    argb[y * size + x] = shade(base, light);
                    height[y * size + x] = dome * 0.75 + grit;
                }
            }
        });
    }

    public static Image kerbAltBump() {
        kerbAlt();
        return bumpOf("kerb-alt");
    }

    /** Degagement peint en vert, comme sur les circuits modernes. */
    public static Image runoff() {
        return bake("runoff", 256, 1.2, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double grain = N.fbm(x * sc * 0.10, y * sc * 0.10, 3);
                    double light = 0.88 + grain * 0.16;
                    int color = shade(Color.web("#2f7d52"), light);
                    // liseres blancs de chaque cote
                    if (u < 0.07 || u > 0.93) color = shade(Color.web("#eef0ec"), light);
                    argb[y * size + x] = color;
                    height[y * size + x] = 0.5 + grain * 0.2;
                }
            }
        });
    }

    public static Image runoffBump() {
        runoff();
        return bumpOf("runoff");
    }

    /**
     * Bac a gravier : des cailloux clairs, en relief marque.
     *
     * <p>Il etait beige sable pour tous les themes de plein air. Sur la
     * Banquise, ou il borde une sortie de virage sur trois, ces grandes plaques
     * ocre etaient la seule couleur chaude du tour et se lisaient comme du
     * sable pose sur la neige. Le degagement d'une piste de neige est fait de
     * la meme matiere que la piste, en vrac : des blocs de neige tassee et de
     * glace concassee. La cle du cache separe donc la neige du reste, et les
     * quatre autres themes gardent la texture au pixel pres.
     */
    public static Image gravel(Theme theme) {
        boolean neige = theme == Theme.SNOW;
        Color fond = neige ? Color.web("#b7c8d4") : Color.web("#c9bda4");
        Color eclat = neige ? Color.web("#e4eef5") : Color.web("#e3dbc8");
        return bake(neige ? "gravel-neige" : "gravel", 512, 2.8,
                (size, y0, y1, argb, height) -> {
            double sc = 128.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double fx = x * sc, fy = y * sc;
                    double stones = N.perlin(fx * 1.7, fy * 1.7);
                    double coarse = N.fbm(fx * 0.25, fy * 0.25, 3);
                    double h = 0.45 + stones * 0.42 + coarse * 0.18;
                    double light = 0.72 + h * 0.5;
                    int color = shade(fond, light);
                    if (stones > 0.55) color = shade(eclat, light);
                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image gravelBump(Theme theme) {
        gravel(theme);
        return bumpOf(theme == Theme.SNOW ? "gravel-neige" : "gravel");
    }

    /** Trainee de gomme en sortie de virage, bords fondus. */
    public static Image rubber() {
        return draw("rubber", 128, 256, (g, w, h, rnd) -> {
            g.setFill(Color.TRANSPARENT);
            g.fillRect(0, 0, w, h);
            for (int i = 0; i < 26; i++) {
                double x = 8 + rnd.nextDouble() * (w - 16);
                double alpha = 0.06 + rnd.nextDouble() * 0.13;
                g.setStroke(Color.color(0.06, 0.06, 0.07, alpha));
                g.setLineWidth(3 + rnd.nextInt(9));
                g.strokeLine(x, 0, x + (rnd.nextDouble() - 0.5) * 14, h);
            }
        });
    }

    /** Plaque d'egout, posee en bordure de chaussee. */
    public static Image drain() {
        return draw("drain", 64, 64, (g, w, h, rnd) -> {
            g.setFill(Color.web("#3c4045"));
            g.fillRect(0, 0, w, h);
            g.setStroke(Color.web("#22262b"));
            g.setLineWidth(3);
            g.strokeRect(2, 2, w - 4, h - 4);
            g.setFill(Color.web("#2a2e33"));
            for (int i = 0; i < 5; i++) {
                g.fillRect(9, 8 + i * 10, w - 18, 5);
            }
        });
    }

    public static Image kerbBump() {
        kerb();
        return bumpOf("kerb");
    }

    // -------------------------------------------------------------------- sol

    public static Image ground(Theme theme) {
        return bake("ground-" + theme.name(), 512, 1.7, (size, y0, y1, argb, height) -> {
            double sc = 128.0 / size;
            long seed = theme.ordinal() * 7717L;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double fx = x * sc, fy = y * sc;
                    double patch = N.fbm(fx * 0.055, fy * 0.055, 4);
                    double detail = N.fbm(fx * 0.42, fy * 0.42, 2);
                    double h = 0.5 + patch * 0.30 + detail * 0.18;
                    int color;

                    switch (theme) {
                        case SNOW -> {
                            double dune = Math.sin(fx * 0.06 + patch * 2.4) * 0.5 + 0.5;
                            h = 0.5 + patch * 0.34 + dune * 0.10;
                            // on garde du bleu dans les creux, sinon la neige
                            // se confond completement avec le ciel
                            double light = 0.30 + patch * 0.55 + dune * 0.30;
                            color = mix(theme.groundB, theme.groundA, Math.clamp(light, 0, 1));
                            // paillettes de givre
                            if (detail > 0.66 && dither(x, y, seed) < 0.25) color = argb(1, 1, 1);
                        }
                        case DESERT -> {
                            double ripple = Math.sin((fx + patch * 26) * 0.55) * 0.5 + 0.5;
                            h = 0.45 + ripple * 0.42 + detail * 0.12;
                            double light = 0.80 + ripple * 0.30 + detail * 0.10;
                            color = shade(theme.groundA, light);
                            if (detail > 0.72) color = shade(theme.groundB, 0.9);
                        }
                        case VOLCANO -> {
                            double crack = N.ridged(fx * 0.10, fy * 0.10, 3);
                            h = 0.55 + patch * 0.28 - Math.max(0, crack - 0.80) * 2.2;
                            double light = 0.70 + patch * 0.34;
                            color = shade(theme.groundA, light);
                            // Braise au fond des fissures. Le seuil descend de
                            // 0,90 a 0,84 et la fusion monte a 0,95 : a
                            // l'ancien reglage la lave n'occupait qu'un pixel
                            // sur mille et se noyait dans le grain — sur les
                            // captures du sol, aucune fissure n'etait visible
                            // passe cinq metres, et c'est pour cela que le
                            // terrain se lisait comme un labour.
                            if (crack > 0.84) {
                                color = mix(theme.groundB, Color.web("#ff5c14"),
                                        Math.clamp((crack - 0.84) * 6, 0, 0.95));
                            }
                        }
                        case BILLARD -> {
                            // le feutre du hors-piste : meme poil, un ton plus sombre
                            double poil = N.perlin(fx * 2.2, fy * 0.6);
                            h = 0.5 + poil * 0.06;
                            color = shade(theme.groundA, 0.86 + poil * 0.18 + detail * 0.08);
                        }
                        case BAR, SALON -> {
                            // lames de parquet ou trame de laine, selon le decor
                            double lame = Math.floor(fy / 34);
                            double veine = N.fbm(fx * 0.6, fy * 2.8 + lame * 13, 3);
                            double joint = Math.min(fy / 34 - lame, 1 - (fy / 34 - lame));
                            h = 0.55 + veine * 0.12;
                            color = shade(theme.groundA, 0.80 + veine * 0.32);
                            if (theme == Theme.BAR && joint < 0.04) {
                                color = shade(theme.groundB, 0.75);
                                h -= 0.2;
                            }
                        }
                        case DONJON -> {
                            // dalles usees, joints creuses et taches d'humidite
                            double cx2 = fx / 58, cz2 = fy / 58;
                            double jx = Math.min(cx2 - Math.floor(cx2), 1 - (cx2 - Math.floor(cx2)));
                            double jz = Math.min(cz2 - Math.floor(cz2), 1 - (cz2 - Math.floor(cz2)));
                            double joint = Math.min(jx, jz);
                            h = 0.6 + detail * 0.10;
                            color = shade(theme.groundA, 0.70 + patch * 0.36);
                            if (patch > 0.62) color = mix(color, Color.web("#3f5a3a"), 0.35);
                            if (joint < 0.05) {
                                color = shade(theme.groundB, 0.7);
                                h -= 0.3;
                            }
                        }
                        default -> {
                            double light = 0.74 + patch * 0.34 + detail * 0.16;
                            color = mix(theme.groundB, theme.groundA, Math.clamp(light, 0, 1));
                            // touffes plus claires et quelques fleurs
                            if (detail > 0.55) color = shade(theme.groundA, 1.10 + detail * 0.2);
                            double flower = N.perlin(fx * 3.1 + 61, fy * 3.1 + 23);
                            if (flower > 0.80) {
                                color = theme == Theme.FOREST
                                        ? mix(theme.groundA, Color.web("#d9e26a"), 0.6)
                                        : mix(theme.groundA, Color.web("#f2e9a0"), 0.7);
                            }
                        }
                    }

                    argb[y * size + x] = color;
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image groundBump(Theme theme) {
        ground(theme);
        return bumpOf("ground-" + theme.name());
    }

    /**
     * Bordure betonnee : ce qui remplace le vibreur dans les lignes droites.
     *
     * <p>Le beton est gris partout sauf sur la neige, ou un lisere #b9bdb8
     * longeait deux kilometres et demi de banquise : la seule dalle coulee du
     * circuit. La bande y est de neige tassee, plus froide et plus claire que
     * le beton, mais toujours plus sombre que le champ de neige — c'est cet
     * ecart-la qui souligne le bord de la chaussee.
     */
    public static Image verge(Theme theme) {
        Color beton = theme == Theme.SNOW ? Color.web("#c6d9e6") : Color.web("#b9bdb8");
        return bake("verge-" + theme.name(), 256, 1.6, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double grain = N.fbm(x * sc * 0.09, y * sc * 0.09, 3);
                    double h = 0.55 + grain * 0.20;
                    double light = 0.86 + grain * 0.20;
                    // joints de dalle tous les quarts de texture
                    double joint = MathUtil.mod(y / (double) size * 4, 1.0);
                    if (joint < 0.02) {
                        light *= 0.72;
                        h -= 0.35;
                    }
                    if (u < 0.08) light *= 0.80;
                    argb[y * size + x] = shade(beton, light);
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image vergeBump(Theme theme) {
        verge(theme);
        return bumpOf("verge-" + theme.name());
    }

    /** Emplacement de grille : un rectangle blanc peint, interieur transparent. */
    public static Image gridSlot(int number) {
        return draw("gridslot-" + number, 256, 256, (g, w, h, rnd) -> {
            g.setStroke(Color.web("#f2f2ee", 0.88));
            g.setLineWidth(14);
            g.strokeRect(18, 10, w - 36, h - 40);
            g.setFill(Color.web("#f2f2ee", 0.88));
            g.setFont(Font.font("Monospaced", FontWeight.BOLD, 74));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(String.valueOf(number), w / 2, h - 34);
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.BASELINE);
        });
    }

    /** Rustine de bitume : une reparation, avec son joint. */
    public static Image roadPatch(Theme theme) {
        return draw("patch-" + theme.name(), 128, 128, (g, w, h, rnd) -> {
            g.setFill(theme.roadA.deriveColor(0, 1, 0.78, 1));
            g.fillRect(0, 0, w, h);
            g.setStroke(theme.roadA.deriveColor(0, 1, 1.25, 1));
            g.setLineWidth(5);
            g.strokeRect(3, 3, w - 6, h - 6);
            g.setFill(theme.roadA.deriveColor(0, 1, 0.68, 1));
            for (int i = 0; i < 120; i++) {
                g.fillRect(rnd.nextInt(w), rnd.nextInt(h), 2 + rnd.nextInt(4), 2);
            }
        });
    }

    /** Chevrons d'avertissement peints avant un freinage. */
    public static Image chevron() {
        return draw("chevron", 256, 128, (g, w, h, rnd) -> {
            g.setFill(Color.web("#f2f2ee"));
            g.fillRect(0, 0, w, h);
            g.setFill(Color.web("#1a1a1a"));
            for (int i = -1; i < 5; i++) {
                double x = i * 56.0;
                g.fillPolygon(new double[]{x, x + 28, x + 56, x + 28},
                        new double[]{h, 0, h, h}, 4);
            }
        });
    }

    /** Voile sombre au pied des murets : un simple degrade d'opacite. */
    public static Image contactShadow() {
        return draw("contact", 8, 64, (g, w, h, rnd) -> {
            g.setFill(new LinearGradient(0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(0, 0, 0, 0.42)),
                    new Stop(0.45, Color.color(0, 0, 0, 0.16)),
                    new Stop(1, Color.color(0, 0, 0, 0))));
            g.fillRect(0, 0, w, h);
        });
    }

    /** Panneau de distance avant un virage (300, 200, 100 m). */
    public static Image distanceBoard(int meters) {
        return draw("board-" + meters, 256, 256, (g, w, h, rnd) -> {
            g.setFill(Color.web("#f4f4f0"));
            g.fillRect(0, 0, w, h);
            g.setStroke(Color.web("#1a1a1a"));
            g.setLineWidth(12);
            g.strokeRect(6, 6, w - 12, h - 12);
            g.setFill(Color.web("#1a1a1a"));
            int bars = meters / 100;
            for (int i = 0; i < bars; i++) {
                g.fillRect(w * 0.18, h * (0.20 + i * 0.20), w * 0.64, h * 0.11);
            }
            g.setFont(Font.font("Monospaced", FontWeight.BOLD, 52));
            g.setTextAlign(TextAlignment.CENTER);
            g.fillText(meters + "m", w / 2, h * 0.94);
            g.setTextAlign(TextAlignment.LEFT);
        });
    }

    // ------------------------------------------------------------------ muret

    public static Image wall(Theme theme) {
        // le ruban du muret est plaque avec u = hauteur (0 en haut) et
        // v = distance le long de la piste : la texture suit cette convention
        return bake("wall-" + theme.name(), 256, 2.6, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double up = x / (double) size;
                    double along = y / (double) size;

                    // glissiere ondulee : nervures horizontales, comme un rail
                    // de securite ; verticales, elles scintillaient a distance
                    double rib = Math.sin(up * Math.PI * 2 * 2.5) * 0.5 + 0.5;
                    double h = 0.35 + rib * 0.55;

                    boolean red = (y * 4 / size) % 2 == 0;
                    Color base = red ? Color.web("#cf3b2c") : Color.web("#eceae4");
                    double light = 0.74 + rib * 0.38;

                    // main courante en haut, ombre au pied
                    if (up < 0.11) {
                        light *= 1.15;
                        h += 0.30;
                    }
                    if (up > 0.88) light *= 0.50;
                    // montants verticaux entre les panneaux
                    double post = MathUtil.mod(along * 4, 1.0);
                    if (post < 0.06) {
                        light *= 0.70;
                        h += 0.30;
                    }
                    light += N.perlin(x * sc * 0.3, y * sc * 0.3) * 0.06;

                    argb[y * size + x] = shade(base, light);
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image wallBump(Theme theme) {
        wall(theme);
        return bumpOf("wall-" + theme.name());
    }

    // ------------------------------------------------------------------- pneu

    public static Image tyre() {
        return bake("tyre", 256, 3.0, (size, y0, y1, argb, height) -> {
            // motif exprime a la resolution de reference : il garde la meme
            // taille a l'ecran quel que soit le niveau de detail
            double sc = 256.0 / size;
            for (int y = y0; y < y1; y++) {
                for (int x = 0; x < size; x++) {
                    double u = x / (double) size;
                    double v = y / (double) size;
                    // paves de sculpture en quinconce
                    double row = Math.floor(v * 12);
                    double shift = (row % 2 == 0) ? 0 : 0.5;
                    double bx = MathUtil.mod(u * 6 + shift, 1.0);
                    double by = MathUtil.mod(v * 12, 1.0);
                    boolean block = bx > 0.14 && bx < 0.86 && by > 0.18 && by < 0.82;
                    double h = block ? 0.85 : 0.25;
                    double light = block ? 0.95 : 0.55;
                    light += N.perlin(x * sc * 0.5, y * sc * 0.5) * 0.06;
                    argb[y * size + x] = shade(Color.web("#232326"), light);
                    height[y * size + x] = h;
                }
            }
        });
    }

    public static Image tyreBump() {
        tyre();
        return bumpOf("tyre");
    }

    // ------------------------------------------------------ textures dessinees

    private interface Painter {
        void paint(GraphicsContext g, int w, int h, Random rnd);
    }

    /**
     * Texture peinte au Canvas.
     *
     * {@code Canvas.snapshot} n'existe que sur le fil JavaFX : ces textures ne
     * peuvent pas etre preparees en tache de fond, d'ou le cache — chacune
     * n'est peinte qu'une fois pour toute la partie.
     */
    private static Image draw(String key, int w, int h, Painter painter) {
        synchronized (LOCK) {
            Image cached = DRAWN.get(key);
            if (cached != null) return cached;
            Image img = javafx.application.Platform.isFxApplicationThread()
                    ? paint(key, w, h, painter)
                    : onFxThread(() -> paint(key, w, h, painter));
            DRAWN.put(key, img);
            return img;
        }
    }

    private static Image paint(String key, int w, int h, Painter painter) {
        Canvas canvas = new Canvas(w, h);
        GraphicsContext g = canvas.getGraphicsContext2D();
        painter.paint(g, w, h, new Random(key.hashCode()));
        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return canvas.snapshot(sp, new WritableImage(w, h));
    }

    /**
     * Execute la peinture sur le fil JavaFX et attend le resultat.
     *
     * Le constructeur du decor tourne en tache de fond, mais {@code snapshot}
     * n'existe que sur le fil JavaFX. L'attente est sans risque de blocage
     * mutuel tant que le fil JavaFX, lui, n'attend jamais la tache de fond —
     * il anime l'ecran de chargement et se contente de la surveiller.
     */
    private static Image onFxThread(java.util.concurrent.Callable<Image> task) {
        java.util.concurrent.FutureTask<Image> job = new java.util.concurrent.FutureTask<>(task);
        javafx.application.Platform.runLater(job);
        try {
            return job.get(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("peinture de texture interrompue", e);
        } catch (Exception e) {
            throw new IllegalStateException("peinture de texture impossible", e);
        }
    }

    public static Image checker() {
        return draw("checker", 256, 256, (g, w, h, rnd) -> {
            int cells = 8;
            double c = (double) w / cells;
            for (int y = 0; y < cells; y++) {
                for (int x = 0; x < cells; x++) {
                    g.setFill(((x + y) % 2 == 0) ? Color.web("#f5f5f5") : Color.web("#141414"));
                    g.fillRect(x * c, y * c, c, c);
                }
            }
            g.setFill(Color.rgb(0, 0, 0, 0.10));
            for (int i = 0; i < 500; i++) {
                g.fillRect(rnd.nextInt(w), rnd.nextInt(h), 2, 2);
            }
        });
    }

    /** Ciel : degrade, soleil, nuages en bandes, horizon accorde au sol. */
    public static Image sky(Theme theme) {
        if (theme.indoor) return ceiling(theme);
        return draw("sky-" + theme.name(), 1024, 1024, (g, w, h, rnd) -> {
            LinearGradient grad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, theme.skyHigh.deriveColor(0, 1, 0.88, 1)),
                    new Stop(0.16, theme.skyHigh),
                    new Stop(0.36, theme.skyLow),
                    new Stop(0.472, theme.skyLow.deriveColor(0, 0.72, 1.10, 1)),
                    new Stop(0.492, theme.skyLow.deriveColor(0, 0.35, 1.15, 1)),
                    new Stop(0.512, theme.groundB.deriveColor(0, 0.42, 1.05, 1)),
                    new Stop(0.62, theme.groundB.deriveColor(0, 0.60, 0.90, 1)),
                    new Stop(1.00, theme.groundB.deriveColor(0, 0.70, 0.55, 1)));
            g.setFill(grad);
            g.fillRect(0, 0, w, h);

            // soleil et sa couronne
            double sunX = w * 0.68, sunY = h * 0.20;
            Color sunCore = theme == Theme.VOLCANO ? Color.web("#ffd39a") : Color.web("#fffdf0");
            g.setFill(new RadialGradient(0, 0, sunX, sunY, w * 0.16, false, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(1, 1, 1, 0.55)),
                    new Stop(1, Color.color(1, 1, 1, 0))));
            g.fillOval(sunX - w * 0.16, sunY - w * 0.16, w * 0.32, w * 0.32);
            g.setFill(sunCore);
            g.fillOval(sunX - w * 0.022, sunY - w * 0.022, w * 0.044, w * 0.044);

            // nuages : bandes etirees, plus denses vers l'horizon
            Color cloud = theme == Theme.VOLCANO
                    ? Color.web("#40302a") : Color.web("#ffffff");
            for (int i = 0; i < 44; i++) {
                double t = rnd.nextDouble();
                double cy = h * (0.06 + t * 0.34);
                double alpha = (theme == Theme.VOLCANO ? 0.32 : 0.50) * (0.35 + t * 0.65);
                double cw = w * (0.06 + rnd.nextDouble() * 0.20);
                double ch = cw * (0.07 + rnd.nextDouble() * 0.07);
                double cx = rnd.nextDouble() * w;
                g.setFill(Color.color(cloud.getRed(), cloud.getGreen(), cloud.getBlue(), alpha));
                g.fillOval(cx - cw / 2, cy - ch / 2, cw, ch);
                g.fillOval(cx - cw / 3, cy - ch * 1.5, cw * 0.6, ch * 1.1);
                g.fillOval(cx + cw / 6, cy - ch * 1.1, cw * 0.45, ch * 0.9);
            }

            // voile de brume juste au-dessus de l'horizon
            g.setFill(new LinearGradient(0, 0.40, 0, 0.50, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.color(1, 1, 1, 0)),
                    new Stop(1, Color.color(1, 1, 1, theme == Theme.VOLCANO ? 0.16 : 0.28))));
            g.fillRect(0, h * 0.40, w, h * 0.10);
        });
    }

    /** Degrade vertical des reliefs lointains : sombre en bas, noye en haut. */
    /**
     * Plafond d'un decor d'interieur.
     *
     * Ni soleil ni nuages : une penombre qui descend vers le mur du fond, et
     * quelques taches claires pour suggerer des lampes. Le degrade suit le meme
     * decoupage que le ciel — voute en haut, horizon au milieu, sol en bas —
     * pour que le raccord avec le terrain reste invisible.
     */
    private static Image ceiling(Theme theme) {
        return draw("ceil-" + theme.name(), 1024, 1024, (g, w, h, rnd) -> {
            LinearGradient grad = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.00, theme.skyHigh.deriveColor(0, 1, 0.55, 1)),
                    new Stop(0.22, theme.skyHigh),
                    new Stop(0.42, eclaircie(theme.skyLow, 1, 1.6, 0.45)),
                    // le mur du fond capte la lumiere de la piece : sans cette
                    // eclaircie, le haut du champ de vision se confondait avec
                    // le sol des qu'on regardait a hauteur de kart
                    new Stop(0.492, eclaircie(theme.skyLow, 0.85, 3.0, 0.58)),
                    new Stop(0.512, theme.groundB.deriveColor(0, 0.9, 0.8, 1)),
                    new Stop(1.00, theme.groundB.deriveColor(0, 0.9, 0.5, 1)));
            g.setFill(grad);
            g.fillRect(0, 0, w, h);

            // Le donjon est la seule piece dont le plafond est de la pierre, et
            // c'est le degrade qui le trahissait bien avant l'absence d'arcs :
            // un ciel s'eclaircit du zenith vers l'horizon, une voute non. Du
            // bleu nuit degrade au-dessus de la tete se lit comme une nuit
            // claire, quoi qu'on peigne dessus. On rabat donc la pierre par
            // dessus, presque unie, et on la laisse s'effacer avant le mur du
            // fond pour que le raccord reste invisible.
            if (theme == Theme.DONJON) {
                Color pierre = theme.roadB.deriveColor(0, 0.6, 1.05, 1);
                g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0.00, pierre.deriveColor(0, 1, 0.86, 1)),
                        new Stop(0.55, pierre),
                        new Stop(0.86, pierre.deriveColor(0, 1, 1.05, 1)),
                        new Stop(1.00, Color.color(pierre.getRed(), pierre.getGreen(),
                                pierre.getBlue(), 0))));
                g.fillRect(0, 0, w, h * 0.47);

                // taches d'humidite et de suie : sans elles la pierre unie
                // redevient un aplat, et un aplat au-dessus de la tete ne se
                // lit pas davantage comme une voute qu'un degrade
                for (int i = 0; i < 14; i++) {
                    double cx = w * rnd.nextDouble();
                    double cy = h * 0.42 * rnd.nextDouble();
                    double r = w * (0.06 + rnd.nextDouble() * 0.10);
                    g.setFill(new RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.color(0, 0, 0, 0.13)),
                            new Stop(1, Color.color(0, 0, 0, 0))));
                    g.fillOval(cx - r, cy - r, r * 2, r * 2);
                }
            }

            // halos de lampes, poses au plafond
            for (int i = 0; i < 5; i++) {
                double cx = w * (0.08 + i * 0.21 + rnd.nextDouble() * 0.05);
                double cy = h * (0.08 + rnd.nextDouble() * 0.14);
                double r = w * (0.05 + rnd.nextDouble() * 0.04);
                g.setFill(new RadialGradient(0, 0, cx, cy, r, false, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.color(1, 0.94, 0.78, 0.55)),
                        new Stop(1, Color.color(1, 0.94, 0.78, 0))));
                g.fillOval(cx - r, cy - r, r * 2, r * 2);
            }

            // Suspensions du salon.
            //
            // Les halos ci-dessus sont poses entre 8 et 22 % de la hauteur,
            // c'est-a-dire entre 58 et 76 degres au-dessus de l'horizon : on ne
            // les voit jamais. Mesure faite sur une capture du Salon, le champ
            // ne montre du plafond que la bande comprise entre 34 et 44 % —
            // au-dessus, on est hors cadre ; au-dessous, le mur du fond passe
            // devant. Un plafonnier ne dit « on est dedans » que s'il tombe
            // dans cette bande-la, et il se pend a la poutre de 40 %. La cote
            // se relit sur une capture : la poutre du bas y tombe a quatre-
            // vingts pixels du haut du cadre et le faite du mur a cent
            // soixante, soit dix-sept millemes de hauteur de texture pour
            // trente pixels — la suspension tient dans cet intervalle-la.
            //
            // Cinq suspensions tous les 72 degres d'azimut : le dome ne tourne
            // pas avec la camera, et il en faut une dans le champ quel que soit
            // le cap. Aucun tirage n'est pris ici — les positions sont
            // regulieres, pour ne pas decaler les halos des trois autres
            // pieces, qui partagent ce dessin.
            if (theme == Theme.SALON) {
                for (int i = 0; i < 5; i++) {
                    double cx = w * (0.10 + i * 0.20);
                    // la tige sort de la poutre
                    g.setFill(Color.web("#8d7a5e"));
                    g.fillRect(cx - w * 0.0012, h * 0.375, w * 0.0024, h * 0.030);
                    // l'abat-jour, aplat franc : sur un plafond creme, une
                    // coupe creme ne se decoupe pas
                    g.setFill(Color.web("#d98f2e"));
                    g.fillPolygon(
                            new double[]{cx - w * 0.0038, cx + w * 0.0038,
                                    cx + w * 0.009, cx - w * 0.009},
                            new double[]{h * 0.404, h * 0.404, h * 0.427, h * 0.427}, 4);
                    // l'ampoule, et la lumiere qu'elle jette au plafond
                    g.setFill(Color.web("#ffeaa0"));
                    g.fillOval(cx - w * 0.0062, h * 0.4255, w * 0.0124, h * 0.006);
                    double r = w * 0.032;
                    g.setFill(new RadialGradient(0, 0, cx, h * 0.416, r, false, CycleMethod.NO_CYCLE,
                            new Stop(0, Color.color(1, 0.94, 0.78, 0.45)),
                            new Stop(1, Color.color(1, 0.94, 0.78, 0))));
                    g.fillOval(cx - r, h * 0.416 - r, r * 2, r * 2);
                }
            }
            // Poutres, moulures, arcs doubleaux : ce qui decoupe le haut du champ.
            //
            // Elles etaient a 18 % d'opacite, c'est-a-dire invisibles : le haut
            // du champ de vision d'une piece n'etait qu'un degrade, et rien ne
            // s'y decoupait une fois le portique de circuit automobile retire.
            // Un plafond qu'on ne voit pas n'est pas un plafond, c'est de la
            // brume — et c'est aussi ce qui donnait au fond d'une piece son air
            // de lointain de plein air.
            //
            // Les passer a 34 % de noir a regle le cas du salon et rien
            // d'autre : sur les trois autres pieces la voute est presque noire
            // (#0e1119 au donjon), et une bande sombre sur du sombre ne se voit
            // pas davantage qu'une bande pale sur du pale. Le donjon restait
            // donc un degrade bleu nuit sans arete — c'est-a-dire un ciel. Le
            // relief se dessine avec l'ombre quand le fond est clair, et avec
            // la lumiere quand il est sombre.
            // Les arcs se repartissent du zenith jusqu'a la naissance des murs,
            // vers 44 % de l'image — la ou le degrade passe au mur du fond.
            // Groupes pres du zenith ils ne servaient a rien : en conduite la
            // camera regarde droit devant, et le haut du cadre n'est deja plus
            // le sommet de la voute. Il en faut donc sur toute la hauteur pour
            // qu'il y en ait toujours un dans le champ.
            boolean sombre = theme.skyHigh.getBrightness() < 0.35;
            // Ou poser les arcs, et combien : cela se regle a la capture, parce
            // que le cadrage decide de ce qu'on voit. Groupes pres du zenith
            // (3 a 24 %) ils sont hors du champ — en conduite la camera regarde
            // droit devant, et le haut de l'image est deja a mi-hauteur de la
            // voute. Etales sur toute la hauteur a pleine force, ils se lisent
            // comme les bandes d'un chapiteau. Sept arcs discrets, une ombre
            // sans rehaut, du quart superieur jusqu'a la naissance des murs.
            double ecart = 0.052;
            for (int i = 0; i < 7; i++) {
                double by = h * (0.09 + i * ecart);
                double ep = h * 0.012;
                g.setFill(Color.color(0, 0, 0, sombre ? 0.20 : 0.34));
                g.fillRect(0, by, w, ep);
            }

            // Le donjon est la seule piece dont le plafond est un ouvrage de
            // maconnerie. Entre deux arcs, sa voute restait une surface lisse,
            // et une surface lisse au-dessus de la tete se lit comme un ciel
            // quoi qu'on fasse des arcs. Les claveaux sont decales d'un rang a
            // l'autre, comme ceux de la nef : des joints alignes du sol au
            // sommet, aucun macon n'en fait, et l'oeil le remarque.
            if (theme == Theme.DONJON) {
                // Les joints tiennent dans l'entre-deux des arcs. Debordant,
                // ils couraient d'un arc a l'autre et se lisaient non plus
                // comme des claveaux mais comme des barreaux.
                g.setFill(Color.color(0, 0, 0, 0.13));
                double pas = w / 26.0;
                for (int rang = 0; rang < 6; rang++) {
                    double y0 = h * (0.09 + rang * ecart + 0.018);
                    double haut = h * (ecart - 0.026);
                    for (double x = (rang % 2) * pas / 2; x < w; x += pas) {
                        g.fillRect(x, y0, w * 0.0016, haut);
                    }
                }
            }
        });
    }

    public static Image mountain(Theme theme) {
        return draw("mountain-" + theme.name(), 64, 256, (g, w, h, rnd) -> {
            if (theme == Theme.BILLARD) {
                billardRail(g, w, h, theme, rnd);
                return;
            }
            if (theme == Theme.DONJON) {
                donjonWall(g, w, h, theme);
                return;
            }
            if (theme.indoor) {
                // dedans, la silhouette lointaine est un mur ou un meuble : elle
                // reste plus sombre que le plafond et ne s'eclaircit pas vers le
                // haut, sinon elle se lit comme une montagne enneigee
                // le mur appartient a la famille du plafond, pas a celle du
                // sol : sinon eclaircir le sol eclaircit le mur, et le haut de
                // l'image se rapproche du bas au lieu de s'en detacher
                Color mur = theme.skyLow.deriveColor(0, 0.7, murGain(theme), 1);
                g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                        new Stop(0.0, mur.deriveColor(0, 1, 0.75, 1)),
                        new Stop(1.0, mur.deriveColor(0, 1, 1.15, 1))));
                g.fillRect(0, 0, w, h);
                // quelques bandes verticales : joints, etageres, lambris
                g.setFill(Color.color(0, 0, 0, 0.22));
                for (int i = 0; i < 5; i++) g.fillRect(w * (0.10 + i * 0.19), 0, w * 0.045, h);
                return;
            }
            Color far = theme.skyLow.deriveColor(0, 0.55, 0.95, 1);
            Color rock = theme.groundB.deriveColor(0, 0.55, 0.70, 1);
            // Le haut de la bande s'eclaircit d'ordinaire vers le blanc : c'est
            // la brume qui mange le sommet, et cela marche partout sauf sur un
            // ciel de lave, ou le meme blanc se lisait comme de la neige. Le
            // controle visuel decrivait « des sommets a neve derriere un ciel
            // de lave » ; sur le volcan le sommet reste donc une silhouette,
            // juste rougie par l'air chaud.
            // Le desert est loge a la meme enseigne depuis que son sol est
            // ocre : sur un ciel de sable, ces sommets blancs se lisaient comme
            // des neves, et un desert a montagnes enneigees se contredit tout
            // seul. Sa cime reste une silhouette, juste blondie par la brume.
            Color cime = switch (theme) {
                case VOLCANO -> far.deriveColor(0, 1.3, 0.82, 1);
                case DESERT -> far.deriveColor(0, 1.35, 0.90, 1);
                default -> far.interpolate(Color.WHITE, 0.35);
            };
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0.0, cime),
                    new Stop(0.35, far),
                    new Stop(1.0, rock)));
            g.fillRect(0, 0, w, h);
        });
    }

    /**
     * Gain de clarte du mur lointain d'une piece.
     *
     * <p>Le facteur 1,9 avait ete cale sur des pieces sombres — le comptoir
     * part d'un bleu de nuit a 14 % de brillance. Applique tel quel au salon,
     * dont le plafond part deja a 91 %, il sature : le mur devenait blanc pur
     * <b>avant meme</b> l'eclairage de la scene, et sur un cinquieme de la
     * largeur de l'image le mur et le plafond se confondaient — plus de ligne
     * de faite du tout. Compte fait sur une capture : 258 colonnes sur 1280.
     * On plafonne donc le resultat au lieu du facteur, ce qui ne change rien
     * aux pieces sombres et rend au salon sa silhouette. Le plafonnement se
     * releve a la capture : a 0,86 il restait 164 colonnes brulees, a 0,70 il
     * n'en reste plus, et le mur passe une trentaine de niveaux sous le
     * plafond — assez pour qu'une ligne de faite existe, pas assez pour qu'un
     * salon clair devienne une cave.
     */
    private static double murGain(Theme theme) {
        double b = theme.skyLow.getBrightness();
        return b <= 0 ? 1.9 : Math.min(1.9, 0.70 / b);
    }

    /**
     * Eclaircissement d'un ton de plafond, plafonne en clarte.
     *
     * <p>Meme panne que {@link #murGain}, et meme remede. Les gains du degrade
     * — 1,6 puis 3,0 — ont ete cales sur des pieces sombres, dont le plafond
     * part a 14 ou 19 % de clarte. Le salon, lui, part a 91 % : au-dessus du
     * mur du fond, les deux derniers arrets partaient a 145 et 273 % et
     * ressortaient en blanc pur, si bien que la piece avait une bande de jour
     * au ras du mur — trente colonnes sur trente mesurees a 255 sur une capture
     * a s=1015. Le plafond ne se lisait plus comme un plafond mais comme un
     * ciel couvert perce de soleil.
     *
     * <p>Le seuil est celui de la <b>capture</b>, pas du calcul : le dome porte
     * la meme image en carte diffuse et en auto-illumination, la lumiere de la
     * scene s'ajoute a l'illumination propre, et tout ce qui depasse la moitie
     * ressort blanc. Un plafonnement a 0,93 — le seul que le calcul aurait
     * demande — ne changeait pas un pixel a l'ecran, mesure faite. A 0,45 la
     * bande de jour disparait et la ligne de faite redevient nette. Le prix est
     * assume : le mur, plafonne a 0,70 par {@link #murGain}, passe desormais
     * au-dessus du plafond au lieu de rester dessous, mais il y a toujours une
     * ligne de faite, et c'est elle qui compte.
     *
     * <p>Le plafonnement ne mord que la ou la piece est deja claire : il
     * faudrait partir de 28 % de clarte pour que le premier arret bouge et de
     * 19,3 % pour le second, quand le donjon est a 18,8 %, le billard et le
     * comptoir a 14,1 %. Les trois autres pieces sont inchangees.
     */
    private static Color eclaircie(Color base, double saturation, double gain, double clarteMax) {
        double b = base.getBrightness();
        return base.deriveColor(0, saturation, b <= 0 ? gain : Math.min(gain, clarteMax / b), 1);
    }

    /**
     * Mur lointain du donjon : appareil de pierre, contreforts, bandeau.
     *
     * <p>La recette commune pose cinq refends verticaux etroits par repetition
     * de texture, soit un tous les trente pixels a mille metres. Sur le lambris
     * d'un comptoir cela travaille : on lit des lattes. Sur une nef, avec en
     * plus l'ombrage a plat qui arrondit chaque pan, on lisait une rangee de
     * tuyaux d'orgue — quatre par merlon. Il en faut donc moins et de plus
     * larges, pour qu'ils se lisent comme des contreforts et non comme des
     * tubes ; et il faut surtout qu'ils <b>s'arretent</b> : un contrefort meurt
     * sous un bandeau, il ne monte pas jusqu'aux creneaux. C'est cet arret
     * horizontal, plus que la largeur, qui casse la lecture en tuyaux.
     *
     * <p>Les assises horizontales sont a peine visibles a cette distance — un
     * metre de pierre y fait un pixel — mais elles suffisent a faire de la
     * surface un appareil plutot qu'un aplat, comme les claveaux de la voute.
     */
    private static void donjonWall(GraphicsContext g, double w, double h, Theme theme) {
        Color mur = theme.skyLow.deriveColor(0, 0.7, murGain(theme), 1);
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, mur.deriveColor(0, 1, 0.75, 1)),
                new Stop(1.0, mur.deriveColor(0, 1, 1.15, 1))));
        g.fillRect(0, 0, w, h);

        // Trois contreforts par repetition au lieu de cinq, deux fois plus
        // larges, et qui s'arretent au bandeau.
        double bandeau = h * 0.20;
        for (int i = 0; i < 3; i++) {
            double x = w * (0.08 + i * 0.33);
            double larg = w * 0.13;
            // le pilier prend la lumiere, le renfoncement la perd : un relief se
            // dessine avec les deux, alors qu'une simple rayure noire aplatit
            g.setFill(Color.color(1, 1, 1, 0.07));
            g.fillRect(x, bandeau, larg, h - bandeau);
            g.setFill(Color.color(0, 0, 0, 0.20));
            g.fillRect(x + larg, bandeau, w * 0.055, h - bandeau);
        }

        // Le bandeau : une assise saillante qui coupe net les contreforts.
        g.setFill(Color.color(0, 0, 0, 0.22));
        g.fillRect(0, bandeau, w, h * 0.012);
        g.setFill(Color.color(1, 1, 1, 0.10));
        g.fillRect(0, bandeau - h * 0.018, w, h * 0.018);

        // Assises : joints decales d'un rang a l'autre, comme la voute.
        g.setFill(Color.color(0, 0, 0, 0.10));
        double pas = h * 0.055;
        int rang = 0;
        for (double y = bandeau + pas; y < h; y += pas, rang++) {
            g.fillRect(0, y, w, h * 0.004);
        }
    }

    /**
     * Bord lointain de la table de billard : la bande, le cadre, la salle.
     *
     * <p>La recette commune aux pieces — un degrade brun plus clair que le
     * plafond, plus quelques refends verticaux pour figurer des joints — ne
     * disait rien ici. Sur un tapis, ce qui ferme l'horizon n'est ni un mur ni
     * une etagere : c'est le bourrelet de caoutchouc gaine de cuir qui borde le
     * tapis, surmonte du cadre de bois verni, et au-dela la penombre de la
     * salle. C'est la seule chose qui, vue de loin, dise au joueur qu'il roule
     * sur un meuble ; les trois autres pieces gardent la recette commune,
     * parce qu'un mur y est vraiment un mur.
     *
     * <p><b>Ce dessin suppose un ruban de hauteur constante.</b> Il l'est
     * depuis {@link Terrain#indoorCrest} : la crete d'une piece est une
     * horizontale, et la coordonnee de texture est ancree a une altitude
     * commune. Tant que c'etait faux, chaque trait horizontal retombait a une
     * altitude differente d'un pan a l'autre — une trentaine de pixels de
     * decalage a chaque changement de niveau — et il fallait n'employer que des
     * fondus larges, qui ne dessinaient plus rien. Si un jour la crete
     * redevient accidentee dedans, ce dessin redevient un escalier : ce sont
     * les aretes franches d'ici qui le paieront en premier.
     *
     * <p><b>Ou poser le dessin.</b> Deux bornes, relevees a la capture. En bas,
     * le relief lointain du tapis monte a une soixantaine de metres au-dessus
     * du pied du ruban et masque tout ce qui est peint sous v = 0,52 environ :
     * le cadre eclaire doit donc rester au-dessus. En haut, les deux anneaux
     * plus lointains, plus hauts, ne laissent depasser que leur sommet — v
     * inferieur a 0,15 pour celui de 1550 metres, a 0,12 pour celui de 2350 —
     * et tout ce qu'on peint la-haut se repeterait en second horizon flottant.
     * D'ou l'ordre : penombre jusqu'a 0,20, arete du cadre a 0,22, joue de bois
     * jusqu'a 0,46, puis le cuir, qui plonge sous l'herbe et n'apparait qu'aux
     * endroits ou le tapis est plat.
     *
     * <p>Le haut de la texture ne se fond pas en transparence. On a essaye :
     * cela assombrit le sommet du ruban au lieu de l'effacer. Mieux vaut le
     * noyer par la valeur, en le rendant aussi clair que le plafond juste
     * derriere lui.
     */
    private static void billardRail(GraphicsContext g, double w, double h, Theme theme, Random rnd) {
        // La penombre appartient a la famille du plafond, dont elle prolonge le
        // ton a l'horizon : c'est ce qui efface le raccord entre le sommet du
        // ruban et la voute. Le facteur se releve a la capture et non au calcul,
        // l'eclairage de la scene remontant la texture de moitie environ ; a 1,4
        // le sommet restait douze niveaux de luminance sous le plafond, a 1,62
        // il n'en reste quatre.
        Color penombre = theme.skyLow.deriveColor(0, 1, 1.62, 1);
        Color arete = Color.web("#8a6440");
        Color boisSombre = Color.web("#2e2015");
        Color boisClair = Color.web("#5f4229");
        Color cuirFace = Color.web("#2a1d12");
        Color cuir = Color.web("#1c140d");
        Color pied = Color.web("#12100a");
        g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.00, penombre),
                new Stop(0.20, penombre),
                // L'arete du cadre, la ou la lampe de table accroche le vernis.
                // Un trait franc de deux pour cent de l'image : c'est ce que la
                // crete horizontale a rendu possible, et c'est lui qui fait
                // basculer la lecture : une bande brune devient un meuble cire.
                new Stop(0.225, arete),
                new Stop(0.25, boisClair),
                new Stop(0.46, boisClair),
                new Stop(0.50, boisSombre),
                // Le bourrelet : plus clair que l'ombre du joint, plus sombre
                // que le bois, et il s'eteint vers le tapis.
                new Stop(0.56, cuirFace),
                new Stop(0.66, cuir),
                new Stop(1.00, pied)));
        g.fillRect(0, 0, w, h);

        // Le fil du bois court le long de la bande, donc a l'horizontale, et il
        // est peint en clair : sur un fond deja sombre une rayure noire ne se
        // voit pas, une rayure eclairee si.
        for (int i = 0; i < 26; i++) {
            double y = h * (0.26 + rnd.nextDouble() * 0.20);
            double ep = h * (0.004 + rnd.nextDouble() * 0.006);
            g.setFill(Color.color(1, 0.86, 0.66, 0.05 + rnd.nextDouble() * 0.05));
            g.fillRect(0, y, w, ep);
        }
    }

    /** Panneau publicitaire, texte ajuste a la largeur. */
    public static Image banner(String text, Color bg, Color fg) {
        return draw("banner-" + text + bg + fg, 512, 128, (g, w, h, rnd) -> {
            g.setFill(bg);
            g.fillRect(0, 0, w, h);
            g.setFill(bg.deriveColor(0, 1, 1.25, 1));
            g.fillRect(0, 0, w, h * 0.16);
            g.setFill(fg);
            g.setFont(fitFont(text, w - 44, h * 0.66));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(text, w / 2, h * 0.54);
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.BASELINE);
            g.setStroke(fg.deriveColor(0, 1, 1, 0.5));
            g.setLineWidth(7);
            g.strokeRect(4, 4, w - 8, h - 8);
        });
    }

    private static Font fitFont(String text, double maxW, double maxH) {
        double size = maxH;
        Text probe = new Text(text);
        for (int i = 0; i < 24; i++) {
            Font f = Font.font("Monospaced", FontWeight.BOLD, size);
            probe.setFont(f);
            if (probe.getLayoutBounds().getWidth() <= maxW) return f;
            size *= 0.9;
        }
        return Font.font("Monospaced", FontWeight.BOLD, size);
    }

    /** Caisse a objets. */
    public static Image itemBox() {
        return draw("itembox", 256, 256, (g, w, h, rnd) -> {
            g.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.web("#ffffff")),
                    new Stop(1, Color.web("#d5dee6"))));
            g.fillRect(0, 0, w, h);
            g.setStroke(Color.web("#f6a723"));
            g.setLineWidth(16);
            g.strokeRect(9, 9, w - 18, h - 18);
            g.setStroke(Color.web("#f6a723", 0.45));
            g.setLineWidth(6);
            g.strokeRect(28, 28, w - 56, h - 56);
            g.setFill(Color.web("#1d6fb8"));
            g.setFont(Font.font("Monospaced", FontWeight.BOLD, 180));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText("?", w / 2, h * 0.53);
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.BASELINE);
        });
    }

    /** Plaque numerotee collee sur le capot des karts. */
    public static Image numberPlate(int number, Color bg, Color fg) {
        // les deux couleurs entrent dans la cle : sans fg, deux plaques de meme
        // fond mais d'encre differente se partageraient la meme image
        return draw("plate-" + number + bg + fg, 128, 128, (g, w, h, rnd) -> {
            g.setFill(bg);
            g.fillRect(0, 0, w, h);
            g.setFill(fg);
            g.setFont(Font.font("Monospaced", FontWeight.BOLD, 96));
            g.setTextAlign(TextAlignment.CENTER);
            g.setTextBaseline(VPos.CENTER);
            g.fillText(String.valueOf(number), w / 2, h * 0.54);
            g.setTextAlign(TextAlignment.LEFT);
            g.setTextBaseline(VPos.BASELINE);
        });
    }
}
