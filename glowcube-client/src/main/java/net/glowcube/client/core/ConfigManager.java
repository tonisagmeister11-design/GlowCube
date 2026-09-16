package net.glowcube.client.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.glowcube.client.GlowCubeClient;
import net.glowcube.client.core.setting.Setting;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Schreibt und liest glowcube.json im Config-Ordner. */
public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ModuleManager modules;
    private final Path file;

    public ConfigManager(ModuleManager modules) {
        this.modules = modules;
        this.file = FabricLoader.getInstance().getConfigDir().resolve(GlowCubeClient.MOD_ID + ".json");
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", GlowCubeClient.VERSION);

        JsonObject moduleTree = new JsonObject();
        for (Module module : modules.all()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("enabled", module.isEnabled());
            entry.addProperty("key", module.key());

            JsonObject settings = new JsonObject();
            for (Setting setting : module.settings()) {
                settings.add(setting.name(), setting.save());
            }
            entry.add("settings", settings);
            moduleTree.add(module.name(), entry);
        }
        root.add("modules", moduleTree);

        try {
            Files.createDirectories(file.getParent());
            Files.write(file, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            GlowCubeClient.LOGGER.error("Konnte {} nicht schreiben", file, e);
        }
    }

    public void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            String text = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            if (!root.has("modules")) {
                return;
            }
            JsonObject moduleTree = root.getAsJsonObject("modules");

            for (Module module : modules.all()) {
                if (!moduleTree.has(module.name())) {
                    continue;
                }
                JsonObject entry = moduleTree.getAsJsonObject(module.name());
                if (entry.has("key")) {
                    module.setKey(entry.get("key").getAsInt());
                }
                if (entry.has("settings")) {
                    JsonObject settings = entry.getAsJsonObject("settings");
                    for (Setting setting : module.settings()) {
                        setting.load(settings.get(setting.name()));
                    }
                }
                // Zuletzt: erst wenn die Settings stehen, darf onEnable laufen.
                if (entry.has("enabled") && entry.get("enabled").getAsBoolean()) {
                    module.setEnabledSilently(true);
                }
            }
        } catch (Exception e) {
            GlowCubeClient.LOGGER.error("Konnte {} nicht lesen - nehme Standardwerte", file, e);
        }
    }
}
