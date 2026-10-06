package org.tuxkart.track;

import org.tuxkart.track.circuits.Banquise;
import org.tuxkart.track.circuits.CollineDuManchot;
import org.tuxkart.track.circuits.DesertDeGnu;
import org.tuxkart.track.circuits.DonjonHante;
import org.tuxkart.track.circuits.LeBillard;
import org.tuxkart.track.circuits.LeComptoir;
import org.tuxkart.track.circuits.LeSalon;
import org.tuxkart.track.circuits.PetitVolcan;
import org.tuxkart.track.circuits.PisteDeTux;

import java.util.List;

/**
 * Le catalogue des circuits livres avec le jeu.
 *
 * <p>Chaque circuit vit dans son propre fichier, sous {@code track.circuits} :
 * un trace fait plusieurs centaines de lignes une fois ses objets poses, et les
 * neuf tenaient mal ensemble. Cette classe-ci ne fait plus que les rassembler
 * et les retrouver par identifiant.
 *
 * <p><b>Ils ne se ressemblent pas, et c'est voulu.</b> Un jeu de course n'a pas
 * besoin de neuf variantes du meme anneau : il a besoin qu'on reconnaisse un
 * circuit a sa premiere courbe. La table ci-dessous est donc la carte de la
 * diversite recherchee, et chaque colonne y est un axe :
 *
 * <pre>
 *   circuit     longueur  demi-largeur  tours  objets  bosses  ouvrage
 *   billard        925 m     6,5 a 14,0     4      25       3  poche
 *   volcan         930 m     5,2 a 6,2      5      12       3  —
 *   tux          1 070 m     7,5 (fixe)     3      13       1  grange
 *   donjon       1 270 m     5,8 a 13,0     3      46       4  nef
 *   comptoir     1 355 m     6,2 a 13,0     3      57       4  tonneau
 *   colline      1 640 m     6,0 (fixe)     3      32       3  tunnel
 *   desert       1 735 m     6,5 a 11,0     2      26       3  temple
 *   salon        1 900 m     6,5 a 15,0     2      74       5  boite
 *   banquise     2 450 m     9,5 a 12,5     2      20       3  iglou
 * </pre>
 *
 * <p>Du plus court au plus long il y a un facteur deux et demi ; du plus nu au
 * plus meuble, un facteur six. Le volcan n'a aucun ouvrage et douze pieces de
 * decor — sur une coulee, ce vide <i>est</i> le decor ; le salon en a
 * soixante-quatorze, parce qu'une piece habitee se raconte par ses objets.
 */
public final class Tracks {

    private Tracks() {
    }

    public static final TrackDef PISTE_DE_TUX = PisteDeTux.DEF;
    public static final TrackDef BANQUISE = Banquise.DEF;
    public static final TrackDef COLLINE_DU_MANCHOT = CollineDuManchot.DEF;
    public static final TrackDef PETIT_VOLCAN = PetitVolcan.DEF;
    public static final TrackDef DESERT_DE_GNU = DesertDeGnu.DEF;
    public static final TrackDef DONJON_HANTE = DonjonHante.DEF;
    public static final TrackDef LE_BILLARD = LeBillard.DEF;
    public static final TrackDef LE_COMPTOIR = LeComptoir.DEF;
    public static final TrackDef LE_SALON = LeSalon.DEF;

    /** L'ordre du menu : du plus facile au plus exigeant, dehors puis dedans. */
    public static final List<TrackDef> ALL = List.of(
            PISTE_DE_TUX, BANQUISE, COLLINE_DU_MANCHOT, PETIT_VOLCAN, DESERT_DE_GNU,
            DONJON_HANTE, LE_BILLARD, LE_COMPTOIR, LE_SALON);

    public static TrackDef byId(String id) {
        for (TrackDef t : ALL) {
            if (t.id.equals(id)) return t;
        }
        return PISTE_DE_TUX;
    }
}
