#!/usr/bin/env bash
# Lance TuxKart, ou le banc d'essai sans interface avec « ./run.sh bench ».
#
# « ./run.sh plein-ecran [ecran] » occupe tout un moniteur, par defaut celui du
# bas — l'ecran integre d'un portable surmonte d'un moniteur externe — ce qui
# laisse le second libre pour travailler pendant les essais. TUXKART_MONITOR
# change ce choix une fois pour toutes ; « bas », « haut », « gauche »,
# « droite », « principal » ou un numero d'ecran sont acceptes.
set -euo pipefail
cd "$(dirname "$0")"

CP_FILE=target/classpath.txt

# Le jeu demande Java 26, alors que le JDK par defaut du systeme est souvent
# plus ancien. On en choisit donc un ici, pour ce projet seulement, sans rien
# changer pour les autres : TUXKART_JAVA_HOME impose un chemin precis, sinon on
# retient le premier JDK suffisant de $JAVA_HOME, ~/.jdks ou /usr/lib/jvm.
readonly JAVA_MIN=26

select_jdk() {
    local candidates=()
    [ -n "${TUXKART_JAVA_HOME:-}" ] && candidates+=("$TUXKART_JAVA_HOME")
    [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME")
    candidates+=("$HOME"/.jdks/* /usr/lib/jvm/*)

    local dir version
    for dir in "${candidates[@]}"; do
        [ -x "$dir/bin/javac" ] || continue
        version=$("$dir/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]\{1,\}\).*/\1/p')
        [ -n "$version" ] && [ "$version" -ge "$JAVA_MIN" ] || continue
        JAVA_HOME="$dir"
        return 0
    done

    echo "TuxKart demande un JDK $JAVA_MIN ou plus recent." >&2
    echo "Aucun trouve dans \$JAVA_HOME, ~/.jdks ni /usr/lib/jvm." >&2
    echo "Precisez-le ainsi : TUXKART_JAVA_HOME=/chemin/du/jdk ./run.sh $*" >&2
    exit 1
}

select_jdk "$@"
export JAVA_HOME
JAVA="$JAVA_HOME/bin/java"

# JavaFX charge ses bibliotheques natives par System.load(). Depuis Java 24 le
# JDK avertit, et une version future refusera l'appel. Ici JavaFX arrive par le
# classpath, donc l'appelant est le module sans nom. (Avec « mvn javafx:run »
# JavaFX est un vrai module et le drapeau differe : voir le pom.)
NATIVE=--enable-native-access=ALL-UNNAMED

build() {
    if [ ! -d target/classes ] || [ -n "$(find src -newer target/classes -name '*.java' -print -quit 2>/dev/null)" ]; then
        mvn -q compile
    fi
    if [ ! -f "$CP_FILE" ]; then
        mvn -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE"
    fi
}

MONITOR=${TUXKART_MONITOR:-}

case "${1:-play}" in
    test)
        exec mvn test
        ;;
    plein-ecran|pe)
        build
        exec "$JAVA" $NATIVE -Dtuxkart.fullscreen=true \
            -Dtuxkart.monitor="${2:-${MONITOR:-bas}}" \
            -cp "target/classes:$(cat $CP_FILE)" org.tuxkart.Launcher
        ;;
    bench)
        build
        exec "$JAVA" $NATIVE -cp "target/classes:$(cat $CP_FILE)" org.tuxkart.dev.Simulate "${2:-CHAMPION}"
        ;;
    chiffres)
        # les nombres affirmes par le README : fiche de chaque circuit, et
        # « ./run.sh chiffres triangles » pour y ajouter le budget de decor
        build
        exec "$JAVA" $NATIVE -cp "target/classes:$(cat $CP_FILE)" org.tuxkart.dev.Chiffres "${2:-}"
        ;;
    regime)
        # les temps d'image une fois le jeu chaud : le resume de fin de partie
        # compte depuis la premiere image, ou tout le demarrage se trouve
        build
        exec "$JAVA" $NATIVE -cp "target/classes:$(cat $CP_FILE)" org.tuxkart.dev.Regime "${@:2}"
        ;;
    jar)
        mvn -q package
        exec "$JAVA" ${MONITOR:+-Dtuxkart.monitor="$MONITOR"} -jar target/tuxkart.jar
        ;;
    *)
        build
        exec "$JAVA" $NATIVE ${MONITOR:+-Dtuxkart.monitor="$MONITOR"} \
            -cp "target/classes:$(cat $CP_FILE)" org.tuxkart.Launcher "$@"
        ;;
esac
