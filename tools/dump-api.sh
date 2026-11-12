#!/usr/bin/env bash
# Dumps public signatures of the 26.3 classes the client relies on, so the code
# can be written against the real game API. Output: build/api-dump/
set -u
OUT=build/api-dump; mkdir -p "$OUT"
MC=$(find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' 2>/dev/null | grep -iE 'minecraft-(client|merged|common)' | grep -v sources | head -1)
FAPI=$(find ~/.gradle/caches/modules-2 -name 'fabric-*.jar' 2>/dev/null | grep -v sources | tr '\n' ':')
echo "MC jar: $MC" > "$OUT/info.txt"
find ~/.gradle/caches/fabric-loom .gradle/loom-cache -name '*.jar' 2>/dev/null >> "$OUT/info.txt"
unzip -Z1 "$MC" | grep '\.class$' | sed 's/\.class$//; s#/#.#g' | sort > "$OUT/mc-classes.txt"
for j in $(echo "$FAPI" | tr ':' ' '); do unzip -Z1 "$j" | grep '\.class$' | sed 's/\.class$//; s#/#.#g'; done | sort -u > "$OUT/fabric-classes.txt"
CP="$MC:$FAPI"
dump() { for c in "$@"; do echo "=== $c"; javap -public -cp "$CP" "$c" 2>&1 | head -400; done; }
# Explicit list plus pattern matches from the class list
grep -E '^net\.minecraft\.client\.(Minecraft|KeyMapping|Options|DeltaTracker|Camera|MouseHandler|KeyboardHandler)$|gui\.(GuiGraphics[A-Za-z]*|Font|screens\.Screen|components\.(AbstractWidget|EditBox|Renderable))$|client\.input\.[A-Za-z]+Event$|client\.input\.[A-Za-z]+Info$|blaze3d\.platform\.(InputConstants|Window)$|client\.renderer\.(GameRenderer|LevelRenderer|PostChain|RenderPipelines|RenderType)$|client\.player\.LocalPlayer$|client\.multiplayer\.(MultiPlayerGameMode|ClientLevel|ClientPacketListener)$|world\.entity\.(Entity|LivingEntity)$|world\.entity\.player\.(Player|Inventory|Abilities)$|world\.phys\.(Vec3|AABB|HitResult|BlockHitResult|EntityHitResult)$|util\.(ARGB|Mth)$|network\.chat\.Component$|world\.InteractionHand$|core\.BlockPos$|world\.level\.block\.state\.BlockState$|world\.inventory\.ClickType$|client\.gui\.render\.state\.|client\.renderer\.state\.' "$OUT/mc-classes.txt" | grep -v '\$' > "$OUT/targets.txt"
grep -iE 'blur|textinput|TextInputManager' "$OUT/mc-classes.txt" | grep -v '\$' | head -40 >> "$OUT/targets.txt"
grep -E 'event\.lifecycle\.v1\.ClientTickEvents$|keymapping\.v1\.|keybinding\.v1\.|rendering\.v1\.(hud\.|HudRenderCallback|world\.|WorldRender|LevelRender)|screen\.v1\.(ScreenEvents|Screens)$|message\.v1\.ClientReceiveMessageEvents$|command\.v2\.ClientCommandManager$' "$OUT/fabric-classes.txt" | grep -v '\$' >> "$OUT/targets.txt"
[ -f tools/extra-classes.txt ] && cat tools/extra-classes.txt >> "$OUT/targets.txt"
JOML=$(find ~/.gradle/caches/modules-2 -name "joml-*.jar" | grep -v sources | head -1); CP="$CP:$JOML"
dump $(sort -u "$OUT/targets.txt") > "$OUT/signatures.txt"
wc -l "$OUT"/*.txt
