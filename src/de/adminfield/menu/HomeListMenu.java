package de.adminfield.menu;

import com.glowcube.utils.Home;
import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.homes.Homes;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Die Homes eines Spielers ansehen und hinspringen. Fuer Admin und Owner gleichermassen.
 */
public final class HomeListMenu extends Menu {

    private static final int PER_PAGE = 45;

    private final Player target;

    public HomeListMenu(AdminFieldPlugin plugin, Menu parent, Player target) {
        super(plugin, parent);
        this.target = target;
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <green><bold>Homes</bold></green> <dark_gray>· " + this.target.getName());
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
        Homes homes = Homes.instance();
        List<Home> list = homes == null ? new ArrayList<>() : new ArrayList<>(homes.of(this.target.getUniqueId()));

        this.divider(5);
        this.backButton(45);
        this.pager(list.size(), PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.COMPASS, "<green>Homes von " + this.target.getName(),
                List.of("<gray>Gesetzt: <white>" + list.size(),
                        "",
                        "<gray>Ein Klick bringt dich hin.",
                        "<dark_gray>Deine Position wird vorher gemerkt.")));
        this.closeButton(53);

        if (homes == null) {
            this.set(22, Ui.icon(Material.BARRIER, "<red>Home-System nicht verfügbar",
                    List.of("<gray>Es ist beim Serverstart nicht hochgekommen.")));
            this.fillEmpty();
            return;
        }
        if (list.isEmpty()) {
            this.set(22, Ui.icon(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "<gray>Keine Homes",
                    List.of("<gray><white>" + this.target.getName() + "<gray> hat noch keines gesetzt.")));
            this.fillEmpty();
            return;
        }

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
            Home home = list.get(start + i);
            Location at = home.toLocation();
            List<String> lore = new ArrayList<>();
            lore.add("<gray>Welt: <white>" + home.worldName());
            if (at == null) {
                lore.add("<red>Diese Welt ist gerade nicht geladen.");
            } else {
                lore.add("<gray>Ort: <white>" + Ui.pos(at));
                lore.add("");
                lore.add("<yellow>➤ Klicken zum Hinspringen");
            }
            this.set(i, Ui.icon(at == null ? Material.GRAY_DYE : Material.RED_BED,
                    "<green><bold>" + home.name() + "</bold>", lore),
                    event -> this.jump(home));
        }
    }

    private void jump(Home home) {
        Location at = home.toLocation();
        if (at == null) {
            this.plugin.send((CommandSender) this.viewer,
                    "<red>Die Welt <white>" + home.worldName() + "<red> ist gerade nicht geladen.");
            return;
        }
        this.viewer.closeInventory();
        this.plugin.teleport(this.viewer, at, home.name() + " von " + this.target.getName());
    }
}
