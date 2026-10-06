package org.tuxkart.ui;

import javafx.scene.Parent;
import javafx.scene.input.KeyEvent;
import org.tuxkart.TuxKartApp;

/** Un ecran du jeu (menu, course, resultats...). */
public abstract class Screen {

    protected final TuxKartApp app;

    protected Screen(TuxKartApp app) {
        this.app = app;
    }

    public abstract Parent node();

    public void onShow() {
    }

    public void onHide() {
    }

    public void keyPressed(KeyEvent e) {
    }

    public void keyReleased(KeyEvent e) {
    }

    /** @param dt temps ecoule depuis la frame precedente, en secondes */
    public void update(double dt) {
    }
}
