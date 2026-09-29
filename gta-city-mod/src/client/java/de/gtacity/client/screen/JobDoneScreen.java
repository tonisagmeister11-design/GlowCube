package de.gtacity.client.screen;

import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.Jobs;
import de.gtacity.network.Payloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** After a job: what it paid, and "Weitermachen" for the next order of the same job, one level harder. */
public class JobDoneScreen extends Screen {
    private final Jobs.Type type;
    private final int level;
    private final long earned;

    public JobDoneScreen(Jobs.Type type, int level, long earned) {
        super(Component.literal("Job erledigt"));
        this.type = type;
        this.level = level;
        this.earned = earned;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 22;
        addRenderableWidget(Button.builder(Component.literal("Weitermachen (Stufe " + (level + 1) + ")"), b -> {
            ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.CONTINUE_JOB, 0));
            onClose();
        }).bounds(cx - 124, y, 140, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Aufhören"), b -> onClose())
                .bounds(cx + 20, y, 104, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int cx = width / 2, top = height / 2 - 52;
        int accent = type.illegal() ? 0xFFE04040 : 0xFF40A8FF;
        g.fill(cx - 132, top, cx + 132, top + 104, 0xE8101418);
        g.fill(cx - 132, top, cx + 132, top + 2, accent);
        g.centeredText(font, type.label + " - Stufe " + level + " geschafft!", cx, top + 8, 0xFFFFD040);
        if (earned > 0) {
            g.centeredText(font, "Verdient: " + Economy.format(earned), cx, top + 20, 0xFF6BD36B);
        }
        g.centeredText(font, "Nächster Auftrag: Stufe " + (level + 1), cx, top + 36, 0xFFFFFFFF);
        g.centeredText(font, "schwerer, weniger Zeit - aber mehr Geld (+30 %)", cx, top + 48, 0xFFB0B8C0);
        g.centeredText(font, "Im Team macht dein Team automatisch mit.", cx, top + 60, 0xFF9098A0);
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
