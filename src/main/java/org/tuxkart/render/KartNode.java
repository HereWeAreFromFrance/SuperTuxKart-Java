package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import javafx.scene.transform.Translate;
import org.tuxkart.kart.Kart;
import org.tuxkart.kart.KartDef;
import org.tuxkart.kart.KartRoster;

import java.util.ArrayList;
import java.util.List;

/**
 * Modele 3D d'un kart et de son pilote.
 *
 * La coque est un maillage galbe obtenu par {@link Meshes#hull}, tout le reste
 * (roues, echappements, arceau, mascotte) est assemble a partir de primitives.
 * Le nombre de subdivisions est choisi a la main : les valeurs par defaut de
 * JavaFX (64) couteraient des milliers de triangles par boulon.
 *
 * Chaque {@link KartDef.Chassis} a sa propre table de sections, ses propres
 * roues et son propre accastillage : un buggy a cage, une monoplace a ailerons,
 * un trois-roues, un hot-rod a moteur apparent... Le squelette anime (roues qui
 * tournent, direction, feux, turbo) reste commun a tous.
 */
public final class KartNode {

    /** Style de roue, choisi par le chassis. */
    private enum Wheels { RAYONS, LISSES, CRAMPONS, CHROME }

    // ------------------------------------------------------- tables de coques
    // {z, demi-largeur, bas, haut, exposant}, de l'arriere vers l'avant.
    // L'exposant gouverne le galbe : 2 = ellipse, 6 = presque une boite.

    private static final double[][] HULL_CLASSIQUE = {
            {-1.52, 0.52, 0.34, 0.62, 2.6},
            {-1.20, 0.80, 0.25, 0.78, 3.2},
            {-0.65, 0.85, 0.19, 0.80, 3.6},
            {-0.05, 0.83, 0.18, 0.68, 3.6},
            {0.55, 0.75, 0.19, 0.58, 3.2},
            {1.15, 0.60, 0.23, 0.52, 3.0},
            {1.54, 0.32, 0.30, 0.47, 2.4},
    };

    /** Buggy : court, haut sur pattes, coque massive. */
    private static final double[][] HULL_BUGGY = {
            {-1.44, 0.54, 0.48, 0.86, 2.4},
            {-1.10, 0.80, 0.42, 1.02, 2.8},
            {-0.55, 0.86, 0.38, 1.04, 3.0},
            {0.05, 0.84, 0.36, 0.94, 3.0},
            {0.62, 0.78, 0.38, 0.84, 2.8},
            {1.16, 0.66, 0.42, 0.76, 2.6},
            {1.48, 0.44, 0.48, 0.70, 2.2},
    };

    /** Monoplace : nez effile, coque etroite plaquee au sol. */
    private static final double[][] HULL_FUSEE = {
            {-1.46, 0.42, 0.24, 0.62, 2.8},
            {-1.08, 0.58, 0.20, 0.68, 3.2},
            {-0.50, 0.60, 0.16, 0.70, 3.4},
            {0.12, 0.56, 0.15, 0.58, 3.4},
            {0.76, 0.42, 0.16, 0.46, 3.0},
            {1.32, 0.24, 0.18, 0.38, 2.6},
            {1.74, 0.11, 0.22, 0.32, 2.2},
    };

    /** Bulle : trapue et toute ronde. */
    private static final double[][] HULL_BULLE = {
            {-1.18, 0.46, 0.32, 0.70, 2.0},
            {-0.92, 0.74, 0.26, 0.90, 2.1},
            {-0.45, 0.88, 0.22, 0.98, 2.2},
            {0.05, 0.90, 0.20, 0.96, 2.2},
            {0.55, 0.84, 0.21, 0.88, 2.2},
            {1.00, 0.64, 0.26, 0.74, 2.1},
            {1.26, 0.36, 0.32, 0.62, 2.0},
    };

    /** Roadster : long capot, croupe arrondie. */
    private static final double[][] HULL_ROADSTER = {
            {-1.46, 0.56, 0.28, 0.72, 2.6},
            {-1.06, 0.78, 0.24, 0.88, 2.8},
            {-0.50, 0.82, 0.22, 0.90, 3.0},
            {0.12, 0.80, 0.22, 0.74, 3.0},
            {0.82, 0.74, 0.24, 0.64, 2.8},
            {1.36, 0.58, 0.26, 0.58, 2.6},
            {1.64, 0.34, 0.30, 0.52, 2.2},
    };

    /** Hot-rod : cuve basse, arriere large pour d'enormes gommes. */
    private static final double[][] HULL_HOTROD = {
            {-1.48, 0.58, 0.30, 0.68, 2.8},
            {-1.14, 0.84, 0.26, 0.82, 3.2},
            {-0.60, 0.88, 0.22, 0.84, 3.4},
            {-0.02, 0.82, 0.22, 0.76, 3.4},
            {0.60, 0.70, 0.24, 0.66, 3.0},
            {1.10, 0.54, 0.28, 0.58, 2.8},
            {1.42, 0.30, 0.32, 0.52, 2.2},
    };

    /** Trois-roues : chassis fuselé qui se pince vers l'avant. */
    private static final double[][] HULL_TRIKE = {
            {-1.24, 0.48, 0.28, 0.64, 2.6},
            {-0.98, 0.62, 0.24, 0.76, 2.8},
            {-0.48, 0.60, 0.22, 0.78, 3.0},
            {0.06, 0.52, 0.22, 0.66, 3.0},
            {0.60, 0.38, 0.24, 0.54, 2.6},
            {1.02, 0.24, 0.26, 0.48, 2.2},
            {1.30, 0.13, 0.28, 0.42, 2.0},
    };

    /** Blinde : large, anguleux, presque une caisse boulonnee. */
    private static final double[][] HULL_BLINDE = {
            {-1.50, 0.64, 0.30, 0.80, 4.0},
            {-1.14, 0.92, 0.26, 0.94, 5.0},
            {-0.55, 0.96, 0.24, 0.98, 6.0},
            {0.05, 0.94, 0.24, 0.90, 6.0},
            {0.65, 0.86, 0.26, 0.82, 5.0},
            {1.16, 0.70, 0.28, 0.74, 4.0},
            {1.48, 0.44, 0.32, 0.64, 3.0},
    };

    /**
     * Cotes d'un chassis. Les valeurs par defaut decrivent le kart classique ;
     * chaque variante n'ecrase que ce qui la distingue.
     */
    private static final class Spec {
        double[][] hull = HULL_CLASSIQUE;
        Wheels wheels = Wheels.RAYONS;
        double frontR = 0.34, frontW = 0.30, frontX = 0.84, frontZ = 0.94;
        double rearR = 0.37, rearW = 0.40, rearX = 0.88, rearZ = -0.98;
        /** Baquet et volant. */
        double seatY = 0.62, seatZ = -0.28, steerY = 0.98, steerZ = 0.50;
        /** Plaque numerotee. */
        double plateY = 0.55, plateZ = 1.42, plateTilt = -18, plateScale = 1;
        /** Phares (x = 0 : un seul phare central) et feux arriere. */
        double lampX = 0.30, lampY = 0.50, lampZ = 1.44, tailX = 0.32, tailY = 0.66;
        /** Flammes de turbo. */
        double flameX = 0.26, flameY = 1.02;
        /** Decalage de la mascotte, quand elle est assise plus haut ou plus loin. */
        double driverY, driverZ;
        /** Inclinaison de la coque : un hot-rod pique du nez. */
        double rake;
        /** Trois-roues : une seule roue directrice, centrale. */
        boolean trike;
    }

    private static Spec spec(KartDef.Chassis chassis) {
        Spec s = new Spec();
        switch (chassis) {
            case CLASSIQUE -> {
            }
            case BUGGY -> {
                s.hull = HULL_BUGGY;
                s.wheels = Wheels.CRAMPONS;
                s.frontR = 0.44; s.frontW = 0.38; s.frontX = 0.96; s.frontZ = 1.00;
                s.rearR = 0.46; s.rearW = 0.42; s.rearX = 0.98; s.rearZ = -1.02;
                s.seatY = 0.76; s.seatZ = -0.28; s.steerY = 1.14; s.steerZ = 0.46;
                s.plateY = 0.80; s.plateZ = 1.38; s.plateTilt = -10;
                s.lampX = 0.36; s.lampY = 0.84; s.lampZ = 1.40;
                s.tailX = 0.36; s.tailY = 0.88;
                s.flameY = 1.10;
                s.driverY = 0.18;
            }
            case FUSEE -> {
                s.hull = HULL_FUSEE;
                s.wheels = Wheels.LISSES;
                s.frontR = 0.30; s.frontW = 0.24; s.frontX = 0.94; s.frontZ = 1.06;
                s.rearR = 0.40; s.rearW = 0.50; s.rearX = 0.94; s.rearZ = -1.02;
                s.seatY = 0.50; s.seatZ = -0.30; s.steerY = 0.86; s.steerZ = 0.40;
                s.plateY = 0.38; s.plateZ = 1.30; s.plateTilt = -34; s.plateScale = 0.72;
                s.lampX = 0.14; s.lampY = 0.38; s.lampZ = 1.60;
                s.tailX = 0.24; s.tailY = 0.50;
                s.flameX = 0.0; s.flameY = 0.52;
                s.driverY = -0.08;
            }
            case BULLE -> {
                s.hull = HULL_BULLE;
                s.frontR = 0.30; s.frontW = 0.30; s.frontX = 0.90; s.frontZ = 0.86;
                s.rearR = 0.32; s.rearW = 0.34; s.rearX = 0.92; s.rearZ = -0.88;
                s.seatY = 0.66; s.seatZ = -0.20; s.steerY = 1.00; s.steerZ = 0.40;
                s.plateY = 0.62; s.plateZ = 1.16; s.plateTilt = -30; s.plateScale = 0.88;
                s.lampX = 0.26; s.lampY = 0.66; s.lampZ = 1.16;
                s.tailX = 0.28; s.tailY = 0.74;
                s.flameX = 0.20; s.flameY = 0.62;
                s.driverY = 0.06;
            }
            case ROADSTER -> {
                s.hull = HULL_ROADSTER;
                s.wheels = Wheels.CHROME;
                s.frontR = 0.36; s.frontW = 0.30; s.frontX = 0.92; s.frontZ = 1.08;
                s.rearR = 0.38; s.rearW = 0.34; s.rearX = 0.92; s.rearZ = -1.04;
                s.seatY = 0.64; s.seatZ = -0.34; s.steerY = 1.00; s.steerZ = 0.42;
                s.plateY = 0.44; s.plateZ = 1.60; s.plateTilt = -12;
                s.lampX = 0.36; s.lampY = 0.62; s.lampZ = 1.48;
                s.tailX = 0.34; s.tailY = 0.62;
                s.flameX = 0.30; s.flameY = 0.42;
            }
            case HOTROD -> {
                s.hull = HULL_HOTROD;
                s.wheels = Wheels.LISSES;
                s.frontR = 0.28; s.frontW = 0.24; s.frontX = 0.82; s.frontZ = 1.06;
                s.rearR = 0.50; s.rearW = 0.54; s.rearX = 0.98; s.rearZ = -1.00;
                s.seatY = 0.58; s.seatZ = -0.36; s.steerY = 0.96; s.steerZ = 0.42;
                s.plateY = 0.50; s.plateZ = 1.34; s.plateTilt = -22; s.plateScale = 0.9;
                s.lampX = 0.28; s.lampY = 0.54; s.lampZ = 1.36;
                s.tailX = 0.34; s.tailY = 0.60;
                s.flameX = 0.30; s.flameY = 0.44;
                s.rake = -4;
            }
            case TRIKE -> {
                s.hull = HULL_TRIKE;
                s.trike = true;
                s.frontR = 0.38; s.frontW = 0.26; s.frontX = 0; s.frontZ = 1.26;
                s.rearR = 0.36; s.rearW = 0.40; s.rearX = 0.86; s.rearZ = -0.96;
                s.seatY = 0.58; s.seatZ = -0.32; s.steerY = 1.04; s.steerZ = 0.62;
                s.plateY = 0.46; s.plateZ = 1.12; s.plateTilt = -36; s.plateScale = 0.7;
                s.lampX = 0; s.lampY = 0.64; s.lampZ = 1.20;
                s.tailX = 0.22; s.tailY = 0.54;
                s.flameX = 0.20; s.flameY = 0.50;
            }
            case BLINDE -> {
                s.hull = HULL_BLINDE;
                s.wheels = Wheels.CRAMPONS;
                s.frontR = 0.42; s.frontW = 0.44; s.frontX = 1.04; s.frontZ = 1.00;
                s.rearR = 0.44; s.rearW = 0.48; s.rearX = 1.06; s.rearZ = -1.00;
                s.seatY = 0.72; s.seatZ = -0.30; s.steerY = 1.08; s.steerZ = 0.46;
                s.plateY = 0.72; s.plateZ = 1.44; s.plateTilt = -12;
                s.lampX = 0.44; s.lampY = 0.76; s.lampZ = 1.44;
                s.tailX = 0.44; s.tailY = 0.80;
                s.flameX = 0.34; s.flameY = 1.06;
                s.driverY = 0.12;
            }
        }
        return s;
    }

    /** Les quatre teintes de base d'un kart, passees de main en main. */
    private record Mats(PhongMaterial body, PhongMaterial trim,
                        PhongMaterial dark, PhongMaterial chrome) {
    }

    public final Group root = new Group();
    private final Group tilt = new Group();
    private final Group steerFL = new Group();
    private final Group steerFR = new Group();
    private final Rotate yaw = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate pitch = new Rotate(0, Rotate.X_AXIS);
    private final Rotate roll = new Rotate(0, Rotate.Z_AXIS);
    private final Rotate steerL = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate steerR = new Rotate(0, Rotate.Y_AXIS);
    private final Rotate[] spins;
    private final Rotate wheelTilt = new Rotate(0, Rotate.Z_AXIS);
    private final Cylinder shadow;
    private final Rotate driverLean = new Rotate(0, Rotate.Z_AXIS);
    private final Group boostFlames = new Group();
    private final Group brakeLights = new Group();
    private final double wheelRadius;

    private double wheelAngle;

    private final java.util.Random flicker;

    public KartNode(KartDef def) {
        // un flux par kart, seme sur son identifiant : le vacillement des
        // flammes ne depend donc pas de l'ordre dans lequel les karts sont
        // parcourus, qui n'est pas le meme d'une execution a l'autre
        this.flicker = org.tuxkart.core.Rng.stream(def.id.hashCode());
        Spec s = spec(def.chassis);
        root.getTransforms().add(yaw);
        tilt.getTransforms().addAll(pitch, roll);
        root.getChildren().add(tilt);

        Mats m = new Mats(paint(def.kartColor), paint(def.trimColor),
                Meshes.material(Color.web("#1e2024")), chrome());

        double rear = s.hull[0][0];
        double front = s.hull[s.hull.length - 1][0];
        double halfWidth = 0, floor = Double.MAX_VALUE;
        for (double[] section : s.hull) {
            halfWidth = Math.max(halfWidth, section[1]);
            floor = Math.min(floor, section[2]);
        }

        // --- coque et plancher. Le fond plat ne court que sous la partie large
        // de la coque : sur un chassis effile il depasserait comme une planche.
        double panFrom = rear, panTo = front;
        boolean firstWide = true;
        for (double[] section : s.hull) {
            if (section[1] < halfWidth * 0.75) continue;
            if (firstWide) {
                panFrom = section[0];
                firstWide = false;
            }
            panTo = section[0];
        }
        panFrom = Math.max(rear, panFrom - 0.10);
        panTo = Math.min(front, panTo + 0.10);

        MeshView body = Meshes.hull(s.hull, 16, m.body());
        Box pan = box(halfWidth * 2 + 0.08, 0.09, panTo - panFrom,
                0, floor - 0.02, (panFrom + panTo) / 2, m.trim());
        if (s.rake != 0) {
            body.getTransforms().add(new Rotate(s.rake, Rotate.X_AXIS));
            pan.getTransforms().add(new Rotate(s.rake, Rotate.X_AXIS));
        }
        tilt.getChildren().addAll(body, pan);

        // pare-chocs avant et arriere
        tilt.getChildren().add(tube(halfWidth * 1.2, 0.07, 0, floor + 0.18, front + 0.12, m.chrome()));
        tilt.getChildren().add(tube(halfWidth * 1.3, 0.07, 0, floor + 0.24, rear - 0.08, m.chrome()));

        // plaque numerotee sur le nez
        Box plate = new Box(0.62 * s.plateScale, 0.44 * s.plateScale, 0.05);
        plate.setMaterial(Meshes.material(Textures.numberPlate(
                number(def), def.trimColor, contrast(def.trimColor))));
        plate.setTranslateY(Meshes.jy(s.plateY));
        plate.setTranslateZ(s.plateZ);
        plate.getTransforms().add(new Rotate(s.plateTilt, Rotate.X_AXIS));
        tilt.getChildren().add(plate);

        // feux : un phare central sur le trois-roues, deux ailleurs
        PhongMaterial lampMat = glow(Color.web("#fff6d8"), 0.85);
        if (s.lampX == 0) {
            Sphere head = new Sphere(0.13, 12);
            head.setMaterial(lampMat);
            head.setTranslateY(Meshes.jy(s.lampY));
            head.setTranslateZ(s.lampZ);
            tilt.getChildren().add(head);
        } else {
            for (int i = -1; i <= 1; i += 2) {
                Sphere head = new Sphere(0.10, 10);
                head.setMaterial(lampMat);
                head.setTranslateX(i * s.lampX);
                head.setTranslateY(Meshes.jy(s.lampY));
                head.setTranslateZ(s.lampZ);
                tilt.getChildren().add(head);
            }
        }
        // un seul materiau pour les deux feux : ils se fusionnent alors
        PhongMaterial tailMat = glow(Color.web("#e02818"), 0.35);
        for (int i = -1; i <= 1; i += 2) {
            Box tail = new Box(0.20, 0.09, 0.06);
            tail.setMaterial(tailMat);
            tail.setTranslateX(i * s.tailX);
            tail.setTranslateY(Meshes.jy(s.tailY));
            tail.setTranslateZ(rear - 0.01);
            brakeLights.getChildren().add(tail);
        }
        tilt.getChildren().add(brakeLights);

        // baquet, puis volant ou guidon
        tilt.getChildren().add(box(0.62, 0.10, 0.62, 0, s.seatY, s.seatZ, m.dark()));
        tilt.getChildren().add(box(0.62, 0.46, 0.10, 0, s.seatY + 0.24, s.seatZ - 0.30, m.dark()));
        if (s.trike) {
            tilt.getChildren().add(tube(0.72, 0.04, 0, s.steerY, s.steerZ, m.dark()));
            for (int i = -1; i <= 1; i += 2) {
                Cylinder grip = new Cylinder(0.055, 0.16, 8);
                grip.setMaterial(m.dark());
                grip.setTranslateX(i * 0.30);
                grip.setTranslateY(Meshes.jy(s.steerY));
                grip.setTranslateZ(s.steerZ);
                grip.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
                tilt.getChildren().add(grip);
            }
        } else {
            Cylinder rim = new Cylinder(0.20, 0.045, 14);
            rim.setMaterial(m.dark());
            rim.setTranslateY(Meshes.jy(s.steerY));
            rim.setTranslateZ(s.steerZ);
            rim.getTransforms().add(new Rotate(62, Rotate.X_AXIS));
            Cylinder column = new Cylinder(0.035, 0.42, 6);
            column.setMaterial(m.chrome());
            column.setTranslateY(Meshes.jy(s.steerY - 0.12));
            column.setTranslateZ(s.steerZ + 0.12);
            column.getTransforms().add(new Rotate(62, Rotate.X_AXIS));
            tilt.getChildren().addAll(rim, column);
        }

        // --- roues : le trois-roues n'en a qu'une a l'avant, centrale
        steerFL.getTransforms().add(steerL);
        steerFR.getTransforms().add(steerR);
        steerFL.setTranslateX(-s.frontX);
        steerFL.setTranslateZ(s.frontZ);
        steerFL.setTranslateY(Meshes.jy(s.frontR));
        steerFR.setTranslateX(s.frontX);
        steerFR.setTranslateZ(s.frontZ);
        steerFR.setTranslateY(Meshes.jy(s.frontR));

        Group rl = new Group(), rr = new Group();
        rl.setTranslateX(-s.rearX);
        rl.setTranslateZ(s.rearZ);
        rl.setTranslateY(Meshes.jy(s.rearR));
        rr.setTranslateX(s.rearX);
        rr.setTranslateZ(s.rearZ);
        rr.setTranslateY(Meshes.jy(s.rearR));

        WheelMats wm = WheelMats.of(def, s.wheels);
        if (s.trike) {
            spins = new Rotate[]{
                    wheel(steerFL, wm, s.frontW, s.frontR, s.wheels),
                    wheel(rl, wm, s.rearW, s.rearR, s.wheels),
                    wheel(rr, wm, s.rearW, s.rearR, s.wheels)};
            tilt.getChildren().add(steerFL);
        } else {
            spins = new Rotate[]{
                    wheel(steerFL, wm, s.frontW, s.frontR, s.wheels),
                    wheel(steerFR, wm, s.frontW, s.frontR, s.wheels),
                    wheel(rl, wm, s.rearW, s.rearR, s.wheels),
                    wheel(rr, wm, s.rearW, s.rearR, s.wheels)};
            tilt.getChildren().addAll(steerFL, steerFR);
        }
        tilt.getChildren().addAll(rl, rr);
        wheelRadius = s.rearR;
        // essieu arriere
        tilt.getChildren().add(tube(s.rearX * 1.7, 0.045, 0, s.rearR, s.rearZ, m.dark()));

        // --- ce qui distingue vraiment les machines
        extras(def, s, m, halfWidth, front, rear);

        Group driver = DriverNode.build(def);
        driver.getTransforms().add(driverLean);
        driver.setTranslateY(Meshes.jy(s.driverY));
        driver.setTranslateZ(s.driverZ);
        tilt.getChildren().add(driver);

        // flammes de turbo, masquees par defaut
        PhongMaterial flameMat = glow(Color.web("#ff8c1a"), 0.9);
        for (int i = -1; i <= 1; i += 2) {
            Group flame = Meshes.cone(0.19, 1.0, 10, flameMat);
            flame.setTranslateX(i * s.flameX);
            flame.setTranslateY(Meshes.jy(s.flameY));
            flame.setTranslateZ(rear - 0.10);
            flame.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            boostFlames.getChildren().add(flame);
            if (s.flameX == 0) break;   // echappement unique : une seule flamme
        }
        boostFlames.setVisible(false);
        tilt.getChildren().add(boostFlames);

        shadow = new Cylinder(halfWidth + 0.40, 0.03, 16);
        shadow.setMaterial(new PhongMaterial(Color.rgb(0, 0, 0, 0.45)));
        shadow.setScaleZ(((front - rear) / 2 + 0.30) / (halfWidth + 0.40));
        shadow.setMouseTransparent(true);
        root.getChildren().add(shadow);

        root.getTransforms().add(wheelTilt);

        // --- fusion. Une fois pose, un kart ne bouge plus que par ses roues,
        // ses flammes et son pilote : tout le reste — coque, accastillage,
        // boulons — peut partir dans un maillage par materiau au lieu d'une
        // centaine de formes soumises separement a chaque image.
        List<Node> moving = List.of(steerFL, steerFR, rl, rr, boostFlames, brakeLights, driver);
        Group statics = new Group();
        for (Node child : new ArrayList<>(tilt.getChildren())) {
            if (moving.contains(child)) continue;
            tilt.getChildren().remove(child);
            statics.getChildren().add(child);
        }
        MeshMerge.merge(statics);
        tilt.getChildren().add(statics);
        MeshMerge.merge(brakeLights);
        MeshMerge.merge(boostFlames);
        MeshMerge.merge(driver);
    }

    // ------------------------------------------------------- accastillage
    // Un bloc par silhouette. Tout ce qui est ici est purement decoratif :
    // la physique ne connait que la fiche du pilote.

    private void extras(KartDef def, Spec s, Mats m, double halfWidth, double front, double rear) {
        switch (def.chassis) {
            case CLASSIQUE -> classique(s, m, rear);
            case BUGGY -> buggy(s, m, rear);
            case FUSEE -> fusee(s, m, front, rear);
            case BULLE -> bulle(s, m, rear);
            case ROADSTER -> roadster(s, m, front, rear);
            case HOTROD -> hotrod(s, m, rear);
            case TRIKE -> trike(s, m, rear);
            case BLINDE -> blinde(s, m, halfWidth, front, rear);
        }
    }

    /** Kart classique : pontons, arceau, echappements verticaux, aileron plat. */
    private void classique(Spec s, Mats m, double rear) {
        for (int i = -1; i <= 1; i += 2) {
            tilt.getChildren().add(box(0.26, 0.34, 1.35, i * 0.86, 0.46, -0.12, m.trim()));
            tilt.getChildren().add(box(0.20, 0.26, 0.55, i * 0.84, 0.44, 0.72, m.trim()));
            tilt.getChildren().add(box(0.07, 0.07, 0.30, i * 0.30, 0.38, 1.58, m.chrome()));

            Cylinder pipe = new Cylinder(0.05, 0.52, 8);
            pipe.setMaterial(m.chrome());
            pipe.setTranslateX(i * 0.28);
            pipe.setTranslateY(Meshes.jy(0.92));
            pipe.setTranslateZ(rear + 0.16);
            pipe.getTransforms().add(new Rotate(74, Rotate.X_AXIS));
            tilt.getChildren().add(pipe);

            Cylinder leg = new Cylinder(0.035, 0.46, 8);
            leg.setMaterial(m.chrome());
            leg.setTranslateX(i * 0.30);
            leg.setTranslateY(Meshes.jy(1.09));
            leg.setTranslateZ(-0.70);
            leg.getTransforms().add(new Rotate(-14, Rotate.X_AXIS));
            tilt.getChildren().add(leg);
        }
        tilt.getChildren().add(box(0.66, 0.40, 0.60, 0, 0.78, rear + 0.50, m.dark()));
        tilt.getChildren().add(tube(0.66, 0.035, 0, 1.30, -0.76, m.chrome()));
        // aileron
        tilt.getChildren().add(box(1.42, 0.07, 0.34, 0, 1.10, -1.30, m.trim()));
        for (int i = -1; i <= 1; i += 2) {
            tilt.getChildren().add(box(0.08, 0.34, 0.08, i * 0.52, 0.92, -1.30, m.dark()));
        }
    }

    /** Buggy : cage tubulaire, rampe de phares, pare-buffle, roue de secours. */
    private void buggy(Spec s, Mats m, double rear) {
        for (double z : new double[]{0.16, -0.72}) {
            Group hoop = hoopXY(0.86, 0.055, 6, m.chrome());
            hoop.setTranslateY(Meshes.jy(0.92));
            hoop.setTranslateZ(z);
            tilt.getChildren().add(hoop);
        }
        for (int i = -1; i <= 1; i += 2) {
            // longerons de la cage
            tilt.getChildren().add(box(0.055, 0.055, 0.92, i * 0.60, 1.72, -0.28, m.chrome()));
            tilt.getChildren().add(box(0.055, 0.70, 0.055, i * 0.84, 1.10, -0.72, m.chrome()));
            // pare-buffle
            tilt.getChildren().add(box(0.06, 0.44, 0.06, i * 0.34, 1.02, 1.40, m.chrome()));
        }
        tilt.getChildren().add(box(0.055, 0.055, 0.92, 0, 1.78, -0.28, m.chrome()));
        tilt.getChildren().add(tube(0.80, 0.055, 0, 1.24, 1.40, m.chrome()));

        // rampe de phares sur la cage
        tilt.getChildren().add(box(0.92, 0.08, 0.10, 0, 1.86, 0.16, m.dark()));
        PhongMaterial rampMat = glow(Color.web("#fff2c8"), 0.8);
        for (int i = -1; i <= 1; i++) {
            Sphere lamp = new Sphere(0.09, 10);
            lamp.setMaterial(rampMat);
            lamp.setTranslateX(i * 0.30);
            lamp.setTranslateY(Meshes.jy(1.86));
            lamp.setTranslateZ(0.24);
            tilt.getChildren().add(lamp);
        }

        // roue de secours et echappement en trompette
        Cylinder spare = new Cylinder(0.34, 0.24, 16);
        spare.setMaterial(Meshes.material(Textures.tyre(), Textures.tyreBump(),
                Color.rgb(50, 50, 50), 16));
        spare.setTranslateY(Meshes.jy(1.10));
        spare.setTranslateZ(rear - 0.14);
        spare.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        tilt.getChildren().add(spare);
        Cylinder snorkel = new Cylinder(0.06, 0.66, 8);
        snorkel.setMaterial(m.dark());
        snorkel.setTranslateX(0.66);
        snorkel.setTranslateY(Meshes.jy(1.28));
        snorkel.setTranslateZ(-0.30);
        tilt.getChildren().add(snorkel);
    }

    /** Monoplace : ailerons avant et arriere, ecope, museau en ogive. */
    private void fusee(Spec s, Mats m, double front, double rear) {
        Group nose = Meshes.cone(0.13, 0.34, 10, m.body());
        nose.setTranslateY(Meshes.jy(0.27));
        nose.setTranslateZ(front + 0.05);
        nose.getTransforms().add(new Rotate(-90, Rotate.X_AXIS));
        tilt.getChildren().add(nose);

        // aileron avant
        tilt.getChildren().add(box(1.66, 0.05, 0.34, 0, 0.20, front + 0.16, m.trim()));
        for (int i = -1; i <= 1; i += 2) {
            tilt.getChildren().add(box(0.05, 0.26, 0.40, i * 0.82, 0.32, front + 0.14, m.trim()));
            // pontons plats
            tilt.getChildren().add(box(0.22, 0.26, 1.10, i * 0.68, 0.34, -0.20, m.trim()));
        }

        // aileron arriere a deux plans
        tilt.getChildren().add(box(1.34, 0.05, 0.30, 0, 1.02, rear + 0.06, m.trim()));
        Box flap = box(1.34, 0.05, 0.22, 0, 1.20, rear - 0.02, m.trim());
        flap.getTransforms().add(new Rotate(-16, Rotate.X_AXIS));
        tilt.getChildren().add(flap);
        for (int i = -1; i <= 1; i += 2) {
            tilt.getChildren().add(box(0.05, 0.40, 0.46, i * 0.67, 1.11, rear + 0.02, m.dark()));
        }
        tilt.getChildren().add(box(0.16, 0.46, 0.26, 0, 0.84, rear + 0.10, m.dark()));

        // ecope au-dessus du pilote et sortie d'echappement centrale
        // le capot moteur monte d'un seul tenant depuis le fond de coque
        Box airbox = box(0.34, 0.88, 0.58, 0, 1.04, -0.72, m.body());
        airbox.getTransforms().add(new Rotate(-6, Rotate.X_AXIS));
        tilt.getChildren().add(airbox);
        Cylinder intake = new Cylinder(0.15, 0.14, 12);
        intake.setMaterial(m.dark());
        intake.setTranslateY(Meshes.jy(1.40));
        intake.setTranslateZ(-0.46);
        intake.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        tilt.getChildren().add(intake);
        Cylinder pipe = new Cylinder(0.08, 0.34, 10);
        pipe.setMaterial(m.chrome());
        pipe.setTranslateY(Meshes.jy(0.52));
        pipe.setTranslateZ(rear - 0.06);
        pipe.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
        tilt.getChildren().add(pipe);
    }

    /** Bulle : arceaux de verriere, antenne, capot moteur bombe. */
    private void bulle(Spec s, Mats m, double rear) {
        for (double z : new double[]{0.20, -0.10, -0.42}) {
            Group hoop = hoopXY(0.66, 0.045, 6, m.chrome());
            hoop.setTranslateY(Meshes.jy(0.86));
            hoop.setTranslateZ(z);
            tilt.getChildren().add(hoop);
        }
        for (int i = -1; i <= 1; i += 2) {
            tilt.getChildren().add(box(0.045, 0.045, 0.64, i * 0.44, 1.35, -0.10, m.chrome()));
        }
        tilt.getChildren().add(box(0.045, 0.045, 0.64, 0, 1.52, -0.10, m.chrome()));

        // capot moteur arrondi
        Sphere hump = new Sphere(0.42, 14);
        hump.setMaterial(m.trim());
        hump.setScaleY(0.62);
        hump.setScaleZ(0.80);
        hump.setTranslateY(Meshes.jy(0.86));
        hump.setTranslateZ(rear + 0.28);
        tilt.getChildren().add(hump);

        // antenne a boule
        Cylinder mast = new Cylinder(0.02, 0.70, 6);
        mast.setMaterial(m.chrome());
        mast.setTranslateX(0.36);
        mast.setTranslateY(Meshes.jy(1.16));
        mast.setTranslateZ(rear + 0.34);
        tilt.getChildren().add(mast);
        Sphere ball = new Sphere(0.08, 10);
        ball.setMaterial(glow(Color.web("#ffd54a"), 0.7));
        ball.setTranslateX(0.36);
        ball.setTranslateY(Meshes.jy(1.52));
        ball.setTranslateZ(rear + 0.34);
        tilt.getChildren().add(ball);

        // ailettes et petits echappements
        for (int i = -1; i <= 1; i += 2) {
            Group fin = Meshes.cone(0.20, 0.26, 6, m.trim());
            fin.setScaleX(0.22);
            fin.setTranslateX(i * 0.52);
            fin.setTranslateY(Meshes.jy(0.72));
            fin.setTranslateZ(rear + 0.16);
            tilt.getChildren().add(fin);

            Cylinder pipe = new Cylinder(0.055, 0.22, 8);
            pipe.setMaterial(m.chrome());
            pipe.setTranslateX(i * 0.20);
            pipe.setTranslateY(Meshes.jy(0.44));
            pipe.setTranslateZ(rear - 0.02);
            pipe.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            tilt.getChildren().add(pipe);
        }
    }

    /** Roadster : garde-boue, calandre chromee, sorties laterales, arceau bas. */
    private void roadster(Spec s, Mats m, double front, double rear) {
        // garde-boue : ceux de l'avant tournent avec les roues
        steerFL.getChildren().add(fender(s.frontR + 0.10, s.frontW + 0.08, m.body()));
        steerFR.getChildren().add(fender(s.frontR + 0.10, s.frontW + 0.08, m.body()));
        for (int i = -1; i <= 1; i += 2) {
            Group rearFender = fender(s.rearR + 0.11, s.rearW + 0.08, m.body());
            rearFender.setTranslateX(i * s.rearX);
            rearFender.setTranslateY(Meshes.jy(s.rearR));
            rearFender.setTranslateZ(s.rearZ);
            tilt.getChildren().add(rearFender);

            // sorties d'echappement le long des flancs
            Cylinder sidePipe = new Cylinder(0.07, 1.30, 10);
            sidePipe.setMaterial(m.chrome());
            sidePipe.setTranslateX(i * 0.86);
            sidePipe.setTranslateY(Meshes.jy(0.36));
            sidePipe.setTranslateZ(-0.30);
            sidePipe.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            tilt.getChildren().add(sidePipe);
        }

        // calandre
        tilt.getChildren().add(box(0.66, 0.34, 0.06, 0, 0.44, front + 0.04, m.chrome()));
        for (int i = 0; i < 4; i++) {
            tilt.getChildren().add(box(0.05, 0.30, 0.10, -0.24 + i * 0.16, 0.44,
                    front + 0.06, m.dark()));
        }

        // pare-brise incline, en cadre chrome
        Group frame = hoopXY(0.48, 0.055, 6, m.dark());
        frame.setScaleY(0.80);
        frame.setTranslateY(Meshes.jy(0.84));
        frame.setTranslateZ(0.70);
        frame.getTransforms().add(new Rotate(-22, Rotate.X_AXIS));
        tilt.getChildren().add(frame);

        // croupe : becquet en lame et double feu rond
        tilt.getChildren().add(box(1.10, 0.07, 0.26, 0, 0.78, rear + 0.10, m.trim()));
    }

    /** Hot-rod : moteur apparent, compresseur, cornets et sorties laterales. */
    private void hotrod(Spec s, Mats m, double rear) {
        tilt.getChildren().add(box(0.70, 0.46, 0.62, 0, 0.84, 0.92, m.dark()));
        tilt.getChildren().add(box(0.56, 0.30, 0.44, 0, 1.18, 0.92, m.chrome()));
        for (int i = -1; i <= 1; i += 2) {
            for (int k = 0; k < 3; k++) {
                Cylinder stack = new Cylinder(0.055, 0.16, 8);
                stack.setMaterial(m.chrome());
                stack.setTranslateX(i * 0.13);
                stack.setTranslateY(Meshes.jy(1.40));
                stack.setTranslateZ(0.74 + k * 0.18);
                tilt.getChildren().add(stack);
            }
            // collecteurs qui plongent vers les flancs
            Cylinder header = new Cylinder(0.075, 1.50, 10);
            header.setMaterial(m.chrome());
            header.setTranslateX(i * 0.84);
            header.setTranslateY(Meshes.jy(0.34));
            header.setTranslateZ(-0.20);
            header.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            tilt.getChildren().add(header);
            for (int k = 0; k < 4; k++) {
                Cylinder riser = new Cylinder(0.045, 0.34, 8);
                riser.setMaterial(m.chrome());
                riser.setTranslateX(i * 0.82);
                riser.setTranslateY(Meshes.jy(0.56));
                riser.setTranslateZ(0.32 + k * 0.16);
                riser.getTransforms().add(new Rotate(i * 26, Rotate.Z_AXIS));
                tilt.getChildren().add(riser);
            }
        }
        // arceau trapu et reservoir chrome
        Group hoop = hoopXY(0.62, 0.07, 5, m.dark());
        hoop.setTranslateY(Meshes.jy(0.72));
        hoop.setTranslateZ(-0.66);
        tilt.getChildren().add(hoop);
        Cylinder tank = new Cylinder(0.20, 0.90, 12);
        tank.setMaterial(m.chrome());
        tank.setTranslateY(Meshes.jy(0.86));
        tank.setTranslateZ(rear + 0.24);
        tank.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        tilt.getChildren().add(tank);
    }

    /** Trois-roues : fourche avant, derive arriere, chassis apparent. */
    private void trike(Spec s, Mats m, double rear) {
        // fourche et te de direction, solidaires de la roue directrice — donc
        // fusionnees a part, dans leur propre groupe
        Group fork = new Group();
        for (int i = -1; i <= 1; i += 2) {
            Cylinder leg = new Cylinder(0.045, 0.62, 8);
            leg.setMaterial(m.chrome());
            leg.setTranslateX(i * 0.19);
            leg.setTranslateY(Meshes.jy(0.24));
            leg.getTransforms().add(new Rotate(i * -10, Rotate.Z_AXIS));
            fork.getChildren().add(leg);
        }
        Box clamp = new Box(0.42, 0.09, 0.14);
        clamp.setMaterial(m.trim());
        clamp.setTranslateY(Meshes.jy(0.52));
        fork.getChildren().add(clamp);
        MeshMerge.merge(fork);
        steerFL.getChildren().add(fork);
        // bras oblique qui relie la fourche au chassis
        Box arm = box(0.10, 0.10, 0.72, 0, 0.62, s.frontZ - 0.40, m.trim());
        arm.getTransforms().add(new Rotate(-14, Rotate.X_AXIS));
        tilt.getChildren().add(arm);

        // derive arriere et doubles sorties
        Group fin = Meshes.cone(0.30, 0.52, 6, m.trim());
        fin.setScaleX(0.16);
        fin.setTranslateY(Meshes.jy(0.50));
        fin.setTranslateZ(rear + 0.18);
        tilt.getChildren().add(fin);
        for (int i = -1; i <= 1; i += 2) {
            Cylinder pipe = new Cylinder(0.05, 0.46, 8);
            pipe.setMaterial(m.chrome());
            pipe.setTranslateX(i * 0.36);
            pipe.setTranslateY(Meshes.jy(0.42));
            pipe.setTranslateZ(rear + 0.10);
            pipe.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
            tilt.getChildren().add(pipe);
            // longerons apparents
            tilt.getChildren().add(box(0.07, 0.07, 1.30, i * 0.44, 0.30, -0.20, m.dark()));
        }
    }

    /** Blinde : plaques boulonnees, pare-buffle a pointes, cheminees. */
    private void blinde(Spec s, Mats m, double halfWidth, double front, double rear) {
        for (int i = -1; i <= 1; i += 2) {
            for (int k = -1; k <= 1; k += 2) {
                tilt.getChildren().add(box(0.09, 0.34, 0.80, i * (halfWidth + 0.02),
                        0.58, k * 0.46, m.trim()));
                for (int b = -1; b <= 1; b += 2) {
                    Sphere bolt = new Sphere(0.045, 8);
                    bolt.setMaterial(m.chrome());
                    bolt.setTranslateX(i * (halfWidth + 0.06));
                    bolt.setTranslateY(Meshes.jy(0.58));
                    bolt.setTranslateZ(k * 0.46 + b * 0.30);
                    tilt.getChildren().add(bolt);
                }
            }
            // cheminees d'echappement
            Cylinder stack = new Cylinder(0.09, 0.80, 10);
            stack.setMaterial(m.dark());
            stack.setTranslateX(i * 0.52);
            stack.setTranslateY(Meshes.jy(1.24));
            stack.setTranslateZ(rear + 0.34);
            Cylinder capRing = new Cylinder(0.11, 0.07, 10);
            capRing.setMaterial(m.chrome());
            capRing.setTranslateX(i * 0.52);
            capRing.setTranslateY(Meshes.jy(1.62));
            capRing.setTranslateZ(rear + 0.34);
            tilt.getChildren().addAll(stack, capRing);
        }

        // pare-buffle a pointes
        tilt.getChildren().add(tube(halfWidth * 1.6, 0.09, 0, 0.56, front + 0.16, m.dark()));
        for (int i = -2; i <= 2; i++) {
            Group spike = Meshes.cone(0.08, 0.30, 6, m.trim());
            spike.setTranslateX(i * 0.30);
            spike.setTranslateY(Meshes.jy(0.56));
            spike.setTranslateZ(front + 0.18);
            spike.getTransforms().add(new Rotate(-90, Rotate.X_AXIS));
            tilt.getChildren().add(spike);
        }

        // arceau massif et aileron en lame
        Group hoop = hoopXY(0.86, 0.09, 5, m.dark());
        hoop.setTranslateY(Meshes.jy(0.94));
        hoop.setTranslateZ(-0.74);
        tilt.getChildren().add(hoop);
        tilt.getChildren().add(box(1.50, 0.09, 0.30, 0, 1.16, rear + 0.24, m.trim()));
        tilt.getChildren().add(box(0.09, 0.42, 0.44, 0, 0.98, rear + 0.24, m.dark()));
    }

    // --------------------------------------------------------------- helpers

    private static PhongMaterial paint(Color c) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSpecularColor(c.interpolate(Color.WHITE, 0.75));
        m.setSpecularPower(42);
        return m;
    }

    private static PhongMaterial chrome() {
        PhongMaterial m = new PhongMaterial(Color.web("#c8ced4"));
        m.setSpecularColor(Color.WHITE);
        m.setSpecularPower(80);
        return m;
    }

    private static PhongMaterial glow(Color c, double strength) {
        PhongMaterial m = new PhongMaterial(c);
        m.setSelfIlluminationMap(Terrain.flat(c.deriveColor(0, 1, strength, 1)));
        m.setSpecularColor(Color.WHITE);
        m.setSpecularPower(60);
        return m;
    }

    private static Color contrast(Color c) {
        double lum = 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
        return lum > 0.55 ? Color.web("#16181c") : Color.web("#f4f6f8");
    }

    private static int number(KartDef def) {
        int i = KartRoster.ALL.indexOf(def);
        return i < 0 ? 1 : i + 1;
    }

    private static Box box(double w, double h, double d,
                           double x, double y, double z, PhongMaterial m) {
        Box b = new Box(w, h, d);
        b.setMaterial(m);
        b.setTranslateX(x);
        b.setTranslateY(Meshes.jy(y));
        b.setTranslateZ(z);
        return b;
    }

    /** Barre horizontale (cylindre couche selon X). */
    private static Cylinder tube(double length, double radius,
                                 double x, double y, double z, PhongMaterial m) {
        Cylinder c = new Cylinder(radius, length, 8);
        c.setMaterial(m);
        c.setTranslateX(x);
        c.setTranslateY(Meshes.jy(y));
        c.setTranslateZ(z);
        c.getTransforms().add(new Rotate(90, Rotate.Z_AXIS));
        return c;
    }

    /**
     * Demi-arceau vertical, vu de face : une suite de barres droites posees sur
     * un demi-cercle. JavaFX n'a pas de tore, et un vrai tube courbe couterait
     * un maillage dedie par arceau.
     */
    private static Group hoopXY(double radius, double bar, int segments, PhongMaterial m) {
        Group g = new Group();
        for (int i = 0; i < segments; i++) {
            double a = Math.PI * (i + 0.5) / segments;
            double len = radius * Math.PI / segments * 1.12;
            Box seg = new Box(bar, len, bar);
            seg.setMaterial(m);
            seg.setTranslateX(Math.cos(a) * radius);
            seg.setTranslateY(Meshes.jy(Math.sin(a) * radius));
            seg.getTransforms().add(new Rotate(180 - Math.toDegrees(a), Rotate.Z_AXIS));
            g.getChildren().add(seg);
        }
        return g;
    }

    /** Garde-boue : meme principe, mais l'arc enjambe la roue selon Z. */
    private static Group fender(double radius, double width, PhongMaterial m) {
        Group g = new Group();
        int segments = 5;
        double from = Math.toRadians(20), to = Math.toRadians(160);
        for (int i = 0; i < segments; i++) {
            double a = from + (to - from) * (i + 0.5) / segments;
            double len = radius * (to - from) / segments * 1.15;
            Box seg = new Box(width, 0.06, len);
            seg.setMaterial(m);
            seg.setTranslateZ(Math.cos(a) * radius);
            seg.setTranslateY(Meshes.jy(Math.sin(a) * radius));
            seg.getTransforms().add(new Rotate(90 + Math.toDegrees(a), Rotate.X_AXIS));
            g.getChildren().add(seg);
        }
        // le garde-boue suit la roue directrice : il ne rejoindra pas la fusion
        // de la caisse, on le fusionne donc pour lui-meme
        MeshMerge.merge(g);
        return g;
    }

    /**
     * Roue : pneu sculpte, jante et finition propre au chassis.
     *
     * Le couchage et la rotation sont portes par un groupe et non par chaque
     * piece : la jante devient alors immobile dans ce repere, donc fusionnable
     * d'un bloc. Seul le pneu reste a part, sa gomme etant texturee.
     */
    private static Rotate wheel(Group parent, WheelMats mats, double width, double radius,
                                Wheels style) {
        Rotate spin = new Rotate(0, Rotate.Y_AXIS);
        Group hub = new Group();
        hub.getTransforms().addAll(new Rotate(90, Rotate.Z_AXIS), spin);
        parent.getChildren().add(hub);

        Cylinder tyre = new Cylinder(radius, width, 22);
        tyre.setMaterial(mats.tyre());
        hub.getChildren().add(tyre);

        if (style == Wheels.CHROME) {
            // flanc blanc : il deborde legerement du pneu
            Cylinder wall = new Cylinder(radius * 0.90, width + 0.02, 16);
            wall.setMaterial(mats.wall());
            hub.getChildren().add(wall);
        }

        double rimR = radius * (style == Wheels.LISSES ? 0.62 : 0.52);
        Cylinder rim = new Cylinder(rimR, width + 0.03, 18);
        rim.setMaterial(mats.rim());

        Cylinder cap = new Cylinder(radius * 0.16, width + 0.06, 10);
        cap.setMaterial(mats.cap());
        hub.getChildren().addAll(rim, cap);

        // les rayons rendent la rotation lisible ; une jante pleine ne dirait
        // rien de la vitesse des roues
        int spokes = switch (style) {
            case RAYONS -> 5;
            case CHROME -> 8;
            case CRAMPONS -> 3;
            case LISSES -> 3;
        };
        for (int i = 0; i < spokes; i++) {
            Box spoke = new Box(width + 0.03, rimR * 1.7, style == Wheels.CHROME ? 0.035 : 0.05);
            spoke.setMaterial(mats.spoke());
            spoke.getTransforms().add(new Rotate(i * 180.0 / spokes, Rotate.Y_AXIS));
            hub.getChildren().add(spoke);
        }

        if (style == Wheels.CRAMPONS) {
            for (int i = 0; i < 8; i++) {
                Box lug = new Box(0.07, width + 0.05, 0.11);
                lug.setMaterial(mats.lug());
                lug.getTransforms().addAll(new Rotate(i * 45.0, Rotate.Y_AXIS),
                        new Translate(0, 0, radius - 0.02));
                hub.getChildren().add(lug);
            }
        }
        MeshMerge.merge(hub);
        return spin;
    }

    /**
     * Les teintes d'une roue, fabriquees une fois pour les quatre.
     *
     * Deux pieces ne fusionnent que si elles partagent le meme materiau : un
     * materiau par roue en donnerait quatre fois plus, et autant de changements
     * d'etat a chaque image.
     */
    private record WheelMats(PhongMaterial tyre, PhongMaterial wall, PhongMaterial rim,
                             PhongMaterial cap, PhongMaterial spoke, PhongMaterial lug) {

        static WheelMats of(KartDef def, Wheels style) {
            return new WheelMats(
                    Meshes.material(Textures.tyre(), Textures.tyreBump(), Color.rgb(50, 50, 50), 16),
                    Meshes.material(Color.web("#f2f0ea")),
                    paint(def.trimColor),
                    Meshes.material(Color.web("#d0d4d8")),
                    Meshes.material(Color.web(style == Wheels.CHROME ? "#d8dce0" : "#9aa0a6")),
                    Meshes.material(Color.web("#2a2a2e")));
        }
    }

    // ------------------------------------------------------------------ sync

    /** Recopie l'etat physique du kart dans la scene 3D. */
    public void sync(Kart kart, double dt) {
        root.setTranslateX(kart.pos.x);
        root.setTranslateZ(kart.pos.z);
        root.setTranslateY(Meshes.jy(kart.visualY()));
        // le tete-a-queue fait deja tourner le cap physique : rien a ajouter
        // ici, sous peine de voir le kart pivoter deux fois plus vite qu'il ne
        // pivote reellement
        yaw.setAngle(Math.toDegrees(kart.heading));
        pitch.setAngle(kart.visualPitch);
        roll.setAngle(kart.visualRoll);
        // le kart epouse le devers de la piste
        wheelTilt.setAngle(Math.toDegrees(Math.atan(-kart.loc.bankSlope)));

        wheelAngle += kart.speed / wheelRadius * dt;
        double deg = Math.toDegrees(wheelAngle);
        for (Rotate r : spins) r.setAngle(deg);

        double steerDeg = Math.clamp(kart.controls.steer, -1, 1) * 26;
        steerL.setAngle(steerDeg);
        steerR.setAngle(steerDeg);

        boolean boosting = kart.boosting();
        boostFlames.setVisible(boosting);
        if (boosting) boostFlames.setScaleZ(0.7 + flicker.nextDouble() * 0.6);
        brakeLights.setVisible(kart.controls.brake || kart.speed < 0.2);
        // le pilote s'incline dans le virage : le kart parait vivant
        driverLean.setAngle(Math.clamp(kart.controls.steer, -1, 1) * 9
                + Math.clamp(kart.lateralVel * 0.8, -7, 7));

        shadow.setTranslateY(Meshes.jy(kart.pos.y - kart.visualY() + 0.04));
        shadow.setVisible(!kart.beingRescued() && !kart.airborne);
    }
}
