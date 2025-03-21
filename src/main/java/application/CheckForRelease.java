package application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import com.anthropic.client.AnthropicClientAsync;
import domain.ReleaseSummarizer;
import domain.ReleaseSummary;
import domain.RepositoryIdentifier;
import integration.GitHubApiClient;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static akka.Done.done;

@ComponentId("githubReleaseDetails-check")
public final class CheckForRelease extends TimedAction {

  private final ComponentClient componentClient;
  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClientAsync anthropicClient;

  public CheckForRelease(ComponentClient componentClient, GitHubApiClient gitHubApiClient, AnthropicClientAsync anthropicClient) {
    this.componentClient = componentClient;
    this.gitHubApiClient = gitHubApiClient;
    this.anthropicClient = anthropicClient;
  }

  public Effect checkForNewRelease(RepositoryIdentifier repositoryIdentifier) {
    var futureLatestSeenRelease = componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(repositoryIdentifier))
        .method(GitHubRepositoryEntity::getLatestSeenRelease)
        .invokeAsync();
    var futureLatestReleaseFromGitHub = gitHubApiClient.getLatestRelease(repositoryIdentifier.owner(), repositoryIdentifier.repo());

    CompletionStage<Done> summarizationDone =
        futureLatestSeenRelease.thenCombine(futureLatestReleaseFromGitHub, (latestSeenRelease, latestReleaseFromGitHub) -> {
          if (latestSeenRelease.id().isEmpty() || latestSeenRelease.id().get() < latestReleaseFromGitHub.id()) {
            // unseen githubReleaseDetails, trigger workflow
            // FIXME release summarizer could perhaps be what we bootstrap/inject
            var summarizer = new ReleaseSummarizer(gitHubApiClient, anthropicClient);
            var futureSummary = summarizer.summarize(repositoryIdentifier, latestReleaseFromGitHub);
            return futureSummary.thenCompose(summary -> saveSummary(repositoryIdentifier, summary));
          } else {
            return CompletableFuture.completedFuture(done());
          }
        }).thenCompose(cs -> cs);

    return effects().asyncDone(summarizationDone);
  }

  private CompletionStage<Done> saveSummary(RepositoryIdentifier repositoryIdentifier, ReleaseSummarizer.SummaryResult summaryResult) {
    return componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(repositoryIdentifier))
        .method(GitHubRepositoryEntity::addSummary)
        .invokeAsync(new ReleaseSummary(summaryResult.releaseName(), summaryResult.gitHubReleaseId(), "FIXME target audience", Instant.now(), summaryResult.summaryText()));
  }
}
