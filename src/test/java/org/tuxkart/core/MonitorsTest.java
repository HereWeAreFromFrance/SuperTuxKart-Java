package org.tuxkart.core;

import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Choix du moniteur. Rectangle2D est de la geometrie pure : ces cas se
 * verifient sans demarrer JavaFX, et donc sans dependre des ecrans reellement
 * branches sur la machine qui execute les tests.
 */
class MonitorsTest {

    /** La configuration de l'auteur : le portable sous le moniteur externe. */
    private static final List<Rectangle2D> EMPILES = List.of(
            new Rectangle2D(0, 0, 1920, 1080),        // 0 : moniteur, en haut
            new Rectangle2D(0, 1080, 1920, 1080));    // 1 : portable, en bas

    private static final List<Rectangle2D> COTE_A_COTE = List.of(
            new Rectangle2D(0, 0, 1920, 1080),
            new Rectangle2D(1920, 0, 2560, 1440));

    @ParameterizedTest
    @CsvSource({
            "bas, 1", "bottom, 1",
            "haut, 0", "top, 0",
            "principal, 0", "primary, 0",
            "0, 0", "1, 1",
            // la casse et les espaces d'une variable d'environnement mal
            // recopiee ne doivent pas renvoyer sur le mauvais ecran
            "'  Bas ', 1", "HAUT, 0",
    })
    @DisplayName("les ecrans empiles se designent par le haut et le bas")
    void ecransEmpiles(String want, int expected) {
        assertEquals(expected, Monitors.pick(EMPILES, 0, want));
    }

    @ParameterizedTest
    @CsvSource({"gauche, 0", "left, 0", "droite, 1", "right, 1"})
    @DisplayName("les ecrans cote a cote se designent par la gauche et la droite")
    void ecransCoteACote(String want, int expected) {
        assertEquals(expected, Monitors.pick(COTE_A_COTE, 0, want));
    }

    @Test
    @DisplayName("sans consigne, ou avec une consigne absurde, on garde l'ecran principal")
    void repliSurLEcranPrincipal() {
        assertEquals(1, Monitors.pick(EMPILES, 1, null));
        assertEquals(1, Monitors.pick(EMPILES, 1, "  "));
        assertEquals(1, Monitors.pick(EMPILES, 1, "eDP-1"));
        // un indice hors bornes ne doit pas faire sortir du tableau
        assertEquals(1, Monitors.pick(EMPILES, 1, "7"));
        assertEquals(1, Monitors.pick(EMPILES, 1, "-1"));
        // un indice principal incoherent est ramene dans les bornes plutot que
        // de faire echouer le demarrage du jeu
        assertEquals(1, Monitors.pick(EMPILES, 99, null));
    }

    @Test
    @DisplayName("un ecran unique repond a toutes les directions")
    void ecranUnique() {
        List<Rectangle2D> seul = List.of(new Rectangle2D(0, 0, 1366, 768));
        for (String want : List.of("bas", "haut", "gauche", "droite", "principal", "0", "quoi")) {
            assertEquals(0, Monitors.pick(seul, 0, want), want);
        }
    }

    @Test
    @DisplayName("des ecrans alignes sont departages de facon stable")
    void egalitesDepartagees() {
        // meme bord superieur : « bas » ne peut pas trancher sur l'axe demande
        // et doit alors designer toujours le meme, pas un ecran au hasard
        assertEquals(1, Monitors.pick(COTE_A_COTE, 0, "bas"));
        assertEquals(0, Monitors.pick(COTE_A_COTE, 0, "haut"));
    }

    @Test
    @DisplayName("sans aucun ecran, le choix est une erreur et non un indice invalide")
    void aucunEcran() {
        assertThrows(IllegalArgumentException.class, () -> Monitors.pick(List.of(), 0, "bas"));
    }
}
