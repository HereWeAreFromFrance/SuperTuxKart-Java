# TuxKart — repères pour travailler ici

Clone jouable de **TuxKart** (Steve Baker), réécrit en **Java 26 / JavaFX 26**.
Le jeu ne lit **aucun fichier de données** : circuits, reliefs, textures, cartes
de normales, maillages et bruitages sont tous calculés au démarrage. Ajouter une
image ou un `.obj` serait donc un contresens — tout se construit par le code.

Le `README.md` (130 Ko) est la documentation de fond : il explique *pourquoi*
chaque choix a été fait, et il est écrit pour être lu. Ce fichier-ci n'est que le
mode d'emploi.

## Commandes

```bash
./run.sh                       # jouer
./run.sh test                  # = mvn test (358 tests, ~15 s)
./run.sh bench                 # simulation sans interface : les 9 circuits sont-ils praticables
./run.sh chiffres [triangles]  # les nombres affirmés par le README, mesurés
./run.sh regime [journal…]     # les temps d'image en régime établi, chauffe écartée
./run.sh plein-ecran [ecran]   # bas (défaut), haut, gauche, droite, principal, 0, 1…
```

**JDK 26 obligatoire.** `run.sh` retient le premier JDK suffisant de
`$JAVA_HOME`, `~/.jdks` ou `/usr/lib/jvm` (ici `~/.jdks/openjdk-26.0.2`) ;
`mvn` passe par un toolchain, il peut donc tourner sur un JDK plus ancien.
Seul **`mvn javafx:run` exige que Maven lui-même tourne sur le JDK 26** — d'où
`./run.sh`, qui lance le jeu sans ce plugin.

**Ce dépôt n'est pas sous git.** Aucun diff, aucun historique : pour savoir ce
qu'une séance précédente a changé, il faut relire son transcript dans
`~/.claude/projects/-home-remi-developpement-tuxkart-java/*.jsonl`, ou mesurer.

## Voir ce qu'on a fait

Une modification du rendu ne se juge pas à la lecture. Le jeu sait se piloter
tout seul et se photographier :

```bash
java --enable-native-access=ALL-UNNAMED \
  -Dtuxkart.demo=true -Dtuxkart.track=salon -Dtuxkart.quality=ULTRA \
  -Dtuxkart.shots=10,20,30 -Dtuxkart.shotDir=/tmp/x \
  -cp "target/classes:$(cat target/classpath.txt)" org.tuxkart.Launcher
```

Identifiants de circuit : `tux`, `banquise`, `colline`, `volcan`, `desert`,
`donjon`, `billard`, `bar` (affiché « Comptoir »), `salon`. Autres drapeaux utiles : `-Dtuxkart.screen`
(`menu`, `kart`, `track`, `options`, `help`, `race`), `-Dtuxkart.seed` et
`-Dtuxkart.fixeddt` (course reproductible), `-Dtuxkart.nodecor`,
`-Dtuxkart.nohud`, `-Dtuxkart.keys=UP,RIGHT`, `-Dtuxkart.perflog`.

Deux réglages graphiques indépendants, quatre niveaux chacun (`FLUIDE`,
`EQUILIBRE`, `QUALITE`, `ULTRA`) : `-Dtuxkart.polygons=` pour la géométrie,
`-Dtuxkart.textures=` pour les pixels. `-Dtuxkart.quality=` règle les deux à la
fois — c'est ce que veulent les captures. « Maximum » n'existe plus.

Pour photographier un **endroit** du circuit et non un instant :
`-Dtuxkart.shotAtS=495` déclenche la capture au passage de cette abscisse — la
même que celle qui pose les ouvrages et les bosses dans la définition du
circuit. **L'abscisse est celle du kart, pas celle du cadre** : la caméra se
tient huit à dix mètres derrière lui et vise quatorze mètres devant, si bien
qu'une capture à `s` montre à peu près de `s−10` à l'horizon. Pour cadrer
l'intérieur d'un ouvrage, viser une trentaine de mètres avant l'endroit voulu ;
viser l'ancrage donne souvent une image vide, la queue aveugle ayant déjà effacé
les murs. Une seule capture à la fois : `scene.snapshot` bloque le fil
d'application deux cents millisecondes, ce qui se voit dans le graphe de temps
d'image de la capture suivante.

L'agent **contrôle-visuel** fait tout cela et rend un verdict ; c'est lui qu'il
faut appeler plutôt que de charger les images dans la conversation.

## Le code

```
org.tuxkart
├── math      Vec3, splines, bruit de Perlin
├── core      qualité, réglages, cadence, moniteurs, captures
├── track     définition des circuits, thèmes, revêtements, ruban échantillonné
│   └── circuits   un fichier par circuit — le tracé, les largeurs, les bosses, les objets
├── kart      physique du kart, châssis, commandes
├── race      course, IA, objets au sol
├── items     projectiles et ramassages
├── render    maillages, textures, terrain, décor fusionné, caméra
├── audio     synthèse des bruitages
├── ui        écrans, ATH, menus
└── dev       Simulate (banc d'essai), Chiffres (les nombres du README),
              Regime (les temps d'image, chauffe écartée)
```

**Ce qui gouverne le rendu.** Le décor est fusionné par tronçon (un maillage,
pas mille nœuds) et dessiné en `CullFace.BACK` : l'intérieur d'un cube n'existe
pas, un mur qu'on traverse doit donc être une **plaque**, pas un bloc. Le décor
n'a **aucune collision** — la physique ne connaît que le ruban et ses bords —
donc la largeur libre d'un ouvrage est affaire de mise en scène.

**Le semis est tiré par un `Random` de graine fixe, et le nombre de tirages ne
doit pas dépendre de la finesse.** Sauter un tirage décale toute l'implantation.
Seul le nombre de facettes varie avec la distance.

**Dedans n'est pas dehors.** Quatre circuits sont des pièces (donjon, billard,
comptoir, salon) : `theme.indoor` les distingue. Pas de rambarde, décor au ras
du ruban, largeur variable, et surtout **aucun mobilier de circuit automobile** —
une tribune ou un panneau publicitaire sur une table de billard annule l'échelle
que toute la scène raconte.

**Un objet doit se reconnaître.** La cohérence passe avant la performance et
avant la variété. Un nom dans le `switch` de `TrackNode.piece(...)` n'est pas un
modèle : la direction artistique de TuxKart est très colorée et *peu détaillée*,
mais peu détaillé ne veut pas dire informe. Quatre ou cinq volumes d'aplat franc,
une silhouette juste, reconnaissable de loin et sous n'importe quel angle — si on
doit expliquer ce qu'est la pièce, elle est ratée. L'échelle compte autant : ces
circuits racontent un kart minuscule dans une pièce d'humain, et un dé, une
brique et un canapé n'ont pas la même taille. En cas de doute sur une forme,
prendre modèle sur l'original plutôt qu'inventer, et signaler ce qu'on invente.
Une pièce n'est pas finie tant qu'on ne l'a pas regardée : capture, verdict.

## Mesurer la performance sans se tromper

Quatre pièges, chacun payé d'une campagne de mesure perdue.

**Ne jamais mesurer la performance sans limite de cadence.** Une image de 100 à
250 ms ne veut pas dire que la scène est trop lourde : elle veut dire que le jeu
a demandé plus d'images que la chaîne graphique n'en livrait, et que le temps
part dans l'échange des tampons d'écran (`-Djavafx.pulseLogger=true` le montre
en une ligne). C'est ce contresens qui avait plafonné Ultra à moitié de ce qu'il
encaisse. `FrameLimiter` descend maintenant tout seul à la première bouffée, et
`Quality.frameCap` n'est plus qu'un **plafond** ; `-Dtuxkart.framecap=0` retire
le plafond, pas la descente.

**Le résumé de fin de partie compte depuis la première image, donc il compte le
démarrage.** À basse densité de décor, *tous* les blocages sont dans les dix
premières secondes — construction des maillages, cuisson des textures,
compilation à la volée du code de rendu. Le compteur « images au-delà de 100 ms »
y ramasse le chargement, et on le lit comme un verdict sur le rendu : trois
campagnes ont ainsi conclu faux sur une enquête de saccades, et deux réglages
qu'on croyait séparés d'un facteur deux ne différaient que par leur temps de
construction. `./run.sh regime` relit `~/tuxkart-perf.csv` (écrit par
`-Dtuxkart.perflog`) en **écartant les quinze premières secondes**, et rend
médiane, 1 % bas, images au-delà de 100 ms et pire image.

**La démonstration ne rend son relevé qu'après sa dernière capture.** Sans
`-Dtuxkart.shots`, elle tourne indéfiniment et on n'obtient jamais de journal :
donner toujours une liste d'instants, même si les images ne servent à rien.

**Ne jamais lire la cadence dans le résumé de fin de partie d'une course à
captures.** `scene.snapshot` gèle le fil d'application deux cents millisecondes,
et l'image de rattrapage qui suit est longue elle aussi : deux blocages
rapprochés, donc la signature exacte d'une file de présentation pleine. Le
limiteur descendait d'un cran, et la quarantaine doublant, il ne remontait plus.
Une campagne de captures mesurait la cadence qu'elle détruisait — 30,9 images par
seconde relevées sur le Donjon hanté, là où sept passes en donnent 60,1 à 60,4.
`FrameLimiter.capture()` neutralise maintenant ce blocage-là, mais le résumé
compte toujours depuis la première image : la cadence se lit dans
`./run.sh regime`, jamais dans la ligne « Performances : … » de fin de course.

**Deux configurations ne se comparent que mesurées en alternance dans la même
session.** La machine est chargée, et sa charge dérive plus vite que l'écart
qu'on cherche : A puis B, puis B puis A, jamais A ce matin et B ce soir. D'où
deux poignées qui évitent de recompiler entre deux passes :
`-Dtuxkart.decordensity=<n>` impose la densité du semis (36 en `ULTRA`) et
`-Dtuxkart.decorband=<mètres>` la largeur de la bande semée, normalement déduite
de la densité.

## Conventions d'écriture

- **Commentaires et javadoc en français sans accents** (`decor`, `trace`,
  `perimee`) ; les accents ne servent qu'aux **chaînes vues par le joueur**
  (`"Désert de GNU"`, `"Trente-quatre mètres de dénivelé par tour"`).
- Un commentaire dit **pourquoi**, pas quoi : la contrainte, la mesure ou la
  panne qui a imposé ce code. Le README est écrit dans le même esprit.
- Pas de dépendance nouvelle, pas de fichier de données.

## Ce qui doit rester vrai

Les tests sont la vraie spécification (`mvn test`, 358 tests). Les invariants
qui coûtent le plus cher à casser :

- le **couloir de piste ne se recoupe jamais** — sinon le circuit se coupe et le
  décor déborde ;
- le **terrain reste sous la chaussée** ;
- la **grille de huit tient sur le bitume et hors des virages** — pas « sur du
  droit », ce que ce fichier a longtemps affirmé : mesure faite, huit circuits
  posent leur grille au-delà de 175 m de rayon, mais la Piste de Tux à 80 m.
  C'est trois fois son épingle la plus serrée, donc « pas dans un virage » ;
  c'est ce seuil-là (60 m) que le test verrouille ;
- **l'IA boucle la course sans sauvetage**, sur les 9 circuits × 3 difficultés ;
- un **ouvrage laisse passer la piste** : ses murs restent à 1,5 m des bords du
  couloir sur toute sa longueur, donc pas d'ouvrage dans un virage ; et son
  rapport longueur sur largeur reste **entre 1,25 et 2,5** — trop large on passe
  sous un auvent, trop long c'est un boyau ;
- un **tremplin se pose sur du droit**, réception comprise : les bosses d'un
  mètre et demi et plus veulent un rayon d'au moins 120 m de la crête à cinquante
  mètres au-delà. Les petits dos d'âne échappent au filtre et passent parfois en
  courbe — celui du Petit Volcan est posé dans l'épingle la plus serrée de son
  circuit.

## Les chiffres

Le README affirme beaucoup de nombres, et ils se périment. Ils se mesurent :
`./run.sh test` (effectifs), `./run.sh bench` (longueurs, sauts, praticabilité),
`./run.sh chiffres triangles` (fiches et budget de décor). Ne jamais recopier un
chiffre de mémoire — l'agent **audit-des-chiffres** existe pour cela.
