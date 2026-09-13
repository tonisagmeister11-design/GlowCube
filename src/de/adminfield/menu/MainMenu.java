/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.AdminRole;
import de.adminfield.Ui;
import de.adminfield.menu.BuildMenu;
import de.adminfield.menu.CheatMenu;
import de.adminfield.menu.DeathNoteMenu;
import de.adminfield.menu.JailMenu;
import de.adminfield.menu.LogMenu;
import de.adminfield.menu.LuckyBlockMenu;
import de.adminfield.menu.Menu;
import de.adminfield.menu.MobDisguiseMenu;
import de.adminfield.menu.NameDisguiseMenu;
import de.adminfield.menu.OwnerStuffMenu;
import de.adminfield.menu.PlayerListMenu;
import de.adminfield.menu.ServerMenu;
import de.adminfield.menu.ToolsMenu;
import de.adminfield.menu.WorldMenu;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;

public final class MainMenu
extends Menu {
    public MainMenu(AdminFieldPlugin adminFieldPlugin) {
        super(adminFieldPlugin, null);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gradient:#5ad1ff:#a06bff><bold>AdminField</bold></gradient> <dark_gray>· Kontrollzentrum");
    }

    @Override
    protected int rows() {
        return 6;
    }

    @Override
    public boolean live() {
        return true;
    }

    @Override
    protected void draw() {
        boolean bl;
        double d = Bukkit.getServer().getTPS()[0];
        int n = Bukkit.getOnlinePlayers().size();
        int n2 = this.plugin.tracker().confirmed().size();
        AdminRole adminRole = this.plugin.access().role(this.viewer.getUniqueId());
        String string = adminRole == null ? "<gray>ohne Rang" : adminRole.gradient() + "<bold>" + adminRole.label() + "</bold></gradient>";
        this.set(4, Ui.glowing(Material.NETHER_STAR, "<gradient:#5ad1ff:#a06bff><bold>AdminField</bold></gradient>", List.of("<gray>Angemeldet als <white>" + this.viewer.getName() + " <dark_gray>·</dark_gray> " + string, "", "<gray>Spieler online: <white>" + n + "<dark_gray>/</dark_gray><white>" + Bukkit.getMaxPlayers(), "<gray>Erkannte Bauwerke: <white>" + n2, "<gray>TPS: " + Ui.gradeHigh(d, 19.0, 17.0) + String.format("%.2f", d), "<gray>Laufzeit: <white>" + Ui.duration(this.plugin.serverUptime()))));
        this.set(10, Ui.icon(Material.PLAYER_HEAD, "<aqua><bold>Spieler</bold>", List.of("<gray>Alle Spieler mit Biom, Koordinaten,", "<gray>Leben, Hunger und Ping.", "", "<gray>Heilen, sättigen, teleportieren,", "<gray>einfrieren, kicken, töten.", "", "<white>" + n + " <gray>online", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new PlayerListMenu(this.plugin, this).open(this.viewer));
        this.set(12, Ui.icon(Material.BRICKS, "<gold><bold>Bauwerke</bold>", List.of("<gray>Erkennt automatisch, wo Spieler", "<gray>viele Blöcke setzen – und was", "<gray>daraus wird (Haus, Turm, Farm ...).", "", "<white>" + n2 + " <gray>Bauwerke erkannt", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new BuildMenu(this.plugin, this).open(this.viewer));
        this.set(14, Ui.icon(Material.WRITABLE_BOOK, "<yellow><bold>Verlauf</bold>", List.of("<gray>Die letzten Ereignisse: Joins, Tode,", "<gray>neue Bauwerke, Grief-Alarme,", "<gray>Admin-Eingriffe.", "", "<white>" + this.plugin.log().size() + " <gray>Einträge", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new LogMenu(this.plugin, this).open(this.viewer));
        this.set(16, Ui.icon(Material.COMPARATOR, "<green><bold>Serverstatus</bold>", List.of("<gray>TPS, Tickzeit, Arbeitsspeicher,", "<gray>Chunks, Entities und Laufzeit.", "", "<gray>Dazu: Aufräum-Werkzeuge.", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new ServerMenu(this.plugin, this).open(this.viewer));
        this.set(27, Ui.icon(Material.CLOCK, "<aqua><bold>Welt & Zeit</bold>", List.of("<gray>Tageszeit, Wetter, Schwierigkeit", "<gray>und die wichtigsten Spielregeln.", "", "<gray>Aktuelle Welt: <white>" + this.viewer.getWorld().getName(), "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new WorldMenu(this.plugin, this, this.viewer.getWorld()).open(this.viewer));
        this.set(29, Ui.icon(Material.FEATHER, "<light_purple><bold>Eigene Werkzeuge</bold>", List.of("<gray>Vanish, Flug, Godmode, Nachtsicht,", "<gray>Spielmodus und Tempo.", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new ToolsMenu(this.plugin, this).open(this.viewer));
        boolean bl2 = this.plugin.jail().isSet();
        this.set(31, Ui.icon(Material.IRON_BARS, "<red><bold>Gefängnis</bold>", List.of("<gray>Ort festlegen, Gefangene sehen", "<gray>und wieder freilassen.", "", bl2 ? "<gray>Sitzen ein: <white>" + this.plugin.jail().count() : "<red>Noch kein Ort festgelegt", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new JailMenu(this.plugin, this).open(this.viewer));
        this.set(33, Ui.icon(Material.BELL, "<gold><bold>Ansage schreiben</bold>", List.of("<gray>Schickt eine Nachricht an alle", "<gray>Spieler auf dem Server.", "", "<yellow>➤ Klicken, dann in den Chat tippen")), inventoryClickEvent -> this.plugin.state().prompt(this.viewer, "Was soll allen angesagt werden?", text -> {
            Component component = Ui.mm("<gradient:#5ad1ff:#a06bff><bold>Ansage</bold></gradient> <dark_gray>»</dark_gray> <white>" + text);
            Bukkit.broadcast((Component)component);
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " sagte an: " + text);
        }));
        if (this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            bl = this.plugin.luckyBlocks().enabled() && this.plugin.luckyBlocks().pluginPresent();
            this.set(38, Ui.icon(Material.NOTE_BLOCK, "<gradient:#ffe259:#4f8bff><bold>Lucky Blocks</bold></gradient>", List.of("<gray>Lucky Blocks direkt ins Inventar holen.", "", bl ? "<gray>Normal und Super verfügbar," : "<red>Lucky-Block-Plugin nicht gefunden.", bl ? "<gray>Subscribe bleibt gesperrt." : "<dark_gray>Menü öffnet trotzdem.", "", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new LuckyBlockMenu(this.plugin, this).open(this.viewer));
            this.set(40, Ui.glowing(Material.BOOK, "<dark_red><bold>Death Note</bold></dark_red>", List.of("<gray>Einen Namen eintragen und sein", "<gray>Schicksal wählen.", "", "<gray>Es passiert erst, wenn er wirklich", "<gray>in die passende Lage kommt – und", "<gray>sieht dann aus wie normales Pech.", "", "<gray>Offene Einträge: <white>" + this.plugin.deathNote().count(), "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new DeathNoteMenu(this.plugin, this).open(this.viewer));
            this.set(42, Ui.glowing(Material.GOLDEN_HELMET, "<gradient:#ffd166:#ff8a00><bold>Owner-Ausrüstung</bold></gradient>", List.of("<gray>Waffen, Rüstung und Stäbe,", "<gray>die es sonst nirgends gibt.", "", "<gray>Unter anderem: <white>Schärfe 255<gray>,", "<gray>TNT-Regen, Blitzstab, Eisstab.", "", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new OwnerStuffMenu(this.plugin, this).open(this.viewer));
            String nameFake = de.adminfield.disguise.NameDisguise.get(this.plugin).nameOf(this.viewer.getUniqueId());
            this.set(36, Ui.glowing(Material.PLAYER_HEAD, "<gradient:#5ad1ff:#a06bff><bold>Fremder Name</bold></gradient>", List.of("<gray>Gib dich als beliebiges Minecraft-Konto", "<gray>aus – mit Namen und echtem Skin.", "", "<gray>Auch von Leuten, die hier gar nicht", "<gray>spielen. Name über dem Kopf und in", "<gray>der Tabliste inklusive.", "", nameFake == null ? "<dark_gray>Zurzeit trägst du deinen Namen." : "<gray>Aktuell: <white>" + nameFake, "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new NameDisguiseMenu(this.plugin, this).open(this.viewer));
            int n4 = de.adminfield.disguise.MobDisguise.get(this.plugin).count();
            this.set(44, Ui.glowing(Material.CREEPER_HEAD, "<gradient:#7bffb0:#00a86b><bold>Mob-Verkleidung</bold></gradient>", List.of("<gray>Verwandelt dich oder einen anderen", "<gray>Spieler in einen echten Mob –", "<gray>nicht nur optisch.", "", "<gray>Creeper, Baby-Zombie, Villager,", "<gray>Enderman, Wither, Eisengolem, Tiere.", "", "<gray>Ohne Nametag, ohne Chat-Meldung.", n4 > 0 ? "<gray>Verkleidet gerade: <white>" + n4 : "<dark_gray>Zurzeit ist niemand verkleidet.", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new MobDisguiseMenu(this.plugin, this).open(this.viewer));
        }
        this.set(48, Ui.icon((bl = this.plugin.state().hasBack(this.viewer)) ? Material.ENDER_PEARL : Material.ENDER_EYE, "<aqua><bold>Zurückspringen</bold>", List.of("<gray>Bringt dich an die Position zurück,", "<gray>von der aus du dich zuletzt", "<gray>per AdminField teleportiert hast.", "", bl ? "<yellow>➤ Klicken zum Zurückspringen" : "<dark_gray>Kein Rücksprungpunkt vorhanden")), inventoryClickEvent -> {
            Location location = this.plugin.state().popBack(this.viewer);
            if (location == null) {
                this.plugin.send((CommandSender)this.viewer, "<red>Es gibt keinen Rücksprungpunkt.");
                return;
            }
            this.viewer.closeInventory();
            this.viewer.teleportAsync(location);
            this.plugin.send((CommandSender)this.viewer, "<gray>Zurück bei <white>" + Ui.pos(location) + "<gray>.");
        });
        this.closeButton(50);
        int n3 = this.plugin.cheats().count();
        this.set(35, n3 > 0 ? Ui.glowing(Material.SPYGLASS, "<red><bold>Cheat-Überwachung</bold>", List.of("<gray>Beobachtet gerade: <white>" + n3 + " Spieler", "", "<gray>Fliegen, Speed, X-Ray, Reach, NoFall.", "<yellow>➤ Klicken zum Öffnen")) : Ui.icon(Material.SPYGLASS, "<gray><bold>Cheat-Überwachung</bold>", List.of("<gray>Zurzeit wird niemand beobachtet.", "", "<gray>Einschalten im Spielermenü beim", "<gray>jeweiligen Spieler.", "<yellow>➤ Klicken zum Öffnen")), inventoryClickEvent -> new CheatMenu(this.plugin, this).open(this.viewer));
        this.fillEmpty();
    }
}

