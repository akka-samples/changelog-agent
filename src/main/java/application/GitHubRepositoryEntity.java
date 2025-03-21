package application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import domain.ReleaseSummary;
import domain.RepositoryEvent;
import domain.RepositoryIdentifier;
import domain.RepositoryState;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static akka.Done.done;

@ComponentId("github-repository")
public class GitHubRepositoryEntity extends EventSourcedEntity<RepositoryState, RepositoryEvent> {

  public static String entityIdFor(RepositoryIdentifier repositoryIdentifier) {
    return entityIdFor(repositoryIdentifier.owner(), repositoryIdentifier.repo());
  }
  public static String entityIdFor(String organization, String repository) {
    return organization + "/" + repository;
  }

  public record SetUpRepository(String gitHubOrganization, String repositoryName, Set<String> targetSummaryAudiences) {}

  public record LatestSeenRelease(Optional<Long> id) {}

  public Effect<Done> setUp(SetUpRepository setUpRepository) {
    if (currentState() != null) {
      throw new IllegalStateException("Repository [" + commandContext().entityId() + "] is already previously setup");
    }
    return effects().persist(new RepositoryEvent.Created(Instant.now(), setUpRepository.targetSummaryAudiences))
        .thenReply(ignored -> done());
  }

  public ReadOnlyEffect<LatestSeenRelease> getLatestSeenRelease() {
    return effects().reply(new LatestSeenRelease(currentState().getLatestSeenRelease()));
  }

  public Effect<Done> addSummary(ReleaseSummary summary) {
    // in case of multiple writes for same id, latest writer wins
    return effects().persist(new RepositoryEvent.SummaryAdded(summary))
        .thenReply(ignored -> done());
  }

  @Override
  public RepositoryState applyEvent(RepositoryEvent event) {
    return switch(event) {
      case RepositoryEvent.Created created -> new RepositoryState(created.creationDate(), created.summaryTargetAudience());
      case RepositoryEvent.SummaryAdded summaryAdded -> currentState().addSummary(summaryAdded);
    };
  }
}
