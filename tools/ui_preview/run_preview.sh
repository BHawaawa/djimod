P=/sessions/wizardly-zealous-carson/mnt/DJI
LC=$P/fabric_mod/.gradle/loom-cache
CP=$(find $LC/minecraftMaven -name 'minecraft-merged-*.jar' ! -name '*sources*' | head -1)
for j in $(find $LC/remapped_mods -name '*.jar' ! -name '*-sources.jar') $HOME/w/libs/*.jar; do CP=$CP:$j; done
mkdir -p $HOME/w/pv
$HOME/w/jdk/jdk-17.0.21+9/bin/javac -nowarn -encoding UTF-8 -d $HOME/w/pv -cp "$CP:$HOME/w/out" $P/tools/ui_preview/Rc2UiPreview.java || exit 1
$HOME/w/jdk/jdk-17.0.21+9/bin/java -Djava.awt.headless=true -cp "$CP:$HOME/w/out:$HOME/w/pv" Rc2UiPreview $P/fabric_mod/src/main/resources/assets/djimod /sessions/wizardly-zealous-carson/mnt/outputs/pv ${1:-1}
