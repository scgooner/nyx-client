package dev.nyx;

import net.fabricmc.api.ClientModInitializer;

public final class NyxClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        System.out.println("[Nyx] bootstrap");
    }
}
