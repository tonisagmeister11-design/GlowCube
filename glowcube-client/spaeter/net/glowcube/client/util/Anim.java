package net.glowcube.client.util;

/**
 * Ein Wert, der seinem Ziel weich folgt. Rechnet mit echter Zeit, nicht mit
 * Ticks - dadurch laeuft das GUI bei jeder Framerate gleich schnell.
 */
public final class Anim {
    private float value;
    private float target;
    private final float speed;
    private long last = System.nanoTime();

    public Anim(float start, float speed) {
        this.value = start;
        this.target = start;
        this.speed = speed;
    }

    public void target(float target) {
        this.target = target;
    }

    public void snap(float value) {
        this.value = value;
        this.target = value;
    }

    public float value() {
        long now = System.nanoTime();
        float delta = (now - last) / 1_000_000_000.0f;
        last = now;
        // Framerate-unabhaengiges Nachziehen.
        float factor = 1.0f - (float) Math.exp(-speed * Math.min(delta, 0.1f));
        value += (target - value) * factor;
        if (Math.abs(target - value) < 0.001f) {
            value = target;
        }
        return value;
    }

    public float target() {
        return target;
    }

    public static float easeOut(float t) {
        float p = Math.max(0.0f, Math.min(1.0f, t));
        return 1.0f - (1.0f - p) * (1.0f - p) * (1.0f - p);
    }
}
