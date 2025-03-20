package application;

import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import com.anthropic.client.AnthropicClientAsync;
import domain.SummaryState;
import integration.GitHubApiClient;

@ComponentId("summary-workflow")
public final class ReleaseSummaryWorkflow extends Workflow<SummaryState> {

  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClientAsync anthropicClient;

  public ReleaseSummaryWorkflow(GitHubApiClient gitHubApiClient, AnthropicClientAsync anthropicClient) {
    this.gitHubApiClient = gitHubApiClient;
    this.anthropicClient = anthropicClient;
  }

  @Override
  public WorkflowDef<SummaryState> definition() {
    // FIXME workflow steps
    // 1. get release from github? or is that what is posted as start command perhaps?
    // 2. call LLM to summarize with fetch-details as tool
    // 3. call tools
    // 4. fork out over different targets to summarize for
    // 5. send summary to repository entity

    return null;
  }
}
