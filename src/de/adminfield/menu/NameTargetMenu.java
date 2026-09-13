package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.disguise.NameDisguise;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Aussehen von jemandem uebernehmen, der gerade online ist.
 */
public final class NameTargetMenu extends Menu {

    private static final int PER_PAGE = 45;

    public NameTargetMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gold><bold>Als wen?</bold></gold>");
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
        if (!this.plugin.access().isOwner(this.viewer.getUniqueId())) {
            this.viewer.closeInventory();
            return;
        }
        NameDisguise names = NameDisguise.get(this.plugin);

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.removeIf(player -> player.getUniqueId().equals(this.viewer.getUniqueId()));
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        this.divider(5);
        this.backButton(45);
        this.pager(players.size(), PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.NAME_TAG, "<gold>Online-Spieler",
                List.of("<gray>Sonst online: <white>" + players.size(),
                        "",
                        "<gray>Der Skin kommt direkt vom Server,",
                        "<gray>ohne Umweg über Mojang.")));
        this.closeButton(53);

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < players.size(); i++) {
            Player target = players.get(start + i);
            this.set(i, Ui.head(target, "<white><bold>" + target.getName() + "</bold>",
                    List.of("<gray>Welt: <white>" + target.getWorld().getName(),
                            "",
                            "<gray>Danach siehst du für alle anderen",
                            "<gray>genauso aus wie er.",
                            "<yellow>➤ Klicken zum Übernehmen")),
                    event -> {
                        if (names.disguiseAs(this.viewer, target)) {
                            this.plugin.send((CommandSender) this.viewer,
                                    "<gray>Du siehst jetzt aus wie <white>" + target.getName() + "<gray>.");
                        } else {
                            this.plugin.send((CommandSender) this.viewer,
                                    "<red>Von <white>" + target.getName() + "<red> ließ sich kein Skin lesen.");
                        }
                        this.redraw();
                    });
        }
    }
}
