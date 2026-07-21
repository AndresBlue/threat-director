package com.andresblue.threatdirector.pattern;

public record PatternExecutionResult(Status status, String reason) {
    public enum Status { RUNNING, SUCCESS, CANCELLED }
}
