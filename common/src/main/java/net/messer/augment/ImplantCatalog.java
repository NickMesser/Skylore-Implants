package net.messer.augment;
import com.google.gson.Gson;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
public final class ImplantCatalog {
    public record Definition(String id, String name, String description, List<String> slots, String source) {
        public boolean fits(BodySlot slot) { return slots.contains(slot.id()); }
        public boolean placeholder() { return slots.isEmpty(); }
    }
    public static final List<Definition> ALL;
    static {
        try (var in = ImplantCatalog.class.getResourceAsStream("/assets/augment/implants.json")) {
            ALL=List.of(new Gson().fromJson(new InputStreamReader(Objects.requireNonNull(in),StandardCharsets.UTF_8),Definition[].class));
        } catch(IOException e) { throw new ExceptionInInitializerError(e); }
    }
    public static Definition find(String id) { return ALL.stream().filter(d->d.id().equals(id)).findFirst().orElse(null); }
}
