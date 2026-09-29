package de.gtacity.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import de.gtacity.client.map.CityMapTexture;
import de.gtacity.client.map.MapDraw;
import de.gtacity.client.map.Waypoint;
import de.gtacity.entity.CarVariant;
import de.gtacity.gameplay.Economy;
import de.gtacity.gameplay.Jobs;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModAttachments;
import de.gtacity.world.CityLayout;
import de.gtacity.world.CityMap;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The big map (key M), a bit like the GTA pause menu: a zoomable map of the whole city with a legend, click to set
 * a waypoint (the GPS then draws the route on the radar), click a villa to buy it or to teleport home. More tabs for
 * jobs, the garage and owned villas.
 */
public class CityMapScreen extends Screen {
    private enum Tab {
        MAP("Karte"), JOBS("Jobs"), GARAGE("Garage"), VILLAS("Villen");

        final String label;

        Tab(String label) {
            this.label = label;
        }
    }

    private static final int TOP = 26;
    private static final int LEGEND_WIDTH = 124;
    private static final double MIN_SCALE = 0.6;
    private static final double MAX_SCALE = 14.0;

    private static Tab tab = Tab.MAP;
    private static double centerX = Double.NaN;
    private static double centerZ;
    private static double scale = 4.0;

    private boolean dragged;
    private @Nullable CityMap.Place selected;
    /** Icons drawn in the last frame, for clicks. */
    private final List<Object[]> drawnIcons = new ArrayList<>();

    public CityMapScreen() {
        super(Component.literal("Karte"));
    }

    @Override
    protected void init() {
        LocalPlayer player = minecraft.player;
        if (player != null && (Double.isNaN(centerX) || !CityLayout.insideCity((int) centerX, (int) centerZ))) {
            centerX = player.getX();
            centerZ = player.getZ();
        }
        int x = 4;
        for (Tab t : Tab.values()) {
            Button b = Button.builder(Component.literal(t.label), button -> {
                tab = t;
                selected = null;
                rebuildWidgets();
            }).bounds(x, 3, 56, 20).build();
            b.active = t != tab;
            addRenderableWidget(b);
            x += 58;
        }
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(width - 64, 3, 60, 20).build());
        switch (tab) {
            case MAP -> initMap();
            case JOBS -> initJobs();
            case GARAGE -> initGarage();
            case VILLAS -> initVillas();
        }
    }

    // ------------------------------------------------------------------ map tab

    private void initMap() {
        addRenderableWidget(Button.builder(Component.literal("Zu mir"), b -> {
            if (minecraft.player != null) {
                centerX = minecraft.player.getX();
                centerZ = minecraft.player.getZ();
            }
        }).bounds(width - 64 - 62, 3, 60, 20).build());
        if (Waypoint.get() != null) {
            addRenderableWidget(Button.builder(Component.literal("Ziel löschen"), b -> {
                Waypoint.clear();
                rebuildWidgets();
            }).bounds(width - 64 - 62 - 76, 3, 74, 20).build());
        }
        if (selected != null && selected.kind() == CityMap.Kind.VILLA) {
            boolean owned = MapDraw.owned(minecraft, selected);
            int bx = width / 2 - 100, by = height - 26;
            if (owned) {
                long id = selected.id();
                addRenderableWidget(Button.builder(Component.literal("Hinteleportieren"), b -> {
                    ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.VILLA_TELEPORT, id));
                    onClose();
                }).bounds(bx, by, 98, 20).build());
            } else {
                long id = selected.id();
                addRenderableWidget(Button.builder(Component.literal("Kaufen"), b -> {
                    ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.BUY_VILLA, id));
                    selected = null;
                    rebuildWidgets();
                }).bounds(bx, by, 98, 20).build());
            }
            CityMap.Place place = selected;
            addRenderableWidget(Button.builder(Component.literal("Navi setzen"), b -> {
                Waypoint.set(place.entrance().getX() + 0.5, place.entrance().getZ() + 0.5);
                selected = null;
                rebuildWidgets();
            }).bounds(bx + 102, by, 98, 20).build());
        }
    }

    private double worldX(double screenX) {
        return centerX + (screenX - mapCenterX()) * scale;
    }

    private double worldZ(double screenY) {
        return centerZ + (screenY - mapCenterY()) * scale;
    }

    private int screenX(double wx) {
        return (int) Math.round(mapCenterX() + (wx - centerX) / scale);
    }

    private int screenY(double wz) {
        return (int) Math.round(mapCenterY() + (wz - centerZ) / scale);
    }

    private double mapCenterX() {
        return (LEGEND_WIDTH + width) / 2.0;
    }

    private double mapCenterY() {
        return (TOP + height) / 2.0;
    }

    private boolean inMap(double x, double y) {
        return tab == Tab.MAP && x >= LEGEND_WIDTH && y >= TOP;
    }

    private void renderMap(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        LocalPlayer player = minecraft.player;
        int x0 = LEGEND_WIDTH, y0 = TOP, x1 = width, y1 = height;
        g.enableScissor(x0, y0, x1, y1);
        CityMapTexture.draw(g, x0, y0, x1, y1, worldX(x0), worldZ(y0), worldX(x1), worldZ(y1));
        if (!CityMapTexture.ready()) {
            g.centeredText(font, "Karte wird gezeichnet...", (int) mapCenterX(), (int) mapCenterY(), 0xFFFFFFFF);
        }

        double[] target = player == null ? null : MapDraw.target(player);
        if (player != null && target != null) {
            List<double[]> route = CityMap.route(player.getX(), player.getZ(), target[0], target[1]);
            MapDraw.route(g, route, centerX, centerZ, scale, (float) mapCenterX(), (float) mapCenterY(),
                    MapDraw.targetColor(player), scale < 3 ? 4 : 3);
        }

        // Symbols: sorted by importance, anything that would overlap an earlier one is left out.
        MapDraw.Layout layout = new MapDraw.Layout();
        drawnIcons.clear();
        int px = 0, py = 0;
        if (player != null) {
            px = screenX(player.getX());
            py = screenY(player.getZ());
            layout.reserve(px, py, 11);
        }
        if (target != null) {
            layout.reserve(screenX(target[0]), screenY(target[1]), 11);
        }
        for (CityMap.Place place : CityMap.places()) {
            int x = screenX(place.x()), y = screenY(place.z());
            if (x < x0 + 6 || x > x1 - 6 || y < y0 + 6 || y > y1 - 6) {
                continue;
            }
            boolean owned = MapDraw.owned(minecraft, place);
            // Owned villas always win against other symbols.
            if (!owned && !layout.place(x, y, MapDraw.ICON)) {
                continue;
            }
            if (owned) {
                layout.reserve(x, y, MapDraw.ICON);
            }
            MapDraw.icon(g, font, x, y, place, owned);
            drawnIcons.add(new Object[]{place, x, y});
            if (place == selected) {
                g.outline(x - 7, y - 7, 15, 15, 0xFFFFFFFF);
            }
        }
        if (target != null) {
            MapDraw.flag(g, screenX(target[0]), screenY(target[1]), MapDraw.targetColor(player));
        }
        if (player != null) {
            g.pose().pushMatrix();
            g.pose().translate(px, py);
            g.pose().rotate((float) Math.toRadians(player.getYRot() + 180.0F));
            MapDraw.arrow(g, 0xFFFFFFFF);
            g.pose().popMatrix();
        }
        g.disableScissor();

        // Tooltip for the symbol under the mouse.
        Object[] hover = iconAt(mouseX, mouseY);
        if (hover != null) {
            CityMap.Place place = (CityMap.Place) hover[0];
            String text = place.kind() == CityMap.Kind.VILLA
                    ? CityMap.villaName(place) + (MapDraw.owned(minecraft, place) ? " (deine)" : " - "
                    + Economy.format(CityMap.villaPrice(place)))
                    : place.kind().label;
            int w = font.width(text);
            g.fill(mouseX + 8, mouseY - 12, mouseX + 12 + w, mouseY, 0xE0000000);
            g.text(font, text, mouseX + 10, mouseY - 10, 0xFFFFFFFF, false);
        }

        if (selected != null) {
            int w = 206, x = width / 2 - w / 2, y = height - 56;
            g.fill(x - 2, y - 2, x + w + 2, height - 2, 0xE0000000);
            boolean owned = MapDraw.owned(minecraft, selected);
            g.text(font, CityMap.villaName(selected), x + 2, y + 2, 0xFFFFFFFF, false);
            g.text(font, owned ? "Gehört dir" : "Preis: " + Economy.format(CityMap.villaPrice(selected)), x + 2, y + 14,
                    owned ? MapDraw.VILLA_OWNED : 0xFF6BD36B, false);
        }

        renderLegend(g);
    }

    private void renderLegend(GuiGraphicsExtractor g) {
        g.fill(0, TOP, LEGEND_WIDTH, height, 0xF0101418);
        int y = TOP + 6;
        g.text(font, "Legende", 6, y, 0xFFFFD040, false);
        y += 14;
        for (CityMap.Kind kind : CityMap.Kind.values()) {
            MapDraw.icon(g, font, 12, y + 4, kind.color, kind.textColor, kind.symbol);
            g.text(font, kind.label, 22, y, 0xFFE0E0E0, false);
            y += 13;
        }
        MapDraw.icon(g, font, 12, y + 4, MapDraw.VILLA_OWNED, 0xFFFFFFFF, "H");
        g.text(font, "Deine Villa", 22, y, 0xFFE0E0E0, false);
        y += 13;
        MapDraw.flag(g, 12, y + 4, MapDraw.WAYPOINT_COLOR);
        g.text(font, "Dein Ziel (Navi)", 22, y, 0xFFE0E0E0, false);
        y += 13;
        MapDraw.flag(g, 12, y + 4, MapDraw.MISSION_COLOR);
        g.text(font, "Job-Ziel", 22, y, 0xFFE0E0E0, false);
        y += 13;
        g.fill(8, y + 1, 17, y + 8, 0xFFFF3030);
        g.text(font, "Polizei", 22, y, 0xFFE0E0E0, false);
        y += 18;
        y = g.textWithWordWrap(font, Component.literal("Klick: Ziel setzen. Klick auf V: Villa. "
                + "Rechtsklick: Ziel weg. Ziehen: verschieben, Mausrad: Zoom."), 6, y, LEGEND_WIDTH - 10, 0xFF9098A0);
    }

    private @Nullable Object[] iconAt(double x, double y) {
        Object[] best = null;
        double bestDist = 7 * 7;
        for (Object[] icon : drawnIcons) {
            double dx = (int) icon[1] - x, dy = (int) icon[2] - y;
            double d = dx * dx + dy * dy;
            if (d <= bestDist) {
                bestDist = d;
                best = icon;
            }
        }
        return best;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        dragged = false;
        if (!inMap(event.x(), event.y())) {
            return false;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
            Waypoint.clear();
            selected = null;
            rebuildWidgets();
            return true;
        }
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (inMap(event.x(), event.y()) && event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (Math.abs(dx) + Math.abs(dy) > 0.5) {
                dragged = true;
            }
            centerX -= dx * scale;
            centerZ -= dy * scale;
            clampCenter();
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && !dragged && inMap(event.x(), event.y())
                && getChildAt(event.x(), event.y()).isEmpty()) {
            click(event.x(), event.y());
            dragged = false;
            return true;
        }
        dragged = false;
        return super.mouseReleased(event);
    }

    private void click(double x, double y) {
        Object[] icon = iconAt(x, y);
        if (icon != null) {
            CityMap.Place place = (CityMap.Place) icon[0];
            if (place.kind() == CityMap.Kind.VILLA) {
                selected = place;
            } else {
                selected = null;
                Waypoint.set(place.entrance().getX() + 0.5, place.entrance().getZ() + 0.5);
            }
        } else {
            double[] current = Waypoint.get();
            if (current != null && Math.abs(screenX(current[0]) - x) < 6 && Math.abs(screenY(current[1]) - y) < 6) {
                Waypoint.clear();
            } else {
                Waypoint.set(worldX(x), worldZ(y));
            }
            selected = null;
        }
        rebuildWidgets();
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (!inMap(x, y) || scrollY == 0) {
            return super.mouseScrolled(x, y, scrollX, scrollY);
        }
        // Zoom around the mouse position.
        double wx = worldX(x), wz = worldZ(y);
        scale = Mth.clamp(scale * Math.pow(0.8, scrollY), MIN_SCALE, MAX_SCALE);
        centerX = wx - (x - mapCenterX()) * scale;
        centerZ = wz - (y - mapCenterY()) * scale;
        clampCenter();
        return true;
    }

    private void clampCenter() {
        double min = CityMapTexture.ORIGIN, max = CityMapTexture.ORIGIN
                + CityMapTexture.SIZE * CityMapTexture.BLOCKS_PER_PIXEL;
        centerX = Mth.clamp(centerX, min, max);
        centerZ = Mth.clamp(centerZ, min, max);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (de.gtacity.client.ClientInput.MAP.matches(event)) {
            onClose();
            return true;
        }
        return super.keyPressed(event);
    }

    // ------------------------------------------------------------------ jobs tab

    private void initJobs() {
        LocalPlayer player = minecraft.player;
        boolean busy = player != null && player.getAttached(ModAttachments.MISSION) != null;
        int y = TOP + 40;
        for (Jobs.Type type : Jobs.Type.values()) {
            Button b = Button.builder(Component.literal("Starten"),
                            button -> {
                                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.START_JOB, type.ordinal()));
                                onClose();
                            })
                    .bounds(width / 2 + 120, y, 60, 20).build();
            b.active = !busy;
            addRenderableWidget(b);
            y += 44;
        }
        if (busy) {
            addRenderableWidget(Button.builder(Component.literal("Job abbrechen"), b -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.CANCEL_JOB, 0));
                onClose();
            }).bounds(width / 2 - 50, height - 30, 100, 20).build());
        }
    }

    private void renderJobs(GuiGraphicsExtractor g) {
        int left = width / 2 - 180;
        g.fill(left - 6, TOP + 4, width / 2 + 186, height - 4, 0xE0101418);
        g.text(font, "Jobs - verdien Geld für Autos und Villen", left, TOP + 12, 0xFFFFD040, false);
        int y = TOP + 40;
        for (Jobs.Type type : Jobs.Type.values()) {
            g.text(font, type.label + (type.illegal ? "  (illegal)" : "  (legal)"), left, y,
                    type.illegal ? 0xFFFF6060 : 0xFF60E0FF, false);
            g.textWithWordWrap(font, Component.literal(type.description), left, y + 11, 290, 0xFFC8C8C8);
            y += 44;
        }
        ModAttachments.Mission mission = minecraft.player == null ? null
                : minecraft.player.getAttached(ModAttachments.MISSION);
        y += 4;
        g.text(font, "Straßenkriminalität:", left, y, 0xFFFFD040, false);
        g.textWithWordWrap(font, Component.literal("Taschendiebstahl: von hinten an Passanten schleichen, Schleichen + "
                + "Rechtsklick mit leerer Hand. Ladendiebstahl: im 24/7 Schleichen + Rechtsklick mit leerer Hand auf "
                + "die Theke. Überfall: dasselbe mit einer Waffe in der Hand."), left, y + 11, 360, 0xFFC8C8C8);
        if (mission != null) {
            g.centeredText(font, "Aktueller Job: " + mission.label(), width / 2, height - 44, 0xFFFFD020);
        }
    }

    // ------------------------------------------------------------------ garage tab

    private List<Integer> garage() {
        List<Integer> list = minecraft.player == null ? null : minecraft.player.getAttached(ModAttachments.GARAGE);
        return list == null ? List.of() : list;
    }

    private void initGarage() {
        List<Integer> cars = garage();
        int perColumn = Math.max(1, (height - TOP - 50) / 24);
        for (int i = 0; i < cars.size(); i++) {
            int col = i / perColumn, row = i % perColumn;
            int x = width / 2 - 180 + col * 184 + 118, y = TOP + 34 + row * 24;
            long index = i;
            addRenderableWidget(Button.builder(Component.literal("Liefern"), b -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.CALL_CAR, index));
                onClose();
            }).bounds(x, y, 56, 20).build());
        }
    }

    private void renderGarage(GuiGraphicsExtractor g) {
        int left = width / 2 - 180;
        g.fill(left - 6, TOP + 4, width / 2 + 186, height - 4, 0xE0101418);
        g.text(font, "Deine Garage", left, TOP + 12, 0xFFFFD040, false);
        List<Integer> cars = garage();
        if (cars.isEmpty()) {
            g.textWithWordWrap(font, Component.literal("Noch keine Autos. Im Autohaus (A auf der Karte) kaufst du "
                    + "Limousinen, SUVs, Sportwagen und Supersportwagen mit bis zu 300 km/h. Gekaufte Autos "
                    + "bringt dir der Mechaniker von hier aus an die nächste Straße."), left, TOP + 34, 360,
                    0xFFC8C8C8);
            return;
        }
        int perColumn = Math.max(1, (height - TOP - 50) / 24);
        for (int i = 0; i < cars.size(); i++) {
            int col = i / perColumn, row = i % perColumn;
            int x = left + col * 184, y = TOP + 34 + row * 24;
            CarVariant variant = CarVariant.byId(cars.get(i));
            g.fill(x, y, x + 116, y + 20, 0x90000000);
            g.text(font, variant.shape.label, x + 4, y + 2, 0xFFFFFFFF, false);
            g.text(font, colorName(variant) + "  " + Math.round(variant.shape.maxSpeed * 72) + " km/h", x + 4, y + 11,
                    0xFF9098A0, false);
        }
    }

    private static String colorName(CarVariant variant) {
        String texture = variant.texture;
        int cut = texture.indexOf('_');
        String color = cut < 0 ? texture : texture.substring(cut + 1);
        return switch (color) {
            case "red" -> "rot";
            case "blue" -> "blau";
            case "black" -> "schwarz";
            case "white" -> "weiß";
            case "silver" -> "silber";
            case "green", "darkgreen", "lime" -> "grün";
            case "purple", "magenta" -> "lila";
            case "yellow" -> "gelb";
            case "orange" -> "orange";
            case "navy" -> "dunkelblau";
            case "pearl" -> "perlweiß";
            case "carbon" -> "carbon";
            default -> color;
        };
    }

    // ------------------------------------------------------------------ villas tab

    private List<CityMap.Place> ownedVillas() {
        List<CityMap.Place> list = new ArrayList<>();
        List<Long> ids = minecraft.player == null ? null : minecraft.player.getAttached(ModAttachments.VILLAS);
        if (ids != null) {
            for (long id : ids) {
                CityMap.Place villa = CityMap.villa(id);
                if (villa != null) {
                    list.add(villa);
                }
            }
        }
        return list;
    }

    private void initVillas() {
        List<CityMap.Place> villas = ownedVillas();
        for (int i = 0; i < villas.size() && i < 8; i++) {
            long id = villas.get(i).id();
            int y = TOP + 34 + i * 24;
            addRenderableWidget(Button.builder(Component.literal("Hinteleportieren"), b -> {
                ClientPlayNetworking.send(new Payloads.Phone(Payloads.Phone.VILLA_TELEPORT, id));
                onClose();
            }).bounds(width / 2 + 60, y, 110, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Villen auf der Karte zeigen"), b -> {
            tab = Tab.MAP;
            CityMap.Place any = CityMap.of(CityMap.Kind.VILLA).stream().findFirst().orElse(null);
            if (any != null) {
                centerX = any.x();
                centerZ = any.z() + 250;
                scale = 3.0;
            }
            rebuildWidgets();
        }).bounds(width / 2 - 80, height - 30, 160, 20).build());
    }

    private void renderVillas(GuiGraphicsExtractor g) {
        int left = width / 2 - 180;
        g.fill(left - 6, TOP + 4, width / 2 + 186, height - 4, 0xE0101418);
        g.text(font, "Deine Villen", left, TOP + 12, 0xFFFFD040, false);
        List<CityMap.Place> villas = ownedVillas();
        if (villas.isEmpty()) {
            g.textWithWordWrap(font, Component.literal("Du hast noch keine Villa. In den Hills im Norden stehen "
                    + "Villen mit Pool zum Verkauf (V auf der Karte, $150.000 - $400.000). Klick auf der Karte auf "
                    + "eine Villa, um sie zu kaufen. Danach kannst du dich von hier jederzeit nach Hause "
                    + "teleportieren (nur ohne Fahndungssterne)."), left, TOP + 34, 360, 0xFFC8C8C8);
            return;
        }
        for (int i = 0; i < villas.size() && i < 8; i++) {
            int y = TOP + 34 + i * 24;
            g.fill(left, y, width / 2 + 56, y + 20, 0x90000000);
            g.text(font, CityMap.villaName(villas.get(i)), left + 4, y + 6, 0xFFFFFFFF, false);
        }
    }

    // ------------------------------------------------------------------ frame

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, TOP, 0xF0101418);
        switch (tab) {
            case MAP -> renderMap(g, mouseX, mouseY);
            case JOBS -> renderJobs(g);
            case GARAGE -> renderGarage(g);
            case VILLAS -> renderVillas(g);
        }
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        Long money = minecraft.player == null ? null : minecraft.player.getAttached(ModAttachments.MONEY);
        String text = Economy.format(money == null ? 0 : money);
        int moneyX = tab == Tab.MAP ? width - 64 - 62 - (Waypoint.get() != null ? 76 : 0) - 8 - font.width(text)
                : width - 72 - font.width(text);
        g.text(font, text, moneyX, 9, 0xFF6BD36B, true);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        if (tab != Tab.MAP) {
            super.extractBackground(g, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
