package summarizer.application;

import akka.actor.Timers;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.consumer.Consumer;
import akka.javasdk.timer.TimerScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.domain.RepositoryEvent;

import java.time.Duration;

/**
 * Listens for new repository creation, when seen, schedules checking for releases to summarize
 */
@ComponentId("new-repo")
@Consume.FromEventSourcedEntity(GitHubRepositoryEntity.class)
public final class NewRepoListener extends Consumer {

  private final Logger logger = LoggerFactory.getLogger(NewRepoListener.class);

  private final TimerScheduler timerScheduler;
  private final ComponentClient componentClient;

  public NewRepoListener(TimerScheduler timerScheduler, ComponentClient componentClient) {
    this.timerScheduler = timerScheduler;
    this.componentClient = componentClient;
  }

  public Effect onEvent(RepositoryEvent event) {
    if (event instanceof RepositoryEvent.Created created) {
      var repositoryId = messageContext().eventSubject().get();
      logger.info("Saw repository created [{}] scheduling immediate check for release", repositoryId);

      timerScheduler.startSingleTimer(repositoryId, Duration.ofSeconds(1),
          componentClient.forTimedAction()
              .method(CheckForRelease::checkForNewRelease)
              .deferred(GitHubRepositoryEntity.identifierFor(repositoryId)));

      return effects().done();
    } else {
      return effects().ignore();
    }
  }

}
