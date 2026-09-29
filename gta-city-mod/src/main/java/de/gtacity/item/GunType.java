package de.gtacity.item;

/** Stats of every gun. Times are in ticks (20 ticks = 1 second), spread in degrees. */
public enum GunType {
    //          name              dmg  delay mag reload spread pellets range auto  ammo
    PISTOL("Glock 18", 6.0F, 6, 17, 30, 1.5F, 1, 70, false, AmmoKind.PISTOL, 1.7F),
    SMG("MP5", 4.5F, 2, 30, 40, 3.5F, 1, 60, true, AmmoKind.SMG, 2.0F),
    CARBINE("M4A1", 6.5F, 3, 30, 45, 2.0F, 1, 100, true, AmmoKind.RIFLE, 1.3F),
    SHOTGUN("M1014 Schrotflinte", 3.5F, 14, 8, 60, 9.0F, 8, 28, false, AmmoKind.SHELLS, 0.9F),
    SNIPER("AWM Scharfschützengewehr", 32.0F, 30, 5, 60, 0.05F, 1, 220, false, AmmoKind.SNIPER, 0.6F),
    MINIGUN("Minigun", 4.0F, 1, 200, 100, 5.0F, 1, 80, true, AmmoKind.RIFLE, 1.6F),
    RPG("RPG-7", 0.0F, 40, 1, 50, 0.0F, 1, 0, false, AmmoKind.ROCKET, 0.5F),
    DEAGLE("Desert Eagle", 11.0F, 10, 7, 36, 1.2F, 1, 85, false, AmmoKind.PISTOL, 1.2F),
    AK47("AK-47", 7.5F, 3, 30, 48, 2.8F, 1, 95, true, AmmoKind.RIFLE, 1.1F);

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
