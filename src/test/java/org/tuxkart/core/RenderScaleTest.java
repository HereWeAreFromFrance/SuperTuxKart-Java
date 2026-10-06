package org.tuxkart.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Echelle de rendu de la scene 3D et ses plafonds. */
class RenderScaleTest {

    @Test
    @DisplayName("un facteur qui tient sous les plafonds est applique tel quel")
    void facteurRespecte() {
        // 1280x800 surechantillonne deux fois : 4,1 Mpx, sous le plafond
        assertEquals(2.0, RenderScale.effective(2.0, 1280, 800), 1e-9);
        assertEquals(1.0, RenderScale.effective(1.0, 1920, 1080), 1e-9);
        assertEquals(0.75, RenderScale.effective(0.75, 1920, 1080), 1e-9);
    }

    @Test
    @DisplayName("le plafond de pixels rabote le surechantillonnage")
    void plafondDePixels() {
        // 1920x1080 a facteur 2 reclamerait 8,3 Mpx : ramene a 5 Mpx
        double e = RenderScale.effective(2.0, 1920, 1080);
        assertTrue(e < 2.0 && e > 1.5, "facteur effectif " + e);
        assertEquals(RenderScale.MAX_PIXELS, 1920 * 1080 * e * e, 1.0);
    }

    @Test
    @DisplayName("le plafond ne descend jamais sous la resolution native")
    void jamaisSousLeNatif() {
        // une fenetre enorme : les plafonds voudraient reduire sous 1, mais
        // degrader une image que le joueur n'a pas demande de degrader serait
        // pire que de rendre a la taille de la fenetre
        assertEquals(1.0, RenderScale.effective(2.0, 8000, 4000), 1e-9);
    }

    @Test
    @DisplayName("un facteur inferieur a 1 est un choix, pas une degradation a rattraper")
    void sousResolutionDemandee() {
        // le plancher vaut alors le facteur demande : sans cela la borne basse
        // depassait la borne haute et Math.clamp levait une exception, ce qui
        // faisait echouer la construction de l'ecran de course
        assertEquals(0.50, RenderScale.effective(0.50, 1920, 1080), 1e-9);
        assertEquals(0.50, RenderScale.effective(0.50, 8000, 4000), 1e-9);
        assertEquals(0.25, RenderScale.effective(0.25, 1280, 800), 1e-9);
    }

    @Test
    @DisplayName("une fenetre degeneree ne fait pas diverger le calcul")
    void fenetreDegeneree() {
        for (double w : new double[]{0, -5, 1}) {
            double e = RenderScale.effective(2.0, w, 0);
            assertTrue(Double.isFinite(e) && e >= 1.0 && e <= 2.0, "largeur " + w + " -> " + e);
        }
    }

    @Test
    @DisplayName("les paliers proposes vont du plus flou au plus fin")
    void paliersOrdonnes() {
        double[] steps = RenderScale.STEPS;
        assertEquals(RenderScale.FOLLOW_QUALITY, steps[0], 1e-9);
        for (int i = 2; i < steps.length; i++) {
            assertTrue(steps[i] > steps[i - 1], "palier " + i + " mal ordonne");
        }
        assertEquals("50 %", RenderScale.label(0.5));
        assertEquals("200 %", RenderScale.label(2.0));
    }

    @Test
    @DisplayName("le reglage suit le niveau de detail tant qu'on n'y touche pas")
    void suitLeNiveauDeDetail() {
        GameSettings s = new GameSettings();
        assertEquals(s.textures.supersample, s.effectiveRenderScale(), 1e-9);
        s.textures = Quality.FLUIDE;
        assertEquals(Quality.FLUIDE.supersample, s.effectiveRenderScale(), 1e-9);
        // c'est bien l'axe des textures qui commande, pas celui des polygones
        s.polygons = Quality.ULTRA;
        assertEquals(Quality.FLUIDE.supersample, s.effectiveRenderScale(), 1e-9);
        s.renderScale = 0.75;
        assertEquals(0.75, s.effectiveRenderScale(), 1e-9, "un choix explicite doit primer");
    }
}
