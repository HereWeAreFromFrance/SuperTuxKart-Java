package org.tuxkart.race;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuxkart.core.GameSettings;
import org.tuxkart.items.ItemType;
import org.tuxkart.items.Pickup;
import org.tuxkart.items.Projectile;
import org.tuxkart.kart.Kart;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Tracks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Regles de la course : depart, objets, contacts, classement, arrivee. */
class RaceTest {

    private static final double DT = 1.0 / 60;

    private Race race;

    @BeforeEach
    void setUp() {
        GameSettings settings = new GameSettings();
        settings.track = Tracks.PISTE_DE_TUX;
        settings.opponents = 3;
        settings.laps = 2;
        race = new Race(settings);
    }

    private void step(double seconds) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            race.update(DT);
            race.sounds.clear();
        }
    }

    private void startRace() {
        while (race.state == Race.State.COUNTDOWN) {
            race.update(DT);
            race.sounds.clear();
        }
    }

    @Test
    @DisplayName("la course commence par un decompte, puis passe au vert")
    void decomptePuisDepart() {
        assertSame(Race.State.COUNTDOWN, race.state);
        assertTrue(race.time < 0, "le chrono doit etre negatif pendant le decompte");
        for (Kart k : race.karts) {
            assertEquals(0, k.speed, 1e-9, "personne ne bouge avant le vert");
        }
        startRace();
        assertSame(Race.State.RUNNING, race.state);
        assertEquals(0, race.time, 0.05, "le chrono repart de zero au depart");
    }

    @Test
    @DisplayName("le joueur part en fond de grille")
    void leJoueurPartDernier() {
        assertNotNull(race.player);
        assertEquals(race.karts.size(), race.player.position,
                "le joueur doit etre dernier au depart");
        assertTrue(race.player.human);
        long humains = race.karts.stream().filter(k -> k.human).count();
        assertEquals(1, humains, "un seul kart humain");
    }

    @Test
    @DisplayName("le classement suit la progression et reste complet")
    void classementCoherent() {
        startRace();
        step(20);
        var ordre = race.standings();
        assertEquals(race.karts.size(), ordre.size());
        for (int i = 1; i < ordre.size(); i++) {
            assertTrue(ordre.get(i - 1).totalProgress >= ordre.get(i).totalProgress,
                    "classement mal trie en position " + i);
            assertEquals(i + 1, ordre.get(i).position);
        }
    }

    @Test
    @DisplayName("kartAhead designe bien le kart immediatement devant")
    void kartDevant() {
        startRace();
        step(15);
        for (Kart k : race.karts) {
            Kart devant = race.kartAhead(k);
            if (k.position == 1) {
                assertNull(devant, "le leader n'a personne devant");
            } else {
                assertNotNull(devant);
                assertTrue(devant.totalProgress > k.totalProgress);
                assertEquals(k.position - 1, devant.position);
            }
        }
    }

    @Test
    @DisplayName("le turbo est consomme et pousse le kart")
    void turboConsomme() {
        startRace();
        Kart p = race.player;
        p.giveItem(ItemType.ZIPPER);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;

        assertNull(p.item, "l'objet doit etre consomme");
        assertEquals(0, p.itemCount);
        assertTrue(p.boostTimer > 0);
        assertTrue(p.boostFactor > 1.05);
    }

    @Test
    @DisplayName("l'etincelle laisse deux tirs et cree un projectile")
    void etincelleTroisTirs() {
        startRace();
        Kart p = race.player;
        p.giveItem(ItemType.SPARK);
        assertEquals(3, p.itemCount);

        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;
        assertEquals(2, p.itemCount);
        assertSame(ItemType.SPARK, p.item);
        assertEquals(1, race.projectiles.size());
        assertSame(Projectile.Kind.SPARK, race.projectiles.get(0).kind);
    }

    @Test
    @DisplayName("le missile part et vise un kart devant")
    void missileVisDevant() {
        startRace();
        step(8);
        Kart p = race.player;
        // les IA tirent aussi : on repart d'une liste vide pour n'observer que
        // le projectile du joueur
        race.projectiles.clear();
        p.giveItem(ItemType.HOMING);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;

        assertEquals(1, race.projectiles.stream().filter(x -> x.owner == p).count());
        Projectile m = race.projectiles.stream().filter(x -> x.owner == p)
                .findFirst().orElseThrow();
        assertSame(Projectile.Kind.HOMING, m.kind);
        assertSame(p, m.owner);
        if (m.target != null) {
            assertTrue(m.target.totalProgress > p.totalProgress);
        }
    }

    @Test
    @DisplayName("un projectile qui touche met la cible en tete-a-queue")
    void projectileTouche() {
        startRace();
        step(6);
        Kart cible = race.karts.stream().filter(k -> !k.human).findFirst().orElseThrow();
        cible.spinTimer = 0;

        Projectile p = new Projectile(Projectile.Kind.SPARK, race.player,
                new Vec3(cible.pos.x, cible.pos.y, cible.pos.z), 0, 0, 5);
        p.life = 1.0;
        race.projectiles.add(p);
        race.update(DT);

        assertTrue(cible.spinTimer > 0, "la cible doit partir en tete-a-queue");
        // d'autres projectiles peuvent voler : on ne suit que celui-ci
        assertFalse(race.projectiles.contains(p), "le projectile doit disparaitre a l'impact");
    }

    @Test
    @DisplayName("les projectiles finissent par expirer")
    void projectilesExpirent() {
        startRace();
        Kart p = race.player;
        race.projectiles.clear();
        p.giveItem(ItemType.SPARK);
        p.controls.fire = true;
        race.update(DT);
        p.controls.fire = false;

        Projectile tir = race.projectiles.stream().filter(x -> x.owner == p)
                .findFirst().orElseThrow();
        step(12);
        // les IA en creent d'autres entre-temps : on suit celui-ci precisement
        assertFalse(race.projectiles.contains(tir),
                "ce projectile ne doit pas survivre douze secondes");
    }

    @Test
    @DisplayName("deux karts ne restent pas l'un dans l'autre")
    void contactsSeparentLesKarts() {
        startRace();
        Kart a = race.karts.get(0);
        Kart b = race.karts.get(1);
        b.pos = new Vec3(a.pos.x + 0.2, a.pos.y, a.pos.z + 0.2);
        race.update(DT);
        assertTrue(a.pos.distanceXZ(b.pos) > 0.5,
                "les karts devraient s'ecarter : " + a.pos.distanceXZ(b.pos));
    }

    @Test
    @DisplayName("le contact ecarte les karts quelle que soit son orientation dans le monde")
    void contactIndependantDeLOrientation() {
        // Le decompte fige les karts : seul le contact peut encore agir sur eux.
        // Cap a PI/2, donc vecteur lateral du kart aligne sur -Z ; en placant le
        // second kart plein Z, la poussee doit etre maximale. Une projection qui
        // ne regarderait que l'axe X la trouverait nulle.
        Kart a = race.karts.get(0);
        Kart b = race.karts.get(1);
        Vec3 c = race.track.centerAtS(100);
        a.pos = c;
        b.pos = new Vec3(c.x, c.y, c.z + 1.5);
        a.heading = Math.PI / 2;
        b.heading = Math.PI / 2;
        a.speed = b.speed = 0;
        a.lateralVel = b.lateralVel = 0;
        race.track.locate(a.pos.x, a.pos.z, -1, a.loc);
        race.track.locate(b.pos.x, b.pos.z, -1, b.loc);

        race.update(DT);

        assertTrue(Math.abs(a.lateralVel) > 0.5,
                "aucune poussee laterale sur a : " + a.lateralVel);
        assertTrue(Math.abs(b.lateralVel) > 0.5,
                "aucune poussee laterale sur b : " + b.lateralVel);
    }

    @Test
    @DisplayName("l'aimant ne prolonge pas le facteur du turbo")
    void aimantEtTurboSontIndependants() {
        startRace();
        Kart p = race.player;
        p.boost(0.5, 1.62);
        p.magnetTimer = 8;
        assertEquals(1.62, p.speedFactor(), 1e-9);

        // au-dela du turbo il ne doit rester que l'aspiration, bien plus faible,
        // meme si l'aimant rearme son minuteur a chaque image
        step(1.5);
        assertEquals(1.0, p.boostFactor, 1e-9, "le turbo doit avoir expire");
        assertTrue(p.speedFactor() <= 1.20 + 1e-9,
                "poussee residuelle trop forte : " + p.speedFactor());
    }

    @Test
    @DisplayName("la course bascule d'elle-meme vers les resultats apres l'arrivee")
    void arriveePuisFinDeCourse() {
        startRace();
        step(3);
        race.player.totalProgress = race.totalLaps * race.track.length + 1;
        race.update(DT);
        assertSame(Race.State.PLAYER_FINISHED, race.state);

        step(Race.FINISH_LINGER + 0.5);
        assertSame(Race.State.OVER, race.state,
                "la course doit se cloturer sans que l'ecran ait a compter lui-meme");
    }

    @Test
    @DisplayName("l'arrivee est detectee et le chrono fige")
    void arriveeDetectee() {
        startRace();
        step(3);
        Kart p = race.player;
        p.totalProgress = race.totalLaps * race.track.length + 1;
        race.update(DT);

        assertTrue(p.finished);
        assertTrue(p.finishTime > 0);
        assertSame(Race.State.PLAYER_FINISHED, race.state);

        double fige = p.finishTime;
        step(2);
        assertEquals(fige, p.finishTime, 1e-9, "le temps d'arrivee ne doit plus bouger");
    }

    @Test
    @DisplayName("allFinished ne devient vrai qu'une fois tout le monde arrive")
    void toutLeMondeArrive() {
        startRace();
        assertFalse(race.allFinished());
        for (Kart k : race.karts) {
            k.totalProgress = race.totalLaps * race.track.length + 1;
        }
        race.update(DT);
        assertTrue(race.allFinished());
    }

    @Test
    @DisplayName("le tour affiche reste dans les bornes")
    void tourAffiche() {
        startRace();
        assertEquals(1, race.playerLap());
        race.player.totalProgress = race.track.length + 1;
        assertEquals(2, race.playerLap());
        race.player.totalProgress = 99 * race.track.length;
        assertEquals(race.totalLaps, race.playerLap(), "jamais au-dela du nombre de tours");
    }

    @Test
    @DisplayName("les messages a l'ecran expirent")
    void messagesExpirent() {
        // apres le depart : sinon le message « PARTEZ ! » du decompte fausse la mesure
        startRace();
        race.notices.clear();
        race.notice("essai");
        assertFalse(race.notices.isEmpty());
        step(3.5);
        assertTrue(race.notices.isEmpty(), "les messages doivent disparaitre");
    }

    @Test
    @DisplayName("le pilote automatique conduit le kart du joueur")
    void piloteAutomatique() {
        assertFalse(race.hasAutopilot());
        race.enableAutopilot();
        assertTrue(race.hasAutopilot());
        startRace();
        step(10);
        assertTrue(race.player.speed > 5, "le pilote automatique doit accelerer");
    }

    @Test
    @DisplayName("les objets sont semes sur la piste et se ramassent")
    void semisEtRamassage() {
        assertFalse(race.items.pickups.isEmpty());
        double half = race.track.halfRoad;
        for (Pickup p : race.items.pickups) {
            assertTrue(Math.abs(p.lateral) <= half, "objet hors du bitume : " + p.lateral);
            assertTrue(p.s >= 0 && p.s < race.track.length);
            assertTrue(p.available(), "tout doit etre disponible au depart");
        }
        assertTrue(race.items.pickups.stream().anyMatch(p -> p.kind == Pickup.Kind.BOX));
        assertTrue(race.items.pickups.stream().anyMatch(p -> p.kind == Pickup.Kind.GREEN));
        assertTrue(race.items.pickups.stream().anyMatch(p -> p.kind == Pickup.Kind.RED));

        startRace();
        Kart p = race.player;
        Pickup vert = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.GREEN).findFirst().orElseThrow();
        Vec3 w = race.track.worldAt(vert.s, vert.lateral);
        p.pos = new Vec3(w.x, w.y, w.z);
        race.track.locate(p.pos.x, p.pos.z, -1, p.loc);
        int avant = p.herrings;

        race.update(DT);
        assertEquals(avant + 1, p.herrings);
        assertFalse(vert.available(), "un hareng ramasse doit disparaitre");
    }

    @Test
    @DisplayName("un objet ramasse reapparait plus tard")
    void reapparitionDesObjets() {
        startRace();
        Pickup vert = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.GREEN).findFirst().orElseThrow();
        vert.respawn = 0.5;
        assertFalse(vert.available());
        step(1);
        assertTrue(vert.available(), "l'objet doit revenir");
    }

    @Test
    @DisplayName("une caisse donne un objet, jamais deux")
    void caisseDonneUnObjet() {
        startRace();
        Kart p = race.player;
        Pickup caisse = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.BOX).findFirst().orElseThrow();
        Vec3 w = race.track.worldAt(caisse.s, caisse.lateral);
        p.pos = new Vec3(w.x, w.y, w.z);
        race.track.locate(p.pos.x, p.pos.z, -1, p.loc);

        race.update(DT);
        assertNotNull(p.item, "la caisse doit donner un objet");
        assertTrue(p.itemCount > 0);
        ItemType premier = p.item;

        // une deuxieme caisse ne doit pas ecraser l'objet en main
        Pickup autre = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.BOX && x != caisse && x.available())
                .findFirst().orElseThrow();
        Vec3 w2 = race.track.worldAt(autre.s, autre.lateral);
        p.pos = new Vec3(w2.x, w2.y, w2.z);
        race.track.locate(p.pos.x, p.pos.z, -1, p.loc);
        race.update(DT);
        assertSame(premier, p.item, "l'objet en main ne doit pas etre remplace");
        assertTrue(autre.available(), "la caisse doit rester en place");
    }

    @Test
    @DisplayName("le hareng rouge coute des points et ralentit")
    void harengRougePenalise() {
        startRace();
        Kart p = race.player;
        p.herrings = 5;
        Pickup rouge = race.items.pickups.stream()
                .filter(x -> x.kind == Pickup.Kind.RED).findFirst().orElseThrow();
        Vec3 w = race.track.worldAt(rouge.s, rouge.lateral);
        p.pos = new Vec3(w.x, w.y, w.z);
        race.track.locate(p.pos.x, p.pos.z, -1, p.loc);

        race.update(DT);
        assertEquals(3, p.herrings, "deux harengs perdus");
        assertTrue(p.slowTimer > 0, "le malus de vitesse doit s'appliquer");
    }

    @Test
    @DisplayName("aucun kart ne quitte le couloir pendant une course complete")
    void personneNeSortDuCircuit() {
        race.enableAutopilot();
        startRace();
        for (int i = 0; i < 60 * 45; i++) {
            race.update(DT);
            race.sounds.clear();
            for (Kart k : race.karts) {
                if (k.beingRescued()) continue;
                assertTrue(Math.abs(k.loc.lateral) <= race.track.halfCorridor + 0.1,
                        k.def.name + " est sorti du couloir : " + k.loc.lateral);
                assertTrue(Double.isFinite(k.pos.x) && Double.isFinite(k.pos.z),
                        k.def.name + " a une position invalide");
                assertTrue(Math.abs(k.speed) < k.def.maxSpeed * 2,
                        k.def.name + " a une vitesse aberrante : " + k.speed);
            }
        }
    }
}
