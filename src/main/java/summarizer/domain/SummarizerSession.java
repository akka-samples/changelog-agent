package summarizer.domain;

import akka.javasdk.JsonSupport;
import com.anthropic.client.AnthropicClient;
import com.anthropic.client.AnthropicClientAsync;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.TextBlock;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlock;
import summarizer.integration.GitHubApiClient;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class SummarizerSession {

  private static final String FETCH_ISSUE_DETAILS_TOOL_NAME = "fetchIssueDetails";
  private static final Tool FETCH_ISSUE_DETAILS_TOOL = Tool.builder()
      .name(FETCH_ISSUE_DETAILS_TOOL_NAME)
      .description("Get more details for an issue id. The id must be a known issue id in the repository of the release notes. " +
          "Use when the release notes does not contain enough information about an issue to provide a summary for it. " +
          "Only ask for issue details once for a given issue id, subsequent calls will return the same information.")
      .inputSchema(Tool.InputSchema.builder()
          .properties(JsonValue.from(
              Map.of("issueId",
                  Map.of(
                      "type", "integer",
                      "description", "The github issue id to get details for"
                  ))))
          .putAdditionalProperty("required", JsonValue.from(List.of("issueId")))
          .build())
      .build();

  private static MessageCreateParams.Builder initialMessageCreateParamsBuilder() {
    return MessageCreateParams.builder()
        .model(Model.CLAUDE_3_5_SONNET_LATEST)
        .maxTokens(2048)
        .addTool(FETCH_ISSUE_DETAILS_TOOL)
        .toolChoice(ToolChoiceAuto.builder().build())
        .system("You are a technical writer");
  }

  public record SummaryResult(long gitHubReleaseId, String releaseName, RepositoryIdentifier repositoryIdentifier,
                              String summaryText) {
  }

  private final Logger logger = LoggerFactory.getLogger(SummarizerSession.class);

  private final GitHubApiClient gitHubApiClient;
  private final AnthropicClient anthropicClient;
  private final RepositoryIdentifier repositoryIdentifier;
  private final GitHubApiClient.ReleaseDetails gitHubReleaseDetails;

  public SummarizerSession(GitHubApiClient gitHubApiClient, AnthropicClient anthropicClient, RepositoryIdentifier repositoryIdentifier, GitHubApiClient.ReleaseDetails gitHubReleaseDetails) {
    this.gitHubApiClient = gitHubApiClient;
    this.anthropicClient = anthropicClient;
    this.repositoryIdentifier = repositoryIdentifier;
    this.gitHubReleaseDetails = gitHubReleaseDetails;
  }

  public SummaryResult summarize() {
    // FIXME target audience
    // FIXME perhaps a project overview would make sense?
    infoLog("Starting summarization");
    MessageCreateParams.Builder paramsBuilder = initialMessageCreateParamsBuilder()
        .addUserMessage("""
            <instructions>
            Summarize release notes taken from github so that they are suitable for a developer or user of the project.
            
            Group changes based on category, features first, then bugfixes, then documentation changes, then dependency bumps. 
            Completely omit changes to the build and continuous integration workflows.
            
            Numbers with a hash in front signify github issue ids. You can use tools for acquiring more details for an issue id if the release
            notes does not contain enough details for an individual change to create a good summary.
            
            If there is a mention of a CVE for a dependency bump, add that to the issue summary.
            
            Keep a link to the issue or PR in each summarized issue.
            
            Include a representative emoji in each category header.
            
            Provide the summary as markdown without any preamble, or additional text before and after, so that it can be published as is.
            </instructions>
            Here are the github release notes for
            """ + repositoryIdentifier.repo() + " " + gitHubReleaseDetails.name() + " in markdown: \n<data>\n" +
            gitHubReleaseDetails.body() + "\n</data>");

    Message latestResponse = anthropicClient.messages().create(paramsBuilder.build());
    while (latestResponse.stopReason().equals(Optional.of(Message.StopReason.TOOL_USE))) {
      // claude wants us to call one or more tools
      // FIXME could it ever be a mix of other types of content blocks?
      infoLog("Tool requests from AI");
      List<ToolResultBlockParam> toolResults =
          latestResponse.content().stream().flatMap(contentBlock -> contentBlock.toolUse().stream())
              .map(this::toolResultFor).toList();


      // send new query including tool results to anthropic
      infoLog(toolResults.size()  + " tool results to send to AI");
      var newParamsBuilder = paramsBuilder
            .addMessage(latestResponse)
            .addUserMessageOfBlockParams(toolResults.stream().map(ContentBlockParam::ofToolResult).toList());

      var newParams = newParamsBuilder.build();

      if (logger.isDebugEnabled()) {
        debugLog("Next request to AI " + JsonSupport.encodeToAkkaByteString(newParams).utf8String());
      }

      latestResponse = anthropicClient.messages().create(newParams);
    }

    if (latestResponse.stopReason().equals(Optional.of(Message.StopReason.END_TURN))) {
      infoLog("End turn from AI");
      // done
      var summaryText = latestResponse.content().stream().flatMap(contentBlock -> contentBlock.text().stream())
          .map(TextBlock::text)
          .reduce("", (str1, str2) -> str1 + str2);

      var tokensUsed = "(tokens used: in: " + latestResponse.usage().inputTokens() + ", out: " + latestResponse.usage().outputTokens() + ")";
      if (logger.isDebugEnabled()) {
        debugLog("Summary " + tokensUsed + ": " + summaryText);
      } else {
        infoLog("Summary complete " + tokensUsed);
      }
      if (summaryText.isBlank()) throw new RuntimeException("Empty response");
      else {
        return new SummaryResult(gitHubReleaseDetails.id(), gitHubReleaseDetails.name(), repositoryIdentifier, summaryText);
      }
    } else if (latestResponse.stopReason().equals(Optional.of(Message.StopReason.MAX_TOKENS))) {
      errorLog("Max tokens reached");
      throw new RuntimeException("Max tokens reached");
    } else {
      errorLog("Unexpected stop sequence from AI " + latestResponse.stopSequence());
      throw new IllegalStateException("Unexpected stop sequence: " + latestResponse.stopSequence());
    }
  }

  private ToolResultBlockParam toolResultFor(ToolUseBlock toolUseBlock) {
    if (toolUseBlock.name().equals(FETCH_ISSUE_DETAILS_TOOL_NAME)) {
      // Note: issue id parameter is required, so will not throw
      // Note: return type is kotlin stdlib map, hence the unchecked cast warning
      Map<String, JsonValue> inputMap = (Map<String, JsonValue>) toolUseBlock._input().asObject().orElseThrow();
      String issueId = inputMap.get("issueId").asNumber().get().toString();
      infoLog("(tool) fetching additional details for issue: " + issueId + " (tool use id [" + toolUseBlock.id() + "]");
      var issueDetails = gitHubApiClient.getDetails(repositoryIdentifier.owner(), repositoryIdentifier.repo(), issueId);
      debugLog("Details back from github for issue " + issueId + " (tool use id [" + toolUseBlock.id() + "]: " + issueDetails);

      return ToolResultBlockParam.builder()
          .toolUseId(toolUseBlock.id())
          .content(
              (issueDetails.title() == null ? "" : ("##" + issueDetails.title() + "\n")) +
                  (issueDetails.body() == null ? "" : issueDetails.body()))
          .build();
    } else {
      throw new IllegalStateException("Unexpected tool name: " + toolUseBlock.name());
    }
  }

  private void debugLog(String message) {
    logger.debug("[{}/{}] release [{} ({})]: {}", repositoryIdentifier.owner(), repositoryIdentifier.repo(), gitHubReleaseDetails.name(), gitHubReleaseDetails.id(), message);
  }


  private void infoLog(String message) {
    logger.info("[{}/{}] release [{} ({})]: {}", repositoryIdentifier.owner(), repositoryIdentifier.repo(), gitHubReleaseDetails.name(), gitHubReleaseDetails.id(), message);
  }

  private void errorLog(String message) {
    logger.error("[{}/{}] release [{} ({})]: {}", repositoryIdentifier.owner(), repositoryIdentifier.repo(), gitHubReleaseDetails.name(), gitHubReleaseDetails.id(), message);
  }

}
