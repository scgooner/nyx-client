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
exit $fail
