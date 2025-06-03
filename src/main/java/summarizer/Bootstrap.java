package summarizer;

import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.http.HttpClientProvider;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import summarizer.integration.GitHubApiClient;

import java.util.Optional;

@Setup
public final class Bootstrap implements ServiceSetup {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private final GitHubApiClient gitHubApiClient;

  public Bootstrap(Config config, HttpClientProvider httpClientProvider) {
    var defaultGitHubApiToken = blankAsEmpty(config.getString("github-api-token"));
    if (defaultGitHubApiToken.isPresent()) {
      log.info("Using a default GitHub API access token for all requests to github APIs");
    } else {
      log.info("""
          GitHub API interactions will be unauthenticated. For access to private repositories 
          and a higher allowed request rate, set config 'github-api-token' or environment variable GITHUB_API_TOKEN
        """);
    }
    gitHubApiClient = new GitHubApiClient(httpClientProvider, defaultGitHubApiToken);
    // Note: uses the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
    if (!System.getenv().containsKey("ANTHROPIC_API_KEY")) {
      log.error("No ANTHROPIC_API_KEY found, interactions with anthropic LLM will not work");
    } else {
      log.info("Using anthropic access from ANTHROPIC_API_KEY environment variable");
    }
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> aClass) {
        if (aClass.equals(GitHubApiClient.class)) {
          return (T) gitHubApiClient;
        } else {
          return null;
        }
      }
    };
  }

  private Optional<String> blankAsEmpty(String value) {
    return value.isBlank() ? Optional.empty() : Optional.of(value);
  }
}
