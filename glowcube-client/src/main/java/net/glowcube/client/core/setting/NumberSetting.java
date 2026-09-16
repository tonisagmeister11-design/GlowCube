package net.glowcube.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** Ein Schieberegler. Rastet auf `step` ein, damit im GUI runde Werte stehen. */
public final class NumberSetting extends Setting {
    private final double min;
    private final double max;
    private final double step;
    private double value;

    public NumberSetting(String name, String description, double value, double min, double max, double step) {
        super(name, description);
        this.min = min;
        this.max = max;
        this.step = step;
        this.value = value;
    }

    public double get() {
        return value;
    }

    public float getFloat() {
        return (float) value;
    }

    public int getInt() {
        return (int) Math.round(value);
    }

    public void set(double raw) {
        double clamped = Math.max(min, Math.min(max, raw));
        this.value = Math.round(clamped / step) * step;
    }

    /** 0..1 - so haelt das GUI den Regler, ohne min/max zu kennen. */
    public double ratio() {
        if (max - min == 0.0) {
            return 0.0;
        }
        return (value - min) / (max - min);
    }

    public void setRatio(double ratio) {
        set(min + (max - min) * Math.max(0.0, Math.min(1.0, ratio)));
    }

    public String display() {
        if (step >= 1.0) {
            return String.valueOf(getInt());
        }
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    @Override
    public JsonElement save() {
        return new JsonPrimitive(value);
    }

    @Override
    public void load(JsonElement json) {
        if (json != null && json.isJsonPrimitive()) {
            set(json.getAsDouble());
        }
    }
}
