package domain;

import java.time.Instant;

public record ReleaseSummary(String version, long githubReleaseId, String targetAudience, Instant creationDate, String summary) {
}
