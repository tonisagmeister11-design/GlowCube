/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield;

import de.adminfield.AdminFieldPlugin;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.bukkit.Location;

public final class ActivityLog {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final AdminFieldPlugin plugin;
    private final Deque<Entry> entries = new ArrayDeque<Entry>();

    public ActivityLog(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
    }

    public void add(Level level, String string, String string2, int n, int n2, int n3, UUID uUID) {
        this.entries.addFirst(new Entry(System.currentTimeMillis(), level, string, string2, n, n2, n3, uUID));
        int n4 = Math.max(20, this.plugin.getConfig().getInt("log.size", 200));
        while (this.entries.size() > n4) {
            this.entries.removeLast();
        }
    }

    public void add(Level level, String string, Location location, UUID uUID) {
        if (location == null || location.getWorld() == null) {
            this.add(level, string, null, 0, 0, 0, uUID);
        } else {
            this.add(level, string, location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ(), uUID);
        }
    }

    public void add(Level level, String string) {
        this.add(level, string, null, 0, 0, 0, null);
    }

    public List<Entry> recent(int n) {
        ArrayList<Entry> arrayList = new ArrayList<Entry>(n);
        for (Entry entry : this.entries) {
            if (arrayList.size() >= n) break;
            arrayList.add(entry);
        }
        return arrayList;
    }

    public int size() {
        return this.entries.size();
    }

    public void clear() {
        this.entries.clear();
    }

    public static String clock(long l) {
        return CLOCK.format(LocalTime.ofInstant(Instant.ofEpochMilli(l), ZoneId.systemDefault()));
    }

    public record Entry(long time, Level level, String text, String world, int x, int y, int z, UUID player) {
        public boolean hasLocation() {
            return this.world != null;
        }

        public String coordinates() {
            return this.x + " / " + this.y + " / " + this.z;
        }
    }

    public static enum Level {
        INFO("<gray>", "<gray>·</gray>"),
        WARN("<yellow>", "<yellow>▲</yellow>"),
        ALERT("<red>", "<red>■</red>");

        private final String color;
        private final String marker;

        private Level(String string2, String string3) {
            this.color = string2;
            this.marker = string3;
        }

        public String color() {
            return this.color;
        }

        public String marker() {
            return this.marker;
        }
    }
}

