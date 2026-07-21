package com.andresblue.threatdirector.telemetry;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Bounded, filesystem-only NDJSON recorder.
 *
 * <p>Event production performs serialization and a non-blocking queue offer, but never disk I/O.
 * The owning runtime decides when to call {@link #flush()} under its performance budget.
 */
public final class NdjsonTelemetryRecorder implements TelemetryRecorder {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON =
            new GsonBuilder().disableHtmlEscaping().create();
    private static final OpenOption[] APPEND_OPTIONS = {
        StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND
    };

    private final Path outputFile;
    private final int flushBatchSize;
    private final ArrayBlockingQueue<String> queue;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile boolean closed;
    private long accepted;
    private long written;
    private long droppedQueueFull;
    private long serializationFailures;
    private long writeFailures;
    private String lastError = "";

    public NdjsonTelemetryRecorder(Path outputFile, int queueCapacity, int flushBatchSize) {
        this.outputFile = Objects.requireNonNull(outputFile, "outputFile")
                .toAbsolutePath()
                .normalize();
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (flushBatchSize <= 0 || flushBatchSize > queueCapacity) {
            throw new IllegalArgumentException(
                    "flushBatchSize must be positive and <= queueCapacity");
        }
        this.flushBatchSize = flushBatchSize;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
    }

    public Path outputFile() {
        return outputFile;
    }

    @Override
    public boolean isEnabled() {
        return !closed;
    }

    @Override
    public TelemetryRecordResult record(TelemetryEvent event) {
        Objects.requireNonNull(event, "event");
        if (closed) {
            return TelemetryRecordResult.CLOSED;
        }

        String line;
        try {
            line = serialize(event);
        } catch (RuntimeException error) {
            lock.lock();
            try {
                serializationFailures++;
                lastError = describe(error);
            } finally {
                lock.unlock();
            }
            return TelemetryRecordResult.SERIALIZATION_FAILED;
        }

        lock.lock();
        try {
            if (closed) {
                return TelemetryRecordResult.CLOSED;
            }
            if (!queue.offer(line)) {
                droppedQueueFull++;
                return TelemetryRecordResult.QUEUE_FULL;
            }
            accepted++;
            return TelemetryRecordResult.ENQUEUED;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int flush() {
        lock.lock();
        try {
            return flushOneBatch();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public TelemetrySnapshot snapshot() {
        lock.lock();
        try {
            return new TelemetrySnapshot(
                    !closed,
                    closed,
                    queue.size(),
                    queue.remainingCapacity() + queue.size(),
                    accepted,
                    written,
                    droppedQueueFull,
                    serializationFailures,
                    writeFailures,
                    lastError);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void close() {
        lock.lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
            while (!queue.isEmpty()) {
                if (flushOneBatch() == 0) {
                    break;
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private int flushOneBatch() {
        if (queue.isEmpty()) {
            return 0;
        }
        int count = Math.min(flushBatchSize, queue.size());
        List<String> batch = new ArrayList<>(count);
        int index = 0;
        for (String line : queue) {
            batch.add(line);
            if (++index == count) {
                break;
            }
        }

        StringBuilder payload = new StringBuilder();
        for (String line : batch) {
            payload.append(line).append('\n');
        }
        try {
            Path parent = outputFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    outputFile,
                    payload,
                    StandardCharsets.UTF_8,
                    APPEND_OPTIONS);
        } catch (Exception error) {
            writeFailures++;
            lastError = describe(error);
            return 0;
        }

        for (int ignored = 0; ignored < count; ignored++) {
            queue.remove();
        }
        written += count;
        return count;
    }

    private static String serialize(TelemetryEvent event) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("recordedAt", event.recordedAt().toString());
        root.addProperty("eventType", event.eventType());
        root.add("attributes", GSON.toJsonTree(event.attributes()));
        return GSON.toJson(root);
    }

    private static String describe(Exception error) {
        String message = error.getMessage();
        return error.getClass().getName() + (message == null ? "" : ": " + message);
    }
}
