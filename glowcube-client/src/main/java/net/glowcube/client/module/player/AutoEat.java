package net.glowcube.client.module.player;

import net.glowcube.client.core.Category;
import net.glowcube.client.core.Module;
import net.glowcube.client.core.setting.BooleanSetting;
import net.glowcube.client.core.setting.ModeSetting;
import net.glowcube.client.core.setting.NumberSetting;
import net.glowcube.client.module.combat.KillAura;
import net.glowcube.client.util.FindItemResult;
import net.glowcube.client.util.InvUtils;
import net.glowcube.client.util.SlotUtils;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/**
 * Uebertragen aus Meteor Client (GPL-3.0), Modul {@code AutoEat}.
 *
 * <p>Isst von selbst, sobald Leben oder Hunger unter die eingestellte
 * Schwelle fallen, und tauscht danach auf den vorherigen Platz zurueck. Der
 * Ablauf des Originals ist uebernommen:
 *
 * <ol>
 *   <li>Erst pruefen, ob ueberhaupt gegessen werden soll (Schwellenmodus:
 *       Leben, Hunger, eines von beiden, beide).</li>
 *   <li>Das beste Essen suchen - zuerst die Nebenhand, dann die Hotbar, auf
 *       Wunsch der ganze Rucksack. Bewertet nach Naehrwert oder
 *       Saettigung.</li>
 *   <li>Dorthin wechseln (aus dem Rucksack notfalls auf einen freien
 *       Hotbar-Platz), die Benutzen-Taste halten, bis es reicht, und
 *       zuruecktauschen.</li>
 * </ol>
 *
 * <p>Die Sperrliste ist die des Originals: goldene Aepfel, Chorusfrucht,
 * Kugelfisch und alles, was mehr schadet als nuetzt, wird nicht gegessen.
 *
 * <p>Damit sich KillAura und AutoEat nicht gegenseitig die Hand streitig
 * machen, wird KillAura fuers Essen kurz stillgelegt und danach wieder
 * eingeschaltet - genau wie im Original.
 *
 * <p><b>Nicht uebernommen:</b> die Baritone-Pause und der Gleichlauf mit
 * AutoGap (das es hier nicht gibt).
 */
public final class AutoEat extends Module {
    private final ModeSetting schwelle = register(new ModeSetting("Schwelle",
            "Wonach das Essen ausgeloest wird",
            "Eines", "Leben", "Hunger", "Eines", "Beide"));
    private final NumberSetting lebenSchwelle = register(new NumberSetting("Leben",
            "Ab wie wenig Leben gegessen wird", 10, 1, 19, 1));
    private final NumberSetting hungerSchwelle = register(new NumberSetting("Hunger",
            "Ab wie wenig Hunger gegessen wird", 16, 1, 19, 1));
    private final ModeSetting bevorzugung = register(new ModeSetting("Bevorzugung",
            "Was das beste Essen ausmacht", "Saettigung", "Saettigung", "Naehrwert"));
    private final BooleanSetting imRucksack = register(new BooleanSetting("Im Rucksack suchen",
            "Auch ausserhalb der Hotbar nach Essen suchen", false));
    private final BooleanSetting killAuraPausieren = register(new BooleanSetting("KillAura pausieren",
            "KillAura waehrend des Essens kurz stilllegen", true));

    /** Auf den Ess-Aepfeln aus dem Original - alles, was man nicht essen will. */
    private static final String[] GESPERRT = {
            "enchanted_golden_apple", "golden_apple", "chorus_fruit", "poisonous_potato",
            "pufferfish", "chicken", "rotten_flesh", "spider_eye", "suspicious_stew"
    };

    private boolean isst;
    private int platz;
    private int vorherigerPlatz;
    private boolean killAuraWarAn;

    public AutoEat() {
        super("AutoEat", "Isst von selbst, wenn es eng wird", Category.PLAYER);
    }

    @Override
    public void onDisable() {
        if (isst) {
            aufhoeren();
        }
    }

    @Override
    public void onTick() {
        if (!inGame()) {
            return;
        }

        if (isst) {
            if (!sollteEssen()) {
                aufhoeren();
                return;
            }
            // Ist das, was in der Hand liegt, kein Essen mehr (aufgegessen)?
            if (nahrung(player().getInventory().getItem(platz)) == null) {
                int neu = platzSuchen();
                if (neu == -1) {
                    aufhoeren();
                    return;
                }
                wechseln(neu);
            }
            essen();
            return;
        }

        if (sollteEssen()) {
            anfangen();
        }
    }

    private void anfangen() {
        vorherigerPlatz = player().getInventory().getSelectedSlot();
        essen();

        if (killAuraPausieren.get()) {
            KillAura killAura = net.glowcube.client.GlowCubeClient.modules().get(KillAura.class);
            killAuraWarAn = killAura.isEnabled();
            if (killAuraWarAn) {
                killAura.setEnabled(false);
            }
        }
    }

    private void essen() {
        if (!wechseln(platz)) {
            return;
        }
        mc.options.keyUse.setDown(true);
        isst = true;
    }

    private void aufhoeren() {
        if (vorherigerPlatz != SlotUtils.OFFHAND) {
            wechseln(vorherigerPlatz);
        }
        mc.options.keyUse.setDown(false);
        isst = false;

        if (killAuraPausieren.get() && killAuraWarAn) {
            net.glowcube.client.GlowCubeClient.modules().get(KillAura.class).setEnabled(true);
            killAuraWarAn = false;
        }
    }

    /**
     * Auf den gewuenschten Platz wechseln. Nebenhand geht direkt; Hotbar per
     * Auswahl; aus dem Rucksack erst auf einen freien Hotbar-Platz legen -
     * gibt es keinen, wird abgebrochen.
     */
    private boolean wechseln(int ziel) {
        if (ziel == SlotUtils.OFFHAND) {
            platz = SlotUtils.OFFHAND;
            return true;
        }
        if (SlotUtils.istHotbar(ziel)) {
            InvUtils.tausche(ziel, false);
            platz = ziel;
            return true;
        }
        FindItemResult leer = InvUtils.finde(ItemStack::isEmpty,
                SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END);
        if (!leer.found()) {
            return false;
        }
        InvUtils.verschieben().von(ziel).nachHotbar(leer.slot());
        InvUtils.tausche(leer.slot(), false);
        platz = leer.slot();
        return true;
    }

    public boolean sollteEssen() {
        boolean lebenKnapp = player().getHealth() <= lebenSchwelle.get();
        boolean hungerKnapp = player().getFoodData().getFoodLevel() <= hungerSchwelle.get();
        if (!schwelleErfuellt(lebenKnapp, hungerKnapp)) {
            return false;
        }

        platz = platzSuchen();
        if (platz == -1) {
            return false;
        }
        FoodProperties essen = nahrung(player().getInventory().getItem(platz));
        if (essen == null) {
            return false;
        }
        return player().getFoodData().needsFood() || essen.canAlwaysEat();
    }

    private boolean schwelleErfuellt(boolean leben, boolean hunger) {
        return switch (schwelle.get()) {
            case "Leben" -> leben;
            case "Hunger" -> hunger;
            case "Beide" -> leben && hunger;
            default -> leben || hunger;
        };
    }

    private int platzSuchen() {
        // Zuerst die Nebenhand.
        ItemStack neben = player().getOffhandItem();
        if (nahrung(neben) != null && !gesperrt(neben)) {
            return SlotUtils.OFFHAND;
        }
        int inHotbar = bestesEssen(SlotUtils.HOTBAR_START, SlotUtils.HOTBAR_END);
        if (inHotbar != -1) {
            return inHotbar;
        }
        if (imRucksack.get()) {
            return bestesEssen(SlotUtils.MAIN_START, SlotUtils.MAIN_END);
        }
        return -1;
    }

    private int bestesEssen(int von, int bis) {
        int bester = -1;
        float bestwert = -1.0f;
        for (int i = von; i <= bis; i++) {
            ItemStack stack = player().getInventory().getItem(i);
            FoodProperties essen = nahrung(stack);
            if (essen == null || gesperrt(stack)) {
                continue;
            }
            float wert = bevorzugung.is("Naehrwert")
                    ? essen.nutrition()
                    : essen.saturation();
            if (wert > bestwert) {
                bestwert = wert;
                bester = i;
            }
        }
        return bester;
    }

    private static FoodProperties nahrung(ItemStack stack) {
        return stack.get(DataComponents.FOOD);
    }

    private static boolean gesperrt(ItemStack stack) {
        String id = net.minecraft.core.registries.BuiltInRegistries.ITEM
                .getKey(stack.getItem()).getPath();
        for (String eintrag : GESPERRT) {
            if (eintrag.equals(id)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String hudSuffix() {
        return isst ? "isst" : null;
    }
}
