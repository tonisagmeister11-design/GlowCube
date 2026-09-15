package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.build.BuildTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

/**
 * Woraus gebaut wird.
 *
 * <p>Die Auswahl steht als Namensliste im Code, nicht als feste Verweise: was diese
 * Serverversion nicht kennt, faellt einfach aus der Liste, statt das Plugin zu zerlegen.
 */
public final class BuildBlockMenu extends Menu {

    private static final List<String> PALETTE = List.of(
            "STONE", "COBBLESTONE", "SMOOTH_STONE", "STONE_BRICKS", "DEEPSLATE", "ANDESITE",
            "DIRT", "GRASS_BLOCK", "SAND", "GRAVEL", "CLAY", "TERRACOTTA",
            "OAK_PLANKS", "SPRUCE_PLANKS", "DARK_OAK_PLANKS", "OAK_LOG", "BRICKS", "SANDSTONE",
            "QUARTZ_BLOCK", "GLASS", "TINTED_GLASS", "OBSIDIAN", "NETHERRACK", "BLACKSTONE",
            "WHITE_CONCRETE", "LIGHT_GRAY_CONCRETE", "BLACK_CONCRETE", "RED_CONCRETE",
            "ORANGE_CONCRETE", "YELLOW_CONCRETE", "LIME_CONCRETE", "BLUE_CONCRETE",
            "LIGHT_BLUE_CONCRETE", "PURPLE_CONCRETE", "PINK_CONCRETE",
            "GLOWSTONE", "SEA_LANTERN", "SNOW_BLOCK", "ICE", "MOSS_BLOCK",
            "IRON_BLOCK", "GOLD_BLOCK", "DIAMOND_BLOCK", "EMERALD_BLOCK", "BEDROCK");

    public BuildBlockMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <aqua><bold>Block wählen</bold></aqua>");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    protected void draw() {
        BuildTool tool = BuildTool.instance();
        if (tool == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Das Bau-Werkzeug läuft nicht."));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }
        Material chosen = tool.chosen(this.viewer);

        int slot = 0;
        for (String name : PALETTE) {
            if (slot >= 45) {
                break;
            }
            Material material = BuildTool.material(name, null);
            if (material == null) {
                continue;
            }
            boolean active = material == chosen;
            List<String> lore = new ArrayList<>();
            lore.add(active ? "<green>▪ Ausgewählt" : "<yellow>➤ Klicken zum Auswählen");
            this.set(slot++, active
                    ? Ui.glowing(material, "<green><bold>" + BuildToolMenu.pretty(material) + "</bold>", lore)
                    : Ui.icon(material, "<white>" + BuildToolMenu.pretty(material), lore),
                    event -> this.pick(tool, material));
        }

        this.divider(5);
        this.backButton(45);
        this.set(48, Ui.icon(Material.IRON_PICKAXE, "<aqua><bold>Block aus deiner Hand</bold>",
                List.of("<gray>Nimmt, was du gerade in der Hand hältst.",
                        "",
                        "<yellow>➤ Klicken")),
                event -> this.fromHand(tool));
        this.set(50, Ui.icon(Material.WRITABLE_BOOK, "<aqua><bold>Namen eintippen</bold>",
                List.of("<gray>Für alles, was hier nicht steht.",
                        "<dark_gray>Zum Beispiel: <white>mossy_cobblestone",
                        "",
                        "<yellow>➤ Klicken, dann Namen in den Chat")),
                event -> this.byName(tool));
        this.closeButton(53);
        this.fillEmpty();
    }

    private void pick(BuildTool tool, Material material) {
        tool.choose(this.viewer, material);
        this.plugin.send((CommandSender) this.viewer,
                "<gray>Block: <white>" + BuildToolMenu.pretty(material));
        this.redraw();
    }

    private void fromHand(BuildTool tool) {
        ItemStack held;
        try {
            held = this.viewer.getInventory().getItemInMainHand();
        } catch (Throwable ignored) {
            held = null;
        }
        if (held == null || held.getType() == null || !held.getType().isBlock()
                || held.getType().isAir()) {
            this.plugin.send((CommandSender) this.viewer,
                    "<red>Du hältst nichts in der Hand, woraus sich bauen liesse.");
            return;
        }
        this.pick(tool, held.getType());
    }

    private void byName(BuildTool tool) {
        this.plugin.state().prompt(this.viewer, "Welcher Block? (z. B. mossy_cobblestone)", text -> {
            String wanted = text == null ? "" : text.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
            Material material = BuildTool.material(wanted, null);
            if (material == null || !material.isBlock()) {
                this.plugin.send((CommandSender) this.viewer,
                        "<red>Einen Block namens <white>" + text + "<red> gibt es nicht.");
                return;
            }
            tool.choose(this.viewer, material);
            this.plugin.send((CommandSender) this.viewer,
                    "<gray>Block: <white>" + BuildToolMenu.pretty(material));
        });
    }
}
