package de.gtacity.client.screen;

import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.Jobs;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModAttachments;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The job board of a station: the clerk's story chapter on top, below two tabs - the jobs you do alone (your crew
 * joins in) and the team jobs, where every other player in the world gets asked to come along.
 */
public class JobBoardScreen extends Screen {
    private static final int ROW = 23;
    private static boolean teamTab;

    private final Jobs.Station station;
    private final List<Jobs.Type> jobs = new ArrayList<>();
    private final List<Jobs.Type> teamJobs = new ArrayList<>();
    private int left;
    private int storyTop;
    private int listTop;
    private int panelWidth;

    public JobBoardScreen(Jobs.Station station) {
        super(Component.literal(station.label));
        this.station = station;
        teamTab = false;
        for (Jobs.Type type : Jobs.Type.values()) {
            if (type.station == station && !type.teamOnly) {
                jobs.add(type);
            }
            if (type.station == station && type.team()) {
                teamJobs.add(type);
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

    private List<Jobs.Type> shown() {
        return teamTab ? teamJobs : jobs;
    }

    @Override
    protected void init() {
        panelWidth = Math.min(410, width - 8);
        left = (width - panelWidth) / 2;
        storyTop = 28;
        int tabsTop = storyTop + 47;
        listTop = tabsTop + 21;
        int next = chapter() + 1;
        if (next <= Jobs.MAX_CHAPTER && Jobs.CHAPTER_STATION[next - 1] == station) {
            Button b = Button.builder(Component.literal(next == 1 ? "Führung starten" : "Kapitel starten"), button -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.START_STORY, next));
                onClose();
            }).bounds(left + panelWidth - 96, storyTop + 21, 90, 20).build();
            b.active = !busy();
            addRenderableWidget(b);
        }
        Button solo = Button.builder(Component.literal("Aufträge"), b -> {
            teamTab = false;
            rebuildWidgets();
        }).bounds(left, tabsTop, 100, 18).build();
        solo.active = teamTab;
        addRenderableWidget(solo);
        Button team = Button.builder(Component.literal("Team-Jobs (" + teamJobs.size() + ")"), b -> {
            teamTab = true;
            rebuildWidgets();
        }).bounds(left + 102, tabsTop, 100, 18).build();
        team.active = !teamTab;
        team.setTooltip(Tooltip.create(Component.literal("Jobs, die ihr zusammen macht: alle anderen Spieler in der "
                + "Welt bekommen eine Anfrage im Chat. Wer Ja sagt, wird zu dir teleportiert.")));
        addRenderableWidget(team);

        List<Jobs.Type> list = shown();
        for (int i = 0; i < list.size(); i++) {
            Jobs.Type type = list.get(i);
            Button b = Button.builder(Component.literal(teamTab ? "Team starten" : "Annehmen"), button -> {
                ClientPlayNetworking.send(new Payloads.Phone(teamTab ? Payloads.Phone.START_TEAM_JOB
                        : Payloads.Phone.START_JOB, type.ordinal()));
                onClose();
            }).bounds(left + panelWidth - 82, listTop + i * ROW + 1, 76, 20).build();
            b.active = !busy();
            b.setTooltip(Tooltip.create(Component.literal(type.description)));
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
        g.text(font, cash, left + panelWidth - font.width(cash), 19, 0xFF6BD36B, false);

        // story
        int next = chapter() + 1;
        g.fill(left, storyTop, left + panelWidth, storyTop + 44, 0x60000000);
        if (next > Jobs.MAX_CHAPTER) {
            g.text(font, "Story abgeschlossen - du bist der Boss von Los Santos!", left + 6, storyTop + 5, 0xFFFFD040,
                    false);
            g.textWithWordWrap(font, Component.literal(station.clerk + ": Schau ab und zu vorbei, es gibt immer "
                    + "Arbeit. Mit jedem Job steigt dein Rang und dein Lohn."), left + 6, storyTop + 17,
                    panelWidth - 12, 0xFFC8C8C8);
        } else if (Jobs.CHAPTER_STATION[next - 1] == station) {
            g.text(font, "Story: Kapitel " + next + " - " + Jobs.CHAPTER_TITLES[next - 1], left + 6, storyTop + 5,
                    0xFFFFD040, false);
            g.textWithWordWrap(font, Component.literal(Jobs.CHAPTER_TEXT[next - 1]), left + 6, storyTop + 17,
                    panelWidth - 110, 0xFFC8C8C8);
        } else {
            Jobs.Station other = Jobs.CHAPTER_STATION[next - 1];
            g.text(font, "Story: Kapitel " + next + " - " + Jobs.CHAPTER_TITLES[next - 1], left + 6, storyTop + 5,
                    0xFFFFD040, false);
            g.textWithWordWrap(font, Component.literal("Das nächste Kapitel gibt es im " + other.label + " ("
                    + (other == Jobs.Station.SHADY ? "D" : "J") + " auf der Karte)."), left + 6, storyTop + 17,
                    panelWidth - 12, 0xFFC8C8C8);
        }

        // jobs: name, pay and a short hint; the full description is the button tooltip
        List<Jobs.Type> list = shown();
        String hint = teamTab ? "Mitspieler bekommen eine Anfrage" : "Nach jedem Auftrag: Weitermachen = nächste Stufe";
        g.text(font, hint, left + 206, listTop - 16, 0xFF9098A0, false);
        for (int i = 0; i < list.size(); i++) {
            Jobs.Type type = list.get(i);
            int y = listTop + i * ROW;
            g.fill(left, y, left + panelWidth, y + ROW - 1, teamTab ? 0x5020A040 : 0x50000000);
            int color = teamTab ? 0xFF60F080 : type.illegal() ? 0xFFFF6060 : 0xFF60E0FF;
            g.text(font, type.label, left + 6, y + 3, color, false);
            g.text(font, type.pay + (teamTab ? " für jeden" : ""), left + 6, y + 12, 0xFF6BD36B, false);
            if (mouseX >= left && mouseX < left + panelWidth - 84 && mouseY >= y && mouseY < y + ROW - 1) {
                g.setTooltipForNextFrame(font, font.split(Component.literal(type.description), 220), mouseX, mouseY);
            }
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
