package summarizer.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RepositoryState {

  final Instant creationDate;
  // GitHub release id:s
  final Set<Long> seenReleases;
  final List<ReleaseSummary> summaries;
  final Optional<String> gitHubApiToken;

  public RepositoryState(Instant creationDate, Optional<String> gitHubApiToken) {
    this.creationDate = creationDate;
    this.seenReleases = new HashSet<>();
    this.summaries = new ArrayList<>();
    this.gitHubApiToken = gitHubApiToken;
  }

  public Optional<Long> getLatestSeenRelease() {
    return seenReleases.stream().max(Long::compareTo);
  }

  public RepositoryState addSummary(RepositoryEvent.SummaryAdded summaryAdded) {
    // FIXME limit the number we keep around
    summaries.addFirst(summaryAdded.summary());
    seenReleases.add(summaryAdded.summary().githubReleaseId());
    return this;
  }

  public List<ReleaseSummary> getSummaries() {
    return summaries; // defensive copy
  }

  /**
   * @return the specific GitHub api token to use for this repository if there is one
   */
  public Optional<String> getGitHubApiToken() {
    return gitHubApiToken;
  }
}
