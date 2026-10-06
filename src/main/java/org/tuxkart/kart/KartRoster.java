package org.tuxkart.kart;

import javafx.scene.paint.Color;
import org.tuxkart.kart.KartDef.Brow;
import org.tuxkart.kart.KartDef.Build;
import org.tuxkart.kart.KartDef.Chassis;
import org.tuxkart.kart.KartDef.Ears;
import org.tuxkart.kart.KartDef.Headgear;
import org.tuxkart.kart.KartDef.Look;
import org.tuxkart.kart.KartDef.Snout;

import java.util.List;

/**
 * Le plateau de pilotes. On reprend les mascottes libres qui accompagnent la
 * lignee TuxKart depuis ses debuts, Tux en tete.
 *
 * Chaque fiche choisit son chassis : aucun kart du plateau n'a la silhouette
 * d'un autre, et la mascotte assortit museau, oreilles, couvre-chef et
 * corpulence.
 */
public final class KartRoster {

    private KartRoster() {
    }

    public static final KartDef TUX = new KartDef(
            "tux", "Tux", "Le manchot officiel du noyau",
            Look.colors(Color.web("#1c1c22"), Color.web("#f6a723"),
                            Color.web("#15151a"), Color.web("#fdfdfd"), Color.web("#f6a723"))
                    .chassis(Chassis.CLASSIQUE).snout(Snout.BEAK)
                    .gear(Headgear.LUNETTES).build(Build.TRAPU).brow(Brow.DOUX),
            34.0, 11.5, 1.95, 0.90, 100);

    public static final KartDef GNU = new KartDef(
            "gnu", "Gnu", "Démarrages foudroyants",
            Look.colors(Color.web("#e9e9e4"), Color.web("#6b6b60"),
                            Color.web("#efefe8"), Color.web("#d8d8cc"), Color.web("#8a7a53"))
                    .chassis(Chassis.BUGGY).snout(Snout.MUZZLE).ears(Ears.LONGUES)
                    .build(Build.NORMAL).brow(Brow.DOUX).horns().beard(),
            32.5, 13.8, 1.90, 0.88, 105);

    public static final KartDef WILBER = new KartDef(
            "wilber", "Wilber", "Vitesse de pointe redoutable",
            Look.colors(Color.web("#b4702e"), Color.web("#3d2a18"),
                            Color.web("#c9884a"), Color.web("#f0dcc0"), Color.web("#3d2a18"))
                    .chassis(Chassis.ROADSTER).snout(Snout.MUZZLE).ears(Ears.POINTUES)
                    .gear(Headgear.BANDEAU).build(Build.FIN),
            37.0, 9.8, 1.75, 0.86, 112);

    public static final KartDef PUFFY = new KartDef(
            "puffy", "Puffy", "Colle à la route en toute circonstance",
            Look.colors(Color.web("#e3cf3f"), Color.web("#c0392b"),
                            Color.web("#f2dd58"), Color.web("#fff3b0"), Color.web("#c0392b"))
                    .chassis(Chassis.BULLE).ears(Ears.NAGEOIRE)
                    .gear(Headgear.COURONNE).build(Build.TRAPU).brow(Brow.DOUX).spikes(),
            31.5, 11.0, 2.15, 0.97, 96);

    public static final KartDef KONQI = new KartDef(
            "konqi", "Konqi", "Le dragon polyvalent",
            Look.colors(Color.web("#33a852"), Color.web("#1c5c2e"),
                            Color.web("#3fbf60"), Color.web("#d9f2c0"), Color.web("#e14b2a"))
                    .chassis(Chassis.FUSEE).snout(Snout.HORN_SNOUT)
                    .gear(Headgear.LUNETTES).build(Build.NORMAL).horns(),
            34.5, 11.2, 1.92, 0.89, 102);

    public static final KartDef HEXLEY = new KartDef(
            "hexley", "Hexley", "Léger et très agile",
            Look.colors(Color.web("#33366e"), Color.web("#e8802a"),
                            Color.web("#2b2f60"), Color.web("#c9cbe8"), Color.web("#e8802a"))
                    .chassis(Chassis.TRIKE).snout(Snout.BEAK).ears(Ears.TOUFFE)
                    .gear(Headgear.CASQUETTE).build(Build.FIN).brow(Brow.DOUX),
            32.0, 12.6, 2.20, 0.93, 88);

    public static final KartDef BEASTIE = new KartDef(
            "beastie", "Beastie", "Lourd, rapide, difficile à bouger",
            Look.colors(Color.web("#c0392b"), Color.web("#2b1210"),
                            Color.web("#d4483a"), Color.web("#f0b9a8"), Color.web("#2b1210"))
                    .chassis(Chassis.HOTROD).snout(Snout.MUZZLE).ears(Ears.RONDES)
                    .build(Build.TRAPU).brow(Brow.FACHE).horns(),
            36.0, 10.4, 1.72, 0.85, 124);

    public static final KartDef NOLOK = new KartDef(
            "nolok", "Nolok", "Puissant mais peu maniable",
            Look.colors(Color.web("#2c3a2b"), Color.web("#8fbf3f"),
                            Color.web("#1f2a1f"), Color.web("#6b7f5a"), Color.web("#8fbf3f"))
                    .chassis(Chassis.BLINDE).snout(Snout.HORN_SNOUT)
                    .gear(Headgear.CASQUE).build(Build.NORMAL).brow(Brow.FACHE)
                    .horns().spikes(),
            36.5, 11.8, 1.62, 0.84, 130);

    public static final List<KartDef> ALL = List.of(
            TUX, GNU, WILBER, PUFFY, KONQI, HEXLEY, BEASTIE, NOLOK);

    public static KartDef byId(String id) {
        for (KartDef k : ALL) {
            if (k.id.equals(id)) return k;
        }
        return TUX;
    }
}
