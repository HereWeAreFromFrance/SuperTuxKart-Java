package org.tuxkart;

/**
 * Point d'entree du jar auto-portant. JavaFX refuse d'etre lance depuis une
 * classe qui herite d'Application quand les modules sont sur le classpath :
 * cette petite classe intermediaire regle le probleme.
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        TuxKartApp.main(args);
    }
}
