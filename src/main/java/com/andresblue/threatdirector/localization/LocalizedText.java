package com.andresblue.threatdirector.localization;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Creates client-localized text with readable English fallbacks for unmodded clients. */
public final class LocalizedText {
    private static final String ENGLISH_RESOURCE =
            "/assets/threatdirector/lang/en_us.json";
    private static final Map<String, String> ENGLISH = loadEnglishFallbacks();

    private LocalizedText() {
    }

    public static MutableText of(String key, Object... arguments) {
        return Text.translatableWithFallback(key, ENGLISH.getOrDefault(key, key), arguments);
    }

    private static Map<String, String> loadEnglishFallbacks() {
        try (var stream = LocalizedText.class.getResourceAsStream(ENGLISH_RESOURCE)) {
            if (stream == null) {
                return Map.of();
            }
            try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                Map<String, String> translations = new HashMap<>();
                for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                    if (entry.getValue().isJsonPrimitive()
                            && entry.getValue().getAsJsonPrimitive().isString()) {
                        translations.put(entry.getKey(), entry.getValue().getAsString());
                    }
                }
                return Map.copyOf(translations);
            }
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
