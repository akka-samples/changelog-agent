package summarizer.application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import akka.javasdk.timer.TimerScheduler;
import com.anthropic.client.AnthropicClientAsync;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.domain.SummarizerSession;
import summarizer.domain.ReleaseSummary;
import summarizer.domain.RepositoryIdentifier;
import summarizer.integration.GitHubApiClient;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.Done.done;

/**
 * Timed action that checks for a new release, runs summarization if there is a new release, reschedules itself
 * for the next check.
 */
@ComponentId("check-for-release")
public final class CheckForRelease extends TimedAction {

  private final Logger logger = LoggerFactory.getLogger(CheckForRelease.class);

  private final ComponentClient componentClient;
  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClientAsync anthropicClient;
  private final TimerScheduler timerScheduler;
  private final Duration checkInterval;

  public CheckForRelease(ComponentClient componentClient, GitHubApiClient gitHubApiClient, AnthropicClientAsync anthropicClient, TimerScheduler timerScheduler, Config config) {
    this.componentClient = componentClient;
    this.gitHubApiClient = gitHubApiClient;
    this.anthropicClient = anthropicClient;
    this.timerScheduler = timerScheduler;
    this.checkInterval = config.getDuration("new-release-check-interval");
  }

  public Effect checkForNewRelease(RepositoryIdentifier repositoryIdentifier) {
    logger.info("Checking for new release [{}]", repositoryIdentifier);
    var futureLatestSeenRelease = componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(repositoryIdentifier))
        .method(GitHubRepositoryEntity::getLatestSeenRelease)
        .invokeAsync();

    CompletionStage<Done> summarizationDone = futureLatestSeenRelease.thenCompose(latestSeenRelease -> {
      var authorizedGitHubApiClient =
          latestSeenRelease.gitHubApiToken().map(gitHubApiClient::withApiToken).orElse(gitHubApiClient);

      var futureLatestReleaseFromGitHub = authorizedGitHubApiClient.getLatestRelease(repositoryIdentifier.owner(), repositoryIdentifier.repo());

      return futureLatestReleaseFromGitHub.thenApply(latestReleaseFromGitHub -> {
        if (latestSeenRelease.id().isEmpty() || latestSeenRelease.id().get() < latestReleaseFromGitHub.id()) {
          // FIXME for each target audience (in parallel or sequential)
          logger.info("Found new release for [{}], starting summarization", repositoryIdentifier);
          var summarizer = new SummarizerSession(authorizedGitHubApiClient , anthropicClient, repositoryIdentifier, latestReleaseFromGitHub);
          var futureSummary = summarizer.summarize();

          return futureSummary.thenCompose(summary -> saveSummary(repositoryIdentifier, summary));
        } else {
          logger.debug("No new release found for [{}]", repositoryIdentifier);
          // no new releases, we are done
          return CompletableFuture.completedFuture(done());
        }
      }).thenCompose(cs -> cs);
    });

    summarizationDone.whenComplete((done, throwable) -> {
      if (throwable != null) {
        logger.error("Summarization failed (will be retried)", throwable);
      } else {
        logger.debug("Scheduling next release check [{}]", Instant.now().plus(checkInterval));
        timerScheduler.startSingleTimer(repositoryIdentifier.toString(),
            checkInterval,
            componentClient.forTimedAction()
                .method(CheckForRelease::checkForNewRelease)
                .deferred(repositoryIdentifier)
            );
      }
    });

    return effects().asyncDone(summarizationDone);
  }

  private CompletionStage<Done> saveSummary(RepositoryIdentifier repositoryIdentifier, SummarizerSession.SummaryResult summaryResult) {
    return componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(repositoryIdentifier))
        .method(GitHubRepositoryEntity::addSummary)
        .invokeAsync(new ReleaseSummary(summaryResult.releaseName(), summaryResult.gitHubReleaseId(), Instant.now(), summaryResult.summaryText()));
  }
}