package org.tuxkart.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Les briques mathematiques dont depend tout le reste du jeu. */
class MathTest {

    @Test
    @DisplayName("le modulo reste positif pour les entrees negatives")
    void moduloEstToujoursPositif() {
        assertAll(
                () -> assertEquals(3.0, MathUtil.mod(-7, 10), 1e-9),
                () -> assertEquals(0.0, MathUtil.mod(-10, 10), 1e-9),
                () -> assertEquals(7, MathUtil.mod(-3, 10)),
                () -> assertEquals(0, MathUtil.mod(-10, 10)));
    }

    @Test
    @DisplayName("wrapPi ramene tout angle dans ]-PI, PI]")
    void wrapPiBorneLAngle() {
        for (double a = -20; a <= 20; a += 0.37) {
            double w = MathUtil.wrapPi(a);
            // l'intervalle est ouvert en -PI : une tolerance le refermerait et
            // le test ne verifierait plus la borne annoncee
            assertTrue(w > -Math.PI && w <= Math.PI, "angle " + a + " -> " + w);
            // l'angle equivalent doit etre conserve
            assertEquals(Math.sin(a), Math.sin(w), 1e-9);
            assertEquals(Math.cos(a), Math.cos(w), 1e-9);
        }
    }

    @Test
    @DisplayName("approach atteint la cible sans la depasser")
    void approachNeDepassePas() {
        assertEquals(5.0, MathUtil.approach(0, 5, 100), 1e-9);
        assertEquals(-5.0, MathUtil.approach(0, -5, 100), 1e-9);
        assertEquals(1.0, MathUtil.approach(0, 5, 1), 1e-9);
        assertEquals(5.0, MathUtil.approach(5, 5, 1), 1e-9);
    }

    @Test
    @DisplayName("damp est independant du pas de temps")
    void dampNeDependPasDuPasDeTemps() {
        double enUnPas = MathUtil.damp(0, 10, 4, 0.5);
        double courant = 0;
        for (int i = 0; i < 50; i++) courant = MathUtil.damp(courant, 10, 4, 0.01);
        assertEquals(enUnPas, courant, 1e-9,
                "cinquante petits pas doivent donner le meme resultat qu'un grand");
    }

    @Test
    @DisplayName("smoothstep est borne et monotone")
    void smoothstepEstBorneEtMonotone() {
        assertEquals(0, MathUtil.smoothstep(0, 1, -3), 1e-9);
        assertEquals(1, MathUtil.smoothstep(0, 1, 4), 1e-9);
        double precedent = -1;
        for (double x = -0.5; x <= 1.5; x += 0.05) {
            double v = MathUtil.smoothstep(0, 1, x);
            assertTrue(v >= precedent - 1e-12, "doit croitre en " + x);
            precedent = v;
        }
    }

    @Test
    @DisplayName("le chrono est formate en minutes, secondes et centiemes")
    void formatageDuChrono() {
        assertEquals("0:00.00", MathUtil.formatTime(0));
        assertEquals("1:05.25", MathUtil.formatTime(65.25));
        assertEquals("2:00.00", MathUtil.formatTime(120));
        assertEquals("--:--.--", MathUtil.formatTime(-1));
        assertEquals("--:--.--", MathUtil.formatTime(Double.NaN));
        assertEquals("--:--.--", MathUtil.formatTime(Double.MAX_VALUE / 2));
    }

    @Test
    @DisplayName("cap et vecteur sont reciproques")
    void capEtVecteurSontReciproques() {
        // convention centrale du jeu : cap 0 = +Z, croissant vers +X
        for (double h = -3; h <= 3; h += 0.25) {
            Vec3 f = Vec3.fromHeading(h);
            assertEquals(1.0, f.length(), 1e-9);
            assertEquals(h, f.heading(), 1e-9);
        }
        assertEquals(0, Vec3.fromHeading(0).x, 1e-9);
        assertEquals(1, Vec3.fromHeading(0).z, 1e-9);
        assertEquals(1, Vec3.fromHeading(Math.PI / 2).x, 1e-9);
    }

    @Test
    @DisplayName("le vecteur lateral est perpendiculaire au cap")
    void lateralPerpendiculaireAuCap() {
        for (double h = -3; h <= 3; h += 0.3) {
            Vec3 f = Vec3.fromHeading(h);
            Vec3 side = new Vec3(f.z, 0, -f.x);
            assertEquals(0, f.dot(side), 1e-9);
            assertEquals(1, side.length(), 1e-9);
        }
    }

    @Test
    @DisplayName("les operations vectorielles de base sont correctes")
    void operationsVectorielles() {
        Vec3 a = new Vec3(3, 4, 0);
        assertEquals(5, a.length(), 1e-9);
        assertEquals(1, a.normalize().length(), 1e-9);
        assertEquals(0, Vec3.ZERO.normalize().length(), 1e-9,
                "normaliser le vecteur nul doit rendre le vecteur nul, pas NaN");
        assertEquals(1, new Vec3(1, 0, 0).cross(new Vec3(0, 1, 0)).z, 1e-9);
        assertEquals(5, new Vec3(3, 99, 4).lengthXZ(), 1e-9);
        assertEquals(5, new Vec3(0, 0, 0).distanceXZ(new Vec3(3, 7, 4)), 1e-9);
    }

    @Test
    @DisplayName("la spline fermee passe par ses points de controle")
    void laSplinePasseParSesPoints() {
        var points = java.util.List.of(
                new Vec3(0, 0, 0), new Vec3(10, 1, 0),
                new Vec3(10, 2, 10), new Vec3(0, 0, 10));
        Spline s = new Spline(points);
        for (int i = 0; i < points.size(); i++) {
            Vec3 p = s.point(i);
            assertEquals(points.get(i).x, p.x, 1e-9, "point " + i);
            assertEquals(points.get(i).y, p.y, 1e-9, "point " + i);
            assertEquals(points.get(i).z, p.z, 1e-9, "point " + i);
        }
        // et elle boucle vraiment
        Vec3 debut = s.point(0);
        Vec3 tour = s.point(points.size());
        assertEquals(debut.x, tour.x, 1e-9);
        assertEquals(debut.z, tour.z, 1e-9);
    }

    @Test
    @DisplayName("la spline est continue, sans saut entre segments")
    void laSplineEstContinue() {
        var points = java.util.List.of(
                new Vec3(0, 0, 0), new Vec3(30, 0, 5),
                new Vec3(40, 0, 40), new Vec3(-10, 0, 30), new Vec3(-20, 0, 5));
        Spline s = new Spline(points);
        Vec3 precedent = s.point(0);
        for (double t = 0.01; t <= points.size(); t += 0.01) {
            Vec3 p = s.point(t);
            assertTrue(p.distance(precedent) < 2.0,
                    "saut de " + p.distance(precedent) + " en t=" + t);
            precedent = p;
        }
    }

    @Test
    @DisplayName("le bruit est borne, reproductible, et differe selon la graine")
    void proprietesDuBruit() {
        Noise a = new Noise(42);
        Noise b = new Noise(42);
        Noise c = new Noise(43);
        double ecart = 0;
        for (double x = 0; x < 40; x += 0.7) {
            for (double y = 0; y < 40; y += 0.7) {
                double v = a.perlin(x, y);
                assertTrue(v >= -1.001 && v <= 1.001, "perlin hors bornes : " + v);
                assertEquals(v, b.perlin(x, y), 1e-12, "meme graine, meme bruit");
                ecart += Math.abs(v - c.perlin(x, y));
                double f = a.fbm(x, y, 4);
                assertTrue(f >= -1.001 && f <= 1.001, "fbm hors bornes : " + f);
                double r = a.ridged(x, y, 3);
                assertTrue(r >= -0.001 && r <= 1.001, "ridged hors bornes : " + r);
            }
        }
        assertTrue(ecart > 1, "deux graines differentes doivent donner des bruits differents");
    }

    @Test
    @DisplayName("le bruit varie vraiment dans l'espace")
    void leBruitNEstPasConstant() {
        Noise n = new Noise(7);
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE;
        for (double x = 0; x < 60; x += 0.3) {
            double v = n.perlin(x, x * 0.37);
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        assertTrue(max - min > 0.8, "amplitude trop faible : " + (max - min));
        assertNotEquals(min, max);
    }
}
