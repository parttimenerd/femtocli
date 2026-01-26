package me.bechberger.minicli;

/**
 * Public API — keep as record
 */
public record RunResult(String out, String err, int exitCode) {
}