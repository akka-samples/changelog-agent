package domain;

import java.time.Instant;
import java.util.Set;

public sealed interface RepositoryEvent {

  record Created(Instant creationDate, Set<String> summaryTargetAudience) implements RepositoryEvent {}
}
