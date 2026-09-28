package de.gtacity.item;

/** Stats of every gun. Times are in ticks (20 ticks = 1 second), spread in degrees. */
public enum GunType {
    //          name              dmg  delay mag reload spread pellets range auto  ammo
    PISTOL("Pistole", 6.0F, 6, 12, 30, 1.5F, 1, 70, false, AmmoKind.PISTOL, 1.7F),
    SMG("Micro-SMG", 4.0F, 2, 30, 40, 4.0F, 1, 55, true, AmmoKind.SMG, 2.0F),
    CARBINE("Karabiner", 6.5F, 3, 30, 45, 2.0F, 1, 100, true, AmmoKind.RIFLE, 1.3F),
    SHOTGUN("Pumpgun", 3.5F, 18, 8, 60, 9.0F, 8, 28, false, AmmoKind.SHELLS, 0.9F),
    SNIPER("Scharfschützengewehr", 32.0F, 30, 5, 60, 0.05F, 1, 220, false, AmmoKind.SNIPER, 0.6F),
    MINIGUN("Minigun", 4.0F, 1, 200, 100, 5.0F, 1, 80, true, AmmoKind.RIFLE, 1.6F),
    RPG("Raketenwerfer", 0.0F, 40, 1, 50, 0.0F, 1, 0, false, AmmoKind.ROCKET, 0.5F);

    public final String label;
    public final float damage;
    public final int fireDelay;
    public final int magazine;
    public final int reloadTicks;
    public final float spread;
    public final int pellets;
    public final int range;
    public final boolean automatic;
    public final AmmoKind ammo;
    public final float pitch;

    GunType(String label, float damage, int fireDelay, int magazine, int reloadTicks, float spread, int pellets,
            int range, boolean automatic, AmmoKind ammo, float pitch) {
        this.label = label;
        this.damage = damage;
        this.fireDelay = fireDelay;
        this.magazine = magazine;
        this.reloadTicks = reloadTicks;
        this.spread = spread;
        this.pellets = pellets;
        this.range = range;
        this.automatic = automatic;
        this.ammo = ammo;
        this.pitch = pitch;
    }

    public enum AmmoKind {
        PISTOL, SMG, RIFLE, SHELLS, SNIPER, ROCKET
    }
}
