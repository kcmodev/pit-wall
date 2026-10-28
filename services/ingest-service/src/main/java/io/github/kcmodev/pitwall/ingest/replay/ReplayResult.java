package io.github.kcmodev.pitwall.ingest.replay;

public record ReplayResult(int sessionKey,
                           int totalLaps,
                           int lapsPublished,
                           int lapsFailed) {}
