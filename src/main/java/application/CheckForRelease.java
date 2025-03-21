package application;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.timedaction.TimedAction;
import domain.RepositoryIdentifier;
import integration.GitHubApiClient;

import java.util.concurrent.CompletableFuture;

import static akka.Done.done;

@ComponentId("githubReleaseDetails-check")
public final class CheckForRelease extends TimedAction {

  private final ComponentClient componentClient;
  private final GitHubApiClient gitHubApiClient;

  public CheckForRelease(ComponentClient componentClient, GitHubApiClient gitHubApiClient) {
    this.componentClient = componentClient;
    this.gitHubApiClient = gitHubApiClient;
  }

  public Effect checkForRelease(RepositoryIdentifier repositoryIdentifier) {
    var futureLatestSeenRelease = componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(repositoryIdentifier))
        .method(GitHubRepositoryEntity::getLatestSeenRelease)
        .invokeAsync();
    var futureLatestReleaseFromGitHub = gitHubApiClient.getLatestRelease(repositoryIdentifier.owner(), repositoryIdentifier.repo());

    var doneOrWorkflowTriggered =
        futureLatestSeenRelease.thenCombine(futureLatestReleaseFromGitHub, (latestSeenRelease, latestReleaseFromGitHub) -> {
          if (latestSeenRelease.id().isEmpty() || latestSeenRelease.id().get() < latestReleaseFromGitHub.id()) {
            // unseen githubReleaseDetails, trigger workflow
            componentClient.forWorkflow(ReleaseSummaryWorkflow.workflowIdFor(repositoryIdentifier, latestReleaseFromGitHub.id()))
                .method(ReleaseSummaryWorkflow::startSummarizing)

          } else {
            return CompletableFuture.completedFuture(done());
          }
        });
  }
}
