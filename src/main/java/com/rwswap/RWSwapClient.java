package com.rwswap;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Client-side player-head/offhand swapper. It never uses the armor/head equipment slot. */
public final class RWSwapClient implements ClientModInitializer {
    private static final int FIRST_STORAGE_SLOT = 9;
    private static final int LAST_STORAGE_SLOT = 44;
    private static final int OFFHAND_SLOT = 45;

    private KeyMapping swapKey;
    private int step;
    private int sourceSlot = -1;
    private long nextStepAtNanos;
    private boolean swapping;

    @Override
    public void onInitializeClient() {
        SwapConfig.load();
        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath("rwswap", "main"));
        this.swapKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.rwswap.swap_head_offhand",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_H,
                category
        ));
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    private void onClientTick(Minecraft mc) {
        boolean pressed = false;
        while (this.swapKey.consumeClick()) pressed = true;

        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null) {
            this.swapping = false;
            return;
        }

        if (this.swapping) {
            if (System.nanoTime() >= this.nextStepAtNanos) runStep(mc, player);
            return;
        }

        if (!pressed || mc.gui.screen() != null) return;

        AbstractContainerMenu menu = player.inventoryMenu;
        if (player.containerMenu != menu || !menu.getCarried().isEmpty()) return;

        for (int slot = FIRST_STORAGE_SLOT; slot <= LAST_STORAGE_SLOT; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(Items.PLAYER_HEAD)) {
                this.sourceSlot = slot;
                this.step = 0;
                this.swapping = true;
                this.nextStepAtNanos = 0L;
                return;
            }
        }

        player.sendOverlayMessage(Component.translatable("text.rwswap.no_player_head"));
    }

    private void runStep(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (player.containerMenu != menu || mc.gui.screen() != null) {
            this.swapping = false;
            return;
        }

        switch (this.step) {
            case 0 -> {
                if (!menu.getSlot(this.sourceSlot).getItem().is(Items.PLAYER_HEAD)) {
                    this.swapping = false;
                    return;
                }
                click(mc, player, menu, this.sourceSlot);
            }
            case 1 -> {
                if (menu.getCarried().isEmpty()) {
                    this.swapping = false;
                    return;
                }
                click(mc, player, menu, OFFHAND_SLOT);
            }
            case 2 -> {
                if (!menu.getCarried().isEmpty()) click(mc, player, menu, this.sourceSlot);
            }
            default -> {
                if (!menu.getSlot(OFFHAND_SLOT).getItem().is(Items.PLAYER_HEAD)) {
                    player.sendOverlayMessage(Component.translatable("text.rwswap.swap_failed"));
                }
                this.swapping = false;
                return;
            }
        }

        this.step++;
        this.nextStepAtNanos = System.nanoTime() + SwapConfig.getDelayMillis() * 1_000_000L;
    }

    private static void click(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, int slot) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
    }
}
