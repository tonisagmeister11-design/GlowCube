package de.theboys.power;

import de.theboys.TheBoys;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.Entity;

public final class PowerAttachments {
	public static final AttachmentType<PowerData> POWER = AttachmentRegistry.create(TheBoys.id("power"), b -> b
			.initializer(() -> PowerData.NONE)
			.persistent(PowerData.CODEC)
			.copyOnDeath()
			.syncWith(PowerData.STREAM_CODEC, AttachmentSyncPredicate.all()));

	public static final AttachmentType<ActiveState> ACTIVE = AttachmentRegistry.create(TheBoys.id("active"), b -> b
			.initializer(() -> ActiveState.IDLE)
			.syncWith(ActiveState.STREAM_CODEC, AttachmentSyncPredicate.all()));

	private PowerAttachments() {
	}

	public static void init() {
	}

	public static PowerData power(Entity e) {
		PowerData d = e.getAttached(POWER);
		return d == null ? PowerData.NONE : d;
	}

	public static Power powerOf(Entity e) {
		return power(e).power();
	}

	public static ActiveState active(Entity e) {
		ActiveState s = e.getAttached(ACTIVE);
		return s == null ? ActiveState.IDLE : s;
	}

	/** Only writes (and therefore syncs) when something actually changed. */
	public static void setActive(Entity e, ActiveState s) {
		if (!s.equals(active(e))) {
			e.setAttached(ACTIVE, s);
		}
	}
}
