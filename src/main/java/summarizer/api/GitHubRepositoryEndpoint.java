package summarizer.api;

import akka.http.javadsl.model.*;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.application.GitHubRepositoryEntity;
import summarizer.domain.ReleaseSummary;

import java.util.Optional;

/**
 * Main endpoint for interacting with the service
 */
// FIXME LLMs consumes tokens which cost actual money, for a deployable service we'd want some form of auth
//       before allowing access from the public internet
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/repo")
public class GitHubRepositoryEndpoint extends AbstractHttpEndpoint {

  private static final Logger logger = LoggerFactory.getLogger(GitHubRepositoryEndpoint.class);

  public record CreateRepositoryRequest(Optional<String> gitHubApiToken) {
  }

  private final ComponentClient componentClient;

  public GitHubRepositoryEndpoint(ComponentClient componentClient) {
    this.componentClient = componentClient;
  }

  @Post("/{owner}/{repo}")
  public String setUp(String owner, String repo, CreateRepositoryRequest createRepository) {
    logger.info("Setting up GitHub Repository [{}/{}]", owner, repo);

    componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(owner, repo))
        .method(GitHubRepositoryEntity::setUp)
        .invoke(new GitHubRepositoryEntity.SetUpRepository(owner, repo, createRepository.gitHubApiToken));

    return "Repository set up successful";
  }

  @Get("/{owner}/{repo}/summaries")
  public HttpResponse getSummaries(String owner, String repo) {
    var summariesResponse = componentClient.forEventSourcedEntity(GitHubRepositoryEntity.entityIdFor(owner, repo))
      .method(GitHubRepositoryEntity::getSummaries)
      .invoke();

    String markdownForSummaries = summariesResponse.summaries().stream()
        .map(ReleaseSummary::summary)
        .reduce("# Releases\n\n", (acc, summary) -> acc + "\n\n" + summary);

    return HttpResponses.ok().withEntity(HttpEntities.create(ContentTypes.create(MediaTypes.TEXT_MARKDOWN, HttpCharsets.UTF_8), markdownForSummaries));
  }

}
