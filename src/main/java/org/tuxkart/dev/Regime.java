package org.tuxkart.dev;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Ce que valent les temps d'image <b>une fois le jeu chaud</b>.
 *
 * <p>Le resume que {@link org.tuxkart.core.PerfLog} imprime en fin de partie
 * compte depuis la premiere image. C'est ce qui a fait conclure faux trois
 * campagnes de mesure d'affilee sur une enquete de saccades : a basse densite
 * de decor, <b>tous</b> les blocages sont dans les dix premieres secondes —
 * construction des maillages, cuisson des textures, compilation a la volee du
 * code de rendu. Le compteur « images au-dela de 100 ms » y ramassait donc le
 * demarrage, et on le lisait comme un verdict sur le rendu. Deux reglages
 * qu'on croyait separes par un facteur deux ne differaient en fait que par le
 * temps de chargement.
 *
 * <p>D'ou cette lecture-ci, qui <b>ecarte les quinze premieres secondes</b> et
 * ne rend que le regime etabli : mediane, 1 % bas, images au-dela de 100 ms,
 * pire image. Quinze secondes, parce que la construction du decor du plus
 * lourd des neuf circuits en prend une dizaine et que la compilation a la volee
 * traine encore un peu apres.
 *
 * <p>Deux precautions vont avec, et elles ont coute du temps toutes les deux :
 *
 * <ul>
 *   <li>la demonstration n'ecrit son journal qu'a la fin ; sans
 *       {@code -Dtuxkart.shots}, elle tourne indefiniment et on n'obtient
 *       jamais de releve ;</li>
 *   <li>deux configurations ne se comparent que <b>mesurees en alternance dans
 *       la meme session</b>. La machine est chargee, et sa charge derive plus
 *       vite que l'ecart qu'on cherche.</li>
 * </ul>
 *
 * <p>Usage : {@code ./run.sh regime [journal...]}, le journal par defaut etant
 * {@code ~/tuxkart-perf.csv}.
 */
public final class Regime {

    /**
     * Secondes ecartees en tete de journal. Ce n'est pas un reglage de confort :
     * c'est la frontiere entre « le jeu se construit » et « le jeu dessine ».
     */
    private static final double CHAUFFE_S = 15;

    private Regime() {
    }

    public static void main(String[] args) {
        List<Path> journaux = new ArrayList<>();
        for (String arg : args) journaux.add(Paths.get(arg));
        if (journaux.isEmpty()) {
            journaux.add(Paths.get(System.getProperty("user.home"), "tuxkart-perf.csv"));
        }
        System.out.printf(Locale.ROOT, "Regime etabli : les %.0f premieres secondes sont ecartees.%n",
                CHAUFFE_S);
        for (Path journal : journaux) lis(journal);
    }

    private static void lis(Path journal) {
        List<String> lignes;
        try {
            lignes = Files.readAllLines(journal, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.out.println(nom(journal) + " : illisible (" + e.getMessage() + ")");
            return;
        }

        String entete = null;
        double[] images = new double[lignes.size()];
        int n = 0;
        for (String ligne : lignes) {
            if (ligne.startsWith("#")) {
                // l'en-tete porte le pipeline et les deux niveaux de qualite :
                // sans lui, deux releves ne se distinguent plus l'un de l'autre
                if (entete == null) entete = ligne.substring(1).trim();
                continue;
            }
            String[] champs = ligne.split(";");
            if (champs.length < 2) continue;
            try {
                double t = Double.parseDouble(champs[0]);
                if (t < CHAUFFE_S) continue;
                images[n++] = Double.parseDouble(champs[1]);
            } catch (NumberFormatException ignore) {
                // la ligne de titre des colonnes, et rien d'autre
            }
        }

        System.out.println();
        System.out.println(nom(journal) + (entete == null ? "" : "  [" + entete + "]"));
        if (n == 0) {
            System.out.println("  pas de regime etabli : le releve dure moins de "
                    + (int) CHAUFFE_S + " s");
            return;
        }

        double[] triees = java.util.Arrays.copyOf(images, n);
        java.util.Arrays.sort(triees);
        double mediane = triees[n / 2];
        // le « 1 % bas » est la millieme image la plus lente sur mille : c'est
        // elle qu'on ressent comme un a-coup, pas la moyenne
        double bas1 = triees[Math.min(n - 1, (int) (n * 0.99))];
        double pire = triees[n - 1];
        int gros = 0;
        for (int i = 0; i < n; i++) if (images[i] > 100) gros++;

        System.out.printf(Locale.ROOT,
                "  %6d img   median %6.1f fps   1%% bas %5.1f fps   >100 ms %3d   pire %6.1f ms%n",
                n, 1000 / mediane, 1000 / bas1, gros, pire);
    }

    private static String nom(Path journal) {
        String s = journal.getFileName().toString();
        return s.endsWith(".csv") ? s.substring(0, s.length() - 4) : s;
    }
}
