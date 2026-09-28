package de.gtacity.client.hud;

import de.gtacity.entity.CarEntity;
import de.gtacity.gameplay.Economy;
import de.gtacity.item.AmmoItem;
import de.gtacity.item.GunItem;
import de.gtacity.registry.ModAttachments;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

/** GTA style HUD: money, wanted stars, ammo, speedometer and the minimap. */
public final class CityHud {
    private CityHud() {
    }

    private static final int GREEN = 0xFF6BD36B;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFF505050;

    public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gui.hud.isHidden()) {
            return;
        }
        Font font = mc.font;
        int width = g.guiWidth();
        int height = g.guiHeight();

        // money
        Long moneyValue = player.getAttached(ModAttachments.MONEY);
        String money = Economy.format(moneyValue == null ? 0 : moneyValue);
        g.pose().pushMatrix();
        g.pose().scale(1.5F, 1.5F);
        g.text(font, money, (int) ((width - 8) / 1.5F) - font.width(money), 6, GREEN, true);
        g.pose().popMatrix();

        // wanted stars
        Integer wantedValue = player.getAttached(ModAttachments.WANTED);
        Integer hiddenValue = player.getAttached(ModAttachments.WANTED_HIDDEN);
        int wanted = wantedValue == null ? 0 : wantedValue;
        boolean hidden = hiddenValue != null && hiddenValue == 1;
        boolean blink = hidden && (player.tickCount / 6) % 2 == 0;
        StringBuilder stars = new StringBuilder();
        int x = width - 8;
        int y = 24;
        g.pose().pushMatrix();
        g.pose().scale(1.3F, 1.3F);
        int sx = (int) (x / 1.3F);
        int sy = (int) (y / 1.3F);
        for (int i = 4; i >= 0; i--) {
            boolean filled = i < wanted;
            int color = filled ? (blink ? 0xFF8888AA : WHITE) : (wanted > 0 ? GRAY : 0x40FFFFFF);
            sx -= font.width("★") + 1;
            g.text(font, "★", sx, sy, color, true);
        }
        g.pose().popMatrix();

        // ammo
        ItemStack held = player.getMainHandItem();
        if (held.getItem() instanceof GunItem gun) {
            String ammo = GunItem.ammo(held) + " / " + AmmoItem.count(player, gun.type.ammo);
            boolean reloading = player.getCooldowns().isOnCooldown(held);
            String line = reloading ? "Nachladen..." : ammo;
            g.text(font, gun.type.label, width - 8 - font.width(gun.type.label), 40, WHITE, true);
            g.text(font, line, width - 8 - font.width(line), 50, reloading ? 0xFFFFC040 : WHITE, true);
            if (de.gtacity.client.ClientInput.isAiming()) {
                int cx = width / 2, cy = height / 2;
                g.fill(cx - 6, cy, cx - 2, cy + 1, 0xC0FFFFFF);
                g.fill(cx + 2, cy, cx + 6, cy + 1, 0xC0FFFFFF);
                g.fill(cx, cy - 6, cx + 1, cy - 2, 0xC0FFFFFF);
                g.fill(cx, cy + 2, cx + 1, cy + 6, 0xC0FFFFFF);
            }
        }

        // speedometer
        if (player.getVehicle() instanceof CarEntity car) {
            int kmh = Math.round(Math.abs(car.speed) * 20.0F * 3.6F);
            String speed = kmh + " km/h";
            g.pose().pushMatrix();
            g.pose().scale(2.0F, 2.0F);
            g.text(font, speed, (int) ((width - 10) / 2.0F) - font.width(speed), (int) ((height - 50) / 2.0F),
                    WHITE, true);
            g.pose().popMatrix();
            String hp = "Zustand: " + Math.round(car.healthFraction() * 100) + "%";
            g.text(font, hp, width - 10 - font.width(hp), height - 30,
                    car.healthFraction() < 0.3F ? 0xFFFF5050 : 0xFFB0B0B0, true);
        }

        Minimap.render(g, mc, player, 8, height - 8 - Minimap.SIZE);
    }
}
