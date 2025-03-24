package summarizer.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public sealed interface RepositoryEvent {

  record Created(Instant creationDate, Optional<String> gitApiHubToken) implements RepositoryEvent {}
  record SummaryAdded(ReleaseSummary summary) implements RepositoryEvent {}
}
