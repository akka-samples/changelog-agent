package summarizer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import com.anthropic.client.AnthropicClientAsync;
import summarizer.domain.SummarizerSession;
import summarizer.domain.RepositoryIdentifier;
import summarizer.integration.GitHubApiClient;

import java.util.List;
import java.util.concurrent.CompletionStage;

/**
 * Some test endpoints to play around with the entity and summarization
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/testing/repo")
public class TestingEndpoint extends AbstractHttpEndpoint {

  private final AnthropicClientAsync anthropicClient;
  private final GitHubApiClient gitHubApiClient;

  public TestingEndpoint(AnthropicClientAsync anthropicClient, GitHubApiClient gitHubApiClient) {
    this.anthropicClient = anthropicClient;
    this.gitHubApiClient = gitHubApiClient;
  }

  // fire off a summarization for a repo, don't store anything, just return the result
  // response will be a single element SSE since operation is too slow to complete as a normal response without hitting timeouts
  @Post("/{owner}/{repo}/summarize-latest")
  public HttpResponse summarize(String owner, String repo) {
    var githubApiClientWithAuth =
        requestContext().queryParams().getString("github-api-token")
          .map(gitHubApiClient::withApiToken)
          .orElse(gitHubApiClient);

    var futureSummary = githubApiClientWithAuth.getLatestRelease(owner, repo).thenCompose(releaseDetails -> {
      var summarizer = new SummarizerSession(githubApiClientWithAuth, anthropicClient, new RepositoryIdentifier(owner, repo), releaseDetails);
      return summarizer.summarize();
    });

    // response is too slow to return like a normal response, so send as a single element stream
    // (this will work locally but not deployed where there is an inbetween timeout )
    return HttpResponses.serverSentEvents(Source.fromCompletionStage(futureSummary));
  }

}
