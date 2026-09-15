package de.adminfield.offline;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/**
 * Wer war jemals auf diesem Server?
 *
 * <p>Die Antwort kommt aus den Spielerdaten des Servers, nicht aus unseren eigenen Dateien.
 * Das ist der Unterschied, auf den es ankommt: ein frisch hochgeladenes Plugin hat noch von
 * niemandem ein Abbild, der Server aber kennt jeden, der je hier war - und das schon seit
 * dessen erstem Besuch.
 */
public final class KnownPlayers {

    /** Ein bekannter Spieler mit dem Namen, unter dem er zuletzt hier war. */
    public record Known(UUID id, String name) {
    }

    private KnownPlayers() {
    }

    /**
     * Wann der Server diesen Spieler zuletzt gesehen hat, 0 wenn er es nicht sagt.
     *
     * <p>Diese Zeit kommt vom Server selbst und ist damit unabhaengig davon, ob wir von ihm
     * ein Abbild haben. Nur so laesst sich der Unterschied zeigen zwischen "war noch nie hier"
     * und "war gerade eben hier, wir haben nur nichts gesichert".
     */
    public static long lastSeen(UUID id) {
        OfflinePlayer known;
        try {
            known = Bukkit.getOfflinePlayer(id);
        } catch (Throwable ignored) {
            return 0L;
        }
        if (known == null) {
            return 0L;
        }
        try {
            long seen = known.getLastSeen();
            if (seen > 0L) {
                return seen;
            }
        } catch (Throwable ignored) {
            // Aeltere Server kennen das nicht.
        }
        try {
            return known.getLastPlayed();
        } catch (Throwable ignored) {
            return 0L;
        }
    }

    /** Alle Bekannten, nach Namen sortiert - aus allen Quellen zusammengetragen. */
    public static List<Known> all() {
        Map<UUID, Known> found = new LinkedHashMap<>();
        try {
            for (Player online : Bukkit.getOnlinePlayers()) {
                found.put(online.getUniqueId(), new Known(online.getUniqueId(), online.getName()));
            }
        } catch (Throwable ignored) {
        }
        OfflineStore store = OfflineStore.instance();
        if (store != null) {
            try {
                for (OfflineStore.Entry entry : store.all()) {
                    found.putIfAbsent(entry.id(), new Known(entry.id(), entry.name()));
                }
            } catch (Throwable ignored) {
            }
        }
        try {
            for (OfflinePlayer past : Bukkit.getOfflinePlayers()) {
                if (past == null || past.getName() == null) {
                    continue;
                }
                found.putIfAbsent(past.getUniqueId(), new Known(past.getUniqueId(), past.getName()));
            }
        } catch (Throwable ignored) {
            // Aeltere Server geben die Liste nicht her - dann bleibt der Weg ueber den Namen.
        }
        List<Known> out = new ArrayList<>(found.values());
        out.sort((left, right) -> left.name().compareToIgnoreCase(right.name()));
        return out;
    }
}
