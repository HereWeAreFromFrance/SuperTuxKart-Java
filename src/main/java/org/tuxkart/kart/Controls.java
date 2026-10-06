package org.tuxkart.kart;

/** Etat des commandes d'un kart, rempli soit par le joueur soit par l'IA. */
public final class Controls {

    /** -1 = a gauche, +1 = a droite. */
    public double steer;
    public boolean accel;
    public boolean brake;
    public boolean skid;
    public boolean wheelie;
    public boolean jump;
    public boolean fire;
    public boolean rescue;

    // Le regard vers l'arriere ne figure pas ici : il ne change rien a la
    // conduite, seulement au placement de la camera, qui lit le clavier.

    public void reset() {
        steer = 0;
        accel = brake = skid = wheelie = jump = fire = rescue = false;
    }
}
