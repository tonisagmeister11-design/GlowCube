package de.glowcube.claudeai.npc;

import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import de.glowcube.claudeai.ClaudeAIPlugin;

/**
 * Setzt das Aussehen des Mannequins ueber den Vanilla-Befehl /data - so braucht das
 * Plugin keine versionsabhaengige Profil-Schnittstelle.
 *
 *   steve / alex / ari / efe / kai / makena / noor / sunny / zuri   Standard-Skins
 *   player:Name                                                     Skin eines echten Spielers
 */
final class Skin {

    private static final Set<String> DEFAULTS = Set.of("steve", "alex", "ari", "efe", "kai", "makena", "noor", "sunny", "zuri");
    private static final Set<String> SLIM = Set.of("alex", "noor", "sunny", "zuri", "makena");

    private Skin() {}

    static void apply(ClaudeAIPlugin plugin, LivingEntity body, String skin) {
        if (!body.getType().name().equals("MANNEQUIN")) return;
        String id = body.getUniqueId().toString();
        String s = skin == null ? "steve" : skin.trim();
        String profile;
        if (s.toLowerCase(Locale.ROOT).startsWith("player:")) {
            String name = s.substring(7).replaceAll("[^A-Za-z0-9_]", "");
            profile = "\"" + name + "\"";
        } else {
            String key = s.toLowerCase(Locale.ROOT);
            if (!DEFAULTS.contains(key)) key = "steve";
            String model = SLIM.contains(key) ? "slim" : "wide";
            String texture = "entity/player/" + model + "/" + key;
            // Erst nur die Textur, dann mit Armform - klappt das zweite nicht, bleibt das erste
            run(plugin, "data merge entity " + id + " {profile:{texture:\"" + texture + "\"}}");
            profile = "{texture:\"" + texture + "\",model:\"" + model + "\"}";
        }
        // Einzeln, damit ein unbekanntes Feld nicht die anderen verhindert
        run(plugin, "data merge entity " + id + " {profile:" + profile + "}");
        run(plugin, "data merge entity " + id + " {immovable:1b}");
        run(plugin, "data merge entity " + id + " {hide_description:1b}");
    }

    private static void run(ClaudeAIPlugin plugin, String command) {
        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        } catch (Throwable t) {
            plugin.getLogger().fine("Skin-Befehl ging nicht: " + command + " (" + t + ")");
        }
    }
}
