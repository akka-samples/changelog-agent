import akka.javasdk.DependencyProvider;
import akka.javasdk.ServiceSetup;
import akka.javasdk.annotations.Setup;
import akka.javasdk.http.HttpClientProvider;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.client.okhttp.AnthropicOkHttpClientAsync;
import com.typesafe.config.Config;
import integration.GitHubApiClient;

@Setup
public final class Bootstrap implements ServiceSetup {

  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClientAsync anthropicClient;

  public Bootstrap(Config config, HttpClientProvider httpClientProvider) {
    gitHubApiClient = new GitHubApiClient(httpClientProvider);
    // Note: uses the `ANTHROPIC_API_KEY` and `ANTHROPIC_AUTH_TOKEN` environment variables
    anthropicClient = AnthropicOkHttpClientAsync.fromEnv();
  }

  @Override
  public DependencyProvider createDependencyProvider() {
    return new DependencyProvider() {
      @SuppressWarnings("unchecked")
      @Override
      public <T> T getDependency(Class<T> aClass) {
        if (aClass.equals(GitHubApiClient.class)) {
          return (T) gitHubApiClient;
        } else if (aClass.equals(AnthropicClientAsync.class)) {
          return (T) anthropicClient;
        } else {
          return null;
        }
      }
    };
  }
}
