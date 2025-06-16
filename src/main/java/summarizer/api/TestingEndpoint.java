package summarizer.api;

import akka.http.javadsl.model.HttpResponse;
import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.client.ComponentClient;
import akka.javasdk.http.AbstractHttpEndpoint;
import akka.javasdk.http.HttpResponses;
import akka.stream.javadsl.Source;
import summarizer.domain.RepositoryIdentifier;
import summarizer.application.SummarizerAgent;
import summarizer.integration.GitHubApiClient;

import java.util.UUID;

/**
 * Some test endpoints to play around with the entity and summarization, mostly meant for local testing
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/testing/repo")
public class TestingEndpoint extends AbstractHttpEndpoint {

  private final ComponentClient componentClient;
  private final GitHubApiClient gitHubApiClient;

  public TestingEndpoint(ComponentClient componentClient, GitHubApiClient gitHubApiClient) {
    this.componentClient = componentClient;
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

    var releaseDetails = githubApiClientWithAuth.getLatestRelease(owner, repo);

    var sessionId = UUID.randomUUID().toString();
    // Fire off slow summarization, but don't wait on the completion here since the HTTP client would time out the request
    var futureSummary = componentClient.forAgent()
        .inSession(sessionId)
        .method(SummarizerAgent::summarize)
        .invokeAsync(new SummarizerAgent.SummarizeRequest(new RepositoryIdentifier(owner, repo),releaseDetails));


    // Response is too slow to return like a normal response, so send it as a single element stream
    // (this will work locally but not deployed where there is an in between timeout in the ingress)
    return HttpResponses.serverSentEvents(Source.completionStage(futureSummary));
  }

}
