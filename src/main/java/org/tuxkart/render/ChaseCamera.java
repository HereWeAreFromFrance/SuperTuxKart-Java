package org.tuxkart.render;

import javafx.scene.PerspectiveCamera;
import javafx.scene.transform.Affine;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;

/** Camera de poursuite, avec vue capot, vue large et regard vers l'arriere. */
public final class ChaseCamera {

    public static final int MODE_CHASE = 0;
    public static final int MODE_COCKPIT = 1;
    public static final int MODE_WIDE = 2;
    public static final int MODE_COUNT = 3;

    public static final String[] MODE_LABELS = {"Poursuite", "Pare-chocs", "Large"};

    public final PerspectiveCamera camera = new PerspectiveCamera(true);

    /**
     * Tremblement des impacts.
     *
     * Un flux a part, et non l'alea commun : partage, sa suite dependait de
     * l'ordre dans lequel les autres sous-systemes puisaient dedans, et cet
     * ordre varie d'une execution a l'autre. Deux courses de meme graine
     * cadraient alors legerement differemment apres le moindre choc.
     */
    private final java.util.Random jitter = org.tuxkart.core.Rng.stream(7);
    private final Affine affine = new Affine();

    private Vec3 pos = Vec3.ZERO;
    private Vec3 look = new Vec3(0, 0, 1);
    private double shake;

    public ChaseCamera() {
        camera.setNearClip(0.5);
        camera.setFarClip(5200);
        camera.setFieldOfView(62);
        camera.setVerticalFieldOfView(false);
        camera.getTransforms().add(affine);
    }

    /**
     * Oriente une camera fixe (utilisee par les apercus des menus).
     * Les positions sont donnees dans le repere du jeu, Y vers le haut.
     */
    public static void aim(PerspectiveCamera cam, Vec3 eye, Vec3 target) {
        double px = eye.x, py = -eye.y, pz = eye.z;
        Vec3 f = new Vec3(target.x - px, -target.y - py, target.z - pz).normalize();
        Vec3 down = new Vec3(0, 1, 0);
        Vec3 r = down.cross(f).normalize();
        if (r.length() < 1e-6) r = new Vec3(1, 0, 0);
        Vec3 u = f.cross(r);
        Affine a = new Affine(
                r.x, u.x, f.x, px,
                r.y, u.y, f.y, py,
                r.z, u.z, f.z, pz);
        cam.getTransforms().setAll(a);
    }

    /** Position de l'oeil dans le repere du jeu. */
    public Vec3 position() {
        return pos;
    }

    /** Point vise dans le repere du jeu ; sa difference avec l'oeil donne l'axe de visee. */
    public Vec3 target() {
        return look;
    }

    public void addShake(double amount) {
        shake = Math.min(1.2, shake + amount);
    }

    public void snapTo(Kart kart, int mode) {
        Placement p = placement(kart, mode, false);
        pos = p.eye;
        look = p.target;
        apply();
    }

    private static final class Placement {
        Vec3 eye;
        Vec3 target;
    }

    private Placement placement(Kart kart, int mode, boolean lookBack) {
        Vec3 f = kart.forwardVec();
        if (lookBack) f = f.mul(-1);
        double speedNorm = Math.clamp(Math.abs(kart.speed) / kart.def.maxSpeed, 0, 1);

        double back, up, ahead, height;
        switch (mode) {
            case MODE_COCKPIT -> {
                // vue pare-chocs : devant le kart, sinon la camera se retrouve
                // a l'interieur de la tete du pilote
                back = -1.85;
                up = 1.05;
                ahead = 17;
                height = 1.05;
            }
            case MODE_WIDE -> {
                back = 11.5 + speedNorm * 2.5;
                up = 5.2;
                ahead = 16;
                height = 1.6;
            }
            default -> {
                back = 7.4 + speedNorm * 2.4;
                up = 3.25;
                ahead = 14;
                height = 1.45;
            }
        }

        Placement p = new Placement();
        p.eye = new Vec3(
                kart.pos.x - f.x * back,
                kart.visualY() + up,
                kart.pos.z - f.z * back);
        p.target = new Vec3(
                kart.pos.x + f.x * ahead,
                kart.visualY() + height,
                kart.pos.z + f.z * ahead);
        return p;
    }

    public void update(Kart kart, double dt, int mode, boolean lookBack) {
        Placement p = placement(kart, mode, lookBack);

        double follow = (mode == MODE_COCKPIT || lookBack) ? 28 : 8.5;
        pos = new Vec3(
                MathUtil.damp(pos.x, p.eye.x, follow, dt),
                MathUtil.damp(pos.y, p.eye.y, follow * 1.4, dt),
                MathUtil.damp(pos.z, p.eye.z, follow, dt));
        look = new Vec3(
                MathUtil.damp(look.x, p.target.x, follow * 1.6, dt),
                MathUtil.damp(look.y, p.target.y, follow * 1.6, dt),
                MathUtil.damp(look.z, p.target.z, follow * 1.6, dt));

        if (shake > 0) shake = Math.max(0, shake - dt * 2.2);

        double speedNorm = Math.clamp(Math.abs(kart.speed) / kart.def.maxSpeed, 0, 1);
        double boost = kart.boosting() ? 6 : 0;
        camera.setFieldOfView(60 + speedNorm * 9 + boost);

        apply();
    }

    private void apply() {
        double sx = 0, sy = 0;
        if (shake > 0) {
            sx = (jitter.nextDouble() - 0.5) * shake * 0.5;
            sy = (jitter.nextDouble() - 0.5) * shake * 0.5;
        }

        // passage en repere JavaFX : Y vers le bas
        double px = pos.x + sx, py = -(pos.y + sy), pz = pos.z;
        double tx = look.x, ty = -look.y, tz = look.z;

        Vec3 f = new Vec3(tx - px, ty - py, tz - pz).normalize();
        Vec3 down = new Vec3(0, 1, 0);
        Vec3 r = down.cross(f).normalize();
        if (r.length() < 1e-6) r = new Vec3(1, 0, 0);
        Vec3 u = f.cross(r);

        affine.setToTransform(
                r.x, u.x, f.x, px,
                r.y, u.y, f.y, py,
                r.z, u.z, f.z, pz);
    }
}
