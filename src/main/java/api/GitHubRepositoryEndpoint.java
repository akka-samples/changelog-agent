package api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.*;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import application.GitHubRepositoryEntity;
import integration.GitHubApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletionStage;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/repo")
public class GitHubRepositoryEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryEndpoint.class);

  public record CreateRepositoryRequest(Set<String> targetSummaryAudiences) {}

  private final ComponentClient componentClient;
  private final GitHubApiClient gitHubApiClient;

  public GitHubRepositoryEndpoint(ComponentClient componentClient, GitHubApiClient gitHubApiClient) {
    this.componentClient = componentClient;
    this.gitHubApiClient = gitHubApiClient;
  }

  @Post("/{owner}/{repo}")
  public CompletionStage<String> setUp(String owner, String repo, CreateRepositoryRequest createRepository) {
    logger.info("Setting up GitHub Repository {}/{}", owner, repo);

    return componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(owner, repo))
        .method(GitHubRepositoryEntity::setUp)
        .invokeAsync(new GitHubRepositoryEntity.SetUpRepository(owner, repo, createRepository.targetSummaryAudiences))
        .thenApply(ignored -> "Repository set up successful");
  }

}
