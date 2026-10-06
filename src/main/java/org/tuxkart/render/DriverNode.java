package org.tuxkart.render;

import javafx.scene.Group;
import javafx.scene.paint.Color;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.Box;
import javafx.scene.shape.Cylinder;
import javafx.scene.shape.Sphere;
import javafx.scene.transform.Rotate;
import org.tuxkart.kart.KartDef;

/**
 * La mascotte assise dans le kart.
 *
 * Le corps est commun a tout le plateau — un tronc, un ventre, une tete et deux
 * bras poses sur le volant — et c'est l'accastillage qui distingue les pilotes :
 * museau, oreilles, couvre-chef, sourcils, cornes, barbe, piquants. Chaque piece
 * est facultative, si bien qu'aucune mascotte n'a la meme silhouette.
 *
 * Les cotes sont donnees dans le repere du monde (Y monte), {@link Meshes#jy}
 * fait la conversion vers JavaFX.
 */
final class DriverNode {

    /** Centre de la tete : tout le visage et les couvre-chefs s'y rapportent. */
    private static final double HEAD_Y = 1.48;
    private static final double HEAD_Z = -0.08;

    private DriverNode() {
    }

    static Group build(KartDef def) {
        Group g = new Group();
        PhongMaterial body = Meshes.material(def.bodyColor);
        PhongMaterial belly = Meshes.material(def.bellyColor);
        PhongMaterial accent = Meshes.material(def.accentColor);
        PhongMaterial black = Meshes.material(Color.web("#0e0e10"));
        PhongMaterial white = Meshes.material(Color.web("#fbfbfb"));

        Sphere torso = new Sphere(0.35, 18);
        torso.setMaterial(body);
        torso.setScaleY(1.22);
        torso.setScaleZ(0.88);
        torso.setTranslateY(Meshes.jy(1.00));
        torso.setTranslateZ(-0.14);
        g.getChildren().add(torso);

        Sphere front = new Sphere(0.30, 16);
        front.setMaterial(belly);
        front.setScaleY(1.08);
        front.setScaleZ(0.52);
        front.setTranslateY(Meshes.jy(0.98));
        front.setTranslateZ(0.09);
        g.getChildren().add(front);

        Sphere head = new Sphere(0.27, 18);
        head.setMaterial(body);
        head.setScaleZ(1.05);
        head.setTranslateY(Meshes.jy(HEAD_Y));
        head.setTranslateZ(HEAD_Z);
        g.getChildren().add(head);

        for (int i = -1; i <= 1; i += 2) {
            Sphere eye = new Sphere(0.082, 12);
            eye.setMaterial(white);
            eye.setTranslateX(i * 0.108);
            eye.setTranslateY(Meshes.jy(1.545));
            eye.setTranslateZ(0.135);
            Sphere pupil = new Sphere(0.040, 10);
            pupil.setMaterial(black);
            pupil.setTranslateX(i * 0.112);
            pupil.setTranslateY(Meshes.jy(1.545));
            pupil.setTranslateZ(0.198);
            g.getChildren().addAll(eye, pupil);
        }

        snout(g, def, body, belly, accent, black);
        ears(g, def, body, belly, accent);
        headgear(g, def, accent, black);
        brows(g, def);

        if (def.horns) {
            for (int i = -1; i <= 1; i += 2) {
                Group horn = Meshes.cone(0.06, 0.34, 8, accent);
                horn.setTranslateX(i * 0.20);
                horn.setTranslateY(Meshes.jy(1.62));
                horn.setTranslateZ(-0.06);
                horn.getTransforms().add(new Rotate(i * 30, Rotate.Z_AXIS));
                g.getChildren().add(horn);
            }
        }
        if (def.beard) {
            Box beard = new Box(0.22, 0.28, 0.13);
            beard.setMaterial(belly);
            beard.setTranslateY(Meshes.jy(1.23));
            beard.setTranslateZ(0.17);
            g.getChildren().add(beard);
        }
        if (def.spikes) {
            for (int i = 0; i < 10; i++) {
                double a = i * Math.PI * 2 / 10;
                Group spike = Meshes.cone(0.045, 0.22, 6, accent);
                spike.setTranslateX(Math.cos(a) * 0.31);
                spike.setTranslateY(Meshes.jy(1.00 + Math.sin(a) * 0.34));
                spike.setTranslateZ(-0.14);
                spike.getTransforms().add(new Rotate(90 - Math.toDegrees(a), Rotate.Z_AXIS));
                g.getChildren().add(spike);
            }
        }

        // bras poses sur le volant, avec des mains
        for (int i = -1; i <= 1; i += 2) {
            Cylinder arm = new Cylinder(0.062, 0.46, 8);
            arm.setMaterial(body);
            arm.setTranslateX(i * 0.25);
            arm.setTranslateY(Meshes.jy(1.02));
            arm.setTranslateZ(0.30);
            arm.getTransforms().add(new Rotate(58, Rotate.X_AXIS));
            Sphere hand = new Sphere(0.075, 8);
            hand.setMaterial(def.snout == KartDef.Snout.BEAK ? accent : body);
            hand.setTranslateX(i * 0.19);
            hand.setTranslateY(Meshes.jy(0.99));
            hand.setTranslateZ(0.52);
            g.getChildren().addAll(arm, hand);
        }

        // la corpulence etire ou tasse l'ensemble ; le pivot est au plancher du
        // kart, un pilote elance depasse donc davantage du baquet
        switch (def.build) {
            case FIN -> {
                g.setScaleX(0.88);
                g.setScaleZ(0.90);
                g.setScaleY(1.10);
            }
            case TRAPU -> {
                g.setScaleX(1.12);
                g.setScaleZ(1.08);
                g.setScaleY(0.93);
            }
            case NORMAL -> {
            }
        }
        return g;
    }

    // ----------------------------------------------------------------- museau

    private static void snout(Group g, KartDef def, PhongMaterial body, PhongMaterial belly,
                              PhongMaterial accent, PhongMaterial black) {
        switch (def.snout) {
            case BEAK -> {
                Group beak = Meshes.cone(0.105, 0.30, 10, accent);
                beak.setTranslateY(Meshes.jy(1.44));
                beak.setTranslateZ(0.16);
                beak.getTransforms().add(new Rotate(-90, Rotate.X_AXIS));
                g.getChildren().add(beak);
                // pieds palmes qui depassent du baquet
                for (int i = -1; i <= 1; i += 2) {
                    Sphere foot = new Sphere(0.13, 10);
                    foot.setMaterial(accent);
                    foot.setScaleY(0.4);
                    foot.setScaleZ(1.7);
                    foot.setTranslateX(i * 0.17);
                    foot.setTranslateY(Meshes.jy(0.70));
                    foot.setTranslateZ(0.66);
                    g.getChildren().add(foot);
                }
            }
            case MUZZLE -> {
                Box muzzle = new Box(0.21, 0.16, 0.28);
                muzzle.setMaterial(belly);
                muzzle.setTranslateY(Meshes.jy(1.41));
                muzzle.setTranslateZ(0.20);
                Sphere nose = new Sphere(0.065, 10);
                nose.setMaterial(black);
                nose.setTranslateY(Meshes.jy(1.43));
                nose.setTranslateZ(0.34);
                g.getChildren().addAll(muzzle, nose);
            }
            case HORN_SNOUT -> {
                Box muzzle = new Box(0.23, 0.17, 0.32);
                muzzle.setMaterial(body);
                muzzle.setTranslateY(Meshes.jy(1.40));
                muzzle.setTranslateZ(0.22);
                Group horn = Meshes.cone(0.055, 0.22, 8, accent);
                horn.setTranslateY(Meshes.jy(1.50));
                horn.setTranslateZ(0.30);
                horn.getTransforms().add(new Rotate(-58, Rotate.X_AXIS));
                g.getChildren().addAll(muzzle, horn);
                // crete dorsale
                for (int i = 0; i < 3; i++) {
                    Group spike = Meshes.cone(0.07, 0.20, 6, accent);
                    spike.setTranslateY(Meshes.jy(1.35 - i * 0.16));
                    spike.setTranslateZ(-0.32 - i * 0.06);
                    spike.getTransforms().add(new Rotate(-28, Rotate.X_AXIS));
                    g.getChildren().add(spike);
                }
            }
            case NONE -> {
            }
        }
    }

    // --------------------------------------------------------------- oreilles

    private static void ears(Group g, KartDef def, PhongMaterial body, PhongMaterial belly,
                             PhongMaterial accent) {
        switch (def.ears) {
            case RONDES -> {
                for (int i = -1; i <= 1; i += 2) {
                    Sphere ear = new Sphere(0.115, 10);
                    ear.setMaterial(body);
                    ear.setScaleZ(0.42);
                    ear.setTranslateX(i * 0.25);
                    ear.setTranslateY(Meshes.jy(1.60));
                    ear.setTranslateZ(HEAD_Z);
                    Sphere inner = new Sphere(0.068, 8);
                    inner.setMaterial(belly);
                    inner.setScaleZ(0.30);
                    inner.setTranslateX(i * 0.27);
                    inner.setTranslateY(Meshes.jy(1.60));
                    inner.setTranslateZ(HEAD_Z + 0.03);
                    g.getChildren().addAll(ear, inner);
                }
            }
            case LONGUES -> {
                for (int i = -1; i <= 1; i += 2) {
                    Sphere ear = new Sphere(0.075, 10);
                    ear.setMaterial(body);
                    ear.setScaleY(2.9);
                    ear.setScaleZ(0.55);
                    ear.setTranslateX(i * 0.21);
                    ear.setTranslateY(Meshes.jy(1.74));
                    ear.setTranslateZ(HEAD_Z - 0.02);
                    ear.getTransforms().add(new Rotate(i * 16, Rotate.Z_AXIS));
                    g.getChildren().add(ear);
                }
            }
            case POINTUES -> {
                for (int i = -1; i <= 1; i += 2) {
                    Group ear = Meshes.cone(0.10, 0.30, 6, body);
                    ear.setTranslateX(i * 0.18);
                    ear.setTranslateY(Meshes.jy(1.62));
                    ear.setTranslateZ(HEAD_Z - 0.02);
                    ear.getTransforms().add(new Rotate(i * 24, Rotate.Z_AXIS));
                    Group inner = Meshes.cone(0.055, 0.20, 6, belly);
                    inner.setTranslateX(i * 0.18);
                    inner.setTranslateY(Meshes.jy(1.66));
                    inner.setTranslateZ(HEAD_Z + 0.05);
                    inner.getTransforms().add(new Rotate(i * 24, Rotate.Z_AXIS));
                    g.getChildren().addAll(ear, inner);
                }
            }
            case NAGEOIRE -> {
                // nageoire dorsale : trois cones ecrases dans l'axe du kart
                for (int i = 0; i < 3; i++) {
                    Group fin = Meshes.cone(0.15 - i * 0.03, 0.26 - i * 0.05, 6, accent);
                    fin.setScaleX(0.22);
                    fin.setTranslateY(Meshes.jy(1.70));
                    fin.setTranslateZ(HEAD_Z + 0.06 - i * 0.14);
                    g.getChildren().add(fin);
                }
            }
            case TOUFFE -> {
                // huppe qui s'echappe par l'arriere du crane, sous un couvre-chef
                for (int i = 0; i < 3; i++) {
                    Group tuft = Meshes.cone(0.055, 0.24 - i * 0.03, 6, body);
                    tuft.setTranslateX((i - 1) * 0.08);
                    tuft.setTranslateY(Meshes.jy(1.68));
                    tuft.setTranslateZ(HEAD_Z - 0.10);
                    tuft.getTransforms().add(new Rotate(30 + i * 11, Rotate.X_AXIS));
                    g.getChildren().add(tuft);
                }
            }
            case NONE -> {
            }
        }
    }

    // ------------------------------------------------------------ couvre-chef

    private static void headgear(Group g, KartDef def, PhongMaterial accent, PhongMaterial black) {
        PhongMaterial trim = Meshes.material(def.trimColor);
        switch (def.gear) {
            case CASQUETTE -> {
                Sphere dome = new Sphere(0.285, 14);
                dome.setMaterial(accent);
                dome.setScaleY(0.52);
                dome.setTranslateY(Meshes.jy(1.70));
                dome.setTranslateZ(HEAD_Z);
                Box visor = new Box(0.34, 0.035, 0.26);
                visor.setMaterial(accent);
                visor.setTranslateY(Meshes.jy(1.655));
                visor.setTranslateZ(0.20);
                visor.getTransforms().add(new Rotate(-8, Rotate.X_AXIS));
                g.getChildren().addAll(dome, visor);
            }
            case CASQUE -> {
                Sphere shell = new Sphere(0.305, 16);
                shell.setMaterial(accent);
                shell.setScaleY(0.50);
                shell.setTranslateY(Meshes.jy(1.72));
                shell.setTranslateZ(HEAD_Z);
                g.getChildren().add(shell);
                // arete centrale et ecouteurs, pour que le casque se lise de profil
                Group crest = Meshes.cone(0.10, 0.16, 6, trim);
                crest.setScaleX(0.30);
                crest.setTranslateY(Meshes.jy(1.79));
                crest.setTranslateZ(HEAD_Z - 0.02);
                g.getChildren().add(crest);
                for (int i = -1; i <= 1; i += 2) {
                    Sphere cup = new Sphere(0.105, 10);
                    cup.setMaterial(trim);
                    cup.setScaleX(0.45);
                    cup.setTranslateX(i * 0.27);
                    cup.setTranslateY(Meshes.jy(1.60));
                    cup.setTranslateZ(HEAD_Z - 0.02);
                    g.getChildren().add(cup);
                }
            }
            case LUNETTES -> {
                // remontees sur le front : les yeux restent visibles
                for (int i = -1; i <= 1; i += 2) {
                    Cylinder lens = new Cylinder(0.088, 0.07, 12);
                    lens.setMaterial(black);
                    lens.setTranslateX(i * 0.115);
                    lens.setTranslateY(Meshes.jy(1.665));
                    lens.setTranslateZ(0.16);
                    lens.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
                    g.getChildren().add(lens);
                }
                Cylinder strap = new Cylinder(0.245, 0.075, 14);
                strap.setMaterial(accent);
                strap.setTranslateY(Meshes.jy(1.665));
                strap.setTranslateZ(HEAD_Z - 0.02);
                strap.getTransforms().add(new Rotate(90, Rotate.X_AXIS));
                g.getChildren().add(strap);
            }
            case COURONNE -> {
                Cylinder band = new Cylinder(0.215, 0.075, 14);
                band.setMaterial(accent);
                band.setTranslateY(Meshes.jy(1.70));
                band.setTranslateZ(HEAD_Z);
                g.getChildren().add(band);
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI * 2 / 6;
                    Group point = Meshes.cone(0.045, 0.14, 6, accent);
                    point.setTranslateX(Math.cos(a) * 0.19);
                    point.setTranslateY(Meshes.jy(1.74));
                    point.setTranslateZ(HEAD_Z + Math.sin(a) * 0.19);
                    g.getChildren().add(point);
                }
            }
            case BANDEAU -> {
                Cylinder band = new Cylinder(0.225, 0.085, 14);
                band.setMaterial(accent);
                band.setTranslateY(Meshes.jy(1.68));
                band.setTranslateZ(HEAD_Z);
                g.getChildren().add(band);
                // les deux pans flottent derriere la tete
                for (int i = -1; i <= 1; i += 2) {
                    Box tail = new Box(0.05, 0.24, 0.05);
                    tail.setMaterial(accent);
                    tail.setTranslateX(i * 0.07);
                    tail.setTranslateY(Meshes.jy(1.58));
                    tail.setTranslateZ(HEAD_Z - 0.24);
                    tail.getTransforms().add(new Rotate(i * 12, Rotate.Z_AXIS));
                    g.getChildren().add(tail);
                }
            }
            case NONE -> {
            }
        }
    }

    // --------------------------------------------------------------- sourcils

    private static void brows(Group g, KartDef def) {
        if (def.brow == KartDef.Brow.NONE) return;
        double lum = 0.299 * def.bodyColor.getRed() + 0.587 * def.bodyColor.getGreen()
                + 0.114 * def.bodyColor.getBlue();
        // sur une mascotte sombre, un sourcil sombre ne se verrait pas
        PhongMaterial mat = Meshes.material(lum > 0.35
                ? def.bodyColor.deriveColor(0, 1, 0.45, 1)
                : def.bodyColor.interpolate(Color.web("#e6e6ee"), 0.78));
        for (int i = -1; i <= 1; i += 2) {
            Box brow = new Box(0.125, 0.032, 0.05);
            brow.setMaterial(mat);
            brow.setTranslateX(i * 0.115);
            brow.setTranslateY(Meshes.jy(1.638));
            brow.setTranslateZ(0.175);
            // fache : le bout interieur plonge vers le nez ; doux : il remonte
            double tilt = def.brow == KartDef.Brow.FACHE ? -i * 17 : i * 13;
            brow.getTransforms().add(new Rotate(tilt, Rotate.Z_AXIS));
            g.getChildren().add(brow);
        }
    }
}
