package de.adminfield.menu;

import de.adminfield.AdminFieldPlugin;
import de.adminfield.Ui;
import de.adminfield.disguise.MobDisguise;
import de.adminfield.disguise.MobKind;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * Wen soll es treffen? Spielerauswahl fuer die Mob-Verkleidung.
 */
public final class MobTargetMenu extends Menu {

    private static final int PER_PAGE = 45;

    public MobTargetMenu(AdminFieldPlugin plugin, Menu parent) {
        super(plugin, parent);
    }

    @Override
    protected Component title() {
        return Ui.mm("<dark_gray>▏ <gold><bold>Wen verkleiden?</bold></gold>");
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
        MobDisguise disguise = MobDisguise.get(this.plugin);

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));

        this.divider(5);
        this.backButton(45);
        this.pager(players.size(), PER_PAGE, 48, 50);
        this.set(49, Ui.icon(Material.NAME_TAG, "<gold>Spieler auswählen",
                List.of("<gray>Online: <white>" + players.size(),
                        "",
                        "<gray>Der Ausgewählte erfährt nichts davon.")));
        this.closeButton(53);

        int start = this.page * PER_PAGE;
        for (int i = 0; i < PER_PAGE && start + i < players.size(); i++) {
            Player target = players.get(start + i);
            MobKind current = disguise.kindOf(target.getUniqueId());
            List<String> lore = List.of(
                    "<gray>Welt: <white>" + target.getWorld().getName(),
                    current == null ? "<gray>Verkleidung: <white>keine"
                                    : "<gray>Verkleidung: <white>" + current.label(),
                    "",
                    "<yellow>➤ Klicken zum Aussuchen des Mobs");
            this.set(i, Ui.head(target, "<white><bold>" + target.getName() + "</bold>", lore),
                    event -> new MobPickMenu(this.plugin, this, target).open(this.viewer));
        }
    }
}
