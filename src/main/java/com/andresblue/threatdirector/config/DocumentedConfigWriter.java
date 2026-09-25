package com.andresblue.threatdirector.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Map;

/** Writes strict JSON values with JSON5 comments before every configurable property. */
final class DocumentedConfigWriter {
    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    private DocumentedConfigWriter() {
    }

    static String write(ThreatDirectorConfig config) {
        StringBuilder output = new StringBuilder(24_000);
        output.append("// Threat Director 2.0 - fully documented server configuration.\n")
                .append("// Probabilities use decimals: 0.25 = 25%. Minecraft runs at 20 ticks per second.\n")
                .append("// GENERAL affects everything; VANILLA works with unmodded clients; MODDED adds extraordinary behavior.\n")
                .append("// Run /threatdirector debug config reload after editing; assignment/startup-only options are noted in the guide.\n");
        appendObject(output, GSON.toJsonTree(config).getAsJsonObject(), "", 0);
        return output.append(System.lineSeparator()).toString();
    }

    private static void appendObject(
            StringBuilder output, JsonObject object, String parentPath, int depth) {
        output.append("{");
        if (!object.entrySet().isEmpty()) {
            output.append('\n');
        }
        int index = 0;
        for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
            String path = parentPath.isEmpty() ? entry.getKey() : parentPath + "." + entry.getKey();
            indent(output, depth + 1).append("// ").append(ConfigCommentCatalog.describe(path)).append('\n');
            indent(output, depth + 1).append(GSON.toJson(entry.getKey())).append(": ");
            appendValue(output, entry.getValue(), path, depth + 1);
            if (++index < object.entrySet().size()) {
                output.append(',');
            }
            output.append('\n');
        }
        if (!object.entrySet().isEmpty()) {
            indent(output, depth);
        }
        output.append('}');
    }

    private static void appendValue(
            StringBuilder output, JsonElement value, String path, int depth) {
        if (value.isJsonObject()) {
            appendObject(output, value.getAsJsonObject(), path, depth);
        } else if (value.isJsonArray()) {
            appendArray(output, value.getAsJsonArray(), depth);
        } else {
            output.append(GSON.toJson(value));
        }
    }

    private static void appendArray(StringBuilder output, JsonArray array, int depth) {
        if (array.isEmpty()) {
            output.append("[]");
            return;
        }
        output.append("[\n");
        for (int index = 0; index < array.size(); index++) {
            indent(output, depth + 1).append(GSON.toJson(array.get(index)));
            if (index + 1 < array.size()) {
                output.append(',');
            }
            output.append('\n');
        }
        indent(output, depth).append(']');
    }

    private static StringBuilder indent(StringBuilder output, int depth) {
        return output.append("  ".repeat(Math.max(0, depth)));
    }
}
