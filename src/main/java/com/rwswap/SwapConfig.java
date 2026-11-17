package com.rwswap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import net.fabricmc.loader.api.FabricLoader;

/** Persistent settings for RW Swap. */
public final class SwapConfig {
    private static final int DEFAULT_DELAY = 300;
    private static final int MAX_DELAY = 5000;
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("rwswap.properties");
    private static int delayMillis = DEFAULT_DELAY;

    private SwapConfig() {}

    public static int getDelayMillis() {
        return delayMillis;
    }

    public static void setDelayMillis(int value) {
        delayMillis = Math.max(0, Math.min(MAX_DELAY, value));
        save();
    }

    public static void load() {
        if (!Files.isRegularFile(FILE)) return;
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
            int value = Integer.parseInt(properties.getProperty("delayMillis", String.valueOf(DEFAULT_DELAY)));
            delayMillis = Math.max(0, Math.min(MAX_DELAY, value));
        } catch (IOException | NumberFormatException ignored) {
            delayMillis = DEFAULT_DELAY;
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("delayMillis", String.valueOf(delayMillis));
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                properties.store(output, "RW Swap settings");
            }
        } catch (IOException exception) {
            System.err.println("[RW Swap] Could not save settings: " + exception.getMessage());
        }
    }
}
