package summarizer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import com.anthropic.client.AnthropicClient;
import summarizer.domain.RepositoryIdentifier;
import summarizer.domain.SummarizerSession;
import summarizer.integration.GitHubApiClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Some test endpoints to play around with the entity and summarization, mostly meant for local testing
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/testing/repo")
public class TestingEndpoint extends AbstractHttpEndpoint {

  private final AnthropicClient anthropicClient;
  private final GitHubApiClient gitHubApiClient;
  private final Executor vtExecutor;

  public TestingEndpoint(AnthropicClient anthropicClient, GitHubApiClient gitHubApiClient, Executor vtExecutor) {
    this.anthropicClient = anthropicClient;
    this.gitHubApiClient = gitHubApiClient;
    this.vtExecutor = vtExecutor;
  }

  // fire off a summarization for a repo, don't store anything, just return the result
  // response will be a single element SSE since operation is too slow to complete as a normal response without hitting timeouts
  @Post("/{owner}/{repo}/summarize-latest")
  public HttpResponse summarize(String owner, String repo) {
    var githubApiClientWithAuth =
        requestContext().queryParams().getString("github-api-token")
          .map(gitHubApiClient::withApiToken)
          .orElse(gitHubApiClient);

    var releaseDetails = githubApiClientWithAuth.getLatestRelease(owner, repo);

    var summarizer = new SummarizerSession(githubApiClientWithAuth, anthropicClient, new RepositoryIdentifier(owner, repo), releaseDetails);

    // Fire off slow summarization, but don't wait on the completion here, the HTTP client would time out the request
    var futureSummary = CompletableFuture.supplyAsync(summarizer::summarize, vtExecutor);

    // response is too slow to return like a normal response, so send as a single element stream
    // (this will work locally but not deployed where there is an inbetween timeout in the ingress)
    return HttpResponses.serverSentEvents(Source.fromCompletionStage(futureSummary));
  }

}
