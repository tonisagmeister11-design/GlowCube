package de.gtacity.client.screen;

import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.Jobs;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModAttachments;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** The job board of a station: the clerk's story chapter on top, the repeatable jobs below. */
public class JobBoardScreen extends Screen {
    private static final int ROW = 31;
    private final Jobs.Station station;
    private final List<Jobs.Type> jobs = new ArrayList<>();
    private int left;
    private int storyTop;
    private int listTop;
    private int panelWidth;

    public JobBoardScreen(Jobs.Station station) {
        super(Component.literal(station.label));
        this.station = station;
        for (Jobs.Type type : Jobs.Type.values()) {
            if (type.station == station) {
                jobs.add(type);
            }
        }
    }

    private LocalPlayer player() {
        return minecraft.player;
    }

    private int chapter() {
        Integer c = player().getAttached(ModAttachments.STORY);
        return c == null ? 0 : c;
    }

    private boolean busy() {
        return player().getAttached(ModAttachments.MISSION) != null;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(410, width - 8);
        left = (width - panelWidth) / 2;
        storyTop = 30;
        listTop = storyTop + 56;
        int next = chapter() + 1;
        if (next <= Jobs.MAX_CHAPTER && Jobs.CHAPTER_STATION[next - 1] == station) {
            Button b = Button.builder(Component.literal(next == 1 ? "Führung starten" : "Kapitel starten"), button -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.START_STORY, next));
                onClose();
            }).bounds(left + panelWidth - 96, storyTop + 30, 90, 20).build();
            b.active = !busy();
            addRenderableWidget(b);
        }
        for (int i = 0; i < jobs.size(); i++) {
            Jobs.Type type = jobs.get(i);
            Button b = Button.builder(Component.literal("Annehmen"), button -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.START_JOB, type.ordinal()));
                onClose();
            }).bounds(left + panelWidth - 70, listTop + i * ROW + 3, 64, 20).build();
            b.active = !busy() && (!type.crewOnly || de.gtacity.client.map.OtherPlayers.inCrew());
            if (type.crewOnly && !de.gtacity.client.map.OtherPlayers.inCrew()) {
                b.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Component.literal(
                        "Nur mit Crew - Karte (M) → Crew")));
            }
            addRenderableWidget(b);
        }
        if (busy()) {
            addRenderableWidget(Button.builder(Component.literal("Aktuellen Job abbrechen"), b -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.CANCEL_JOB, 0));
                onClose();
            }).bounds(left, height - 24, 130, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(left + panelWidth - 70, height - 24, 70, 20).build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        int accent = station.illegal ? 0xFFE04040 : 0xFF40A8FF;
        g.fill(left - 4, 4, left + panelWidth + 4, height - 28, 0xE0101418);
        g.fill(left - 4, 4, left + panelWidth + 4, 6, accent);
        g.text(font, station.label + " - " + station.clerk, left, 10, accent, false);
        int done = Jobs.done(player());
        String rank = "Rang: " + Jobs.rank(done) + " (" + done + " Jobs, +" + Math.min(50, done * 3) + " % Lohn)";
        g.text(font, rank, left + panelWidth - font.width(rank), 10, 0xFFFFD040, false);
        Long money = player().getAttached(ModAttachments.MONEY);
        String cash = Economy.format(money == null ? 0 : money);
        g.text(font, cash, left + panelWidth - font.width(cash), 20, 0xFF6BD36B, false);

        // story
        int chapter = chapter();
        int next = chapter + 1;
        g.fill(left, storyTop, left + panelWidth, storyTop + 52, 0x60000000);
        if (next > Jobs.MAX_CHAPTER) {
            g.text(font, "Story abgeschlossen - du bist der Boss von Los Santos!", left + 6, storyTop + 6, 0xFFFFD040,
                    false);
            g.textWithWordWrap(font, Component.literal(station.clerk + ": Schau ab und zu vorbei, es gibt immer "
                    + "Arbeit. Mit jedem Job steigt dein Rang und dein Lohn."), left + 6, storyTop + 20,
                    panelWidth - 12, 0xFFC8C8C8);
        } else if (Jobs.CHAPTER_STATION[next - 1] == station) {
            g.text(font, "Story: Kapitel " + next + " - " + Jobs.CHAPTER_TITLES[next - 1], left + 6, storyTop + 6,
                    0xFFFFD040, false);
            g.textWithWordWrap(font, Component.literal(Jobs.CHAPTER_TEXT[next - 1]), left + 6, storyTop + 20,
                    panelWidth - 110, 0xFFC8C8C8);
        } else {
            Jobs.Station other = Jobs.CHAPTER_STATION[next - 1];
            g.text(font, "Story: Kapitel " + next + " - " + Jobs.CHAPTER_TITLES[next - 1], left + 6, storyTop + 6,
                    0xFFFFD040, false);
            g.textWithWordWrap(font, Component.literal("Das nächste Kapitel gibt es im " + other.label + " ("
                    + (other == Jobs.Station.SHADY ? "D" : "J") + " auf der Karte). " + station.clerk
                    + ": Schau dort vorbei."), left + 6, storyTop + 20, panelWidth - 12, 0xFFC8C8C8);
        }

        // jobs
        g.text(font, "Aufträge (jederzeit wiederholbar)", left, listTop - 11, 0xFFFFFFFF, false);
        for (int i = 0; i < jobs.size(); i++) {
            Jobs.Type type = jobs.get(i);
            int y = listTop + i * ROW;
            g.fill(left, y, left + panelWidth, y + ROW - 2, 0x50000000);
            g.text(font, type.label + (type.crewOnly ? "  [Crew]" : ""), left + 6, y + 3,
                    type.illegal() ? 0xFFFF6060 : 0xFF60E0FF, false);
            String pay = type.pay;
            g.text(font, pay, left + panelWidth - 78 - font.width(pay), y + 3, 0xFF6BD36B, false);
            g.textWithWordWrap(font, Component.literal(type.description), left + 6, y + 13, panelWidth - 92,
                    0xFFC8C8C8);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
