package de.gtacity.client.map;

import de.gtacity.network.Payloads;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** The other players in the world (sent by the server twice a second), for the radar, the map and the crew tab. */
public final class OtherPlayers {
    private OtherPlayers() {
    }

    public static final int CREW_COLOR = 0xFF40E060;
    public static final int OTHER_COLOR = 0xFF60B8FF;

    private static List<Payloads.PlayerDot> dots = List.of();
    private static String invitedBy = "";

    public static void update(Payloads.Players payload) {
        dots = List.copyOf(payload.dots());
        invitedBy = payload.invitedBy();
    }

    public static void clear() {
        dots = List.of();
        invitedBy = "";
    }

    public static List<Payloads.PlayerDot> all() {
        return dots;
    }

    /** Name of the player whose crew invitation is open, or "". */
    public static String invitedBy() {
        return invitedBy;
    }

    public static boolean inCrew() {
        return dots.stream().anyMatch(Payloads.PlayerDot::crew);
    }

    public static int color(Payloads.PlayerDot dot) {
        return dot.crew() ? CREW_COLOR : OTHER_COLOR;
    }

    /** Arrow in the player's colour, pointing the way they look, with the name underneath. */
    public static void draw(GuiGraphicsExtractor g, Font font, int x, int y, Payloads.PlayerDot dot, float extraRot,
                            boolean name) {
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        g.pose().rotate((float) Math.toRadians(dot.yaw() + 180.0F) + extraRot);
        MapDraw.arrow(g, color(dot));
        g.pose().popMatrix();
        if (name) {
            String text = dot.name() + (dot.wanted() > 0 ? " " + "★".repeat(dot.wanted()) : "");
            int w = font.width(text);
            g.fill(x - w / 2 - 2, y + 6, x + w / 2 + 2, y + 16, 0xC0000000);
            g.text(font, text, x - w / 2, y + 7, color(dot), false);
        }
    }
}
