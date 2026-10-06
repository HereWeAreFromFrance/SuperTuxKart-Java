package org.tuxkart.math;

/**
 * Vecteur 3D immuable.
 *
 * Convention du monde du jeu : le plan du sol est (X, Z), Y est l'altitude
 * et pointe vers le HAUT. JavaFX, lui, oriente Y vers le BAS : la conversion
 * (une simple negation de Y) n'a lieu qu'au moment du rendu.
 */
public final class Vec3 {

    public static final Vec3 ZERO = new Vec3(0, 0, 0);
    public static final Vec3 UP = new Vec3(0, 1, 0);

    public final double x, y, z;

    public Vec3(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static Vec3 xz(double x, double z) {
        return new Vec3(x, 0, z);
    }

    public Vec3 add(Vec3 o) {
        return new Vec3(x + o.x, y + o.y, z + o.z);
    }

    public Vec3 add(double dx, double dy, double dz) {
        return new Vec3(x + dx, y + dy, z + dz);
    }

    public Vec3 sub(Vec3 o) {
        return new Vec3(x - o.x, y - o.y, z - o.z);
    }

    public Vec3 mul(double s) {
        return new Vec3(x * s, y * s, z * s);
    }

    public Vec3 withY(double ny) {
        return new Vec3(x, ny, z);
    }

    public double dot(Vec3 o) {
        return x * o.x + y * o.y + z * o.z;
    }

    public Vec3 cross(Vec3 o) {
        return new Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x);
    }

    public double length() {
        return Math.sqrt(x * x + y * y + z * z);
    }

    public double lengthXZ() {
        return Math.sqrt(x * x + z * z);
    }

    public double distance(Vec3 o) {
        return sub(o).length();
    }

    public double distanceXZ(Vec3 o) {
        double dx = x - o.x, dz = z - o.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    public Vec3 normalize() {
        double l = length();
        return l < 1e-9 ? ZERO : new Vec3(x / l, y / l, z / l);
    }

    public Vec3 normalizeXZ() {
        double l = lengthXZ();
        return l < 1e-9 ? new Vec3(0, 0, 1) : new Vec3(x / l, 0, z / l);
    }

    public static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return new Vec3(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t);
    }

    /** Angle de cap (radians) d'un vecteur horizontal, 0 = +Z, croissant vers +X. */
    public double heading() {
        return Math.atan2(x, z);
    }

    /** Vecteur horizontal unitaire correspondant a un cap. */
    public static Vec3 fromHeading(double h) {
        return new Vec3(Math.sin(h), 0, Math.cos(h));
    }

    @Override
    public String toString() {
        return String.format("(%.2f, %.2f, %.2f)", x, y, z);
    }
}
