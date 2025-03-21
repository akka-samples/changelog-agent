package domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class RepositoryState {

  final Instant creationDate;
  final Set<String> targetSummaryAudiences;
  // GitHub release id:s
  final Set<Long> seenReleases;
  final List<ReleaseSummary> summaries;

  public RepositoryState(Instant creationDate, Set<String> targetSummaryAudiences) {
    this.creationDate = creationDate;
    this.targetSummaryAudiences = targetSummaryAudiences;
    this.seenReleases = new HashSet<>();
    this.summaries = new ArrayList<>();
  }

  public Optional<Long> getLatestSeenRelease() {
    return seenReleases.stream().max(Long::compareTo);
  }

  public RepositoryState addSummary(RepositoryEvent.SummaryAdded summaryAdded) {
    summaries.addFirst(summaryAdded.summary());
    seenReleases.add(summaryAdded.summary().githubReleaseId());
    return this;
  }
}
