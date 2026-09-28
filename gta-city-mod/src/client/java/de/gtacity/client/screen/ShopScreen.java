package de.gtacity.client.screen;

import de.gtacity.gameplay.Economy;
import de.gtacity.network.Payloads;
import de.gtacity.registry.ModAttachments;
import de.gtacity.shop.ShopCatalog;
import de.gtacity.shop.ShopType;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Buy menu of Ammu-Nation, 24/7 and the car dealer. */
public class ShopScreen extends Screen {
    private static final int ROW = 22;
    private static final int COL_WIDTH = 190;

    private final ShopType type;
    private final List<ShopCatalog.Offer> offers;
    private int left;
    private int top;
    private int rowsPerCol;

    public ShopScreen(ShopType type) {
        super(Component.literal(type.title));
        this.type = type;
        this.offers = ShopCatalog.offers(type);
    }

    @Override
    protected void init() {
        int columns = offers.size() > 9 ? 2 : 1;
        rowsPerCol = (offers.size() + columns - 1) / columns;
        int w = columns * COL_WIDTH;
        left = (width - w) / 2;
        top = Math.max(40, (height - rowsPerCol * ROW) / 2);
        for (int i = 0; i < offers.size(); i++) {
            int col = i / rowsPerCol, row = i % rowsPerCol;
            int x = left + col * COL_WIDTH;
            int y = top + row * ROW;
            final int index = i;
            addRenderableWidget(Button.builder(Component.literal("Kaufen"),
                            b -> ClientPlayNetworking.send(new Payloads.Buy(type.ordinal(), index)))
                    .bounds(x + COL_WIDTH - 58, y, 52, 20).build());
        }
        addRenderableWidget(Button.builder(Component.literal("Schließen"), b -> onClose())
                .bounds(width / 2 - 50, top + rowsPerCol * ROW + 8, 100, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, top - 30, 0xFFFFD040);
        Long money = minecraft.player == null ? null : minecraft.player.getAttached(ModAttachments.MONEY);
        g.drawCenteredString(font, "Dein Geld: " + Economy.format(money == null ? 0 : money), width / 2, top - 18,
                0xFF6BD36B);
        for (int i = 0; i < offers.size(); i++) {
            ShopCatalog.Offer offer = offers.get(i);
            int col = i / rowsPerCol, row = i % rowsPerCol;
            int x = left + col * COL_WIDTH;
            int y = top + row * ROW;
            g.fill(x, y, x + COL_WIDTH - 60, y + 20, 0x90000000);
            g.renderItem(offer.icon(), x + 2, y + 2);
            g.drawString(font, offer.name(), x + 22, y + 2, 0xFFFFFFFF, false);
            g.drawString(font, Economy.format(offer.price()), x + 22, y + 11, 0xFF6BD36B, false);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
