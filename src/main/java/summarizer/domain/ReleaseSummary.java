package summarizer.domain;

import java.time.Instant;

public record ReleaseSummary(String version, long githubReleaseId, Instant creationDate, String summary) {
}
