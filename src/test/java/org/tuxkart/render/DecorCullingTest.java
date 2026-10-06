package org.tuxkart.render;

import javafx.scene.Node;
import javafx.scene.shape.Box;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuxkart.FxToolkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Tri des troncons de decor par champ de vision.
 *
 * <p>Un troncon masque a tort est un trou dans le paysage, et ce genre de
 * defaut ne se voit qu'a l'endroit precis du circuit ou il se produit : les cas
 * limites se verifient donc ici, sur une geometrie choisie, plutot qu'a l'oeil.
 */
class DecorCullingTest {

    @BeforeAll
    static void demarrerToolkit() {
        assumeTrue(FxToolkit.start(), "toolkit JavaFX indisponible : tests de rendu ignores");
    }

    /** Un troncon cubique de 20 m de cote, centre en (x, z). */
    private static Node chunk(double x, double z) {
        Box b = new Box(20, 20, 20);
        b.setTranslateX(x);
        b.setTranslateZ(z);
        return b;
    }

    @Test
    @DisplayName("ce qui est devant reste, ce qui est derriere part")
    void devantEtDerriere() {
        FxToolkit.run(() -> {
            Node devant = chunk(0, 300);
            Node derriere = chunk(0, -300);
            DecorCulling c = new DecorCulling(List.of(devant, derriere),
                    DecorCulling.DEFAULT_HALF_ANGLE);

            c.update(0, 0, 0, 1);          // camera a l'origine, regardant vers +Z
            assertTrue(devant.isVisible(), "le troncon regarde a disparu");
            assertFalse(derriere.isVisible(), "le troncon dans le dos est toujours dessine");
            assertEquals(1, c.visible());
            assertEquals(2, c.total());

            // demi-tour : les roles s'echangent, y compris en marche arriere
            c.update(0, 0, 0, -1);
            assertFalse(devant.isVisible());
            assertTrue(derriere.isVisible());
        });
    }

    @Test
    @DisplayName("le cone conserve une marge sur le champ de vision reel")
    void margeSurLeChampDeVision() {
        FxToolkit.run(() -> {
            // le champ horizontal avoisine 95 degres en 16:9, soit 48 de
            // demi-angle : un troncon a 50 degres de l'axe est a l'ecran et
            // doit donc etre conserve
            double d = 400;
            Node bord = chunk(d * Math.sin(Math.toRadians(50)), d * Math.cos(Math.toRadians(50)));
            Node cote = chunk(d, 0);       // plein travers, 90 degres
            DecorCulling c = new DecorCulling(List.of(bord, cote),
                    DecorCulling.DEFAULT_HALF_ANGLE);
            c.update(0, 0, 0, 1);
            assertTrue(bord.isVisible(), "un troncon visible a l'ecran a ete masque");
            assertFalse(cote.isVisible(), "rien n'est trie sur les cotes");
        });
    }

    @Test
    @DisplayName("un troncon qui entoure la camera reste dessine")
    void troncontAutourDeLaCamera() {
        FxToolkit.run(() -> {
            // la camera est dans le troncon : son centre est derriere elle,
            // mais la moitie de son contenu est droit devant
            Box grand = new Box(300, 20, 300);
            grand.setTranslateZ(-60);
            DecorCulling c = new DecorCulling(List.of(grand), DecorCulling.DEFAULT_HALF_ANGLE);
            c.update(0, 0, 0, 1);
            assertTrue(grand.isVisible(), "le troncon sous les roues a disparu");
        });
    }

    @Test
    @DisplayName("un demi-angle nul desactive le tri")
    void triDesactivable() {
        FxToolkit.run(() -> {
            Node derriere = chunk(0, -300);
            DecorCulling c = new DecorCulling(List.of(derriere), 0);
            c.update(0, 0, 0, 1);
            assertTrue(derriere.isVisible(), "le tri s'applique alors qu'il est coupe");
            assertEquals(1, c.visible());
        });
    }

    @Test
    @DisplayName("une visee degeneree ne masque rien")
    void viseeDegeneree() {
        FxToolkit.run(() -> {
            Node devant = chunk(0, 300);
            DecorCulling c = new DecorCulling(List.of(devant), DecorCulling.DEFAULT_HALF_ANGLE);
            // oeil et point vise confondus : aucune direction exploitable, on
            // prefere tout dessiner plutot que vider l'ecran
            c.update(0, 0, 0, 0);
            assertTrue(devant.isVisible());
        });
    }

    @Test
    @DisplayName("la moyenne des troncons dessines suit les mises a jour")
    void moyenneSuivie() {
        FxToolkit.run(() -> {
            DecorCulling c = new DecorCulling(List.of(chunk(0, 300), chunk(0, -300)),
                    DecorCulling.DEFAULT_HALF_ANGLE);
            assertEquals(2, c.averageVisible(), 1e-9, "avant tout tri, tout est dessine");
            c.update(0, 0, 0, 1);
            c.update(0, 0, 0, 1);
            assertEquals(1, c.averageVisible(), 1e-9);
        });
    }
}
