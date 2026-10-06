package org.tuxkart.core;

import org.tuxkart.kart.KartDef;
import org.tuxkart.kart.KartRoster;
import org.tuxkart.track.TrackDef;
import org.tuxkart.track.Tracks;

/** Choix du joueur, conserves entre deux courses. */
public final class GameSettings {

    public KartDef kart = KartRoster.TUX;
    public TrackDef track = Tracks.PISTE_DE_TUX;
    public Difficulty difficulty = Difficulty.PILOTE;
    /** Initialise par l'auteur du circuit ; le joueur peut le changer dans les options. */
    public int laps = Tracks.PISTE_DE_TUX.defaultLaps;
    public int opponents = 5;
    /**
     * Detail geometrique : terrain, densite du decor, finesse des facettes.
     * Separe des textures parce que les deux ne coutent pas a la meme chose —
     * voir {@link Quality}.
     */
    public Quality polygons = Quality.QUALITE;
    /** Detail des pixels : taille des textures et anticrenelage. */
    public Quality textures = Quality.QUALITE;

    /**
     * Le plus lourd des deux axes : ce qui decrit a quel point la
     * configuration demandee est exigeante, et ce qui gouverne donc la limite
     * de cadence.
     */
    public Quality heaviestQuality() {
        return Quality.heaviest(polygons, textures);
    }

    /**
     * Limite de cadence choisie par le joueur : -1 pour suivre le niveau de
     * detail, 0 pour ne pas limiter, sinon un nombre d'images par seconde.
     */
    public int frameCap = -1;

    /** Limite effectivement appliquee, une fois le niveau de detail pris en compte. */
    public int effectiveFrameCap() {
        return frameCap < 0 ? heaviestQuality().frameCap : frameCap;
    }

    /**
     * Echelle de rendu de la scene 3D : -1 pour suivre le niveau de detail,
     * sinon une fraction de la taille de la fenetre.
     */
    public double renderScale = RenderScale.FOLLOW_QUALITY;

    /** Echelle effectivement demandee, une fois le niveau de detail pris en compte. */
    public double effectiveRenderScale() {
        return renderScale < 0 ? textures.supersample : renderScale;
    }
    public boolean sound = true;
    /**
     * Musique de fond, reglage distinct du son. Les deux ne repondent pas a la
     * meme demande : couper le son coupe aussi le moteur, qui porte
     * l'information de vitesse, alors que couper la seule musique laisse le jeu
     * entier lisible.
     */
    public boolean music = true;
    public boolean showMinimap = true;
    /** Camera : 0 = poursuite, 1 = capot, 2 = vue lointaine. */
    public int cameraMode = 0;

    public int totalKarts() {
        return opponents + 1;
    }
}
