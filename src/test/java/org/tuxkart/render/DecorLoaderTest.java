package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuxkart.FxToolkit;
import org.tuxkart.core.Quality;
import org.tuxkart.track.Track;
import org.tuxkart.track.Tracks;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Accrochage progressif du decor.
 *
 * <p>L'invariant qui compte : une fois le chargement termine, la scene doit
 * etre exactement celle que {@link TrackNode} a construite. Un decor accroche
 * par morceaux qui perdrait une feuille en route donnerait un circuit troue,
 * et le trou serait ailleurs a chaque circuit.
 */
class DecorLoaderTest {

    @BeforeAll
    static void demarrerToolkit() {
        assumeToolkit();
    }

    private static void assumeToolkit() {
        org.junit.jupiter.api.Assumptions.assumeTrue(FxToolkit.start(),
                "toolkit JavaFX indisponible : tests de rendu ignores");
    }

    /** Feuilles visibles de la scene. */
    private static int visibles(Node node) {
        if (node instanceof Parent p && !p.getChildrenUnmodifiable().isEmpty()) {
            int n = 0;
            for (Node child : p.getChildrenUnmodifiable()) n += visibles(child);
            return n;
        }
        return node.isVisible() ? 1 : 0;
    }

    /** Empreinte de la scene : un chemin par feuille, dans l'ordre. */
    private static List<String> shape(Node node, String path) {
        List<String> out = new ArrayList<>();
        if (node instanceof Parent p && !p.getChildrenUnmodifiable().isEmpty()) {
            int i = 0;
            for (Node child : p.getChildrenUnmodifiable()) {
                out.addAll(shape(child, path + "/" + (i++) + ":" + child.getClass().getSimpleName()));
            }
        } else {
            out.add(path);
        }
        return out;
    }

    @Test
    @DisplayName("le chargement par morceaux rend exactement la scene attendue")
    void memeSceneALaFin() {
        FxToolkit.run(() -> {
            Textures.setResolution(Quality.FLUIDE.textureSize);
            Track track = new Track(Tracks.PISTE_DE_TUX);
            List<String> attendu = shape(TrackNode.build(track, Quality.FLUIDE, 8).root(), "");

            Group decor = TrackNode.build(track, Quality.FLUIDE, 8).root();
            DecorLoader loader = new DecorLoader(decor, 0, 0, 0, 1);
            assertEquals(0, visibles(decor), "tout devrait etre masque au depart");

            int etapes = 0;
            while (!loader.done()) {
                loader.step();
                etapes++;
                assertTrue(etapes < 500, "le chargement ne se termine pas");
            }
            assertEquals(attendu, shape(decor, ""), "la structure de la scene a change");
            assertEquals(attendu.size(), visibles(decor), "des feuilles sont restees masquees");
            assertEquals(1.0, loader.progress(), 1e-9);
            assertTrue(etapes > 1, "tout a ete accroche d'un coup : rien n'est etale");
        });
    }

    @Test
    @DisplayName("la progression avance a chaque etape, sans jamais reculer")
    void progressionMonotone() {
        FxToolkit.run(() -> {
            Textures.setResolution(Quality.FLUIDE.textureSize);
            Group decor = TrackNode.build(new Track(Tracks.PISTE_DE_TUX), Quality.FLUIDE, 8).root();
            DecorLoader loader = new DecorLoader(decor, 0, 0, 0, 1);
            double last = -1;
            while (!loader.done()) {
                loader.step();
                double p = loader.progress();
                assertTrue(p >= last, "la jauge recule : " + last + " -> " + p);
                assertTrue(p >= 0 && p <= 1, "jauge hors bornes : " + p);
                last = p;
            }
        });
    }

    @Test
    @DisplayName("le decor expose des troncons a trier, avec des bornes exploitables")
    void troncontsTriables() {
        FxToolkit.run(() -> {
            Textures.setResolution(Quality.FLUIDE.textureSize);
            TrackNode.Decor decor = TrackNode.build(new Track(Tracks.PISTE_DE_TUX), Quality.FLUIDE, 8);
            DecorCulling culling = decor.culling();
            // decor par troncons de piste, plus les paves de terrain lointain
            assertTrue(culling.total() >= 6, "trop peu de troncons : " + culling.total());
            // la camera au centre du circuit en voit une partie, pas la totalite
            culling.update(0, 0, 0, 1);
            assertTrue(culling.visible() > 0, "tout le decor a disparu");
            assertFalse(culling.visible() == culling.total(),
                    "aucun troncon n'est ecarte, le tri ne sert a rien");
        });
    }
}
