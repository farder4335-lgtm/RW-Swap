package com.rwswap;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
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
    private boolean keysReleased;

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
        ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
        ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
    }

    /**
     * Runs BEFORE the player processes input for this tick. While a swap is pending the movement keys
     * are forced up so the player really stops (zeroing velocity at the end of the tick is not enough,
     * because a held key would push the player again on the next tick).
     */
    private void onStartTick(Minecraft mc) {
        LocalPlayer player = mc.player;
        if (!this.swapping || player == null) return;
        holdStill(mc, player);
    }

    private void holdStill(Minecraft mc, LocalPlayer player) {
        Options options = mc.options;
        options.keyUp.setDown(false);
        options.keyDown.setDown(false);
        options.keyLeft.setDown(false);
        options.keyRight.setDown(false);
        options.keyJump.setDown(false);
        options.keySprint.setDown(false);
        this.keysReleased = true;

        player.setSprinting(false);
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(0.0, motion.y, 0.0);
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
            // Keep the player still while the pause is active.
            holdStill(mc, player);

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
        Minecraft mc = Minecraft.getInstance();
        if (this.keysReleased && mc.gui.screen() == null) {
            // Give control back: re-sync movement keys with the keys that are physically held.
            KeyMapping.setAll();
        }
        this.keysReleased = false;
        this.swapping = false;
        this.sourceSlot = -1;
        this.hotbarSlot = -1;
    }

    private static void click(Minecraft mc, LocalPlayer player, AbstractContainerMenu menu, int slot) {
        mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, player);
    }
}
