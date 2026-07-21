package com.andresblue.threatdirector.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NdjsonTelemetryRecorderTest {
    private static final Instant FIXED_TIME = Instant.parse("2026-07-21T12:34:56Z");

    @TempDir
    Path temporaryDirectory;

    @Test
    void disabledRecorderNeverCreatesAFile() {
        Path output = temporaryDirectory.resolve("telemetry.ndjson");
        TelemetryRecorder recorder =
                TelemetryRecorders.localNdjson(false, output, 2, 1);

        assertEquals(TelemetryRecordResult.DISABLED, recorder.record(event("encounter.started", 1)));
        assertEquals(0, recorder.flush());
        recorder.close();

        assertFalse(Files.exists(output));
        assertFalse(recorder.snapshot().enabled());
    }

    @Test
    void boundedQueueDropsNewestEventWithoutBlocking() {
        NdjsonTelemetryRecorder recorder =
                new NdjsonTelemetryRecorder(temporaryDirectory.resolve("metrics.ndjson"), 2, 1);

        assertEquals(TelemetryRecordResult.ENQUEUED, recorder.record(event("test.event", 1)));
        assertEquals(TelemetryRecordResult.ENQUEUED, recorder.record(event("test.event", 2)));
        assertEquals(TelemetryRecordResult.QUEUE_FULL, recorder.record(event("test.event", 3)));

        TelemetrySnapshot snapshot = recorder.snapshot();
        assertEquals(2, snapshot.queued());
        assertEquals(2, snapshot.accepted());
        assertEquals(1, snapshot.droppedQueueFull());
    }

    @Test
    void flushWritesValidNdjsonInConfiguredBatches() throws Exception {
        Path output = temporaryDirectory.resolve("metrics.ndjson");
        NdjsonTelemetryRecorder recorder = new NdjsonTelemetryRecorder(output, 3, 2);
        recorder.record(event("encounter.phase", 1));
        recorder.record(event("encounter.phase", 2));
        recorder.record(event("encounter.phase", 3));

        assertEquals(2, recorder.flush());
        assertEquals(1, recorder.snapshot().queued());
        assertEquals(1, recorder.flush());

        List<String> lines = Files.readAllLines(output, StandardCharsets.UTF_8);
        assertEquals(3, lines.size());
        JsonObject first = JsonParser.parseString(lines.get(0)).getAsJsonObject();
        assertEquals(1, first.get("schemaVersion").getAsInt());
        assertEquals(FIXED_TIME.toString(), first.get("recordedAt").getAsString());
        assertEquals("encounter.phase", first.get("eventType").getAsString());
        assertEquals(1, first.getAsJsonObject("attributes").get("sequence").getAsInt());
        assertEquals(3, recorder.snapshot().written());
    }

    @Test
    void closeBestEffortDrainsAndRejectsLaterEvents() throws Exception {
        Path output = temporaryDirectory.resolve("metrics.ndjson");
        NdjsonTelemetryRecorder recorder = new NdjsonTelemetryRecorder(output, 4, 2);
        recorder.record(event("encounter.finished", 1));
        recorder.record(event("encounter.finished", 2));
        recorder.record(event("encounter.finished", 3));

        recorder.close();

        assertEquals(3, Files.readAllLines(output, StandardCharsets.UTF_8).size());
        assertEquals(TelemetryRecordResult.CLOSED, recorder.record(event("late.event", 4)));
        assertTrue(recorder.snapshot().closed());
        assertEquals(0, recorder.snapshot().queued());
    }

    @Test
    void ioFailureIsReportedAndDoesNotEscape() throws Exception {
        Path parentThatIsAFile = temporaryDirectory.resolve("not-a-directory");
        Files.writeString(parentThatIsAFile, "block", StandardCharsets.UTF_8);
        NdjsonTelemetryRecorder recorder =
                new NdjsonTelemetryRecorder(parentThatIsAFile.resolve("metrics.ndjson"), 2, 1);
        recorder.record(event("encounter.failed", 1));

        assertEquals(0, recorder.flush());

        TelemetrySnapshot snapshot = recorder.snapshot();
        assertEquals(1, snapshot.writeFailures());
        assertEquals(1, snapshot.queued());
        assertFalse(snapshot.lastError().isBlank());
        recorder.close();
    }

    @Test
    void serializationFailureIsContained() {
        NdjsonTelemetryRecorder recorder =
                new NdjsonTelemetryRecorder(temporaryDirectory.resolve("metrics.ndjson"), 2, 1);
        TelemetryEvent invalidForDefaultGson = new TelemetryEvent(
                FIXED_TIME, "numeric.invalid", Map.of("value", Double.NaN));

        assertEquals(
                TelemetryRecordResult.SERIALIZATION_FAILED,
                recorder.record(invalidForDefaultGson));
        assertEquals(1, recorder.snapshot().serializationFailures());
        assertEquals(0, recorder.snapshot().queued());
    }

    @Test
    void eventCopiesTopLevelAttributesBeforeEnqueue() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("sequence", 1);
        TelemetryEvent event = new TelemetryEvent(FIXED_TIME, "copy.test", attributes);

        attributes.put("sequence", 99);

        assertEquals(1, event.attributes().get("sequence"));
    }

    private static TelemetryEvent event(String type, int sequence) {
        return new TelemetryEvent(FIXED_TIME, type, Map.of("sequence", sequence));
    }
}
