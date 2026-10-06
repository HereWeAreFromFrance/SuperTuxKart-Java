package org.tuxkart.render;

import javafx.scene.AmbientLight;
import javafx.scene.Node;
import javafx.scene.PointLight;
import javafx.scene.paint.Color;
import org.tuxkart.track.Theme;
import org.tuxkart.track.Track;

import java.util.List;

/**
 * Le luminaire d'une course : une ambiante, un soleil, un appoint.
 *
 * <p>Trois lumieres, pas plus. Chaque lumiere de plus est un passage de plus
 * sur chaque triangle, et la scene en compte deux millions.
 *
 * <p>Le luminaire est isole ici, et non noye dans l'ecran de course, parce
 * qu'il se regle <b>a la mesure</b> : la clarte de la chaussee et celle du
 * hors-piste se relevent circuit par circuit, et une scene d'essai doit
 * pouvoir s'eclairer exactement comme le jeu.
 */
public final class Lighting {

    private Lighting() {
    }

    /**
     * Les lumieres a accrocher au monde, dans l'ordre.
     *
     * <p><b>Ces valeurs ont ete reprises, et gardees, apres la remise a
     * l'endroit des normales de piste</b> ({@link Meshes#retourne}). On les
     * soupconnait de compenser le defaut : la chaussee etait eclairee par
     * l'appoint pose sous le circuit et pas par le soleil, et c'est en partie
     * pour la sortir du gris que la Banquise avait monte son ambiante de 0,38
     * a 0,52 et son appoint de 0,44 a 0,80. Mesure faite une fois les normales
     * justes, sur une vue plongeante qui ne cadre que du bitume :
     *
     * <ul>
     * <li>la clarte de la chaussee ne bouge <b>pas d'un dixieme</b> quand
     * l'appoint passe de 0,44 a 0,66 — 126,0 / 147,8 / 97,2 aux memes trois
     * abscisses dans les deux cas. La chaussee ne voit plus l'appoint du
     * tout, il ne peut donc plus la surexposer ;</li>
     * <li>l'ambiante, elle, eclaire la piste et le terrain a parts egales :
     * l'ecart de clarte entre les deux vaut -16,4 % a 0,52 et -16,0 % a 0,38.
     * Elle ne fait ni la lisibilite ni son contraire.</li>
     * </ul>
     *
     * <p>Les deux reglages gardent donc leur justification d'origine, qui
     * portait sur le <b>decor</b> — congeres, blocs de glace, coiffes de neige
     * des sapins — et le decor, lui, a toujours eu ses normales a l'endroit.
     * Ce que la correction a change sur la neige, c'est le sens de l'ecart :
     * la chaussee est passee de 179,6 a 153,5 de clarte moyenne quand le
     * hors-piste restait a 183,6, si bien que la piste est maintenant
     * <b>toujours</b> plus sombre que la neige qui la borde au lieu de croiser
     * sa clarte trois fois par tour.
     */
    public static List<Node> rig(Track track) {
        AmbientLight ambient = new AmbientLight(Color.gray(ambiante(track)));
        // soleil bas sur l'horizon : c'est l'incidence rasante qui fait
        // ressortir le relief des cartes de normales et modele le terrain
        PointLight sun = new PointLight(Color.rgb(255, 246, 224));
        sun.setTranslateX((track.minX + track.maxX) / 2 + 780);
        sun.setTranslateZ((track.minZ + track.maxZ) / 2 - 640);
        sun.setTranslateY(Meshes.jy(track.maxY + 340));
        // Lumiere d'appoint : elle deteint sur tout ce que le soleil rase sans
        // l'atteindre — les flancs a l'ombre, le pied des troncs, l'interieur
        // des ouvrages. Dehors elle est posee sous le circuit et n'eclaire
        // plus la chaussee, qui regarde maintenant le ciel ; dedans elle passe
        // au-dessus, faute de soleil (voir hauteurAppoint).
        PointLight fill = new PointLight(appoint(track));
        fill.setTranslateX((track.minX + track.maxX) / 2);
        fill.setTranslateZ((track.minZ + track.maxZ) / 2);
        fill.setTranslateY(hauteurAppoint(track));
        // -Dtuxkart.onelight : mesure du cout de la lumiere d'appoint
        if (System.getProperty("tuxkart.onelight") != null) return List.of(ambient, sun);
        return List.of(ambient, sun, fill);
    }

    /**
     * Ou poser l'appoint : sous le circuit dehors, au-dessus dedans.
     *
     * <p>Dehors, c'est le soleil qui eclaire la chaussee, et l'appoint ne sert
     * qu'aux dessous. Dedans, il n'y a pas de soleil — une voute de donjon, un
     * plafond de salon — et tant que les normales de la chaussee pointaient
     * vers le bas, l'appoint pose dessous etait la <b>seule</b> lumiere qui
     * l'atteignait. Les remettre a l'endroit ({@link Meshes#retourne}) a donc
     * eteint le sol des quatre pieces d'un coup, ce que la mesure des rubans
     * ne pouvait pas montrer.
     *
     * <p>Capture faite au neuf cent cinquantieme metre du Donjon hante, avant
     * et apres la correction des normales : la chaussee en dalles, jusque-la
     * gris moyen sur un sol de salle gris-bleu plus sombre, est tombee
     * exactement a la clarte de ce sol. Le bord de piste avait disparu, sur
     * le seul circuit qui n'a ni vibreur ({@link org.tuxkart.track.Surface#DALLE}
     * n'en pose pas) ni congere pour le rattraper : il ne restait que la
     * minicarte pour savoir ou finissait la piste.
     *
     * <p>Remonter l'appoint le rend a son sujet. Dans une piece, ce qu'il
     * represente n'est pas un dessous de circuit mais le plafond et les
     * lampes. Ce n'est pas une lumiere de plus : la scene en compte toujours
     * trois. Verifie en capture sur les quatre pieces — le Donjon retrouve
     * integralement la lisibilite d'avant, le parquet de la piste du Comptoir
     * se detache du plancher, le drap du Billard sort du drap de la table, le
     * tapis du Salon y gagne plus modestement. Les cinq circuits de plein air
     * n'en ont pas besoin et ne bougent pas.
     */
    private static float hauteurAppoint(Track track) {
        return track.def.theme.indoor
                ? Meshes.jy(track.maxY + 260)
                : Meshes.jy(track.minY - 260);
    }

    /**
     * Ambiante : plus haute sur la neige, et c'est le seul theme qui la
     * deplace.
     *
     * <p>Un champ de neige renvoie l'essentiel de ce qu'il recoit : le decor y
     * est baigne de lumiere diffuse, alors que dehors un flanc a l'ombre ne
     * recoit que le ciel. A 0,38, tout ce que le soleil ne touche pas tombe
     * vers 90 de clarte — les coiffes de neige des sapins, les congeres, les
     * blocs et les pics de glace viraient au gris anthracite sur le seul
     * circuit dont l'identite est la blancheur. Capture faite a s=1890 apres la
     * correction des normales : a 0,38 la scene entiere redevient grise, coiffes
     * comprises, alors que le contraste piste / hors-piste, lui, ne bouge pas.
     *
     * <p>Les huit autres gardent 0,38 au chiffre pres : le ciel du volcan et
     * celui du desert ont ete juges sans reproche, et une ambiante est globale
     * a la scene.
     */
    private static double ambiante(Track track) {
        return track.def.theme == Theme.SNOW ? 0.52 : 0.38;
    }

    /**
     * Appoint : plus clair et plus froid sur la neige.
     *
     * <p>Un champ de neige renvoie reellement l'essentiel de ce qu'il recoit,
     * et c'est ce qui distingue le theme : ce que le soleil rase sans le
     * toucher — la face nord d'une congere, le dessous d'une branche — n'y est
     * pas noir. L'appoint y vaut donc 0,80 contre 0,44 ailleurs.
     *
     * <p>Ce reglage-la ne touche plus la chaussee depuis que ses normales
     * regardent le ciel, et il ne peut donc plus la surexposer : mesure faite,
     * la clarte de la piste est identique a l'unite pres avec un appoint a 0,44
     * et un appoint a 0,66. Ce qu'il eclaire, c'est le decor et la face des
     * murets que le soleil rase.
     */
    private static Color appoint(Track track) {
        return track.def.theme == Theme.SNOW
                ? Color.rgb(198, 212, 226) : Color.rgb(112, 120, 132);
    }
}
