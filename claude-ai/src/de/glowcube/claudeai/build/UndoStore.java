package de.glowcube.claudeai.build;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;

/** Merkt sich, was vor den letzten Bauten an ihrer Stelle war - fuer "mach das rueckgaengig". */
public final class UndoStore {

    public record Change(Block block, BlockData before) {}

    public record Entry(String name, List<Change> changes) {}

    private final Deque<Entry> entries = new ArrayDeque<>();

    public void push(String name, List<Change> changes) {
        if (changes.isEmpty()) return;
        entries.push(new Entry(name, new ArrayList<>(changes)));
        while (entries.size() > 5) entries.removeLast();
    }

    public Entry pop() {
        return entries.poll();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }
}
