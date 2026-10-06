package org.tuxkart.track;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.tuxkart.dev.Chiffres;
import org.tuxkart.math.MathUtil;
import org.tuxkart.math.Vec3;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Le ruban de piste est la piece maitresse : physique, classement, IA, objets
 * et minicarte en dependent tous. Ces tests verifient ses invariants sur les
 * neuf circuits livres, pas seulement sur un cas favorable.
 */
class TrackTest {

    static List<TrackDef> circuits() {
        return Tracks.ALL;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("l'echantillonnage est regulier et la longueur coherente")
    void echantillonnageRegulier(TrackDef def) {
        Track track = new Track(def);
        assertTrue(track.n > 100, "trop peu d'echantillons");
        assertEquals(track.length, track.n * track.spacing, 1e-6);

        double total = 0;
        for (int i = 0; i < track.n; i++) {
            Vec3 a = track.center[i];
            Vec3 b = track.center[(i + 1) % track.n];
            double d = a.distance(b);
            // pas constant : chaque segment doit coller au pas nominal
            assertEquals(track.spacing, d, track.spacing * 0.25,
                    "segment " + i + " irregulier");
            total += d;
        }
        assertEquals(track.length, total, track.length * 0.02);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("les reperes locaux sont unitaires et orthogonaux")
    void reperesLocaux(TrackDef def) {
        Track track = new Track(def);
        for (int i = 0; i < track.n; i++) {
            assertEquals(1, track.fwdFlat[i].length(), 1e-6, "avant non unitaire en " + i);
            assertEquals(1, track.side[i].length(), 1e-6, "lateral non unitaire en " + i);
            assertEquals(0, track.fwdFlat[i].dot(track.side[i]), 1e-6, "non orthogonaux en " + i);
            assertEquals(0, track.side[i].y, 1e-9, "le lateral doit rester horizontal");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("un point de l'axe se projette avec un ecart lateral nul")
    void projectionDeLAxe(TrackDef def) {
        Track track = new Track(def);
        Track.Loc loc = new Track.Loc();
        for (int i = 0; i < track.n; i += 7) {
            Vec3 c = track.center[i];
            track.locate(c.x, c.z, -1, loc);
            assertEquals(0, loc.lateral, 0.35, "ecart lateral en " + i);
            assertEquals(c.y, loc.height, 0.35, "altitude en " + i);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("la recherche locale donne le meme resultat que la recherche globale")
    void rechercheLocaleEtGlobaleConcordent(TrackDef def) {
        // c'est l'optimisation la plus risquee du moteur : si l'indice indicatif
        // fait deriver le resultat, la physique et le classement partent de travers
        Track track = new Track(def);
        Track.Loc avecIndice = new Track.Loc();
        Track.Loc sansIndice = new Track.Loc();
        int hint = -1;
        for (double s = 0; s < track.length; s += 3.3) {
            for (double lat : new double[]{-track.halfCorridor + 1, 0, track.halfCorridor - 1}) {
                Vec3 p = track.worldAt(s, lat);
                track.locate(p.x, p.z, hint, avecIndice);
                track.locate(p.x, p.z, -1, sansIndice);
                hint = avecIndice.index;
                assertEquals(sansIndice.index, avecIndice.index,
                        "indices differents a s=" + s + " lat=" + lat);
                assertEquals(sansIndice.lateral, avecIndice.lateral, 1e-9);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("coordonnees curvilignes et coordonnees monde sont reciproques")
    void allerRetourCurviligne(TrackDef def) {
        Track track = new Track(def);
        Track.Loc loc = new Track.Loc();
        for (double s = 0; s < track.length; s += 11) {
            for (double lat : new double[]{-4, 0, 5}) {
                Vec3 p = track.worldAt(s, lat);
                track.locate(p.x, p.z, -1, loc);
                double ecart = Math.abs(track.deltaS(s, loc.s));
                assertTrue(ecart < 1.5, "derive de " + ecart + " m a s=" + s);
                assertEquals(lat, loc.lateral, 0.6, "lateral a s=" + s);
            }
        }
    }

    @Test
    @DisplayName("deltaS choisit toujours le chemin le plus court")
    void deltaSPrendLePlusCourt() {
        Track track = new Track(Tracks.PISTE_DE_TUX);
        double l = track.length;
        assertEquals(10, track.deltaS(0, 10), 1e-6);
        assertEquals(-10, track.deltaS(10, 0), 1e-6);
        // franchir la ligne ne doit pas produire un ecart d'un tour
        assertEquals(6, track.deltaS(l - 3, 3), 1e-6);
        assertEquals(-6, track.deltaS(3, l - 3), 1e-6);
        for (double a = 0; a < l; a += 37) {
            for (double b = 0; b < l; b += 53) {
                assertTrue(Math.abs(track.deltaS(a, b)) <= l / 2 + 1e-6);
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("le devers est nul en ligne droite et incline dans les virages")
    void deversSuitLaCourbure(TrackDef def) {
        Track track = new Track(def);
        double maxPente = 0;
        for (int i = 0; i < track.n; i++) {
            double pente = Math.abs(track.bankSlope[i]);
            maxPente = Math.max(maxPente, pente);
            assertTrue(pente < 0.14, "devers excessif en " + i + " : " + pente);
        }
        assertTrue(maxPente > 0.01, "aucun virage releve sur " + def.name);

        // l'altitude renvoyee doit tenir compte du devers
        Track.Loc loc = new Track.Loc();
        int corner = 0;
        for (int i = 0; i < track.n; i++) {
            if (Math.abs(track.bankSlope[i]) > maxPente * 0.8) {
                corner = i;
                break;
            }
        }
        Vec3 gauche = track.worldAt(track.arc[corner], track.halfRoad);
        Vec3 droite = track.worldAt(track.arc[corner], -track.halfRoad);
        assertTrue(Math.abs(gauche.y - droite.y) > 0.1,
                "les deux bords devraient etre a des altitudes differentes");
        track.locate(gauche.x, gauche.z, -1, loc);
        assertEquals(gauche.y, loc.height, 0.3);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("la grille de depart tient sur la piste et ne se chevauche pas")
    void grilleDeDepartValide(TrackDef def) {
        Track track = new Track(def);
        Track.Loc loc = new Track.Loc();
        Vec3[] places = new Vec3[8];
        for (int rank = 0; rank < 8; rank++) {
            Vec3 p = track.gridPosition(rank);
            places[rank] = p;
            track.locate(p.x, p.z, -1, loc);
            assertTrue(Math.abs(loc.lateral) < track.halfRoad - 1,
                    "place " + rank + " hors du bitume");
            double cap = track.gridHeading(rank);
            assertTrue(Math.abs(MathUtil.wrapPi(cap - loc.heading)) < 0.3,
                    "place " + rank + " mal orientee");
        }
        for (int i = 0; i < places.length; i++) {
            for (int j = i + 1; j < places.length; j++) {
                assertTrue(places[i].distanceXZ(places[j]) > 2.4,
                        "places " + i + " et " + j + " trop proches");
            }
        }

        // La grille tient sur une portion peu courbe — pas sur du droit, ce
        // que le depot a longtemps affirme. Mesure faite : huit circuits
        // posent leur grille au-dela de 175 m de rayon, la Piste de Tux a
        // 80 m, soit trois fois son epingle la plus serree. Ce n'est pas droit,
        // c'est « pas dans un virage », et c'est cela qu'on verrouille : sous
        // 60 m la premiere ligne prendrait la corde avant meme le depart, et
        // les huit karts alignes sur deux colonnes ne verraient pas le meme
        // bout de piste. Les virages les plus serres du jeu font 14 a 31 m,
        // donc le seuil separe vraiment les deux cas.
        double rayon = Chiffres.rayonMini(track, track.gridS(7) - 5, track.gridS(0) + 5);
        assertTrue(rayon > 60,
                def.name + " : la grille de depart est posee sur un rayon de "
                        + Math.round(rayon) + " m — un virage, pas une ligne de depart");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("le couloir de piste ne se recoupe jamais lui-meme")
    void leCouloirNeSeRecoupePas(TrackDef def) {
        // si deux portions eloignees en abscisse curviligne se touchent dans le
        // monde, un kart pourrait couper le circuit et le decor deborderait
        Track track = new Track(def);
        double mini = Double.MAX_VALUE;
        int pas = Math.max(1, (int) (4 / track.spacing));
        for (int i = 0; i < track.n; i += pas) {
            for (int j = i + pas; j < track.n; j += pas) {
                double ds = Math.abs(track.deltaS(track.arc[i], track.arc[j]));
                if (ds < 60) continue;
                mini = Math.min(mini, track.center[i].distanceXZ(track.center[j]));
            }
        }
        assertTrue(mini > 2 * track.halfCorridor,
                def.name + " : deux portions distantes de " + Math.round(mini)
                        + " m alors que le couloir en fait " + Math.round(2 * track.halfCorridor));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("la courbure est positive et repere les virages")
    void courbureUtilisable(TrackDef def) {
        Track track = new Track(def);
        double max = 0;
        for (double s = 0; s < track.length; s += 5) {
            double c = track.curvatureAhead(s, 20);
            assertTrue(c >= 0, "courbure negative");
            assertTrue(c < 1, "courbure aberrante : " + c);
            max = Math.max(max, c);
        }
        assertTrue(max > 0.01, "aucun virage detecte sur " + def.name);
    }

    @Test
    @DisplayName("la construction d'un circuit est deterministe")
    void constructionDeterministe() {
        Track a = new Track(Tracks.DESERT_DE_GNU);
        Track b = new Track(Tracks.DESERT_DE_GNU);
        assertEquals(a.n, b.n);
        assertEquals(a.length, b.length, 1e-12);
        for (int i = 0; i < a.n; i++) {
            assertEquals(a.center[i].x, b.center[i].x, 1e-12);
            assertEquals(a.bankSlope[i], b.bankSlope[i], 1e-12);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("la piste passe vraiment dans les ouvrages qu'elle traverse")
    void lesOuvragesLaissentPasserLaPiste(TrackDef def) {
        // Un ouvrage traverse est une boite rigide posee sur une abscisse, avec
        // le cap de la piste a cet endroit. La piste, elle, continue de tourner.
        // Pose dans un virage, l'ouvrage aurait donc un mur en travers de la
        // trajectoire — et comme le decor n'a pas de collision, on le
        // traverserait sans rien heurter : une paroi fantome au milieu du
        // circuit, que rien d'autre que ce test ne signale.
        //
        // Le mur n'etant plus a distance constante mais au profil du couloir,
        // la verification se fait abscisse par abscisse : le point de piste est
        // projete dans le repere de l'ouvrage, et confronte a la demi-largeur
        // de la travee qui lui fait face.
        Track track = new Track(def);
        for (int i = 0; i < def.props.length; i++) {
            Ouvrage ouvrage = Ouvrage.parNom(def.propKinds[i]);
            if (ouvrage == null) continue;
            double s0 = def.props[i][0];
            assertEquals(0, def.props[i][1], 1e-9,
                    def.id + " : un ouvrage se pose sur l'axe, pas de cote");
            assertEquals(0, def.props[i][2], 1e-9,
                    def.id + " : un ouvrage garde le cap de la piste");

            Vec3 axe = track.worldAt(s0, 0);
            Vec3 sens = track.forwardAtS(s0);
            Ouvrage.Profil profil = ouvrage.profil(track, s0);
            for (double d = -ouvrage.demiLongueur; d <= ouvrage.demiLongueur; d += 2) {
                double s = s0 + d;
                double couloir = track.halfCorridorAtS(s);
                for (double lat : new double[]{-couloir, 0, couloir}) {
                    Vec3 p = track.worldAt(s, lat);
                    double along = (p.x - axe.x) * sens.x + (p.z - axe.z) * sens.z;
                    double travers = Math.abs((p.x - axe.x) * sens.z - (p.z - axe.z) * sens.x);
                    double mur = profil.demi(along);
                    assertTrue(travers < mur - 1.5,
                            def.name + " : l'ouvrage " + ouvrage.nom + " pose en " + (int) s0
                                    + " m a son mur a " + Math.round(mur)
                                    + " m de l'axe, mais la piste s'en ecarte de "
                                    + Math.round(travers) + " m a s=" + (int) s);
                }
                // et le sol reste plat sous l'ouvrage, qui est batie d'un bloc
                assertTrue(Math.abs(track.heightAtS(s, 0) - axe.y) < 6,
                        def.name + " : l'ouvrage " + ouvrage.nom + " enjambe "
                                + Math.round(Math.abs(track.heightAtS(s, 0) - axe.y))
                                + " m de denivele");
            }
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("un ouvrage traverse colle a la piste et enferme")
    void lesOuvragesEnfermentVraiment(TrackDef def) {
        // Un ouvrage n'existe que pour une chose : donner deux a quatre
        // secondes pendant lesquelles on ne voit plus les bords du monde. Il
        // rate cette seule chose des que son mur s'eloigne — la nef du donjon
        // etait batie a la demi-largeur la plus grande de son emprise, celle
        // que lui donnait l'elargissement de la grille de depart qu'elle
        // attrapait par un bout : soixante-cinq metres de large sur toute sa
        // longueur, dont vingt-et-un de vide entre la piste et le mur a la
        // sortie, la ou onze etaient declares. Une nef qu'on traverse sans la
        // voir. C'est la marge qui est en cause ici, celle a laquelle la paroi
        // est batie, et non le degagement, qui se compte depuis cette paroi et
        // n'ecarte que le semis.
        //
        // D'ou les deux bornes, verifiees a chaque abscisse de l'emprise. Le
        // mur se tient entre un metre et demi du bord reel de la piste (c'est
        // l'autre test, celui du passage) et la marge que l'ouvrage declare.
        // A cette marge s'ajoute la derive : l'ouvrage est une boite droite, la
        // piste tourne encore un peu sous lui, et son axe s'ecarte de celui de
        // l'ouvrage — d'autant que le mur d'un cote s'eloigne. Le metre et demi
        // de tolerance paie la rotation du bord, que la derive de l'axe seule
        // ne rend pas. Le pire des huit — la poche du billard — depasse sa
        // marge de 0,71 m, trois ne la depassent pas du tout.
        //
        // Reste l'enfermement proprement dit, qui se joue sur la travee la plus
        // large. Les deux echecs sont symetriques et ne se ressemblent pas :
        // un volume plus large que long ne se traverse pas, on passe sous un
        // auvent ; un volume trop long pour sa largeur n'est plus un ouvrage
        // mais un boyau, et le circuit y perd la vue en meme temps que l'air.
        // Mesure faite sur les huit ouvrages depuis qu'ils suivent le couloir
        // travee par travee, le rapport longueur sur largeur va de 1,29 pour
        // l'iglou de la Banquise a 2,25 pour le tunnel de la Colline. La
        // fourchette verrouillee ci-dessous garde ces deux-la de justesse : ce
        // sont eux qui definissent le genre, et les deplacer d'un cran doit se
        // decider, pas se subir.
        Track track = new Track(def);
        for (int i = 0; i < def.props.length; i++) {
            Ouvrage ouvrage = Ouvrage.parNom(def.propKinds[i]);
            if (ouvrage == null) continue;
            double s0 = def.props[i][0];
            Vec3 axe = track.worldAt(s0, 0);
            Vec3 sens = track.forwardAtS(s0);
            Ouvrage.Profil profil = ouvrage.profil(track, s0);

            for (double d = -ouvrage.demiLongueur; d <= ouvrage.demiLongueur; d += 2) {
                double s = s0 + d;
                double couloir = track.halfCorridorAtS(s);
                Vec3 milieu = track.worldAt(s, 0);
                double derive = Math.abs((milieu.x - axe.x) * sens.z
                        - (milieu.z - axe.z) * sens.x);
                for (int cote = -1; cote <= 1; cote += 2) {
                    Vec3 p = track.worldAt(s, cote * couloir);
                    double along = (p.x - axe.x) * sens.x + (p.z - axe.z) * sens.z;
                    double travers = Math.abs((p.x - axe.x) * sens.z - (p.z - axe.z) * sens.x);
                    double vide = profil.demi(along) - travers;
                    assertTrue(vide <= ouvrage.marge + derive + 1.5,
                            def.name + " : " + ouvrage.nom + " pose en " + (int) s0
                                    + " m laisse " + Math.round(vide) + " m entre la piste"
                                    + " et son mur a s=" + (int) s + ", pour une marge"
                                    + " declaree de " + ouvrage.marge + " m et "
                                    + Math.round(derive) + " m de derive — le mur ne suit"
                                    + " plus le couloir");
                }
            }

            double longueur = 2 * ouvrage.demiLongueur;
            double largeur = 2 * profil.enveloppe();
            double rapport = longueur / largeur;
            assertTrue(rapport > 1.25,
                    def.name + " : " + ouvrage.nom + " pose en " + (int) s0
                            + " m fait " + Math.round(longueur) + " m de long pour "
                            + Math.round(largeur) + " m de large, soit un rapport de "
                            + Math.round(rapport * 100) / 100.0 + " — trop large pour"
                            + " qu'on s'y sente dedans, il faut le poser la ou le"
                            + " couloir se resserre");
            assertTrue(rapport < 2.5,
                    def.name + " : " + ouvrage.nom + " pose en " + (int) s0
                            + " m fait " + Math.round(longueur) + " m de long pour "
                            + Math.round(largeur) + " m de large, soit un rapport de "
                            + Math.round(rapport * 100) / 100.0 + " — un boyau, pas un"
                            + " ouvrage : on le traverse a l'aveugle au lieu d'y"
                            + " passer");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("circuits")
    @DisplayName("un tremplin se pose sur du droit, reception comprise")
    void unTremplinSePoseSurDuDroit(TrackDef def) {
        // Une bosse qui fait decoller met le kart en l'air une seconde entiere,
        // pendant laquelle la piste continue de tourner sans lui : il retombe
        // ou il pointait, pas ou la piste est allee. Posee dans un virage, la
        // meme bosse est un piege qu'aucun pilotage ne rattrape, et le symptome
        // — atterrir dans le decor — ne se voit qu'a l'oeil, jamais dans une
        // trace. C'etait la seule regle de dessin du depot que rien ne gardait.
        //
        // Le filtre est la hauteur : au-dela d'un metre et demi la crete se
        // derobe bien avant la vitesse de pointe, en dessous on passe dessus
        // sans quitter le sol. Les dos d'ane, eux, ont le droit d'etre en
        // courbe — celui du Petit Volcan est dans l'epingle meme.
        //
        // Le rayon se mesure de la crete a cinquante metres au-dela, soit le
        // vol le plus long du jeu plus la reprise d'appui. Seuil a 120 m : a la
        // vitesse ou l'on decolle, un rayon de 120 m demande deja pres d'un g
        // rien que pour tourner, donc toute l'adherence disponible — y atterrir
        // reviendrait a reprendre appui en glissant. La mesure du jour donne au
        // pire 149 m, sur la Piste de Tux ; les autres tremplins sont entre 178
        // et 12 000 m. Deplacer l'un d'eux de trente metres suffit a le faire
        // tomber sous le seuil.
        Track track = new Track(def);
        if (def.bumps == null) return;
        for (double[] bump : def.bumps) {
            double s = bump[0];
            double hauteur = bump[1];
            if (hauteur < 1.5) continue;
            double rayon = Chiffres.rayonMini(track, s, s + 50);
            assertTrue(rayon > 120,
                    def.name + " : le tremplin de " + hauteur + " m pose en " + (int) s
                            + " m saute vers un rayon de " + Math.round(rayon)
                            + " m — la piste tourne sous le kart pendant le vol");
        }
    }

    @Test
    @DisplayName("les definitions de circuits sont saines")
    void definitionsSaines() {
        var ids = new java.util.HashSet<String>();
        for (TrackDef def : Tracks.ALL) {
            assertTrue(ids.add(def.id), "identifiant duplique : " + def.id);
            assertNotNull(def.surface);
            assertNotNull(def.barrier);
            assertNotNull(def.theme);
            assertTrue(def.controlPoints.size() >= 4, def.id + " : spline trop courte");
            assertTrue(def.roadHalfWidth >= 5, def.id + " : piste trop etroite");
            assertTrue(def.shoulderWidth > 0, def.id + " : pas de bas-cote");
            assertTrue(def.difficulty >= 1 && def.difficulty <= 5, def.id + " : difficulte hors bornes");
            assertTrue(def.defaultLaps >= 1);
            assertEquals(def, Tracks.byId(def.id));
        }
        assertEquals(Tracks.PISTE_DE_TUX, Tracks.byId("inconnu"), "repli attendu");
    }

    @Test
    @DisplayName("chaque revetement declare une adherence plausible")
    void adherenceDesRevetements() {
        for (Surface s : Surface.values()) {
            assertTrue(s.grip > 0.5 && s.grip <= 1.0, s + " : adherence " + s.grip);
        }
        assertEquals(1.0, Surface.BITUME.grip, 1e-9);
        assertTrue(Surface.BITUME.kerbs, "les vibreurs peints vont avec le bitume");
        assertTrue(!Surface.SABLE.kerbs && !Surface.TERRE.kerbs && !Surface.NEIGE.kerbs);
        for (Barrier b : Barrier.values()) {
            assertTrue(b.height > 1 && b.height < 4, b + " : hauteur " + b.height);
        }
    }
}
