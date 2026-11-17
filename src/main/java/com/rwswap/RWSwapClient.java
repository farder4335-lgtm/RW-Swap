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
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.item.Items;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/** Client-side player-head/offhand swapper. It never uses the armor/head equipment slot. */
public final class RWSwapClient implements ClientModInitializer {
    private static final int FIRST_STORAGE_SLOT = 9;
    private static final int LAST_STORAGE_SLOT = 44;
    private static final int FIRST_MAIN_SLOT = 9;
    private static final int LAST_MAIN_SLOT = 35;
    private static final int FIRST_HOTBAR_SLOT = 36;
    private static final int LAST_HOTBAR_SLOT = 44;
    private static final int OFFHAND_SLOT = 45;

    private KeyMapping swapKey;
    private int sourceSlot = -1;
    private int hotbarSlot = -1;
    private long executeAtNanos;
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
            reset();
            return;
        }

        if (this.swapping) {
            // Freeze the player in place for the full pause: clear velocity and movement input.
            player.setDeltaMovement(Vec3.ZERO);
            player.input.forwardImpulse = 0.0F;
            player.input.leftImpulse = 0.0F;
            player.input.jumping = false;
            player.setSprinting(false);

            if (mc.gui.screen() != null || player.containerMenu != player.inventoryMenu) {
                reset();
                return;
            }
            if (System.nanoTime() >= this.executeAtNanos) {
                runSwap(mc, player);
            }
            return;
        }

        if (!pressed || mc.gui.screen() != null) return;

        AbstractContainerMenu menu = player.inventoryMenu;
        if (player.containerMenu != menu || !menu.getCarried().isEmpty()) return;

        int selectedHotbarSlot = FIRST_HOTBAR_SLOT + player.getInventory().getSelectedSlot();
        this.sourceSlot = findPlayerHeadSlot(menu, selectedHotbarSlot);
        if (this.sourceSlot < 0) {
            player.sendOverlayMessage(Component.translatable("text.rwswap.no_player_head"));
            return;
        }

        // Use the selected hotbar slot unless it contains the head itself.
        this.hotbarSlot = selectedHotbarSlot == this.sourceSlot
                ? FIRST_HOTBAR_SLOT + ((player.getInventory().getSelectedSlot() + 1) % 9)
                : selectedHotbarSlot;

        this.swapping = true;
        this.executeAtNanos = System.nanoTime() + SwapConfig.getDelayMillis() * 1_000_000L;
    }

    private static int findPlayerHeadSlot(AbstractContainerMenu menu, int selectedHotbarSlot) {
        for (int slot = FIRST_MAIN_SLOT; slot <= LAST_MAIN_SLOT; slot++) {
            if (menu.getSlot(slot).getItem().is(Items.PLAYER_HEAD)) return slot;
        }
        for (int slot = FIRST_HOTBAR_SLOT; slot <= LAST_HOTBAR_SLOT; slot++) {
            if (slot != selectedHotbarSlot && menu.getSlot(slot).getItem().is(Items.PLAYER_HEAD)) return slot;
        }
        return -1;
    }

    private void runSwap(Minecraft mc, LocalPlayer player) {
        AbstractContainerMenu menu = player.inventoryMenu;
        if (player.containerMenu != menu || mc.gui.screen() != null
                || !menu.getCarried().isEmpty()
                || this.sourceSlot < FIRST_STORAGE_SLOT
                || this.sourceSlot > LAST_STORAGE_SLOT
                || !menu.getSlot(this.sourceSlot).getItem().is(Items.PLAYER_HEAD)) {
            reset();
            player.sendOverlayMessage(Component.translatable("text.rwswap.swap_failed"));
            return;
        }

        // Move the head through the hotbar, then place it in the offhand.
        click(mc, player, menu, this.sourceSlot);
        click(mc, player, menu, this.hotbarSlot);
        click(mc, player, menu, this.sourceSlot);
        click(mc, player, menu, this.hotbarSlot);
        click(mc, player, menu, OFFHAND_SLOT);
        click(mc, player, menu, this.sourceSlot);
        click(mc, player, menu, this.hotbarSlot);

        reset();
    }

    private void reset() {
        this.swapping = false;
        this.sourceSlot = -1;
        this.hotbarSlot = -1;
    }

    private static void click(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, int slot) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
    }
}
