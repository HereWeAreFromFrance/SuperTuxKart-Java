package org.tuxkart.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reglages, niveaux de detail et journal de performances. */
class CoreTest {

    @Test
    @DisplayName("les reglages par defaut sont jouables")
    void reglagesParDefaut() {
        GameSettings s = new GameSettings();
        assertEquals(s.heaviestQuality().frameCap, s.effectiveFrameCap(),
                "par defaut la limite suit le plus lourd des deux axes");
        s.frameCap = 45;
        assertEquals(45, s.effectiveFrameCap(), "un choix explicite doit primer");
        s.frameCap = -1;
        assertNotNull(s.kart);
        assertNotNull(s.track);
        assertNotNull(s.difficulty);
        assertNotNull(s.polygons);
        assertNotNull(s.textures);
        assertTrue(s.laps >= 1 && s.laps <= 9);
        assertTrue(s.opponents >= 0 && s.opponents <= 7);
        assertEquals(s.opponents + 1, s.totalKarts());
        assertTrue(s.cameraMode >= 0);
    }

    @Test
    @DisplayName("les niveaux de detail sont ordonnes du plus leger au plus lourd")
    void niveauxOrdonnes() {
        Quality[] q = Quality.values();
        assertEquals(4, q.length, "quatre modes, pas plus");
        for (int i = 1; i < q.length; i++) {
            assertTrue(q[i].textureSize >= q[i - 1].textureSize,
                    q[i] + " : textures plus petites que " + q[i - 1]);
            assertTrue(q[i].terrainCell <= q[i - 1].terrainCell,
                    q[i] + " : terrain moins fin que " + q[i - 1]);
            assertTrue(q[i].decorDensity >= q[i - 1].decorDensity,
                    q[i] + " : decor moins dense que " + q[i - 1]);
            assertTrue(q[i].terrainMargin >= q[i - 1].terrainMargin,
                    q[i] + " : paysage moins etendu que " + q[i - 1]);
            assertTrue(q[i].supersample >= q[i - 1].supersample,
                    q[i] + " : moins d'anticrenelage que " + q[i - 1]);
            assertTrue(q[i].sphereDiv >= q[i - 1].sphereDiv,
                    q[i] + " : spheres moins facettees que " + q[i - 1]);
            assertTrue(q[i].coneSides >= q[i - 1].coneSides,
                    q[i] + " : cones moins facettes que " + q[i - 1]);
        }
        for (Quality level : q) {
            assertTrue(level.frameCap == 0 || level.frameCap >= 60,
                    level + " : un niveau ne doit jamais brider sous soixante images");
        }
        // le niveau dont l'objet est la cadence brute ne peut pas etre bride :
        // la machine y depasse largement les soixante images de l'ecran
        assertEquals(0, Quality.FLUIDE.frameCap, "Fluide ne doit pas etre bride");
        // l'axe des textures se coupe l'anticrenelage en premier : c'est ce
        // qui coute le plus cher pour ce qu'il rapporte
        assertFalse(Quality.FLUIDE.antialias, "Fluide ne paie pas le multi-echantillonnage");
        assertTrue(Quality.ULTRA.antialias);
        assertEquals(Quality.ULTRA, Quality.heaviest(Quality.FLUIDE, Quality.ULTRA));
        assertEquals(Quality.QUALITE, Quality.heaviest(Quality.QUALITE, Quality.EQUILIBRE));
        for (Quality level : q) {
            assertTrue(level.textureSize >= 128 && level.textureSize <= 2048);
            assertTrue(level.terrainCell > 0);
            assertTrue(level.sphereDiv >= 4 && level.coneSides >= 4,
                    level + " : facettes trop rares pour faire un volume");
            assertTrue(level.supersample >= 1.0 && level.supersample <= 3.0,
                    level + " : facteur de surechantillonnage deraisonnable");
        }
    }

    @Test
    @DisplayName("les difficultes sont croissantes")
    void difficultesCroissantes() {
        Difficulty[] d = Difficulty.values();
        for (int i = 1; i < d.length; i++) {
            assertTrue(d[i].paceFactor > d[i - 1].paceFactor);
            assertTrue(d[i].lineQuality > d[i - 1].lineQuality);
            assertTrue(d[i].itemSkill > d[i - 1].itemSkill);
        }
        for (Difficulty level : d) {
            assertTrue(level.paceFactor > 0.5 && level.paceFactor <= 1.0);
            assertTrue(level.lineQuality > 0 && level.lineQuality <= 1.0);
        }
    }

    @Test
    @DisplayName("la limite de cadence tient la cible, quel que soit le rythme du moteur")
    void limiteDeCadence() {
        // Le piege : remettre le reste de budget a zero arrondit a un nombre
        // entier de battements. A 78 Hz avec une cible a 60, on obtiendrait une
        // image sur deux, soit 39 par seconde. Ce test verrouille le correctif.
        for (double pulseHz : new double[]{78, 100, 144, 240}) {
            FrameLimiter limiter = new FrameLimiter();
            double raw = 1.0 / pulseHz;
            int images = 0;
            double simule = 0, cumul = 0;
            while (simule < 10) {
                double dt = limiter.accept(raw, 60);
                simule += raw;
                if (dt > 0) {
                    images++;
                    cumul += dt;
                }
            }
            double mesuree = images / simule;
            assertTrue(Math.abs(mesuree - 60) < 2,
                    "moteur a " + (int) pulseHz + " Hz : " + Math.round(mesuree)
                            + " images par seconde au lieu de 60");
            assertEquals(simule, cumul, 0.05,
                    "le temps simule doit etre integralement transmis au jeu");
        }
    }

    @Test
    @DisplayName("une limite plus basse que le moteur est respectee, une plus haute est sans effet")
    void limitesExtremes() {
        FrameLimiter limiter = new FrameLimiter();
        int images = 0;
        for (int i = 0; i < 600; i++) {
            if (limiter.accept(1.0 / 60, 30) > 0) images++;
        }
        assertEquals(300, images, 5, "une cible a 30 doit diviser par deux");

        // impossible de depasser la cadence du moteur d'animation
        FrameLimiter rapide = new FrameLimiter();
        images = 0;
        for (int i = 0; i < 600; i++) {
            if (rapide.accept(1.0 / 50, 120) > 0) images++;
        }
        assertEquals(600, images, "toutes les images doivent passer");
    }

    @Test
    @DisplayName("sans limite, chaque battement produit une image")
    void sansLimite() {
        FrameLimiter limiter = new FrameLimiter();
        for (int i = 0; i < 100; i++) {
            assertEquals(1.0 / 90, limiter.accept(1.0 / 90, 0), 1e-12);
        }
    }

    @Test
    @DisplayName("apres une longue pause, le jeu ne rattrape pas en rafale")
    void pasDeRattrapageEnRafale() {
        FrameLimiter limiter = new FrameLimiter();
        limiter.accept(1.0 / 60, 60);
        // une pause d'une demi-seconde, comme une fenetre reduite puis restauree
        double dt = limiter.accept(0.5, 60);
        assertTrue(dt > 0, "l'image suivante doit passer");
        int rafale = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.accept(1e-4, 60) > 0) rafale++;
        }
        assertTrue(rafale <= 1, "au plus une image de rattrapage, pas " + rafale);
    }

    @Test
    @DisplayName("une bouffee de blocages abaisse la cadence visee, un blocage isole non")
    void cadenceAdaptative() {
        // Le defaut mesure : reclamer plus d'images que la chaine graphique
        // n'en livre ne donne pas une cadence plus basse mais reguliere, cela
        // donne des blocages de 100 a 250 ms. Ce test verrouille la descente.
        FrameLimiter isole = new FrameLimiter();
        isole.accept(1.0 / 60, 60);
        isole.accept(0.15, 60);
        for (int i = 0; i < 400; i++) isole.accept(1.0 / 60, 60);
        assertEquals(60, isole.target(),
                "un blocage isole ne dit rien de la cadence tenable");

        FrameLimiter bouffee = new FrameLimiter();
        bouffee.accept(1.0 / 60, 60);
        bouffee.accept(0.15, 60);
        bouffee.accept(0.15, 60);
        assertTrue(bouffee.target() < 60,
                "deux blocages rapproches doivent faire descendre d'un cran");

        bouffee.reset();
        assertEquals(60, bouffee.target(), "reset repart du plafond");
    }

    @Test
    @DisplayName("une capture d'ecran ne fait pas descendre la cadence")
    void laCaptureNeCompteQuePourElleMeme() {
        // scene.snapshot gele le fil d'application deux cents millisecondes, et
        // l'image de rattrapage qui suit est longue elle aussi : deux blocages
        // rapproches, donc la signature exacte d'une file de presentation
        // pleine. Une campagne de captures mesurait ainsi la cadence qu'elle
        // detruisait — 30,9 images par seconde relevees sur le Donjon hante, la
        // ou sept passes en donnent 60. Et le joueur qui appuie sur F12 perdait
        // la moitie de sa cadence pour le reste de la course.
        FrameLimiter avecCapture = new FrameLimiter();
        avecCapture.accept(1.0 / 60, 60);
        avecCapture.capture();
        avecCapture.accept(0.20, 60);
        avecCapture.accept(0.12, 60);
        assertEquals(60, avecCapture.target(),
                "le gel d'une capture ne dit rien de ce que la scene tient");

        // la grace ne couvre que la capture : la scene reste surveillee juste
        // apres, sinon une capture par seconde aveuglerait tout le limiteur
        FrameLimiter apresLaGrace = new FrameLimiter();
        apresLaGrace.accept(1.0 / 60, 60);
        apresLaGrace.capture();
        apresLaGrace.accept(0.20, 60);
        for (int i = 0; i < 60; i++) apresLaGrace.accept(1.0 / 60, 60);
        apresLaGrace.accept(0.15, 60);
        apresLaGrace.accept(0.15, 60);
        assertTrue(apresLaGrace.target() < 60,
                "passe la grace, une vraie bouffee doit toujours faire descendre");
    }

    @Test
    @DisplayName("un incident isole se rattrape, une scene trop lourde cesse d'etre sondee")
    void cadenceAdaptativeRemontee() {
        // Ne jamais remonter verrouillait toute la course a la moitie de la
        // cadence des qu'une autre application prenait la carte graphique.
        FrameLimiter limiter = new FrameLimiter();
        for (int i = 0; i < 60; i++) limiter.accept(1.0 / 60, 60);
        limiter.accept(0.15, 60);
        limiter.accept(0.15, 60);
        int apresBouffee = limiter.target();
        assertTrue(apresBouffee < 60, "la bouffee doit faire descendre");

        // trente secondes propres : au-dela de la quarantaine de vingt
        for (int i = 0; i < 30 * 60; i++) limiter.accept(1.0 / 60, 60);
        assertEquals(60, limiter.target(), "un incident isole doit se rattraper");

        // en revanche, une scene qui bloque a chaque remontee finit par ne plus
        // etre sondee : la quarantaine double a chaque descente
        for (int passe = 0; passe < 3; passe++) {
            limiter.accept(0.15, 60);
            limiter.accept(0.15, 60);
            for (int i = 0; i < 30 * 60; i++) limiter.accept(1.0 / 60, 60);
        }
        assertTrue(limiter.target() < 60,
                "apres trois descentes, trente secondes de calme ne suffisent plus");
    }

    @Test
    @DisplayName("la descente s'arrete au plancher, meme sans limite demandee")
    void cadenceAdaptativePlancher() {
        // « Aucune limite » veut dire que le joueur n'impose pas de cadence,
        // pas que le jeu doive remplir la file de presentation.
        FrameLimiter limiter = new FrameLimiter();
        for (int i = 0; i < 200; i++) limiter.accept(0.15, 0);
        assertEquals(FrameLimiter.PLANCHER, limiter.target(),
                "la descente doit s'arreter la ou le jeu se pilote encore");
        assertTrue(FrameLimiter.PLANCHER >= 20, "en dessous de vingt, le jeu n'est plus jouable");
    }

    @Test
    @DisplayName("le journal de performances ecrit un fichier lisible")
    void journalDePerformances(@TempDir Path dir) throws IOException {
        Path fichier = dir.resolve("perf.csv");
        System.setProperty("tuxkart.perflog", fichier.toString());
        try {
            PerfLog log = PerfLog.open("essai");
            assertNotNull(log);
            log.event("depart");
            for (int i = 0; i < 500; i++) {
                log.frame(1.0 / 60, 60);
            }
            log.event("tour");
            log.frame(0.2, 5);
            String resume = log.summary();
            log.close();

            List<String> lignes = Files.readAllLines(fichier);
            assertTrue(lignes.get(0).startsWith("#"), "entete manquante");
            assertTrue(lignes.get(0).contains("essai"), "le contexte doit etre note");
            assertEquals("temps_s;image_ms;fps_rendu;evenement", lignes.get(1));
            assertTrue(lignes.size() > 500, "toutes les images doivent etre ecrites");

            assertTrue(lignes.stream().anyMatch(l -> l.endsWith(";depart")),
                    "l'evenement de depart doit apparaitre");
            assertTrue(lignes.stream().anyMatch(l -> l.endsWith(";tour")),
                    "l'evenement de tour doit apparaitre");
            assertTrue(lignes.get(lignes.size() - 1).startsWith("#"),
                    "le resume doit clore le fichier");

            // le resume doit reperer l'image lente qu'on a injectee
            assertTrue(resume.contains("au-dela de 100 ms : 1"), "resume : " + resume);
            assertTrue(resume.contains("501 images"), "resume : " + resume);
        } finally {
            System.clearProperty("tuxkart.perflog");
        }
    }

    @Test
    @DisplayName("le journal se desactive a la demande")
    void journalDesactivable() {
        System.setProperty("tuxkart.perflog", "off");
        try {
            assertNull(PerfLog.open("essai"), "aucun journal ne doit etre ouvert");
        } finally {
            System.clearProperty("tuxkart.perflog");
        }
    }

    @Test
    @DisplayName("un journal sans image ne casse pas a la fermeture")
    void journalVide(@TempDir Path dir) {
        System.setProperty("tuxkart.perflog", dir.resolve("vide.csv").toString());
        try {
            PerfLog log = PerfLog.open("vide");
            assertNotNull(log);
            assertFalse(log.summary().isBlank());
            log.close();
            log.close();
        } finally {
            System.clearProperty("tuxkart.perflog");
        }
    }
}
