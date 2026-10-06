package org.tuxkart.kart;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.tuxkart.items.ItemType;
import org.tuxkart.math.Vec3;
import org.tuxkart.track.Track;
import org.tuxkart.track.Tracks;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Physique du kart : ce qui decide du ressenti de conduite. */
class KartTest {

    private static final double DT = 1.0 / 60;

    private Track track;
    private Kart kart;

    @BeforeEach
    void setUp() {
        track = new Track(Tracks.PISTE_DE_TUX);
        kart = new Kart(KartRoster.TUX, track, true, 0);
    }

    /** Fait avancer le kart pendant une duree donnee, sans assistance. */
    private void run(double seconds) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) kart.update(DT, i * DT, true);
    }

    /**
     * Fait avancer le kart en le remettant sur la trajectoire a chaque pas.
     *
     * Sans cela, un kart lance plein gaz et sans direction finit dans le muret
     * au premier virage : on mesurerait alors le muret, pas l'acceleration.
     */
    private void runOnRails(double seconds, double lateral) {
        int steps = (int) Math.round(seconds / DT);
        for (int i = 0; i < steps; i++) {
            kart.update(DT, i * DT, true);
            Vec3 c = track.worldAt(kart.loc.s, lateral);
            kart.pos = new Vec3(c.x, c.y, c.z);
            kart.heading = track.forwardAtS(kart.loc.s).heading();
            kart.lateralVel = 0;
            track.locate(kart.pos.x, kart.pos.z, kart.loc.index, kart.loc);
        }
    }

    @Test
    @DisplayName("un kart neuf est pose sur la piste, dans le bon sens")
    void positionInitiale() {
        assertTrue(Math.abs(kart.loc.lateral) < track.halfRoad);
        assertEquals(kart.loc.height, kart.pos.y, 0.2);
        assertEquals(0, kart.speed, 1e-9);
        assertEquals(0, kart.lapsDone());
        assertFalse(kart.finished);
    }

    @Test
    @DisplayName("l'acceleration converge vers la vitesse de pointe sans la depasser")
    void accelerationBorneeParLaPointe() {
        kart.controls.accel = true;
        runOnRails(20, 0);
        assertTrue(kart.speed > kart.def.maxSpeed * 0.9,
                "trop lent apres vingt secondes : " + kart.speed);
        assertTrue(kart.speed <= kart.def.maxSpeed * 1.02,
                "la pointe est depassee : " + kart.speed);
    }

    @Test
    @DisplayName("le frein arrete puis fait reculer, dans la limite prevue")
    void freinPuisMarcheArriere() {
        kart.controls.accel = true;
        runOnRails(6, 0);
        double lance = kart.speed;
        assertTrue(lance > 10);

        kart.controls.accel = false;
        kart.controls.brake = true;
        runOnRails(1, 0);
        assertTrue(kart.speed < lance * 0.4, "le frein ne mord pas assez");
        runOnRails(4, 0);
        assertTrue(kart.speed < 0, "le kart devrait reculer");
        assertTrue(kart.speed > -12, "marche arriere trop rapide : " + kart.speed);
    }

    @Test
    @DisplayName("relacher les commandes laisse le kart s'arreter")
    void roulementLibre() {
        kart.controls.accel = true;
        runOnRails(5, 0);
        kart.controls.accel = false;
        runOnRails(15, 0);
        assertEquals(0, kart.speed, 0.5);
    }

    @Test
    @DisplayName("le kart reste dans le couloir meme en braquant contre le muret")
    void leMuretRetientLeKart() {
        kart.controls.accel = true;
        kart.controls.steer = 1;
        for (int i = 0; i < 60 * 25; i++) {
            kart.update(DT, i * DT, true);
            assertTrue(Math.abs(kart.loc.lateral) <= track.halfCorridor + 0.05,
                    "sorti du couloir : " + kart.loc.lateral);
        }
    }

    @Test
    @DisplayName("hors piste, la vitesse de pointe chute")
    void horsPisteOnRalentit() {
        kart.controls.accel = true;
        runOnRails(12, 0);
        double surBitume = kart.speed;
        assertTrue(surBitume > 20, "reference de vitesse invalide");

        // meme kart, meme commandes, mais maintenu sur le bas-cote
        runOnRails(8, track.halfRoad + 3.5);
        assertFalse(kart.onRoad());
        assertTrue(kart.speed < surBitume * 0.75,
                "hors piste on devrait ralentir : " + kart.speed + " contre " + surBitume);
    }

    @Test
    @DisplayName("un tour complet est compte une fois et une seule")
    void comptageDesTours() {
        // au depart le kart est place quelques metres AVANT la ligne : sa
        // progression est donc negative, et le premier tour ne commence qu'au
        // franchissement
        assertTrue(kart.totalProgress < 0, "la grille est en amont de la ligne");
        assertEquals(0, kart.lapsDone());

        kart.totalProgress = 0;
        assertEquals(0, kart.lapsDone(), "franchir la ligne ne compte pas un tour");

        kart.totalProgress = track.length - 1;
        assertEquals(0, kart.lapsDone(), "un tour presque boucle ne compte pas");
        kart.totalProgress = track.length;
        assertEquals(1, kart.lapsDone());
        kart.totalProgress = 2 * track.length + 5;
        assertEquals(2, kart.lapsDone());
    }

    @Test
    @DisplayName("la progression suit le deplacement reel sur la piste")
    void progressionSuitLeDeplacement() {
        double depart = kart.totalProgress;
        kart.controls.accel = true;
        runOnRails(10, 0);
        double avance = kart.totalProgress - depart;
        assertTrue(avance > 50, "progression trop faible : " + avance);
        // la progression doit rester coherente avec la distance parcourue
        assertTrue(avance < 10 * kart.def.maxSpeed * 1.1, "progression aberrante : " + avance);
    }

    @Test
    @DisplayName("le sauvetage ramene le kart sur l'axe de la piste")
    void sauvetageRemetSurLAxe() {
        Vec3 dehors = track.worldAt(200, track.halfCorridor - 1);
        kart.pos = new Vec3(dehors.x, dehors.y, dehors.z);
        track.locate(kart.pos.x, kart.pos.z, -1, kart.loc);
        int avant = kart.rescueCount;

        kart.requestRescue();
        assertEquals(avant + 1, kart.rescueCount);
        assertTrue(kart.beingRescued());
        assertFalse(kart.controllable());

        run(3);
        assertFalse(kart.beingRescued(), "le sauvetage doit se terminer");
        assertTrue(Math.abs(kart.loc.lateral) < 1.5,
                "pas repose sur l'axe : " + kart.loc.lateral);
        assertEquals(0, kart.rescueLift, 1e-6);
    }

    @Test
    @DisplayName("le tete-a-queue coupe les commandes et fait perdre l'objet")
    void teteAQueue() {
        kart.giveItem(ItemType.HOMING);
        kart.herrings = 10;
        kart.controls.accel = true;
        runOnRails(6, 0);
        double lance = kart.speed;

        kart.spinOut(2);
        assertNull(kart.item, "l'objet doit etre perdu");
        assertTrue(kart.herrings < 10, "des harengs doivent etre perdus");
        assertTrue(kart.speed < lance * 0.5);
        assertFalse(kart.controllable());

        runOnRails(1, 0);
        assertFalse(kart.controllable(), "encore en tete-a-queue apres une seconde");
        runOnRails(1.5, 0);
        assertTrue(kart.controllable(), "le controle doit revenir");
    }

    @Test
    @DisplayName("le turbo pousse au-dela de la pointe, puis s'estompe")
    void turbo() {
        kart.controls.accel = true;
        runOnRails(15, 0);
        double pointe = kart.speed;

        kart.boost(3, 1.6);
        runOnRails(1.5, 0);
        assertTrue(kart.speed > pointe * 1.15, "le turbo ne pousse pas : " + kart.speed);
        runOnRails(5, 0);
        assertEquals(1.0, kart.boostFactor, 1e-9, "le turbo doit expirer");
        assertTrue(kart.speed <= kart.def.maxSpeed * 1.02);
    }

    @Test
    @DisplayName("les harengs augmentent la pointe, le ralentissement la reduit")
    void effetsSurLaPointe() {
        kart.controls.accel = true;
        runOnRails(18, 0);
        double sansBonus = kart.speed;

        Kart temoin = kart;
        kart = new Kart(KartRoster.TUX, track, true, 0);
        kart.herrings = 20;
        kart.controls.accel = true;
        runOnRails(18, 0);
        assertTrue(kart.speed > sansBonus, "vingt harengs devraient aider : "
                + kart.speed + " contre " + sansBonus);

        kart = temoin;
        kart.slowDown(3);
        runOnRails(1, 0);
        assertTrue(kart.speed < sansBonus * 0.8, "le malus ne mord pas");
    }

    @Test
    @DisplayName("les commandes sont ignorees quand elles sont desactivees")
    void commandesDesactivees() {
        kart.controls.accel = true;
        for (int i = 0; i < 300; i++) kart.update(DT, i * DT, false);
        assertEquals(0, kart.speed, 1e-6, "le decompte doit bloquer le depart");
    }

    @Test
    @DisplayName("la direction ne tourne pas a l'arret et tourne moins vite lancee")
    void directionDependDeLaVitesse() {
        kart.controls.steer = 1;
        double capInitial = kart.heading;
        run(1);
        assertEquals(capInitial, kart.heading, 1e-6, "un kart a l'arret ne pivote pas");

        // meme braquage a deux vitesses imposees : on compare la rotation
        // obtenue en un seul pas, sans laisser la trajectoire interferer
        assertTrue(rotationEn(10) > rotationEn(30),
                "le braquage doit se durcir avec la vitesse");
        assertTrue(rotationEn(30) > 0, "il doit rester du braquage a haute vitesse");
    }

    /** Rotation obtenue en un pas, braquage a fond, a la vitesse demandee. */
    private double rotationEn(double vitesse) {
        Kart essai = new Kart(KartRoster.TUX, track, true, 0);
        essai.speed = vitesse;
        essai.controls.steer = 1;
        double avant = essai.heading;
        essai.update(DT, 0, true);
        return Math.abs(org.tuxkart.math.MathUtil.wrapPi(essai.heading - avant));
    }

    @Test
    @DisplayName("l'objet donne le bon nombre de charges")
    void chargesDesObjets() {
        kart.giveItem(ItemType.SPARK);
        assertSame(ItemType.SPARK, kart.item);
        assertEquals(3, kart.itemCount);
        kart.giveItem(ItemType.ZIPPER);
        assertEquals(1, kart.itemCount);
    }

    @Test
    @DisplayName("chaque pilote a des caracteristiques dans des bornes jouables")
    void plateauEquilibre() {
        var ids = new java.util.HashSet<String>();
        for (KartDef def : KartRoster.ALL) {
            assertTrue(ids.add(def.id), "identifiant duplique : " + def.id);
            assertTrue(def.maxSpeed > 28 && def.maxSpeed < 42, def.id + " : pointe " + def.maxSpeed);
            assertTrue(def.accel > 8 && def.accel < 16, def.id + " : acceleration");
            assertTrue(def.turnRate > 1.4 && def.turnRate < 2.6, def.id + " : braquage");
            assertTrue(def.grip > 0.7 && def.grip <= 1.0, def.id + " : adherence");
            assertTrue(def.mass > 80 && def.mass < 150, def.id + " : masse");
            assertTrue(def.speedStars() >= 1 && def.speedStars() <= 5);
            assertTrue(def.accelStars() >= 1 && def.accelStars() <= 5);
            assertTrue(def.handlingStars() >= 1 && def.handlingStars() <= 5);
            assertSame(def, KartRoster.byId(def.id));
        }
        assertSame(KartRoster.TUX, KartRoster.byId("inconnu"), "repli attendu");
    }
}
