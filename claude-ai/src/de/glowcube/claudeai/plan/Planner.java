package de.glowcube.claudeai.plan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.task.CombatTask;
import de.glowcube.claudeai.task.CraftTask;
import de.glowcube.claudeai.task.GatherTask;
import de.glowcube.claudeai.task.HarvestTask;
import de.glowcube.claudeai.task.SmeltTask;
import de.glowcube.claudeai.task.Task;
import de.glowcube.claudeai.world.Blocks;
import de.glowcube.claudeai.world.Mats;

/**
 * Plant, wie Claude an ein Item kommt: aus dem Inventar, durch Craften, Schmelzen,
 * Abbauen, Holzfaellen, Jagen oder Ernten - rekursiv, mit allem was dazwischen noetig ist
 * (Werkbank, Ofen, Brennstoff, die richtige Spitzhacke).
 *
 * <p>Dafuer fuehrt der Planer ein gedachtes Inventar mit, damit Zwischenprodukte
 * mitgezaehlt werden.
 */
public final class Planner {

    public record Result(List<Task> tasks, List<String> notes, String problem) {
        public boolean ok() {
            return problem == null;
        }
    }

    private final Npc npc;
    private final Map<Material, Integer> inv = new HashMap<>();
    private final List<Task> tasks = new ArrayList<>();
    private final List<String> notes = new ArrayList<>();
    private String problem;
    private boolean table;
    private boolean furnace;
    private int depth;

    private Planner(Npc npc) {
        this.npc = npc;
        for (ItemStack s : npc.getInventory().getContents()) {
            if (s != null) inv.merge(s.getType(), s.getAmount(), Integer::sum);
        }
        table = has(Mats.get("CRAFTING_TABLE")) || near(npc, "CRAFTING_TABLE");
        furnace = has(Mats.get("FURNACE")) || near(npc, "FURNACE");
    }

    /** Plan, um "count" Stueck von "item" im Inventar zu haben. */
    public static Result obtain(Npc npc, Material item, int count) {
        Planner p = new Planner(npc);
        boolean ok = p.need(item, count);
        if (!ok && p.problem == null) p.problem = "Ich weiss nicht, wie ich an " + Lexicon.name(item) + " komme.";
        if (p.tasks.size() > 40) p.problem = "Das sind mir zu viele Schritte auf einmal - lass uns das aufteilen.";
        return new Result(p.tasks, p.notes, p.problem);
    }

    /** Plan fuer "irgendeinen Stamm" (Holz sammeln ohne bestimmte Sorte). */
    public static Result logs(Npc npc, int count) {
        Planner p = new Planner(npc);
        p.gatherLogs(count);
        return new Result(p.tasks, p.notes, null);
    }

    // ================================================================== gedachtes Inventar

    private boolean has(Material m) {
        return m != null && inv.getOrDefault(m, 0) > 0;
    }

    private int count(Predicate<Material> what) {
        int n = 0;
        for (Map.Entry<Material, Integer> e : inv.entrySet()) if (what.test(e.getKey())) n += e.getValue();
        return n;
    }

    private void add(Material m, int n) {
        inv.merge(m, n, Integer::sum);
    }

    private void consume(Predicate<Material> what, int n) {
        for (Map.Entry<Material, Integer> e : inv.entrySet()) {
            if (n <= 0) break;
            if (!what.test(e.getKey())) continue;
            int use = Math.min(n, e.getValue());
            e.setValue(e.getValue() - use);
            n -= use;
        }
        inv.values().removeIf(v -> v <= 0);
    }

    private static boolean near(Npc npc, String name) {
        Material type = Mats.get(name);
        Location me = npc.location();
        if (type == null || me == null) return false;
        for (int dx = -20; dx <= 20; dx++)
            for (int dz = -20; dz <= 20; dz++)
                for (int dy = -5; dy <= 5; dy++) {
                    Block b = Blocks.at(me.getWorld(), me.getBlockX() + dx, me.getBlockY() + dy, me.getBlockZ() + dz);
                    if (b != null && b.getType() == type) return true;
                }
        return false;
    }

    // ================================================================== Planen

    private boolean need(Material m, int n) {
        if (m == null) return false;
        int have = inv.getOrDefault(m, 0);
        if (have >= n) return true;
        return produce(m, n - have);
    }

    private boolean needIngredient(Recipes.Ingredient ing, int n) {
        int have = count(ing.match());
        if (have < n) {
            int missing = n - have;
            boolean ok;
            if (ing.label().equals("PLANKS")) ok = producePlanks(missing);
            else if (ing.label().equals("LOG")) ok = gatherLogs(missing);
            else ok = produce(ing.fallback(), missing);
            if (!ok) return false;
        }
        consume(ing.match(), n);
        return true;
    }

    private boolean producePlanks(int missing) {
        int times = (missing + 3) / 4;
        if (!needIngredient(Recipes.ANY_PLANKS.ingredients().get(0), times)) return false;
        tasks.add(new CraftTask(Recipes.ANY_PLANKS, times));
        add(Mats.get("OAK_PLANKS"), times * 4);
        return true;
    }

    private boolean gatherLogs(int missing) {
        tasks.add(new GatherTask("Holz", Mats::isLog, Mats::isLog, missing, true, false));
        add(Mats.get("OAK_LOG"), missing);
        return true;
    }

    private boolean produce(Material m, int missing) {
        if (++depth > 14) {
            problem = "Das ist mir zu verschachtelt.";
            return false;
        }
        try {
            if (Mats.isPlanks(m) && Recipes.recipe(m) != null && Mats.is(m, "OAK_PLANKS")) return producePlanks(missing);

            Recipes.Recipe r = Recipes.recipe(m);
            if (r != null) {
                int times = (missing + r.amount() - 1) / r.amount();
                if (r.table() && !ensureTable()) return false;
                for (Recipes.Ingredient i : r.ingredients()) {
                    if (!needIngredient(i, i.count() * times)) return false;
                }
                tasks.add(new CraftTask(r, times));
                add(m, times * r.amount());
                return true;
            }

            Recipes.Smelt s = Recipes.smelt(m);
            if (s != null) {
                if (!ensureFurnace()) return false;
                Recipes.Ingredient input = Mats.isLog(s.input())
                        ? new Recipes.Ingredient(Mats::isLog, 1, s.input(), "LOG")
                        : new Recipes.Ingredient(x -> x == s.input(), 1, s.input(), s.input().name());
                if (!needIngredient(input, missing)) return false;
                if (!ensureFuel(missing)) return false;
                tasks.add(new SmeltTask(s.input(), m, missing));
                add(m, missing);
                return true;
            }

            Recipes.Source src = Recipes.source(m);
            if (src != null) return gather(src, missing);

            problem = "Ich weiss nicht, wie ich an " + Lexicon.name(m) + " komme.";
            return false;
        } finally {
            depth--;
        }
    }

    private boolean gather(Recipes.Source src, int missing) {
        Material item = src.item();
        switch (src.method()) {
            case TREE -> tasks.add(new GatherTask(Lexicon.name(item), x -> x == item, x -> x == item, missing, true, false));
            case MINE -> {
                if (src.pickaxeTier() > 0 && npc.settings().realisticTools && !ensurePickaxe(src.pickaxeTier())) return false;
                boolean dig = src.pickaxeTier() >= 1 && !Mats.is(item, "SUGAR_CANE");
                tasks.add(new GatherTask(src.label(), src.blocks(), x -> x == item, missing, false, dig));
            }
            case HUNT -> {
                List<String> mobs = src.mobs();
                Predicate<Entity> filter = e -> mobs.contains(e.getType().name());
                if (!has(bestSword())) ensureWeaponIfCheap();
                tasks.add(new CombatTask(null, 999, filter, src.label()).until(x -> x == item, missing));
            }
            case HARVEST -> tasks.add(new HarvestTask(src.blocks(), x -> x == item, missing));
        }
        add(item, missing);
        return true;
    }

    private Material bestSword() {
        for (int tier = 5; tier >= 1; tier--) {
            Material m = Mats.get(Mats.TIER_PREFIX[tier] + "SWORD");
            if (has(m)) return m;
        }
        return null;
    }

    /** Ein Holzschwert fuer die Jagd, falls ohnehin Holz da ist - sonst mit der Faust. */
    private void ensureWeaponIfCheap() {
        if (count(Mats::isPlanks) >= 3 || count(Mats::isLog) >= 2) {
            Material sword = Mats.get("WOODEN_SWORD");
            if (sword != null && !has(sword)) need(sword, 1);
        }
    }

    private boolean ensureTable() {
        if (table) return true;
        table = true;
        notes.add("Ich bau mir erst eine Werkbank.");
        return need(Mats.get("CRAFTING_TABLE"), 1);
    }

    private boolean ensureFurnace() {
        if (furnace) return true;
        furnace = true;
        notes.add("Ich brauche einen Ofen, den mache ich mir.");
        return need(Mats.get("FURNACE"), 1);
    }

    private boolean ensurePickaxe(int tier) {
        for (int t = 5; t >= tier; t--) {
            if (has(Mats.get(Mats.TIER_PREFIX[t] + "PICKAXE"))) return true;
        }
        Material pick = Mats.get(Mats.TIER_PREFIX[tier] + "PICKAXE");
        notes.add("Dafuer brauche ich erst eine " + Recipes.tierName(tier) + "spitzhacke.");
        return need(pick, 1);
    }

    private boolean ensureFuel(int items) {
        double have = 0;
        for (Map.Entry<Material, Integer> e : inv.entrySet()) have += Recipes.fuelValue(e.getKey()) * e.getValue();
        if (have < items) {
            int planks = (int) Math.ceil((items - have) / 1.5);
            if (!producePlanks(planks)) return false;
        }
        // Brennstoff gedanklich verbrauchen: erst Kohle, dann Bretter, dann Staemme
        double left = items;
        List<Predicate<Material>> order = List.of(
                m -> Mats.is(m, "COAL") || Mats.is(m, "CHARCOAL") || Mats.is(m, "COAL_BLOCK"),
                Mats::isPlanks, Mats::isLog, m -> Mats.is(m, "STICK"));
        for (Predicate<Material> kind : order) {
            while (left > 0) {
                Material m = null;
                for (Map.Entry<Material, Integer> e : inv.entrySet()) {
                    if (e.getValue() > 0 && kind.test(e.getKey())) {
                        m = e.getKey();
                        break;
                    }
                }
                if (m == null) break;
                Material use = m;
                consume(x -> x == use, 1);
                left -= Recipes.fuelValue(use);
            }
        }
        return true;
    }
}
