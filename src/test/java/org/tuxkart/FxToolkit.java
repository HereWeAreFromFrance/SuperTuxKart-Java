package org.tuxkart;

import javafx.application.Platform;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Demarre le toolkit JavaFX pour les tests de rendu.
 *
 * Ces tests ont besoin d'un vrai contexte graphique : ils construisent des
 * maillages, cuisent des textures et vont jusqu'a rendre une image. Quand
 * aucun affichage n'est disponible — machine d'integration sans ecran — le
 * toolkit ne demarre pas et les tests concernes sont <b>ignores</b> plutot que
 * mis en echec : une suite qui echoue faute de carte graphique ne dit rien sur
 * la qualite du code.
 *
 * En l'absence d'ecran, JavaFX sait aussi tourner en mode logiciel :
 * {@code -Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw}.
 */
public final class FxToolkit {

    private static Boolean available;

    private FxToolkit() {
    }

    public static synchronized boolean start() {
        if (available != null) return available;
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            available = latch.await(20, TimeUnit.SECONDS);
        } catch (IllegalStateException already) {
            // un autre test a deja demarre le toolkit
            available = true;
        } catch (Throwable t) {
            System.out.println("Toolkit JavaFX indisponible : " + t);
            available = false;
        }
        if (Boolean.TRUE.equals(available)) Platform.setImplicitExit(false);
        return available;
    }

    /** Execute une tache sur le fil JavaFX et attend son resultat. */
    public static <T> T call(Callable<T> task) {
        if (Platform.isFxApplicationThread()) {
            try {
                return task.call();
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                error.set(t);
            } finally {
                latch.countDown();
            }
        });
        try {
            if (!latch.await(60, TimeUnit.SECONDS)) {
                throw new AssertionError("delai depasse sur le fil JavaFX");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
        if (error.get() != null) throw new AssertionError(error.get());
        return result.get();
    }

    public static void run(Runnable task) {
        call(() -> {
            task.run();
            return null;
        });
    }
}
