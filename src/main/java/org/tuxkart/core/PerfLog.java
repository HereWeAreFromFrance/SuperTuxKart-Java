package org.tuxkart.core;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Journal des temps d'image, ecrit au fil de la partie.
 *
 * Une chute isolee a dix images par seconde ne dure qu'un dixieme de seconde :
 * le temps de la remarquer, elle est passee. Ce journal enregistre <b>chaque</b>
 * image pour qu'on puisse la retrouver apres coup, avec les evenements du jeu en
 * regard — franchissement de ligne, tir d'objet, sauvetage. C'est ce qui permet
 * de dire « la saccade tombe toujours au passage sur la ligne » au lieu de le
 * supposer.
 *
 * Chaque ligne est mise en forme des l'image ecoulee, mais l'ecriture sur
 * disque n'a lieu qu'une fois par seconde : c'est l'appel au systeme de
 * fichiers qui creerait les a-coups qu'on cherche a mesurer, pas le formatage.
 *
 * Rien n'est conserve au-dela du prochain vidage. Le resume final se contente
 * d'un histogramme des temps d'image — quelques dizaines de kilo-octets, quelle
 * que soit la duree de la partie — la ou garder toutes les images coutait une
 * douzaine de mega-octets par heure a l'outil meme qui traque les excursions.
 */
public final class PerfLog implements AutoCloseable {

    private static final int FLUSH_EVERY = 240;

    /** Largeur d'une case de l'histogramme, en millisecondes. */
    private static final double BIN_MS = 0.1;
    /** Au-dela de cette duree l'image est deja perdue : une seule case suffit. */
    private static final int BINS = 10_000;

    private final Path path;
    private BufferedWriter out;

    private final StringBuilder pending = new StringBuilder(FLUSH_EVERY * 40);
    private int sinceFlush;

    private final int[] histogram = new int[BINS + 1];
    private double totalMs;
    private double worstMs;
    private int slow;
    private int verySlow;
    private int count;
    private String pendingEvent;
    private double clock;
    private boolean broken;

    private PerfLog(Path path, String header) {
        this.path = path;
        try {
            Files.createDirectories(path.toAbsolutePath().getParent());
            out = Files.newBufferedWriter(path, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            out.write("# " + header);
            out.newLine();
            out.write("temps_s;image_ms;fps_rendu;evenement");
            out.newLine();
        } catch (IOException e) {
            broken = true;
        }
    }

    /**
     * Ouvre le journal, sauf si {@code -Dtuxkart.perflog=off}.
     * Par defaut le fichier est cree dans le dossier personnel.
     */
    public static PerfLog open(String header) {
        String spec = System.getProperty("tuxkart.perflog", "");
        if (spec.equalsIgnoreCase("off")) return null;
        Path target = spec.isBlank()
                ? Paths.get(System.getProperty("user.home"), "tuxkart-perf.csv")
                : Paths.get(spec);
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        PerfLog log = new PerfLog(target, stamp + " | " + header);
        if (log.broken) return null;
        System.out.println("Journal de performances : " + target.toAbsolutePath());
        return log;
    }

    public Path path() {
        return path;
    }

    /** Marque l'image suivante avec un evenement de jeu. */
    public void event(String name) {
        pendingEvent = pendingEvent == null ? name : pendingEvent + "+" + name;
    }

    public void frame(double dtSeconds, double fpsRendu) {
        if (broken) return;
        clock += dtSeconds;
        double ms = dtSeconds * 1000;

        pending.append(String.format(Locale.ROOT, "%.3f;%.2f;%.1f;%s%n",
                clock, ms, fpsRendu, pendingEvent == null ? "" : pendingEvent));
        pendingEvent = null;

        histogram[Math.clamp((int) (ms / BIN_MS), 0, BINS)]++;
        totalMs += ms;
        worstMs = Math.max(worstMs, ms);
        if (ms > 1000.0 / 30) slow++;
        if (ms > 100) verySlow++;
        count++;

        if (++sinceFlush >= FLUSH_EVERY) flush();
    }

    private void flush() {
        if (broken || out == null || pending.isEmpty()) return;
        try {
            out.write(pending.toString());
            out.flush();
            pending.setLength(0);
            sinceFlush = 0;
        } catch (IOException e) {
            broken = true;
        }
    }

    /** Resume lisible, ajoute en fin de fichier et affiche sur la console. */
    public String summary() {
        if (count == 0) return "aucune image enregistree";
        return String.format(Locale.ROOT,
                "%d images | moyenne %.1f fps | median %.1f fps | 1%% bas %.1f fps"
                        + " | pire %.1f ms | sous 30 fps : %d (%.2f %%) | au-dela de 100 ms : %d",
                count, count / (totalMs / 1000.0), 1000.0 / percentile(0.50),
                1000.0 / percentile(0.99), worstMs,
                slow, 100.0 * slow / count, verySlow);
    }

    /**
     * Temps d'image au quantile demande, lu dans l'histogramme. La case rendue
     * est celle du milieu : l'erreur ne depasse donc pas une demi-case.
     */
    private double percentile(double q) {
        int target = Math.min(count - 1, (int) (count * q));
        int seen = 0;
        for (int bin = 0; bin <= BINS; bin++) {
            seen += histogram[bin];
            if (seen > target) {
                return bin == BINS ? worstMs : (bin + 0.5) * BIN_MS;
            }
        }
        return worstMs;
    }

    @Override
    public void close() {
        flush();
        if (out == null) return;
        try {
            out.write("# " + summary());
            out.newLine();
            out.close();
        } catch (IOException _) {
            // rien a faire de plus a la fermeture
        }
        out = null;
    }
}
