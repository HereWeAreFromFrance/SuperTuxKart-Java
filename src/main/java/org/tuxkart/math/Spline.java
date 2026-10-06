package org.tuxkart.math;

import java.util.List;

/**
 * Spline de Catmull-Rom fermee : passe exactement par tous les points de
 * controle, ce qui rend le trace des circuits facile a auteur a la main.
 */
public final class Spline {

    private final Vec3[] cp;

    public Spline(List<Vec3> points) {
        if (points.size() < 4) {
            throw new IllegalArgumentException("Une spline fermee demande au moins 4 points de controle");
        }
        this.cp = points.toArray(new Vec3[0]);
    }

    public int segmentCount() {
        return cp.length;
    }

    private Vec3 at(int i) {
        return cp[MathUtil.mod(i, cp.length)];
    }

    /**
     * @param t parametre continu ; la boucle complete correspond a [0, segmentCount()[.
     */
    public Vec3 point(double t) {
        int n = cp.length;
        double tt = MathUtil.mod(t, n);
        int i = (int) Math.floor(tt);
        double u = tt - i;

        Vec3 p0 = at(i - 1), p1 = at(i), p2 = at(i + 1), p3 = at(i + 2);

        double u2 = u * u;
        double u3 = u2 * u;

        double c0 = -0.5 * u3 + u2 - 0.5 * u;
        double c1 = 1.5 * u3 - 2.5 * u2 + 1.0;
        double c2 = -1.5 * u3 + 2.0 * u2 + 0.5 * u;
        double c3 = 0.5 * u3 - 0.5 * u2;

        return new Vec3(
                c0 * p0.x + c1 * p1.x + c2 * p2.x + c3 * p3.x,
                c0 * p0.y + c1 * p1.y + c2 * p2.y + c3 * p3.y,
                c0 * p0.z + c1 * p1.z + c2 * p2.z + c3 * p3.z);
    }
}
