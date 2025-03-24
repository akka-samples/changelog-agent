package summarizer.integration;

import akka.http.javadsl.model.HttpHeader;
import akka.http.javadsl.model.MediaRanges;
import akka.http.javadsl.model.MediaTypes;
import akka.http.javadsl.model.headers.Accept;
import akka.http.javadsl.model.headers.Authorization;
import akka.http.javadsl.model.headers.HttpCredentials;
import akka.http.javadsl.model.headers.Location;
import akka.http.javadsl.model.headers.RawHeader;
import akka.http.javadsl.model.headers.UserAgent;
import akka.javasdk.JsonSupport;
import akka.javasdk.http.HttpClient;
import akka.javasdk.http.HttpClientProvider;
import akka.javasdk.http.StrictResponse;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
  ) {
  }

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
  ) {
  }

  private static final UserAgent USER_AGENT = UserAgent.create("AI Changelog Summarizer");
  private static final HttpHeader GITHUB_API_VERSION = RawHeader.create("X-GitHub-Api-Version", "2022-11-28");

  // FIXME what about access token?

  private final HttpClient httpClient;
  private final Optional<HttpHeader> apiTokenHeader;

  public GitHubApiClient(HttpClientProvider httpClientProvider, Optional<String> apiToken) {
    this(httpClientProvider.httpClientFor("https://api.github.com"), apiToken);
  }

  private GitHubApiClient(HttpClient httpClient, Optional<String> apiToken) {
    this.httpClient = httpClient;
    this.apiTokenHeader = apiToken.map(token -> Authorization.create(HttpCredentials.createOAuth2BearerToken(token)));
  }

  public GitHubApiClient withApiToken(String apiToken) {
    return new GitHubApiClient(httpClient, Optional.of(apiToken));
  }

  private List<HttpHeader> headers(HttpHeader... additionalHeaders) {
    var headers = new ArrayList<HttpHeader>();
    headers.add(USER_AGENT);
    headers.add(GITHUB_API_VERSION);
    apiTokenHeader.ifPresent(headers::add);
    if (additionalHeaders.length > 0) {
      headers.addAll(Arrays.asList(additionalHeaders));
    }
    return headers;
  }

  public CompletionStage<List<ReleaseDetails>> listLast5Releases(String owner, String repository) {
    // https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#list-releases
    return httpClient.GET("/repos/" + owner + "/" + repository + "/releases")
        .withHeaders(
            headers(
                Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github+json")))))
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
            headers(
                Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github+json"))))
        ).responseBodyAs(ReleaseDetails.class)
        .invokeAsync()
        .thenApply(StrictResponse::body);
  }

  public CompletionStage<IssueDetails> getDetails(String owner, String repository, String issueNumber) {
    // https://docs.github.com/en/rest/issues/issues?apiVersion=2022-11-28#get-an-issue
    return httpClient.GET("/repos/" + owner + "/" + repository + "/issues/" + issueNumber)
        .withHeaders(headers(
            Accept.create(MediaRanges.create(MediaTypes.applicationWithOpenCharset("vnd.github.raw+json")))
        ))
        .responseBodyAs(IssueDetails.class)
        .invokeAsync()
        .thenApply(response -> switch (response.status().intValue()) {
          case 200 -> response.body();
          case 301 ->
              throw new RuntimeException("Issue " + issueNumber + " was transferred to another repository (" + response.httpResponse().getHeader(Location.class) + ")");
          case 404 ->
              throw new RuntimeException("Issue " + issueNumber + " was not found or transferred to another repository we dont have access to");
          case 410 -> throw new RuntimeException("Issue " + issueNumber + " was deleted");
          default ->
              throw new RuntimeException("Unexpected response code " + response.status().intValue() + " when trying to get details for issue " + issueNumber);
        });
  }
}
