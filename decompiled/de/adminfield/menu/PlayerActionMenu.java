/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.ActivityLog;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Biomes;
import de.adminfield.BuildSite;
import de.adminfield.CheatWatch;
import de.adminfield.Curses;
import de.adminfield.Jail;
import de.adminfield.Ui;
import de.adminfield.menu.AssassinMenu;
import de.adminfield.menu.CurseMenu;
import de.adminfield.menu.InspectMenu;
import de.adminfield.menu.Menu;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

public final class PlayerActionMenu
extends Menu {
    private final Player target;

    public PlayerActionMenu(AdminFieldPlugin adminFieldPlugin, Menu menu, Player player) {
        super(adminFieldPlugin, menu);
        this.target = player;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <white><bold>" + this.target.getName() + "</bold></white> <dark_gray>· Aktionen");
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
        if (!this.target.isOnline()) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Spieler ist offline", List.of("<gray>" + this.target.getName() + " hat den Server verlassen.")));
            this.backButton(45);
            this.closeButton(53);
            this.fillEmpty();
            return;
        }
        this.set(4, this.infoHead());
        this.set(19, Ui.icon(Material.ENDER_PEARL, "<aqua>Zu ihm teleportieren", List.of("<gray>Bringt dich direkt zu <white>" + this.target.getName() + "<gray>.", "<dark_gray>Deine Position wird gemerkt.")), inventoryClickEvent -> {
            this.viewer.closeInventory();
            this.plugin.teleport(this.viewer, this.target.getLocation(), this.target.getName());
        });
        this.set(20, Ui.icon(Material.LEAD, "<aqua>Zu mir holen", List.of("<gray>Teleportiert <white>" + this.target.getName() + "<gray> zu dir.")), inventoryClickEvent -> {
            this.target.teleportAsync(this.viewer.getLocation());
            this.plugin.send((CommandSender)this.target, "<gray>Du wurdest von <white>" + this.viewer.getName() + "<gray> hergeholt.");
            this.plugin.send((CommandSender)this.viewer, "<gray><white>" + this.target.getName() + "<gray> ist jetzt bei dir.");
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " holte " + this.target.getName() + " zu sich", this.viewer.getLocation(), this.target.getUniqueId());
        });
        this.set(21, Ui.icon(Material.GOLDEN_APPLE, "<green>Vollständig heilen", List.of("<gray>Setzt Leben und Hunger auf voll,", "<gray>löscht alle Effekte, Feuer und Frost.", "", "<yellow>➤ Klicken")), inventoryClickEvent -> {
            PlayerActionMenu.fullyHeal(this.target);
            this.plugin.send((CommandSender)this.viewer, "<green>" + this.target.getName() + " wurde vollständig geheilt.");
            this.plugin.send((CommandSender)this.target, "<green>Du wurdest von <white>" + this.viewer.getName() + "<green> geheilt.");
            this.target.playSound(this.target.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.0f, 1.6f);
            this.plugin.log().add(ActivityLog.Level.INFO, this.viewer.getName() + " heilte " + this.target.getName(), this.target.getLocation(), this.target.getUniqueId());
        });
        this.set(22, Ui.icon(Material.COOKED_BEEF, "<gold>Hunger auffüllen", List.of("<gray>Setzt Hunger und Sättigung auf voll,", "<gray>ohne das Leben anzufassen.", "", "<gray>Aktuell: <white>" + this.target.getFoodLevel() + "<dark_gray>/<white>20", "<yellow>➤ Klicken")), inventoryClickEvent -> {
            this.target.setFoodLevel(20);
            this.target.setSaturation(20.0f);
            this.target.setExhaustion(0.0f);
            this.plugin.send((CommandSender)this.viewer, "<gold>Hunger von " + this.target.getName() + " aufgefüllt.");
            this.plugin.send((CommandSender)this.target, "<gold>Dein Hunger wurde aufgefüllt.");
        });
        this.set(23, Ui.icon(Material.NETHERITE_SWORD, "<red>Töten", List.of("<gray>Tötet <white>" + this.target.getName() + "<gray> sofort.", "<dark_gray>Godmode wird dafür kurz aufgehoben.", "", "<red>➤ Klicken zum Töten")), inventoryClickEvent -> {
            this.plugin.state().setGod(this.target.getUniqueId(), false);
            this.target.setHealth(0.0);
            this.plugin.send((CommandSender)this.viewer, "<red>" + this.target.getName() + " wurde getötet.");
            this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName() + " tötete " + this.target.getName(), this.target.getLocation(), this.target.getUniqueId());
        });
        this.set(24, Ui.icon(Material.BARRIER, "<red>Kicken", List.of("<gray>Wirft den Spieler mit Begründung", "<gray>vom Server.", "", "<yellow>➤ Klicken, dann Grund in den Chat")), inventoryClickEvent -> this.plugin.state().prompt(this.viewer, "Grund für den Kick von " + this.target.getName() + "?", string -> {
            if (!this.target.isOnline()) {
                this.plugin.send((CommandSender)this.viewer, "<red>Der Spieler ist nicht mehr online.");
                return;
            }
            this.target.kick(Ui.mm("<red><bold>Vom Server geworfen</bold></red>\n\n<gray>" + string + "\n\n<dark_gray>von " + this.viewer.getName()));
            this.plugin.send((CommandSender)this.viewer, "<red>" + this.target.getName() + " wurde gekickt.");
            this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName() + " kickte " + this.target.getName() + ": " + string, null, this.target.getUniqueId());
        }));
        boolean bl = this.plugin.state().isFrozen(this.target.getUniqueId());
        this.set(25, Ui.toggle(bl, "<aqua>Einfrieren", List.of("<gray>Hält den Spieler an Ort und Stelle fest.", "<gray>Er kann sich nicht mehr bewegen.")), inventoryClickEvent -> {
            this.plugin.state().setFrozen(this.target.getUniqueId(), !bl);
            if (!bl) {
                this.plugin.send((CommandSender)this.target, "<aqua>Du wurdest von einem Admin eingefroren.");
                this.plugin.send((CommandSender)this.viewer, "<aqua>" + this.target.getName() + " ist jetzt eingefroren.");
            } else {
                this.plugin.send((CommandSender)this.target, "<green>Du kannst dich wieder bewegen.");
                this.plugin.send((CommandSender)this.viewer, "<green>" + this.target.getName() + " wurde aufgetaut.");
            }
            this.redraw();
        });
        this.set(28, Ui.icon(Material.CHEST, "<gold>Inventar", List.of("<gray>Zeigt das komplette Inventar", "<gray>inklusive Rüstung und zweiter Hand.", "", "<gray>Dort lässt sich <white>Bearbeiten<gray> einschalten:", "<gray>dann kannst du Items herausnehmen", "<gray>und hineinlegen – ohne Meldung an ihn.")), inventoryClickEvent -> new InspectMenu(this.plugin, this, this.target, false).open(this.viewer));
        this.set(29, Ui.icon(Material.ENDER_CHEST, "<dark_purple>Enderkiste", List.of("<gray>Zeigt den Inhalt der Enderkiste.", "", "<gray>Auch hier lässt sich <white>Bearbeiten", "<gray>einschalten.")), inventoryClickEvent -> new InspectMenu(this.plugin, this, this.target, true).open(this.viewer));
        GameMode gameMode = PlayerActionMenu.nextMode(this.target.getGameMode());
        this.set(30, Ui.icon(Material.GRASS_BLOCK, "<green>Spielmodus wechseln", List.of("<gray>Aktuell: <white>" + Ui.gameMode(this.target.getGameMode()), "<gray>Danach: <white>" + Ui.gameMode(gameMode), "", "<yellow>➤ Klicken zum Umschalten")), inventoryClickEvent -> {
            this.target.setGameMode(gameMode);
            this.plugin.send((CommandSender)this.viewer, "<gray>Spielmodus von <white>" + this.target.getName() + "<gray> ist jetzt <white>" + Ui.gameMode(gameMode) + "<gray>.");
            this.redraw();
        });
        BuildSite buildSite = this.plugin.tracker().lastSiteOf(this.target.getUniqueId());
        ArrayList<String> arrayList = new ArrayList<String>();
        if (buildSite == null) {
            arrayList.add("<gray>Von diesem Spieler ist noch");
            arrayList.add("<gray>keine Baustelle bekannt.");
        } else {
            arrayList.add("<gray>Letzte Baustelle:");
            arrayList.add(buildSite.kind().color() + buildSite.kind().label());
            arrayList.add("<gray>" + buildSite.total() + " Blöcke bei <white>" + buildSite.centerX() + " / " + buildSite.centerY() + " / " + buildSite.centerZ());
            arrayList.add("<dark_gray>" + Ui.ago(System.currentTimeMillis() - buildSite.lastSeen()));
            arrayList.add("");
            arrayList.add("<yellow>➤ Klicken zum Teleportieren");
        }
        this.set(31, Ui.icon(Material.BRICKS, "<gold>Letzte Baustelle", arrayList), inventoryClickEvent -> {
            if (buildSite == null) {
                this.plugin.send((CommandSender)this.viewer, "<red>Von diesem Spieler ist noch keine Baustelle bekannt.");
                return;
            }
            this.viewer.closeInventory();
            this.plugin.teleport(this.viewer, buildSite.safeSpot(), buildSite.kind().label() + " von " + this.target.getName());
        });
        boolean bl2 = this.plugin.state().isWatched(this.target.getUniqueId());
        this.set(32, Ui.toggle(bl2, "<yellow>Beobachten", List.of("<gray>Schreibt die Befehle und Weltwechsel", "<gray>dieses Spielers in den Verlauf.", "<dark_gray>Tode und Alarme landen dort ohnehin.")), inventoryClickEvent -> {
            this.plugin.state().setWatched(this.target.getUniqueId(), !bl2);
            this.plugin.send((CommandSender)this.viewer, bl2 ? "<gray>" + this.target.getName() + " wird nicht mehr beobachtet." : "<yellow>" + this.target.getName() + " wird jetzt beobachtet.");
            this.redraw();
        });
        this.set(33, Ui.icon(Material.PAPER, "<aqua>Nachricht schicken", List.of("<gray>Private Nachricht an <white>" + this.target.getName() + "<gray>.", "", "<yellow>➤ Klicken, dann Text in den Chat")), inventoryClickEvent -> this.plugin.state().prompt(this.viewer, "Was soll " + this.target.getName() + " lesen?", string -> {
            if (!this.target.isOnline()) {
                this.plugin.send((CommandSender)this.viewer, "<red>Der Spieler ist nicht mehr online.");
                return;
            }
            this.target.sendMessage(Ui.mm("<gold><bold>Admin</bold></gold> <dark_gray>»</dark_gray> <white>" + string));
            this.target.playSound(this.target.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
            this.plugin.send((CommandSender)this.viewer, "<gray>Nachricht an <white>" + this.target.getName() + "<gray> geschickt.");
        }));
        this.set(34, Ui.icon(Material.CAULDRON, "<red>Inventar leeren", List.of("<gray>Löscht das komplette Inventar", "<gray>samt Rüstung. Nicht umkehrbar.", "", "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            this.target.getInventory().clear();
            this.target.getInventory().setArmorContents(null);
            this.plugin.send((CommandSender)this.viewer, "<red>Inventar von " + this.target.getName() + " geleert.");
            this.plugin.send((CommandSender)this.target, "<red>Dein Inventar wurde von einem Admin geleert.");
            this.plugin.log().add(ActivityLog.Level.WARN, this.viewer.getName() + " leerte das Inventar von " + this.target.getName(), this.target.getLocation(), this.target.getUniqueId());
        });
        boolean bl3 = this.plugin.state().isGod(this.target.getUniqueId());
        this.set(36, Ui.toggle(bl3, "<gold>Unverwundbar", List.of("<gray>Der Spieler nimmt keinen Schaden mehr.")), inventoryClickEvent -> {
            this.plugin.state().setGod(this.target.getUniqueId(), !bl3);
            this.plugin.send((CommandSender)this.viewer, "<gray>Unverwundbarkeit für <white>" + this.target.getName() + "<gray>: " + (bl3 ? "<red>aus" : "<green>an"));
            this.redraw();
        });
        this.set(38, Ui.icon(Material.MILK_BUCKET, "<white>Effekte entfernen", List.of("<gray>Entfernt alle Tränke-Effekte.", "<gray>Aktiv: <white>" + this.target.getActivePotionEffects().size())), inventoryClickEvent -> {
            for (PotionEffect potionEffect : new ArrayList(this.target.getActivePotionEffects())) {
                this.target.removePotionEffect(potionEffect.getType());
            }
            this.plugin.send((CommandSender)this.viewer, "<gray>Effekte von " + this.target.getName() + " entfernt.");
            this.redraw();
        });
        this.set(40, Ui.icon(Material.COMPASS, "<aqua>Zum Spawn schicken", List.of("<gray>Teleportiert den Spieler zum", "<gray>Spawnpunkt seiner Welt.")), inventoryClickEvent -> {
            Location location = this.target.getWorld().getSpawnLocation();
            this.target.teleportAsync(location);
            this.plugin.send((CommandSender)this.viewer, "<gray>" + this.target.getName() + " wurde zum Spawn geschickt.");
            this.plugin.send((CommandSender)this.target, "<gray>Du wurdest zum Spawn teleportiert.");
        });
        this.jailButton(42);
        this.cheatButton(44);
        this.divider(5);
        this.backButton(45);
        this.set(49, this.statsIcon());
        if (this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            Curses.Hex hex = this.plugin.curses().hex(this.target.getUniqueId());
            this.set(51, hex == null ? Ui.icon(Material.ENDER_EYE, "<dark_purple><bold>Fluch auflegen</bold>", List.of("<gray>Belegt ihn mit einem Fluch:", "<gray>Creeper-Zischen beim Springen, vertauschte", "<gray>Beute, Schwindel, Grusel-Kisten,", "<gray>starrende Kühe, wechselnde Effekte.", "", "<dark_gray>Er bekommt keine Meldung.", "<yellow>➤ Klicken zum Auswählen")) : Ui.glowing(Material.ENDER_EYE, "<dark_purple><bold>Verflucht</bold>", List.of("<dark_purple>" + hex.curse().label(), "<gray>" + hex.curse().description(), "", "<gray>Seit <white>" + Ui.ago(System.currentTimeMillis() - hex.castAt()), "<yellow>➤ Klicken zum Ändern oder Aufheben")), inventoryClickEvent -> new CurseMenu(this.plugin, this, this.target).open(this.viewer));
            boolean bl4 = this.plugin.assassin().isHunted(this.target.getUniqueId());
            this.set(47, bl4 ? Ui.glowing(Material.NETHERITE_SWORD, "<red><bold>Attentäter unterwegs</bold>", List.of("<gray>Unterwegs: <white>" + this.plugin.assassin().hunterName(this.target.getUniqueId()), "", "<yellow>➤ Klicken zum Verwalten")) : Ui.icon(Material.NETHERITE_SWORD, "<red><bold>Attentäter schicken</bold>", List.of("<gray>Ein gerüsteter Jäger erscheint in", "<gray>seiner Nähe und verfolgt ihn ohne", "<gray>Pause, bis er ihn erwischt hat.", "", "<gray>Im Chat steht dann eine normale", "<gray>Todesmeldung mit dem Kampfnamen.", "", "<red>➤ Klicken – Name und Stärke wählen")), inventoryClickEvent -> new AssassinMenu(this.plugin, this, this.target).open(this.viewer));
        }
        this.closeButton(53);
        this.fillEmpty();
    }

    private void cheatButton(int n) {
        CheatWatch cheatWatch = this.plugin.cheats();
        CheatWatch.Report report = cheatWatch.report(this.target.getUniqueId());
        if (report == null) {
            this.set(n, Ui.icon(Material.SPYGLASS, "<red><bold>Cheat beobachten</bold>", List.of("<gray>Prüft diesen Spieler laufend auf:", "<aqua>· Fliegen <dark_gray>– hängt er in der Luft?", "<yellow>· Speed <dark_gray>– zu schnell ohne Effekte?", "<light_purple>· X-Ray <dark_gray>– zu viele Erze, zu wenig Umweg,", "<dark_gray>    gräbt er schnurgerade darauf zu?", "<red>· Reach <dark_gray>– trifft er aus zu weit weg?", "<green>· NoFall <dark_gray>– Sturz ohne Schaden?", "", "<gray>Bei jedem Verdacht bekommst du", "<gray>eine Meldung mit [TP] im Chat.", "", "<yellow>➤ Klicken zum Einschalten")), inventoryClickEvent -> {
                cheatWatch.start(this.viewer, this.target);
                this.plugin.send((CommandSender)this.viewer, "<red>" + this.target.getName() + "<gray> wird jetzt auf Cheats überwacht.");
                this.redraw();
            });
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Läuft seit <white>" + Ui.ago(System.currentTimeMillis() - report.since()));
        arrayList.add("");
        int n2 = report.totalHits();
        if (n2 == 0) {
            arrayList.add("<green>Bisher nichts Auffälliges.");
        } else {
            arrayList.add("<red><bold>" + n2 + " Auffälligkeiten</bold>");
            for (CheatWatch.Kind kind : CheatWatch.Kind.values()) {
                int n3 = report.hits(kind);
                if (n3 == 0) continue;
                arrayList.add(kind.color() + kind.label() + "<dark_gray>: <white>" + n3 + "x <dark_gray>· " + this.plugin.cheats().confidence(report, kind));
                String string = report.detail(kind);
                if (string == null) continue;
                arrayList.add("<dark_gray>  " + string);
            }
        }
        arrayList.add("");
        arrayList.add("<gray>Abgebaut: <white>" + report.minedTotal() + " Blöcke");
        arrayList.add("<gray>Versteckte Adern: <white>" + report.minedValuable() + "  <dark_gray>·  <gray>sichtbar gefunden: <white>" + report.exposedFinds());
        arrayList.add("");
        arrayList.add("<yellow>➤ Klicken zum Ausschalten");
        this.set(n, Ui.glowing(Material.SPYGLASS, "<red><bold>Cheat-Überwachung läuft</bold>", arrayList), inventoryClickEvent -> {
            cheatWatch.stop(this.target.getUniqueId());
            this.plugin.send((CommandSender)this.viewer, "<gray>Überwachung von " + this.target.getName() + " beendet.");
            this.redraw();
        });
    }

    private void jailButton(int n) {
        Jail jail = this.plugin.jail();
        Jail.Inmate inmate = jail.inmate(this.target.getUniqueId());
        if (inmate != null) {
            ArrayList<String> arrayList = new ArrayList<String>();
            arrayList.add("<red>Dieser Spieler sitzt im Gefängnis.");
            arrayList.add("");
            arrayList.add("<gray>Sitzt seit: <white>" + Ui.ago(System.currentTimeMillis() - inmate.jailedAt()));
            arrayList.add("<gray>Eingesperrt von: <white>" + inmate.jailedBy());
            Location location = inmate.returnLocation();
            if (location != null && location.getWorld() != null) {
                arrayList.add("");
                arrayList.add("<gray>Kommt zurück nach:");
                arrayList.add("<white>" + Ui.pos(location) + " <dark_gray>(" + location.getWorld().getName() + ")");
            }
            arrayList.add("");
            arrayList.add("<green>➤ Klicken zum Freilassen");
            this.set(n, Ui.glowing(Material.IRON_DOOR, "<green><bold>Aus dem Gefängnis holen</bold>", arrayList), inventoryClickEvent -> {
                jail.release(this.viewer.getName(), this.target.getUniqueId());
                this.plugin.send((CommandSender)this.viewer, "<green>" + this.target.getName() + " wurde freigelassen und zurückgebracht.");
                this.redraw();
            });
            return;
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        if (jail.isSet()) {
            Location location = jail.location();
            arrayList.add("<gray>Bringt <white>" + this.target.getName() + "<gray> ins Gefängnis");
            arrayList.add("<gray>und setzt ihn auf <white>" + Ui.gameMode(this.jailGameMode()) + "<gray>.");
            arrayList.add("");
            arrayList.add("<gray>Gefängnis: <white>" + Ui.pos(location) + " <dark_gray>(" + location.getWorld().getName() + ")");
            arrayList.add("<dark_gray>Seine jetzige Position wird gemerkt,");
            arrayList.add("<dark_gray>damit er später dorthin zurückkommt.");
            arrayList.add("");
            arrayList.add("<red>➤ Klicken zum Einsperren");
        } else {
            arrayList.add("<red>Es ist noch kein Gefängnis festgelegt.");
            arrayList.add("");
            arrayList.add("<gray>Beim Klick schließt sich das Menü und");
            arrayList.add("<gray>du klickst im Spiel auf den Boden –");
            arrayList.add("<gray>dieser Punkt wird zum Gefängnis.");
            arrayList.add("<gray>Danach wandert <white>" + this.target.getName() + "<gray> direkt hinein.");
            arrayList.add("");
            arrayList.add("<yellow>➤ Klicken zum Festlegen");
        }
        this.set(n, Ui.icon(Material.IRON_BARS, "<red><bold>Ins Gefängnis schicken</bold>", arrayList), inventoryClickEvent -> {
            if (!jail.isSet()) {
                jail.beginSetup(this.viewer, this.target.getUniqueId());
                return;
            }
            jail.jail(this.viewer, this.target);
            this.redraw();
        });
    }

    private GameMode jailGameMode() {
        try {
            return GameMode.valueOf((String)this.plugin.getConfig().getString("jail.gamemode", "SURVIVAL").toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException illegalArgumentException) {
            return GameMode.SURVIVAL;
        }
    }

    private ItemStack infoHead() {
        Location location = this.target.getLocation();
        double d = 20.0;
        AttributeInstance attributeInstance = this.target.getAttribute(Attribute.MAX_HEALTH);
        if (attributeInstance != null) {
            d = attributeInstance.getValue();
        }
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add("<gray>Welt: <white>" + location.getWorld().getName());
        arrayList.add("<gray>Biom: <white>" + Biomes.name(location.getBlock().getBiome()));
        arrayList.add("<gray>Position: <white>" + Ui.pos(location));
        arrayList.add("<gray>Blickrichtung: <white>" + PlayerActionMenu.facing(location.getYaw()));
        arrayList.add("");
        arrayList.add("<gray>Leben  " + Ui.bar(this.target.getHealth(), d, 10, "red", "dark_gray") + " <white>" + String.format("%.1f", this.target.getHealth()));
        arrayList.add("<gray>Hunger " + Ui.bar(this.target.getFoodLevel(), 20.0, 10, "gold", "dark_gray") + " <white>" + this.target.getFoodLevel());
        arrayList.add("<gray>Sauerstoff: <white>" + this.target.getRemainingAir() / 20 + "s");
        arrayList.add("<gray>Modus: <white>" + Ui.gameMode(this.target.getGameMode()) + "  <dark_gray>·  <gray>Ping: " + Ui.gradeLow(this.target.getPing(), 60.0, 140.0) + this.target.getPing() + " ms");
        return Ui.head((OfflinePlayer)this.target, "<white><bold>" + this.target.getName() + "</bold>", arrayList);
    }

    private ItemStack statsIcon() {
        int n = this.plugin.tracker().blocksBy(this.target.getUniqueId());
        int n2 = this.plugin.tracker().sitesOf(this.target.getUniqueId()).size();
        return Ui.icon(Material.BOOK, "<yellow>Bau-Statistik", List.of("<gray>Bewegte Blöcke: <white>" + n, "<gray>Beteiligt an Baustellen: <white>" + n2, "<gray>Level: <white>" + this.target.getLevel(), "", "<dark_gray>Seit dem letzten Serverstart"));
    }

    private static String facing(float f) {
        float f2 = (f % 360.0f + 360.0f) % 360.0f;
        if (f2 < 45.0f || f2 >= 315.0f) {
            return "Süden";
        }
        if (f2 < 135.0f) {
            return "Westen";
        }
        if (f2 < 225.0f) {
            return "Norden";
        }
        return "Osten";
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

    public static void fullyHeal(Player player) {
        AttributeInstance attributeInstance = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(attributeInstance != null ? attributeInstance.getValue() : 20.0);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setExhaustion(0.0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setRemainingAir(player.getMaximumAir());
        for (PotionEffect potionEffect : new ArrayList(player.getActivePotionEffects())) {
            player.removePotionEffect(potionEffect.getType());
        }
    }
}

