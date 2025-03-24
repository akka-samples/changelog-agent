package summarizer.application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Consume;
import akka.javasdk.consumer.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.domain.RepositoryEvent;

@ComponentId("new-summary-publisher")
@Consume.FromEventSourcedEntity(GitHubRepositoryEntity.class)
public final class NewSummaryPublisher extends Consumer {

  private final Logger log = LoggerFactory.getLogger(NewSummaryPublisher.class);

  public Consumer.Effect onEvent(RepositoryEvent event) {
    if (event instanceof RepositoryEvent.SummaryAdded summaryAdded) {
      // FIXME publish/send release summary somewhere where it can be read
      log.info("New release summary added for repository [{}]: {}",
          messageContext().eventSubject().get(),
          summaryAdded);
      return effects().done();
    } else {
      return effects().ignore();
    }
  }
}
