package domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public final class RepositoryState {

  final Instant creationDate;
  final Set<String> targetSummaryAudiences;
  // GitHub release id:s
  final Set<Long> seenReleases;

  public RepositoryState(Instant creationDate, Set<String> targetSummaryAudiences) {
    this.creationDate = creationDate;
    this.targetSummaryAudiences = targetSummaryAudiences;
    this.seenReleases = new HashSet<>();
  }

  public Optional<Long> getLatestSeenRelease() {
    return seenReleases.stream().max(Long::compareTo);
  }

}
