package org.tuxkart.race;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuxkart.core.Difficulty;
import org.tuxkart.core.GameSettings;
import org.tuxkart.kart.Kart;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le test de bout en bout : huit karts, chaque circuit, chaque difficulte.
 *
 * C'est le seul qui puisse attraper un trace impraticable ou un pilote
 * automatique qui se coince. Il fait tourner la simulation complete — piste,
 * physique, IA, objets, classement — sans rien afficher : le moteur de jeu ne
 * depend pas du rendu, ce qui rend cette verification possible en quelques
 * secondes.
 */
class AiRacesEveryTrackTest {

    private static final double DT = 1.0 / 60;
    private static final double LIMITE_SECONDES = 600;

    record Cas(TrackDef track, Difficulty difficulty) {
        @Override
        public String toString() {
            return track.name + " / " + difficulty.label;
        }
    }

    static List<Cas> tousLesCas() {
        List<Cas> cas = new ArrayList<>();
        for (TrackDef t : Tracks.ALL) {
            for (Difficulty d : Difficulty.values()) cas.add(new Cas(t, d));
        }
        return cas;
    }

    /**
     * Courses deja simulees, partagees par les deux tests de la classe.
     *
     * Une course de huit karts sur deux tours represente des dizaines de
     * milliers de pas de simulation. Le test d'allure a besoin exactement des
     * memes vingt-sept courses que le test de bouclage : les rejouer doublait le
     * temps de la suite pour un resultat identique — les courses sont
     * deterministes a difficulte et circuit donnes.
     */
    private static final java.util.Map<Cas, Race> COURSES =
            java.util.Collections.synchronizedMap(new java.util.HashMap<>());

    private static Race course(Cas cas) {
        return COURSES.computeIfAbsent(cas, c -> {
            GameSettings settings = new GameSettings();
            settings.track = c.track();
            settings.difficulty = c.difficulty();
            settings.laps = 2;
            settings.opponents = 7;
            Race race = new Race(settings);
            race.enableAutopilot();
            double simule = 0;
            while (!race.allFinished() && simule < LIMITE_SECONDES) {
                race.update(DT);
                race.sounds.clear();
                simule += DT;
            }
            return race;
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tousLesCas")
    @DisplayName("tous les karts bouclent la course sans se faire secourir")
    void courseComplete(Cas cas) {
        Race race = course(cas);

        assertTrue(race.allFinished(),
                "course non terminee en " + (int) LIMITE_SECONDES + " s");

        int secours = 0;
        for (Kart k : race.karts) {
            secours += k.rescueCount;
            assertTrue(k.finished, k.def.name + " n'a pas fini");
            assertTrue(k.finishTime > 0, k.def.name + " a un temps invalide");
            assertTrue(k.bestLap < Double.MAX_VALUE, k.def.name + " n'a aucun tour chronometre");
            assertTrue(k.bestLap > 5, k.def.name + " : tour irrealiste de " + k.bestLap + " s");
        }
        assertEquals(0, secours,
                cas.track().name + " : " + secours + " sauvetage(s), un kart s'y coince");

        // le classement final doit etre trie par temps d'arrivee
        List<Kart> ordre = race.standings();
        for (int i = 1; i < ordre.size(); i++) {
            assertTrue(ordre.get(i - 1).finishTime <= ordre.get(i).finishTime,
                    "classement final incoherent en position " + i);
        }
    }

    @Test
    @DisplayName("l'allure de l'IA croit avec la difficulte")
    void laDifficulteChangeLAllure() {
        // Sur un seul circuit la mesure est trop bruitee : chaque IA tire sa
        // propre marge de talent et l'effet elastique resserre le peloton. On
        // agrege donc les neuf circuits, soit soixante-douze karts par difficulte.
        double novice = tempsMoyenSurTousLesCircuits(Difficulty.NOVICE);
        double pilote = tempsMoyenSurTousLesCircuits(Difficulty.PILOTE);
        double champion = tempsMoyenSurTousLesCircuits(Difficulty.CHAMPION);

        assertTrue(pilote < novice,
                "Pilote (" + (int) pilote + " s) devrait battre Novice (" + (int) novice + " s)");
        assertTrue(champion < pilote,
                "Champion (" + (int) champion + " s) devrait battre Pilote ("
                        + (int) pilote + " s)");
    }

    private double tempsMoyenSurTousLesCircuits(Difficulty difficulty) {
        double total = 0;
        int karts = 0;
        for (TrackDef def : Tracks.ALL) {
            Race race = course(new Cas(def, difficulty));
            for (Kart k : race.karts) {
                total += k.finished ? k.finishTime : LIMITE_SECONDES;
                karts++;
            }
        }
        return total / karts;
    }
}
