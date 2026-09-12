/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import de.adminfield.menu.PlayerActionMenu;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class ToolsMenu
extends Menu {
    private static final float[] SPEEDS = new float[]{0.2f, 0.3f, 0.4f, 0.6f};

    public ToolsMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <light_purple><bold>Eigene Werkzeuge</bold>");
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
        this.set(4, Ui.head((OfflinePlayer)this.viewer, "<white><bold>" + this.viewer.getName() + "</bold>", List.of("<gray>Welt: <white>" + this.viewer.getWorld().getName(), "<gray>Position: <white>" + Ui.pos(this.viewer.getLocation()), "<gray>Modus: <white>" + Ui.gameMode(this.viewer.getGameMode()))));
        boolean bl = this.plugin.state().isVanished(this.viewer);
        this.set(10, Ui.toggle(bl, "<dark_gray><bold>Vanish", List.of("<gray>Versteckt dich vor allen Spielern,", "<gray>die selbst kein AdminField haben.")), inventoryClickEvent -> {
            this.plugin.state().setVanished(this.viewer, !bl);
            this.plugin.send((CommandSender)this.viewer, bl ? "<gray>Du bist wieder sichtbar." : "<gray>Du bist jetzt <white>unsichtbar<gray>.");
            this.redraw();
        });
        boolean bl2 = this.viewer.getAllowFlight();
        this.set(11, Ui.toggle(bl2, "<aqua><bold>Flug", List.of("<gray>Erlaubt dir das Fliegen,", "<gray>auch im Überlebensmodus.")), inventoryClickEvent -> {
            this.viewer.setAllowFlight(!bl2);
            if (bl2) {
                this.viewer.setFlying(false);
            }
            this.plugin.send((CommandSender)this.viewer, "<gray>Flug: " + (bl2 ? "<red>aus" : "<green>an"));
            this.redraw();
        });
        boolean bl3 = this.plugin.state().isGod(this.viewer.getUniqueId());
        this.set(12, Ui.toggle(bl3, "<gold><bold>Unverwundbar", List.of("<gray>Du nimmst keinen Schaden mehr –", "<gray>auch nicht durch Sturz oder Lava.")), inventoryClickEvent -> {
            this.plugin.state().setGod(this.viewer.getUniqueId(), !bl3);
            this.plugin.send((CommandSender)this.viewer, "<gray>Unverwundbarkeit: " + (bl3 ? "<red>aus" : "<green>an"));
            this.redraw();
        });
        boolean bl4 = this.viewer.hasPotionEffect(PotionEffectType.NIGHT_VISION);
        this.set(13, Ui.toggle(bl4, "<yellow><bold>Nachtsicht", List.of("<gray>Du siehst auch in Höhlen und", "<gray>nachts alles klar.")), inventoryClickEvent -> {
            if (bl4) {
                this.viewer.removePotionEffect(PotionEffectType.NIGHT_VISION);
            } else {
                this.viewer.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, -1, 0, false, false, false));
            }
            this.redraw();
        });
        GameMode gameMode = ToolsMenu.nextMode(this.viewer.getGameMode());
        this.set(14, Ui.icon(Material.GRASS_BLOCK, "<green><bold>Spielmodus", List.of("<gray>Aktuell: <white>" + Ui.gameMode(this.viewer.getGameMode()), "<gray>Danach: <white>" + Ui.gameMode(gameMode), "", "<yellow>➤ Klicken zum Umschalten")), inventoryClickEvent -> {
            this.viewer.setGameMode(gameMode);
            this.redraw();
        });
        this.set(19, Ui.icon(Material.GOLDEN_APPLE, "<green>Selbst heilen", List.of("<gray>Leben, Hunger, Effekte, Feuer", "<gray>und Frost auf einen Schlag zurücksetzen.")), inventoryClickEvent -> {
            PlayerActionMenu.fullyHeal(this.viewer);
            this.plugin.send((CommandSender)this.viewer, "<green>Du bist wieder frisch.");
            this.redraw();
        });
        boolean bl5 = this.plugin.state().hasBack(this.viewer);
        this.set(20, Ui.icon(bl5 ? Material.ENDER_PEARL : Material.ENDER_EYE, "<aqua>Zurückspringen", List.of("<gray>Zur Position vor dem letzten", "<gray>AdminField-Teleport.", "", bl5 ? "<yellow>➤ Klicken" : "<dark_gray>Kein Punkt gemerkt")), inventoryClickEvent -> {
            Location location = this.plugin.state().popBack(this.viewer);
            if (location == null) {
                this.plugin.send((CommandSender)this.viewer, "<red>Es gibt keinen Rücksprungpunkt.");
                return;
            }
            this.viewer.closeInventory();
            this.viewer.teleportAsync(location);
            this.plugin.send((CommandSender)this.viewer, "<gray>Zurück bei <white>" + Ui.pos(location) + "<gray>.");
        });
        this.set(21, Ui.icon(Material.COMPASS, "<aqua>Zum Weltspawn", List.of("<gray>Bringt dich zum Spawnpunkt", "<gray>deiner aktuellen Welt.")), inventoryClickEvent -> {
            this.viewer.closeInventory();
            this.plugin.teleport(this.viewer, this.viewer.getWorld().getSpawnLocation(), "Spawn von " + this.viewer.getWorld().getName());
        });
        ArrayList arrayList = new ArrayList(Bukkit.getOnlinePlayers());
        arrayList.remove(this.viewer);
        this.set(22, Ui.icon(Material.ENDER_EYE, "<light_purple>Zu zufälligem Spieler", List.of("<gray>Springt zu einem beliebigen", "<gray>anderen Spieler auf dem Server.", "", "<gray>Zur Auswahl: <white>" + arrayList.size())), inventoryClickEvent -> {
            if (arrayList.isEmpty()) {
                this.plugin.send((CommandSender)this.viewer, "<red>Es ist sonst niemand online.");
                return;
            }
            Player player = (Player)arrayList.get(ThreadLocalRandom.current().nextInt(arrayList.size()));
            this.viewer.closeInventory();
            this.plugin.teleport(this.viewer, player.getLocation(), player.getName());
        });
        float f = this.viewer.getWalkSpeed();
        int n = ToolsMenu.speedLevel(f);
        this.set(23, Ui.icon(Material.SUGAR, "<white>Tempo", List.of("<gray>Lauf- und Fluggeschwindigkeit.", "<gray>Stufe: <white>" + (n + 1) + "<dark_gray>/<white>" + SPEEDS.length, "", "<yellow>➤ Klicken für die nächste Stufe")), inventoryClickEvent -> {
            int n2 = (n + 1) % SPEEDS.length;
            this.viewer.setWalkSpeed(SPEEDS[n2]);
            this.viewer.setFlySpeed(Math.min(1.0f, SPEEDS[n2]));
            this.plugin.send((CommandSender)this.viewer, "<gray>Tempo auf Stufe <white>" + (n2 + 1) + "<gray>.");
            this.redraw();
        });
        this.set(24, Ui.icon(Material.BUCKET, "<white>Tempo zurücksetzen", List.of("<gray>Zurück auf normales Lauftempo.")), inventoryClickEvent -> {
            this.viewer.setWalkSpeed(0.2f);
            this.viewer.setFlySpeed(0.1f);
            this.plugin.send((CommandSender)this.viewer, "<gray>Tempo zurückgesetzt.");
            this.redraw();
        });
        this.backButton(36);
        this.closeButton(44);
        this.fillEmpty();
    }

    private static int speedLevel(float f) {
        for (int i = 0; i < SPEEDS.length; ++i) {
            if (!(Math.abs(SPEEDS[i] - f) < 0.01f)) continue;
            return i;
        }
        return 0;
    }

    private static GameMode nextMode(GameMode gameMode) {
        return switch (gameMode) {
            default -> throw new MatchException(null, null);
            case GameMode.SURVIVAL -> GameMode.CREATIVE;
            case GameMode.CREATIVE -> GameMode.ADVENTURE;
            case GameMode.ADVENTURE -> GameMode.SPECTATOR;
            case GameMode.SPECTATOR -> GameMode.SURVIVAL;
        };
    }
}

