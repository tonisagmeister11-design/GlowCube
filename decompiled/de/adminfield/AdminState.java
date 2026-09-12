/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public final class AdminState {
    private final AdminFieldPlugin plugin;
    private final Set<UUID> vanished = new HashSet<UUID>();
    private final Set<UUID> god = new HashSet<UUID>();
    private final Set<UUID> frozen = new HashSet<UUID>();
    private final Set<UUID> watched = new HashSet<UUID>();
    private final Map<UUID, Deque<Location>> back = new HashMap<UUID, Deque<Location>>();
    private final Map<UUID, Prompt> prompts = new HashMap<UUID, Prompt>();

    public AdminState(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public boolean isVanished(Player player) {
        return this.vanished.contains(player.getUniqueId());
    }

    public void setVanished(Player player, boolean bl) {
        if (bl) {
            this.vanished.add(player.getUniqueId());
        } else {
            this.vanished.remove(player.getUniqueId());
        }
        for (Player player2 : Bukkit.getOnlinePlayers()) {
            if (player2.equals((Object)player)) continue;
            if (bl && !player2.hasPermission("adminfield.use")) {
                player2.hidePlayer((Plugin)this.plugin, player);
                continue;
            }
            player2.showPlayer((Plugin)this.plugin, player);
        }
    }

    public void applyVanishFor(Player player) {
        if (player.hasPermission("adminfield.use")) {
            return;
        }
        for (UUID uUID : this.vanished) {
            Player player2 = Bukkit.getPlayer((UUID)uUID);
            if (player2 == null) continue;
            player.hidePlayer((Plugin)this.plugin, player2);
        }
    }

    public boolean isGod(UUID uUID) {
        return this.god.contains(uUID);
    }

    public void setGod(UUID uUID, boolean bl) {
        if (bl) {
            this.god.add(uUID);
        } else {
            this.god.remove(uUID);
        }
    }

    public boolean isFrozen(UUID uUID) {
        return this.frozen.contains(uUID);
    }

    public void setFrozen(UUID uUID, boolean bl) {
        if (bl) {
            this.frozen.add(uUID);
        } else {
            this.frozen.remove(uUID);
        }
    }

    public Set<UUID> frozenPlayers() {
        return this.frozen;
    }

    public boolean isWatched(UUID uUID) {
        return this.watched.contains(uUID);
    }

    public void setWatched(UUID uUID, boolean bl) {
        if (bl) {
            this.watched.add(uUID);
        } else {
            this.watched.remove(uUID);
        }
    }

    public void pushBack(Player player) {
        this.back.computeIfAbsent(player.getUniqueId(), uUID -> new ArrayDeque()).addFirst(player.getLocation().clone());
        Deque<Location> deque = this.back.get(player.getUniqueId());
        while (deque.size() > 10) {
            deque.removeLast();
        }
    }

    public Location popBack(Player player) {
        Deque<Location> deque = this.back.get(player.getUniqueId());
        if (deque == null || deque.isEmpty()) {
            return null;
        }
        return deque.pollFirst();
    }

    public boolean hasBack(Player player) {
        Deque<Location> deque = this.back.get(player.getUniqueId());
        return deque != null && !deque.isEmpty();
    }

    public void prompt(Player player, String string, Consumer<String> consumer) {
        this.prompts.put(player.getUniqueId(), new Prompt(string, consumer));
        Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.closeInventory());
        this.plugin.send((CommandSender)player, "<gray>" + string);
        this.plugin.send((CommandSender)player, "<dark_gray>Schreibe die Antwort in den Chat – <white>abbrechen<dark_gray> bricht ab.");
    }

    public Prompt prompt(UUID uUID) {
        return this.prompts.get(uUID);
    }

    public Prompt takePrompt(UUID uUID) {
        return this.prompts.remove(uUID);
    }

    public void forget(UUID uUID) {
        this.vanished.remove(uUID);
        this.god.remove(uUID);
        this.frozen.remove(uUID);
        this.watched.remove(uUID);
        this.back.remove(uUID);
        this.prompts.remove(uUID);
    }

    public record Prompt(String question, Consumer<String> handler) {
    }
}

