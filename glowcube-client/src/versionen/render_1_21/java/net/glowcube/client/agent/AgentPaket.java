package net.glowcube.client.agent;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Die Nachricht zwischen GlowCube und dem GlowCube-Agent-Plugin auf einem
 * Server. Ein einziger Text mit Semikolons, als UTF-8 mit vorangestellter
 * Laenge (VarInt) - so kann ihn das Plugin ohne Minecraft-Klassen lesen.
 *
 * <p>Zum Server: {@code start;AUFTRAG;art;tempo;abbau;xray;chunks},
 * {@code werte;AUFTRAG;tempo;abbau;xray;chunks}, {@code zurueck;AUFTRAG},
 * {@code alle}. Vom Server: {@code aus;AUFTRAG} (Agent ist fertig).
 */
public record AgentPaket(String text) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AgentPaket> TYP =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath("glowcube", "agent"));
    public static final StreamCodec<io.netty.buffer.ByteBuf, AgentPaket> CODEC =
            ByteBufCodecs.STRING_UTF8.map(AgentPaket::new, AgentPaket::text);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYP;
    }
}
