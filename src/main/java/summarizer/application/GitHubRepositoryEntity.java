package summarizer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.eventsourcedentity.EventSourcedEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.domain.ReleaseSummary;
import summarizer.domain.RepositoryEvent;
import summarizer.domain.RepositoryIdentifier;
import summarizer.domain.RepositoryState;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static akka.Done.done;

/**
 * Represents one GitHub repository, its history of releases and their summaries
 */
@ComponentId("github-repository")
public class GitHubRepositoryEntity extends EventSourcedEntity<RepositoryState, RepositoryEvent> {

  private final Logger logger = LoggerFactory.getLogger(GitHubRepositoryEntity.class);

  public static String entityIdFor(RepositoryIdentifier repositoryIdentifier) {
    return entityIdFor(repositoryIdentifier.owner(), repositoryIdentifier.repo());
  }
  public static String entityIdFor(String organization, String repository) {
    return organization + "/" + repository;
  }

  public static RepositoryIdentifier identifierFor(String entityId) {
    var parts = entityId.split("/");
    if (parts.length == 2) {
      return new RepositoryIdentifier(parts[0], parts[1]);
    } else {
      throw new IllegalArgumentException("Invalid entity id [" + entityId + "], expected form is owner/repo");
    }
  }

  public record SetUpRepository(String gitHubOrganization, String repositoryName, Optional<String> gitHubApiToken) {}

  public record LatestSeenRelease(Optional<Long> id, Optional<String> gitHubApiToken) {}

  public Effect<Done> setUp(SetUpRepository setUpRepository) {
    if (currentState() != null) {
      throw new IllegalStateException("Repository [" + commandContext().entityId() + "] is already previously setup");
    }
    logger.info("Setting up GitHub Repository [{}]{}", commandContext().entityId(),
      setUpRepository.gitHubApiToken.isPresent() ? " (with custom auth token)" : ""
    );

    return effects().persist(new RepositoryEvent.Created(Instant.now(), setUpRepository.gitHubApiToken()))
        .thenReply(ignored -> done());
  }

  public ReadOnlyEffect<LatestSeenRelease> getLatestSeenRelease() {
    var state = currentState();
    return effects().reply(new LatestSeenRelease(state.getLatestSeenRelease(), state.getGitHubApiToken()));
  }

  public Effect<Done> addSummary(ReleaseSummary summary) {
    // in case of multiple writes for same id, latest writer wins
    return effects().persist(new RepositoryEvent.SummaryAdded(summary))
        .thenReply(ignored -> done());
  }

  public ReadOnlyEffect<List<ReleaseSummary>> getSummaries() {
    return effects().reply(currentState().getSummaries());
  }

  @Override
  public RepositoryState applyEvent(RepositoryEvent event) {
    return switch(event) {
      case RepositoryEvent.Created created -> new RepositoryState(created.creationDate(), created.gitApiHubToken());
      case RepositoryEvent.SummaryAdded summaryAdded -> currentState().addSummary(summaryAdded);
    };
  }
}
