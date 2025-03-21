package api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.Get;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import com.anthropic.client.AnthropicClientAsync;
import domain.ReleaseSummarizer;
import domain.RepositoryIdentifier;
import integration.GitHubApiClient;

import java.util.List;
import java.util.concurrent.CompletionStage;

@Acl(allow = @Acl.Matcher(principal = Acl.Principal.INTERNET))
@HttpEndpoint("/testing/repo")
public class TestingEndpoint {

  private final AnthropicClientAsync anthropicClient;
  private final GitHubApiClient gitHubApiClient;

  public TestingEndpoint(AnthropicClientAsync anthropicClient, GitHubApiClient gitHubApiClient) {
    this.anthropicClient = anthropicClient;
    this.gitHubApiClient = gitHubApiClient;
  }

  // test endpoints for now
  @Get("/{owner}/{repo}/issues/{issue}")
  public CompletionStage<String> getIssue(String owner, String repo, String issue) {
    return gitHubApiClient.getDetails(owner, repo, issue)
        .thenApply(GitHubApiClient.IssueDetails::body);
  }

  @Get("/{owner}/{repo}/releases")
  public CompletionStage<List<GitHubApiClient.ReleaseDetails>> getReleases(String owner, String repo) {
    return gitHubApiClient.listLast5Releases(owner, repo);
  }

  @Post("/{owner}/{repo}/summarize-latest")
  public CompletionStage<ReleaseSummarizer.SummaryResult> summarize(String owner, String repo) {
    // use timed action like a normal class instead of as a component
    return gitHubApiClient.getLatestRelease(owner, repo).thenCompose(releaseDetails -> {
      var checkForReleases = new ReleaseSummarizer(gitHubApiClient, anthropicClient);
      return checkForReleases.summarize(new RepositoryIdentifier(owner, repo), releaseDetails);
    });
  }

}
