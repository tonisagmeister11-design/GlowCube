package de.theboys.client.render;

import de.theboys.TheBoys;
import de.theboys.power.Power;
import de.theboys.power.PowerData;
import net.minecraft.core.ClientAsset;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** The skin (and cape) a supe wears while the suit is on. */
public final class SuitSkins {
	private SuitSkins() {
	}

	/** The suit skin, or null when this player wears no suit. */
	public static PlayerSkin suit(PowerData data, PlayerSkin original) {
		Power power = data.power();
		if (power == Power.NONE || !data.suit() || original == null) return null;
		ClientAsset.ResourceTexture body = new ClientAsset.ResourceTexture(TheBoys.id("suit/" + power.id()), TheBoys.id("textures/entity/suit/" + power.id() + ".png"));
		ClientAsset.Texture cape = original.cape();
		if (hasCape(power)) {
			String name = power.id() + "_cape";
			cape = new ClientAsset.ResourceTexture(TheBoys.id(name), TheBoys.id("textures/entity/" + name + ".png"));
		}
		// the women of the Seven get the slim model
		PlayerModelType model = power == Power.STARLIGHT || power == Power.STORMFRONT ? PlayerModelType.SLIM : PlayerModelType.WIDE;
		return new PlayerSkin(body, cape, original.elytra(), model, true);
	}

	public static boolean hasCape(Power power) {
		return power == Power.HOMELANDER || power == Power.STORMFRONT || power == Power.STARLIGHT || power == Power.BLACK_ADAM;
	}
}
