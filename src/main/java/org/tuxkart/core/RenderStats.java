package org.tuxkart.core;

import javafx.scene.Scene;

import java.lang.reflect.Method;

/**
 * Compteur d'images fiable.
 *
 * Un {@code AnimationTimer} ne mesure que la cadence de la boucle de jeu :
 * JavaFX rend sur un fil separe, et quand ce fil prend du retard il saute des
 * images sans que la boucle ralentisse. On peut alors afficher 100 pendant que
 * l'ecran en montre dix.
 *
 * JavaFX tient pourtant la statistique juste, dans une classe interne
 * {@code com.sun.javafx.perf.PerformanceTracker}. Elle est accessible ici parce
 * que le jeu se lance avec JavaFX sur le classpath. On y accede par reflexion :
 * si l'API disparait ou si le jeu tourne sur le module path, on retombe
 * proprement sur la mesure de boucle.
 */
public final class RenderStats {

    private Object tracker;
    private Method instantFps;
    private boolean available;

    public RenderStats(Scene scene) {
        try {
            Class<?> cls = Class.forName("com.sun.javafx.perf.PerformanceTracker");
            Method getSceneTracker = cls.getMethod("getSceneTracker", Scene.class);
            tracker = getSceneTracker.invoke(null, scene);
            instantFps = cls.getMethod("getInstantFPS");
            // un appel a blanc : la premiere lecture ne veut rien dire
            instantFps.invoke(tracker);
            available = true;
        } catch (Throwable t) {
            available = false;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Nom du pipeline de rendu reellement utilise.
     *
     * C'est le diagnostic decisif quand le jeu rame : si JavaFX n'arrive pas a
     * initialiser OpenGL, il bascule sur un rendu <b>logiciel</b> et la 3D
     * s'effondre a quelques images par seconde, sans que la boucle de jeu
     * ralentisse pour autant.
     */
    public static String pipelineName() {
        try {
            Class<?> cls = Class.forName("com.sun.prism.GraphicsPipeline");
            Object pipeline = cls.getMethod("getPipeline").invoke(null);
            if (pipeline == null) return "inconnu";
            return pipeline.getClass().getSimpleName();
        } catch (Throwable t) {
            return "inconnu";
        }
    }

    /** Vrai si le rendu se fait par le processeur : la cause la plus frequente d'un jeu qui rame. */
    public static boolean isSoftwarePipeline() {
        String name = pipelineName().toLowerCase(java.util.Locale.ROOT);
        return name.contains("sw") || name.contains("j2d") || name.contains("software");
    }

    /** Images reellement rendues par seconde, ou -1 si la mesure est indisponible. */
    public double renderFps() {
        return read(instantFps);
    }

    private double read(Method m) {
        if (!available) return -1;
        try {
            return ((Number) m.invoke(tracker)).doubleValue();
        } catch (Throwable t) {
            available = false;
            return -1;
        }
    }
}
