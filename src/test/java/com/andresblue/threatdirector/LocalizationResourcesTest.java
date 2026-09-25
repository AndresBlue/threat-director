package com.andresblue.threatdirector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class LocalizationResourcesTest {
    private static final String[] SPANISH_LOCALES = {
            "es_ar", "es_cl", "es_ec", "es_es", "es_mx", "es_uy", "es_ve"
    };

    @Test
    void englishAndEveryMinecraftSpanishLocaleExposeTheSameTranslationSurface() {
        JsonObject english = language("en_us");
        Set<String> englishKeys = english.keySet();
        assertEquals(78, englishKeys.size());

        for (String locale : SPANISH_LOCALES) {
            JsonObject spanish = language(locale);
            assertEquals(englishKeys, spanish.keySet(), "Key surface differs for " + locale);
            for (String key : englishKeys) {
                assertEquals(
                        placeholderCount(english.get(key).getAsString()),
                        placeholderCount(spanish.get(key).getAsString()),
                        "Placeholder count differs for " + locale + ":" + key);
            }
        }
    }

    @Test
    void gameplayMessagesHaveActualSpanishTranslations() {
        JsonObject english = language("en_us");
        JsonObject spanish = language("es_es");
        assertNotEquals(
                english.get("message.threatdirector.blood_moon.sleep_blocked").getAsString(),
                spanish.get("message.threatdirector.blood_moon.sleep_blocked").getAsString());
        assertNotEquals(
                english.get("command.threatdirector.threat.set").getAsString(),
                spanish.get("command.threatdirector.threat.set").getAsString());
        assertNotEquals(
                english.get("value.threatdirector.encounter_state.engaging").getAsString(),
                spanish.get("value.threatdirector.encounter_state.engaging").getAsString());
    }

    private static JsonObject language(String locale) {
        String path = "/assets/threatdirector/lang/" + locale + ".json";
        var stream = LocalizationResourcesTest.class.getResourceAsStream(path);
        assertNotNull(stream, "Missing language resource " + path);
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (java.io.IOException exception) {
            throw new AssertionError("Could not read " + path, exception);
        }
    }

    private static int placeholderCount(String value) {
        int count = 0;
        for (int index = 0; index + 1 < value.length(); index++) {
            if (value.charAt(index) == '%' && value.charAt(index + 1) == 's') {
                count++;
                index++;
            }
        }
        return count;
    }
}
