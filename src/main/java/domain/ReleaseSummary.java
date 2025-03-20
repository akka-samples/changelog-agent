package domain;

import java.time.Instant;

public record ReleaseSummary(String version, String targetAudience, Instant creationDate, String summary) {
}
