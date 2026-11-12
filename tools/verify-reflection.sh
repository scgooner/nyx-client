#!/usr/bin/env bash
# Fails the build if a private field the client reads by name is missing from the 26.3 game jar.
set -u
MC=$(find ~/.gradle/caches/fabric-loom -name 'minecraft-merged-deobf-*.jar' | grep -v sources | head -1)
fail=0
check() {
  local cls="$1" field="$2"
  if javap -p -cp "$MC" "$cls" 2>/dev/null | grep -qE "[ ]$field;"; then
    echo "ok   $cls.$field"
  else
    echo "MISSING $cls.$field"; fail=1
  fi
}
check net.minecraft.client.Minecraft rightClickDelay
check net.minecraft.client.multiplayer.MultiPlayerGameMode destroyDelay
check net.minecraft.world.entity.projectile.FishingHook biting
check net.minecraft.world.entity.Entity FLAG_GLOWING
if javap -p -cp "$MC" net.minecraft.world.entity.Entity | grep -qE 'void setSharedFlag\(int, boolean\)'; then
  echo "ok   net.minecraft.world.entity.Entity.setSharedFlag(int, boolean)"
else
  echo "MISSING net.minecraft.world.entity.Entity.setSharedFlag(int, boolean)"; fail=1
fi
# Nine-slice sprites rely on the "stretch_inner" key; without it the inner part tiles (slow).
if javap -c -p -cp "$MC" 'net.minecraft.client.resources.metadata.gui.GuiSpriteScaling$NineSlice' | grep -q 'stretch_inner'; then
  echo "ok   GuiSpriteScaling.NineSlice supports stretch_inner"
else
  echo "MISSING GuiSpriteScaling.NineSlice stretch_inner key"; fail=1
fi
exit $fail
