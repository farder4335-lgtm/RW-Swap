package com.rwswap;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Numeric delay settings screen. */
public final class RWSwapConfigScreen extends Screen {
    private final Screen parent;
    private EditBox delayField;

    public RWSwapConfigScreen(Screen parent) {
        super(Component.translatable("screen.rwswap.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int center = this.width / 2;
        this.delayField = new EditBox(this.font, center - 100, this.height / 2 - 8, 200, 20,
                Component.translatable("screen.rwswap.delay"));
        this.delayField.setMaxLength(5);
        this.delayField.setValue(String.valueOf(SwapConfig.getDelayMillis()));
        this.addRenderableWidget(this.delayField);
        this.addRenderableWidget(Button.builder(Component.translatable("gui.done"), button -> saveAndClose())
                .bounds(center - 100, this.height / 2 + 24, 200, 20).build());
    }

    private void saveAndClose() {
        String value = this.delayField.getValue().strip();
        if (!value.isEmpty()) {
            try {
                int parsed = Integer.parseInt(value);
                if (parsed < 0 || parsed > 5000) {
                    this.delayField.setValue(String.valueOf(SwapConfig.getDelayMillis()));
                    return;
                }
                SwapConfig.setDelayMillis(parsed);
            } catch (NumberFormatException exception) {
                this.delayField.setValue(String.valueOf(SwapConfig.getDelayMillis()));
                return;
            }
        }
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.centeredText(this.font, Component.translatable("screen.rwswap.delay"),
                this.width / 2, this.height / 2 - 30, 0xFFFFFFFF);
        graphics.centeredText(this.font, Component.literal("0–5000 ms"),
                this.width / 2, this.height / 2 + 50, 0xFFA0A0A0);
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
