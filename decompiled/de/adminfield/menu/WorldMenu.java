/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;

public final class WorldMenu
extends Menu {
    private final World world;

    public WorldMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, World world) {
        super(adminFieldPlugin, menu);
        this.world = world;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <aqua><bold>Welt</bold></aqua> <dark_gray>· " + this.world.getName());
    }

    @Override
    protected int rows() {
        return 5;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void draw() {
        this.set(4, Ui.glowing(Material.CLOCK, "<aqua><bold>" + this.world.getName() + "</bold>", List.of("<gray>Umgebung: <white>" + Ui.pretty(this.world.getEnvironment().name()), "<gray>Uhrzeit: <white>" + WorldMenu.clock(this.world.getTime()) + " <dark_gray>(" + this.world.getTime() + " Ticks)", "<gray>Wetter: <white>" + this.weatherName(), "<gray>Schwierigkeit: <white>" + WorldMenu.difficultyName(this.world.getDifficulty()), "", "<gray>Spieler hier: <white>" + this.world.getPlayers().size(), "<gray>Geladene Chunks: <white>" + this.world.getLoadedChunks().length, "<gray>Entities: <white>" + this.world.getEntities().size())));
        this.time(10, Material.SUNFLOWER, "Morgen", 1000L);
        this.time(11, Material.TORCH, "Mittag", 6000L);
        this.time(12, Material.CAMPFIRE, "Abend", 12000L);
        this.time(13, Material.CLOCK, "Nacht", 18000L);
        this.set(14, Ui.icon(Material.YELLOW_STAINED_GLASS, "<yellow>Sonne", List.of("<gray>Schaltet das Wetter auf klar.")), inventoryClickEvent -> {
            this.world.setStorm(false);
            this.world.setThundering(false);
            this.world.setWeatherDuration(24000);
            this.plugin.send((CommandSender)this.viewer, "<gray>Wetter in <white>" + this.world.getName() + "<gray>: <yellow>klar");
            this.redraw();
        });
        this.set(15, Ui.icon(Material.WATER_BUCKET, "<aqua>Regen", List.of("<gray>Lässt es regnen.")), inventoryClickEvent -> {
            this.world.setStorm(true);
            this.world.setThundering(false);
            this.plugin.send((CommandSender)this.viewer, "<gray>Wetter in <white>" + this.world.getName() + "<gray>: <aqua>Regen");
            this.redraw();
        });
        this.set(16, Ui.icon(Material.TRIDENT, "<dark_purple>Gewitter", List.of("<gray>Regen mit Blitz und Donner.")), inventoryClickEvent -> {
            this.world.setStorm(true);
            this.world.setThundering(true);
            this.plugin.send((CommandSender)this.viewer, "<gray>Wetter in <white>" + this.world.getName() + "<gray>: <dark_purple>Gewitter");
            this.redraw();
        });
        this.rule(19, (GameRule<Boolean>)GameRules.KEEP_INVENTORY, "Inventar behalten", "Spieler verlieren beim Tod nichts.");
        this.rule(20, (GameRule<Boolean>)GameRules.MOB_GRIEFING, "Mobs dürfen zerstören", "Creeper-Löcher, Endermen, Ziegen und Co.");
        this.rule(21, (GameRule<Boolean>)GameRules.ADVANCE_TIME, "Tag-Nacht-Wechsel", "Aus = die Zeit bleibt stehen.");
        this.rule(22, (GameRule<Boolean>)GameRules.ADVANCE_WEATHER, "Wetterwechsel", "Aus = das Wetter bleibt, wie es ist.");
        this.rule(23, (GameRule<Boolean>)GameRules.TNT_EXPLODES, "TNT explodiert", "Aus = gezündetes TNT macht keinen Schaden.");
        this.rule(24, (GameRule<Boolean>)GameRules.SPAWN_MONSTERS, "Monster spawnen", "Aus = keine neuen feindlichen Kreaturen.");
        this.rule(25, (GameRule<Boolean>)GameRules.PVP, "PvP", "Dürfen Spieler sich gegenseitig verletzen?");
        Difficulty difficulty = WorldMenu.nextDifficulty(this.world.getDifficulty());
        this.set(29, Ui.icon(Material.IRON_SWORD, "<gold>Schwierigkeit", List.of("<gray>Aktuell: <white>" + WorldMenu.difficultyName(this.world.getDifficulty()), "<gray>Danach: <white>" + WorldMenu.difficultyName(difficulty), "", "<yellow>➤ Klicken zum Umschalten")), inventoryClickEvent -> {
            this.world.setDifficulty(difficulty);
            this.plugin.send((CommandSender)this.viewer, "<gray>Schwierigkeit in <white>" + this.world.getName() + "<gray>: <white>" + WorldMenu.difficultyName(difficulty));
            this.redraw();
        });
        List list = Bukkit.getWorlds();
        World world = (World)list.get((list.indexOf(this.world) + 1) % list.size());
        this.set(31, Ui.icon(Material.ENDER_EYE, "<light_purple>Welt wechseln", List.of("<gray>Diese Seite gilt für: <white>" + this.world.getName(), "<gray>Nächste Welt: <white>" + world.getName(), "", "<gray>Insgesamt: <white>" + list.size() + " Welten", "<yellow>➤ Klicken zum Wechseln")), inventoryClickEvent -> new WorldMenu(this.plugin, this.parent, world).open(this.viewer));
        this.set(33, Ui.icon(Material.ENDER_PEARL, "<aqua>Zum Weltspawn", List.of("<gray>Teleportiert dich zum Spawnpunkt", "<gray>von <white>" + this.world.getName() + "<gray>.")), inventoryClickEvent -> {
            this.viewer.closeInventory();
            this.plugin.teleport(this.viewer, this.world.getSpawnLocation(), "Spawn von " + this.world.getName());
        });
        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }

    private void time(int n, Material material, String string, long l) {
        boolean bl = Math.abs(this.world.getTime() - l) < 1000L;
        List<String> list = List.of("<gray>Setzt die Zeit auf <white>" + WorldMenu.clock(l) + "<gray>.", bl ? "<green>▪ Gerade eingestellt" : "<yellow>➤ Klicken");
        this.set(n, bl ? Ui.glowing(material, "<yellow>" + string, list) : Ui.icon(material, "<yellow>" + string, list), inventoryClickEvent -> {
            this.world.setTime(l);
            this.plugin.send((CommandSender)this.viewer, "<gray>Zeit in <white>" + this.world.getName() + "<gray> auf <white>" + string + "<gray> gesetzt.");
            this.redraw();
        });
    }

    private void rule(int n, GameRule<Boolean> gameRule, String string, String string2) {
        Boolean bl = (Boolean)this.world.getGameRuleValue(gameRule);
        boolean bl2 = Boolean.TRUE.equals(bl);
        this.set(n, Ui.toggle(bl2, "<white>" + string, List.of("<gray>" + string2, "<dark_gray>" + gameRule.getKey().getKey())), inventoryClickEvent -> {
            this.world.setGameRule(gameRule, (Object)(!bl2 ? 1 : 0));
            this.plugin.send((CommandSender)this.viewer, "<gray>" + string + " in <white>" + this.world.getName() + "<gray>: " + (bl2 ? "<red>aus" : "<green>an"));
            this.redraw();
        });
    }

    private String weatherName() {
        if (this.world.isThundering()) {
            return "Gewitter";
        }
        return this.world.hasStorm() ? "Regen" : "Klar";
    }

    private static String difficultyName(Difficulty difficulty) {
        return switch (difficulty) {
            default -> throw new MatchException(null, null);
            case Difficulty.PEACEFUL -> "Friedlich";
            case Difficulty.EASY -> "Einfach";
            case Difficulty.NORMAL -> "Normal";
            case Difficulty.HARD -> "Schwer";
        };
    }

    private static Difficulty nextDifficulty(Difficulty difficulty) {
        return switch (difficulty) {
            default -> throw new MatchException(null, null);
            case Difficulty.PEACEFUL -> Difficulty.EASY;
            case Difficulty.EASY -> Difficulty.NORMAL;
            case Difficulty.NORMAL -> Difficulty.HARD;
            case Difficulty.HARD -> Difficulty.PEACEFUL;
        };
    }

    private static String clock(long l) {
        long l2 = (l % 24000L + 24000L) % 24000L;
        long l3 = (l2 / 1000L + 6L) % 24L;
        long l4 = l2 % 1000L * 60L / 1000L;
        return String.format("%02d:%02d", l3, l4);
    }
}

