package integration;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.MediaRanges;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.model.headers.UserAgent;
import akka.javasdk.JsonSupport;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

public final class GitHubApiClient {

  public record ReleaseDetails(
    String url,
    String htmlUrl,
    long id,
    String nodeId,
    String tagName,
    String targetCommitish,
    String name,
    String body,
    boolean draft,
    boolean prerelease,
    ZonedDateTime createdAt,
    ZonedDateTime publishedAt
    // Note: ignoring author and assets for now
  ) {}

  public record IssueDetails(
      long id,
      String nodeId,
      String repositoryUrl,
      String labelsUrl,
      String commentsUrl,
      String eventsUrl,
      String htmlUrl,
      long number,
      String state,
      String title,
      String body
      // Note: ignoring user, labels, assignee, assignees, milestone, locked, activeLockReason, comments, pullRequest,
      //       closedAt, updatedAt, closedBy, authorAssociation, stateReason
    ) {}

  private static final UserAgent USER_AGENT = UserAgent.create("AI Changelog Summarizer");
  private static final HttpHeader GITHUB_API_VERSION = RawHeader.create("X-GitHub-Api-Version", "2022-11-28");

  // FIXME what about access token?

  private final HttpClient httpClient;

  public GitHubApiClient(HttpClientProvider httpClientProvider) {
    this.httpClient = httpClientProvider.httpClientFor("https://api.github.com");
  }

  public CompletionStage<List<ReleaseDetails>> listLast5Releases(String owner, String repository) {
    // https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#list-releases
    return httpClient.GET("/repos/" + owner + "/" + repository + "/releases")
        .withHeaders(
            Arrays.asList(
                USER_AGENT,
                Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github+json"))),
                GITHUB_API_VERSION))
        .addQueryParameter("per_page", "5") // page is 1 by default
        .parseResponseBody(bytes -> {
          // FIXME no support for collection responses
          try {
            return (List<ReleaseDetails>) JsonSupport.getObjectMapper().readerForListOf(ReleaseDetails.class).readValue(bytes);
          } catch (IOException e) {
            throw new RuntimeException("Failed to parse github api response", e);
          }
        })
        .invokeAsync()
        .thenApply(StrictResponse::body);
  }

  public CompletionStage<ReleaseDetails> getLatestRelease(String owner, String repository) {
    // https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#get-the-latest-release
    return httpClient.GET("/repos/" + owner + "/" + repository + "/releases/latest")
        .withHeaders(
            Arrays.asList(
                USER_AGENT,
                Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github+json"))),
                GITHUB_API_VERSION
            )
        ).responseBodyAs(ReleaseDetails.class)
        .invokeAsync()
        .thenApply(StrictResponse::body);
  }

  public CompletionStage<IssueDetails> getDetails(String owner, String repository, String issueNumber) {
    // https://docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#get-an-issue
    return httpClient.GET("/repos/" + owner + "/" + repository + "/issues/" + issueNumber)
        .withHeaders(Arrays.asList(
            USER_AGENT,
            Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github.raw+json"))),
            GITHUB_API_VERSION
        ))
        .responseBodyAs(IssueDetails.class)
        .invokeAsync()
        .thenApply(response -> switch (response.status().intValue()) {
            case 200 -> response.body();
            case 301 -> throw new RuntimeException("Issue " + issueNumber + " was transferred to another repository (" + response.httpResponse().getHeader(Location.class) + ")");
            case 404 -> throw new RuntimeException("Issue " + issueNumber + " was not found or transferred to another repository we dont have access to");
            case 410 -> throw new RuntimeException("Issue " + issueNumber + " was deleted");
            default -> throw new RuntimeException("Unexpected response code " + response.status().intValue() + " when trying to get details for issue " + issueNumber);
          });
  }
}
