package domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

public final class RepositoryState {

  final RepositoryIdentifier identifier;
  final Instant creationDate;
  final Set<String> targetSummaryAudiences;
  // GitHub release id:s
  final Set<Long> seenReleases;

  public RepositoryState(RepositoryIdentifier identifier, Instant creationDate, Set<String> targetSummaryAudiences) {
    this.identifier = identifier;
    this.creationDate = creationDate;
    this.targetSummaryAudiences = targetSummaryAudiences;
    this.seenReleases = new HashSet<>();
  }
}
