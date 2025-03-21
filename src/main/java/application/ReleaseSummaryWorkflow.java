package application;

import static akka.Done.done;

import akka.Done;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.workflow.Workflow;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonObject;
import com.anthropic.core.JsonString;
import com.anthropic.core.JsonValue;
import com.anthropic.models.beta.messages.BetaTool;
import com.anthropic.models.beta.messages.BetaToolChoiceTool;
import com.anthropic.models.beta.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoiceTool;
import domain.RepositoryIdentifier;
import domain.SummaryState;
import integration.GitHubApiClient;

import java.util.List;
import java.util.Map;

@ComponentId("summary-workflow")
public final class ReleaseSummaryWorkflow extends Workflow<SummaryState> {

  public static String workflowIdFor(RepositoryIdentifier repositoryIdentifier, long latestReleaseFromGitHub) {
    return repositoryIdentifier.owner() + "/" + repositoryIdentifier.repo() + "-" + latestReleaseFromGitHub;
  }

  public record StartSummarizing(RepositoryIdentifier repositoryIdentifier, GitHubApiClient.ReleaseDetails githubReleaseDetails) {}

  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClientAsync anthropicClient;

  public ReleaseSummaryWorkflow(GitHubApiClient gitHubApiClient, AnthropicClientAsync anthropicClient) {
    this.gitHubApiClient = gitHubApiClient;
    this.anthropicClient = anthropicClient;
  }

  @Override
  public WorkflowDef<SummaryState> definition() {
    // FIXME workflow steps
    // 1. get githubReleaseDetails from github? or is that what is posted as start command perhaps?
    // 2. call LLM to summarize with fetch-details as tool
    // 3. call tools
    // 4. fork out over different targets to summarize for
    // 5. send summary to repository entity

    var start = step("start")
      .asyncCall(StartSummarizing.class, startSummarizing -> {

        var fetchIssueDetailsTool = BetaTool.builder()
            .name("fetchIssueDetails")
            .description("Get more details for an issue id. The id must be a known issue id in the repository of the release notes. " +
                "Use when the release notes does not contain enough information about an issue to provide a summary for it")
            .inputSchema(BetaTool.InputSchema.builder()
                .properties(JsonValue.from(
                    Map.of("id",
                        Map.of(
                            "type", "integer",
                            "description", "The github issue id to get details for"
                        ))))
                .putAdditionalProperty("required", JsonValue.from(List.of("id")))
                .build())
            .build();

        MessageCreateParams params = MessageCreateParams.builder()
            .model(Model.CLAUDE_3_5_SONNET_LATEST)
            .maxTokens(2048)
            .addTool(fetchIssueDetailsTool)
            .addUserMessage("Desc")
            .toolChoice(BetaToolChoiceTool.builder().name("fetchIssueDetails").build())
            .build();


      })


    return workflow()
        .addStep("start")
        ;
  }

  public Effect<Done> startSummarizing(StartSummarizing startSummarizing) {
    return effects()
        .transitionTo("start", startSummarizing)
        .thenReply(done());
  }


}
