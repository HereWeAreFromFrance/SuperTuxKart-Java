package org.tuxkart.ui;

import javafx.scene.AmbientLight;
import javafx.scene.Group;
import javafx.scene.PerspectiveCamera;
import javafx.scene.PointLight;
import javafx.scene.SceneAntialiasing;
import javafx.scene.SubScene;
import javafx.scene.paint.Color;
import javafx.scene.shape.Cylinder;
import javafx.scene.transform.Rotate;
import org.tuxkart.kart.KartDef;
import org.tuxkart.math.Vec3;
import org.tuxkart.render.ChaseCamera;
import org.tuxkart.render.KartNode;
import org.tuxkart.render.Meshes;

/** Apercu 3D d'un kart qui tourne sur lui-meme, pour les menus. */
public final class KartPreview {

    public final SubScene subScene;
    private final Group turntable = new Group();
    private final Rotate spin = new Rotate(0, Rotate.Y_AXIS);
    private final Group world = new Group();
    private double angle = 25;

    public KartPreview(double w, double h) {
        turntable.getTransforms().add(spin);

        Cylinder base = new Cylinder(2.9, 0.12, 24);
        base.setMaterial(Meshes.material(Color.rgb(28, 46, 70, 0.75)));
        base.setTranslateY(Meshes.jy(-0.06));

        AmbientLight ambient = new AmbientLight(Color.gray(0.66));
        PointLight key = new PointLight(Color.rgb(255, 244, 220));
        key.setTranslateX(6);
        key.setTranslateZ(-7);
        key.setTranslateY(Meshes.jy(9));
        PointLight fill = new PointLight(Color.rgb(120, 150, 190, 0.9));
        fill.setTranslateX(-7);
        fill.setTranslateZ(6);
        fill.setTranslateY(Meshes.jy(-4));

        world.getChildren().addAll(base, turntable, ambient, key, fill);

        PerspectiveCamera cam = new PerspectiveCamera(true);
        cam.setNearClip(0.1);
        cam.setFarClip(200);
        cam.setFieldOfView(38);
        ChaseCamera.aim(cam, new Vec3(0, 2.4, -8.2), new Vec3(0, 0.85, 0));

        subScene = new SubScene(world, w, h, true, SceneAntialiasing.BALANCED);
        subScene.setCamera(cam);
        subScene.setFill(Color.TRANSPARENT);
    }

    /**
     * Modeles deja assembles.
     *
     * Un kart represente une centaine de pieces et pres de quarante
     * millisecondes de construction : le rebatir a chaque fleche du menu se
     * voyait comme un a-coup. Le plateau compte huit machines, on les garde.
     */
    private final java.util.Map<KartDef, KartNode> models = new java.util.HashMap<>();

    public void setKart(KartDef def) {
        KartNode node = models.computeIfAbsent(def, KartNode::new);
        turntable.getChildren().setAll(node.root);
    }

    public void update(double dt) {
        angle += dt * 26;
        spin.setAngle(angle);
    }
}
