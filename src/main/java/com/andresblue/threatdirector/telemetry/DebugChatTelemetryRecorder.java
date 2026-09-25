package com.andresblue.threatdirector.telemetry;

import java.util.Objects;

/** Fan-out recorder that keeps local NDJSON telemetry and optional OP chat logging independent. */
public final class DebugChatTelemetryRecorder implements TelemetryRecorder {
    private final TelemetryRecorder delegate;
    private final ChatDebugLogger chat;

    public DebugChatTelemetryRecorder(TelemetryRecorder delegate, ChatDebugLogger chat) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.chat = Objects.requireNonNull(chat, "chat");
    }

    @Override
    public boolean isEnabled() {
        return delegate.isEnabled() || chat.subscriberCount() > 0;
    }

    @Override
    public TelemetryRecordResult record(TelemetryEvent event) {
        TelemetryRecordResult result = delegate.record(event);
        chat.record(event);
        return result;
    }

    @Override
    public int flush() {
        return delegate.flush();
    }

    @Override
    public TelemetrySnapshot snapshot() {
        return delegate.snapshot();
    }

    @Override
    public void close() {
        delegate.close();
        chat.close();
    }
}
