package de.glowcube.claudeai.task;

import org.bukkit.Material;
import org.bukkit.Sound;

import de.glowcube.claudeai.brain.Lexicon;
import de.glowcube.claudeai.npc.Npc;
import de.glowcube.claudeai.plan.Recipes;
import de.glowcube.claudeai.world.Fx;
import de.glowcube.claudeai.world.Mats;

/** Craftet ein Rezept so oft wie verlangt - an der Werkbank, wenn es eine braucht. */
public final class CraftTask extends StationTask {

    private final Recipes.Recipe recipe;
    private final int times;
    private int done;
    private int timer;

    public CraftTask(Recipes.Recipe recipe, int times) {
        super("CRAFTING_TABLE", recipe.table());
        this.recipe = recipe;
        this.times = Math.max(1, times);
    }

    @Override
    public String label() {
        return "crafte " + (recipe == Recipes.ANY_PLANKS ? "Bretter" : Lexicon.name(recipe.output()));
    }

    @Override
    public Status tick(Npc npc) {
        Status prep = prepare(npc);
        if (prep != Status.DONE) return prep;
        if (++timer < 6) return Status.RUNNING;
        timer = 0;

        if (recipe == Recipes.ANY_PLANKS) {
            Material log = null;
            for (var s : npc.getInventory().getContents()) {
                if (s != null && Mats.isLog(s.getType())) {
                    log = s.getType();
                    break;
                }
            }
            if (log == null) return fail("Mir sind die Baumstaemme ausgegangen.");
            npc.take(log, 1);
            npc.give(Mats.planksFor(log), 4);
        } else {
            for (Recipes.Ingredient i : recipe.ingredients()) {
                if (npc.count(i.match()) < i.count()) {
                    String what = i.label().equals("PLANKS") ? "Bretter" : Lexicon.name(i.fallback());
                    return fail("Fuer " + Lexicon.name(recipe.output()) + " fehlen mir " + what + ".");
                }
            }
            for (Recipes.Ingredient i : recipe.ingredients()) npc.take(i.match(), i.count());
            npc.give(recipe.output(), recipe.amount());
        }
        npc.swing();
        Fx.sound(npc.location(), () -> Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        if (++done >= times) {
            packUp(npc);
            return Status.DONE;
        }
        return Status.RUNNING;
    }
}
