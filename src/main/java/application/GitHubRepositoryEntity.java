package application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import domain.RepositoryEvent;
import domain.RepositoryIdentifier;
import domain.RepositoryState;

import java.time.Instant;
import java.util.Set;

import static akka.Done.done;

@ComponentId("github-repository")
public class GitHubRepositoryEntity extends EventSourcedEntity<RepositoryState, RepositoryEvent> {

  public static String entityIdFor(String organization, String repository) {
    return organization + "/" + repository;
  }

  public record SetUpRepository(String gitHubOrganization, String repositoryName, Set<String> targetSummaryAudiences) {}

  public Effect<Done> setUp(SetUpRepository setUpRepository) {
    var identifier = new RepositoryIdentifier(setUpRepository.gitHubOrganization, setUpRepository.repositoryName);
    if (currentState() != null) {
      throw new IllegalStateException("Repository " + identifier + " is already previously setup");
    }
    return effects().persist(new RepositoryEvent.Created(identifier, Instant.now(), setUpRepository.targetSummaryAudiences))
        .thenReply(ignored -> done());
  }

  @Override
  public RepositoryState applyEvent(RepositoryEvent event) {
    return switch(event) {
      case RepositoryEvent.Created created -> new RepositoryState(created.identifier(), created.creationDate(), created.summaryTargetAudience());
    };
  }
}
