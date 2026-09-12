/*
 * Decompiled with CFR 0.152.
 */
package de.adminfield.nms;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.datafixers.util.Pair;
import de.adminfield.AdminFieldPlugin;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class PlayerDisguise
implements Listener {
    private static final int STEVE_INDEX = 15;
    private static final int DEFAULT_SKIN_COUNT = 18;
    private final AdminFieldPlugin plugin;
    private final Map<UUID, Disguise> disguises = new HashMap<UUID, Disguise>();

    public PlayerDisguise(AdminFieldPlugin adminFieldPlugin) {
        this.plugin = adminFieldPlugin;
        Bukkit.getLogger().fine("PlayerDisguise bereit fuer " + String.valueOf(EntityTypes.PLAYER));
    }

    public void apply(LivingEntity livingEntity, String string, PlayerProfile playerProfile) {
        boolean bl = playerProfile != null && playerProfile.hasTextures();
        UUID uUID = bl ? UUID.randomUUID() : PlayerDisguise.steveUuid();
        GameProfile gameProfile = new GameProfile(uUID, PlayerDisguise.clip(string));
        if (bl) {
            for (ProfileProperty profileProperty : playerProfile.getProperties()) {
                if (!"textures".equals(profileProperty.getName())) continue;
                gameProfile.properties().put((Object)"textures", (Object)new Property("textures", profileProperty.getValue(), profileProperty.getSignature()));
            }
        }
        Disguise disguise = new Disguise(uUID, gameProfile);
        this.disguises.put(livingEntity.getUniqueId(), disguise);
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            for (Player player : livingEntity.getWorld().getPlayers()) {
                if (!(player.getLocation().distanceSquared(livingEntity.getLocation()) <= 16384.0)) continue;
                this.sendTo(player, livingEntity, disguise);
            }
        }, 2L);
    }

    public void remove(Entity entity) {
        Disguise disguise = this.disguises.remove(entity.getUniqueId());
        if (disguise == null) {
            return;
        }
        ClientboundPlayerInfoRemovePacket clientboundPlayerInfoRemovePacket = new ClientboundPlayerInfoRemovePacket(List.of(disguise.profileId()));
        for (Player player : Bukkit.getOnlinePlayers()) {
            PlayerDisguise.send(player, clientboundPlayerInfoRemovePacket);
        }
    }

    public boolean isDisguised(Entity entity) {
        return entity != null && this.disguises.containsKey(entity.getUniqueId());
    }

    @EventHandler
    public void onTrack(PlayerTrackEntityEvent playerTrackEntityEvent) {
        Entity entity;
        Disguise disguise = this.disguises.get(playerTrackEntityEvent.getEntity().getUniqueId());
        if (disguise == null || !((entity = playerTrackEntityEvent.getEntity()) instanceof LivingEntity)) {
            return;
        }
        LivingEntity livingEntity = (LivingEntity)entity;
        entity = playerTrackEntityEvent.getPlayer();
        Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> this.lambda$onTrack$0((Player)entity, livingEntity, disguise), 1L);
    }

    private void sendTo(Player player, LivingEntity livingEntity, Disguise disguise) {
        if (!(player.isOnline() && livingEntity.isValid() && player.getWorld().equals((Object)livingEntity.getWorld()))) {
            return;
        }
        if (!this.disguises.containsKey(livingEntity.getUniqueId())) {
            return;
        }
        try {
            net.minecraft.world.entity.Entity entity = ((CraftEntity)livingEntity).getHandle();
            int n = entity.getId();
            Location location = livingEntity.getLocation();
            ClientboundPlayerInfoUpdatePacket.Entry entry = new ClientboundPlayerInfoUpdatePacket.Entry(disguise.profileId(), disguise.profile(), false, 0, GameType.SURVIVAL, null, true, 0, null);
            PlayerDisguise.send(player, new ClientboundPlayerInfoUpdatePacket(EnumSet.of(ClientboundPlayerInfoUpdatePacket.Action.ADD_PLAYER, ClientboundPlayerInfoUpdatePacket.Action.UPDATE_LISTED), List.of(entry)));
            PlayerDisguise.send(player, new ClientboundRemoveEntitiesPacket(new int[]{n}));
            PlayerDisguise.send(player, new ClientboundAddEntityPacket(n, disguise.profileId(), location.getX(), location.getY(), location.getZ(), location.getPitch(), location.getYaw(), EntityTypes.PLAYER, 0, Vec3.ZERO, (double)location.getYaw()));
            EntityEquipment entityEquipment = livingEntity.getEquipment();
            if (entityEquipment != null) {
                ArrayList<Pair> arrayList = new ArrayList<Pair>();
                arrayList.add(Pair.of((Object)EquipmentSlot.HEAD, (Object)PlayerDisguise.nms(entityEquipment.getHelmet())));
                arrayList.add(Pair.of((Object)EquipmentSlot.CHEST, (Object)PlayerDisguise.nms(entityEquipment.getChestplate())));
                arrayList.add(Pair.of((Object)EquipmentSlot.LEGS, (Object)PlayerDisguise.nms(entityEquipment.getLeggings())));
                arrayList.add(Pair.of((Object)EquipmentSlot.FEET, (Object)PlayerDisguise.nms(entityEquipment.getBoots())));
                arrayList.add(Pair.of((Object)EquipmentSlot.MAINHAND, (Object)PlayerDisguise.nms(entityEquipment.getItemInMainHand())));
                arrayList.add(Pair.of((Object)EquipmentSlot.OFFHAND, (Object)PlayerDisguise.nms(entityEquipment.getItemInOffHand())));
                PlayerDisguise.send(player, new ClientboundSetEquipmentPacket(n, arrayList));
            }
            PlayerDisguise.send(player, new ClientboundRotateHeadPacket(entity, (byte)(location.getYaw() * 256.0f / 360.0f)));
        }
        catch (Throwable throwable) {
            this.plugin.getLogger().warning("Attentaeter-Verkleidung fehlgeschlagen (" + throwable.getClass().getSimpleName() + ") - bleibt als normaler Mob sichtbar.");
        }
    }

    private static net.minecraft.world.item.ItemStack nms(ItemStack itemStack) {
        return CraftItemStack.asNMSCopy((ItemStack)(itemStack == null ? new ItemStack(Material.AIR) : itemStack));
    }

    private static void send(Player player, Packet<?> packet) {
        ServerPlayer serverPlayer = ((CraftPlayer)player).getHandle();
        if (serverPlayer.connection != null) {
            serverPlayer.connection.send(packet);
        }
    }

    private static UUID steveUuid() {
        for (int i = 0; i < 10000; ++i) {
            UUID uUID = UUID.randomUUID();
            if (Math.floorMod(uUID.hashCode(), 18) != 15) continue;
            return uUID;
        }
        return UUID.randomUUID();
    }

    private static String clip(String string) {
        String string2 = string == null ? "Attentaeter" : string.trim();
        return string2.length() <= 16 ? string2 : string2.substring(0, 16);
    }

    private /* synthetic */ void lambda$onTrack$0(Player player, LivingEntity livingEntity, Disguise disguise) {
        this.sendTo(player, livingEntity, disguise);
    }

    private record Disguise(UUID profileId, GameProfile profile) {
    }
}

