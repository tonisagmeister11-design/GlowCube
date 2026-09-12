/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.BuildSite;
import de.adminfield.Ui;
import de.adminfield.menu.Menu;
import java.lang.invoke.CallSite;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;

public final class BuildMenu
extends Menu {
    private static final int PER_PAGE = 45;
    private boolean showAll;

    public BuildMenu(AdminFieldPlugin adminFieldPlugin, Menu menu) {
        super(adminFieldPlugin, menu);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gold><bold>Bauwerke</bold></gold> <dark_gray>· erkannt vom Server");
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
        List<BuildSite> list = this.showAll ? this.plugin.tracker().all() : this.plugin.tracker().confirmed();
        long l = this.plugin.getConfig().getLong("detection.idle-minutes", 15L) * 60000L;
        this.divider(5);
        this.backButton(45);
        this.set(46, Ui.toggle(this.showAll, "<yellow>Auch kleine Stellen zeigen", List.of("<gray>Standardmäßig siehst du nur Orte,", "<gray>an denen die Schwelle von <white>" + this.plugin.getConfig().getInt("detection.block-threshold", 30) + " Blöcken", "<gray>überschritten wurde.")), inventoryClickEvent -> {
            this.showAll = !this.showAll;
            this.page = 0;
            this.redraw();
        });
        this.pager(list.size(), 45, 48, 50);
        this.set(49, Ui.icon(Material.SPYGLASS, "<gold>Bauerkennung", List.of("<gray>Angezeigt: <white>" + list.size(), "<gray>Insgesamt gemerkt: <white>" + this.plugin.tracker().count(), "", "<gray>Ab <white>" + this.plugin.getConfig().getInt("detection.block-threshold", 30) + " Blöcken<gray> im Umkreis von", "<white>" + this.plugin.getConfig().getInt("detection.merge-radius", 24) + " Blöcken<gray> gilt eine Stelle", "<gray>als Bauwerk und wird gemeldet.")));
        this.set(52, Ui.icon(Material.LAVA_BUCKET, "<red>Liste leeren", List.of("<gray>Vergisst alle gemerkten Baustellen.", "<gray>Die Bauwerke selbst bleiben natürlich stehen.", "", "<red>➤ Klicken - passiert sofort")), inventoryClickEvent -> {
            this.plugin.tracker().clear();
            this.plugin.send((CommandSender)this.viewer, "<gray>Alle gemerkten Baustellen wurden vergessen.");
            this.redraw();
        });
        this.closeButton(53);
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.STRUCTURE_VOID, "<gray>Noch nichts erkannt", List.of("<gray>Sobald jemand irgendwo mehr als", "<white>" + this.plugin.getConfig().getInt("detection.block-threshold", 30) + " Blöcke<gray> setzt, taucht das", "<gray>Bauwerk hier automatisch auf.")));
            this.fillEmpty();
            return;
        }
        int n = this.page * 45;
        for (int i = 0; i < 45 && n + i < list.size(); ++i) {
            BuildSite buildSite = list.get(n + i);
            this.set(i, this.iconFor(buildSite, l), inventoryClickEvent -> {
                if (inventoryClickEvent.isRightClick()) {
                    this.plugin.tracker().forget(buildSite);
                    this.plugin.send((CommandSender)this.viewer, "<gray>Baustelle vergessen.");
                    this.redraw();
                    return;
                }
                this.viewer.closeInventory();
                this.plugin.teleport(this.viewer, buildSite.safeSpot(), buildSite.kind().label() + " von " + buildSite.primaryBuilderName());
            });
        }
        this.fillEmpty();
    }

    private ItemStack iconFor(BuildSite buildSite, long l) {
        Object object;
        List<Object> list;
        long l2 = System.currentTimeMillis();
        boolean bl = !buildSite.idle(l);
        ArrayList<String> arrayList = new ArrayList<String>();
        arrayList.add(buildSite.kind().color() + "<bold>" + buildSite.kind().label() + "</bold>");
        arrayList.add("");
        arrayList.add("<gray>Hauptbauer: <white>" + buildSite.primaryBuilderName());
        List<Map.Entry<UUID, Integer>> list2 = buildSite.topContributors(4);
        if (list2.size() > 1) {
            list = new ArrayList<CallSite>();
            for (int i = 1; i < list2.size(); ++i) {
                object = list2.get(i);
                list.add(buildSite.nameOf(object.getKey()) + " <dark_gray>(" + String.valueOf(object.getValue()) + ")<gray>");
            }
            arrayList.add("<gray>Außerdem: <white>" + String.join((CharSequence)"<gray>, <white>", list));
        }
        arrayList.add("");
        arrayList.add("<gray>Gesetzt: <white>" + buildSite.placed() + "  <dark_gray>·  <gray>Abgebaut: <white>" + buildSite.broken());
        arrayList.add("<gray>Ausmaße: <white>" + buildSite.width() + " × " + buildSite.height() + " × " + buildSite.depth() + " <dark_gray>(B×H×T)");
        arrayList.add("");
        arrayList.add("<gray>Welt: <white>" + buildSite.worldName());
        arrayList.add("<gray>Mittelpunkt: <white>" + buildSite.centerX() + " / " + buildSite.centerY() + " / " + buildSite.centerZ());
        list = buildSite.topMaterials(3);
        if (!list.isEmpty()) {
            ArrayList<CallSite> arrayList2 = new ArrayList<CallSite>();
            for (Map.Entry entry : list) {
                arrayList2.add((CallSite)((Object)("<lang:'" + ((Material)entry.getKey()).translationKey() + "'> <dark_gray>×" + String.valueOf(entry.getValue()) + "<gray>")));
            }
            arrayList.add("");
            arrayList.add("<gray>Hauptsächlich: <white>" + String.join((CharSequence)"<gray>, <white>", arrayList2));
        }
        arrayList.add("");
        arrayList.add("<gray>Begonnen: <white>" + Ui.ago(l2 - buildSite.firstSeen()));
        arrayList.add("<gray>Zuletzt: <white>" + Ui.ago(l2 - buildSite.lastSeen()));
        arrayList.add(bl ? "<green>▪ Wird gerade gebaut" : "<dark_gray>▪ Ruht seit einer Weile");
        arrayList.add("");
        arrayList.add("<yellow>➤ Linksklick: Teleportieren");
        arrayList.add("<dark_gray>Rechtsklick: aus der Liste werfen");
        String string = buildSite.kind().color() + "<bold>" + buildSite.kind().label() + "</bold> <dark_gray>· <white>" + buildSite.primaryBuilderName();
        object = bl ? Ui.glowing(buildSite.iconMaterial(), string, arrayList) : Ui.icon(buildSite.iconMaterial(), string, arrayList);
        object.setAmount(Math.max(1, Math.min(64, buildSite.total() / 10)));
        return object;
    }

    public static int activeCount(AdminFieldPlugin adminFieldPlugin) {
        long l = adminFieldPlugin.getConfig().getLong("detection.idle-minutes", 15L) * 60000L;
        int n = 0;
        for (BuildSite buildSite : adminFieldPlugin.tracker().confirmed()) {
            if (buildSite.idle(l)) continue;
            ++n;
        }
        return n;
    }

    public static boolean worldLoaded(BuildSite buildSite) {
        return Bukkit.getWorld((String)buildSite.worldName()) != null;
    }
}

