package net.messer.augment;
import java.util.Locale;
public enum BodySlot {
    R_EYE("Right eye"), L_EYE("Left eye"), HEAD("Head"), R_HAND("Right hand"), L_HAND("Left hand"),
    R_ARM("Right arm"), L_ARM("Left arm"), R_LEG("Right leg"), L_LEG("Left leg"), TORSO("Torso");
    public final String label;
    BodySlot(String label) { this.label=label; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
}
