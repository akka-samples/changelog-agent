package summarizer.domain;

import akka.javasdk.agent.Agent;
import akka.javasdk.agent.ModelProvider;
import akka.javasdk.agent.RemoteMcpTools;
import akka.javasdk.annotations.ComponentId;
import akka.javasdk.annotations.Description;
import akka.javasdk.annotations.FunctionTool;
import summarizer.integration.GitHubApiClient;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

@ComponentId("summarized")
public final class SummarizerAgent extends Agent {

  public record SummaryResult(long gitHubReleaseId, String releaseName, RepositoryIdentifier repositoryIdentifier,
                              String summaryText) { }
  public record SummarizeRequest(RepositoryIdentifier repositoryIdentifier, GitHubApiClient.ReleaseDetails releaseDetails) { }


  private static final String SYSTEM_MESSAGE = """
    You are a technical writer
    
    Summarize release notes taken from github so that they are suitable for a developer or user of the project.

    Group changes based on category, features first, then bugfixes, then documentation changes, then dependency bumps. 
    Completely omit changes to the build and continuous integration workflows.
    
    Numbers with a hash in front signify github issue or pull request ids. You can fetch more details for an issue id or pull request id from github if the release
    notes does not contain enough details for an individual change to create a good summary.
    
    Add a note for each issue who was the author of the change.
    
    If there is a mention of a CVE for a dependency bump, add that to the issue summary.
    
    Keep a link to the issue or PR in each summarized issue.
    
    Include a representative emoji in each category header.
    
    Provide the summary as markdown without any preamble, or additional text before and after, so that it can be published as is.
    """.trim();

  private final Logger logger = LoggerFactory.getLogger(SummarizerAgent.class);

  private final GitHubApiClient gitHubApiClient;
  volatile private RepositoryIdentifier repositoryIdentifier = null;

  public SummarizerAgent(GitHubApiClient gitHubApiClient) {
    this.gitHubApiClient = gitHubApiClient;
  }

  public Effect<SummaryResult> summarize(SummarizeRequest request) {
    var releaseString = "[" + request.repositoryIdentifier.owner() + "/" + request.repositoryIdentifier.repo() + "] release [" + request.releaseDetails.name() + " (" + request.releaseDetails.id() + ")]";
    repositoryIdentifier = request.repositoryIdentifier;
    logger.info("Starting summarization [{}]", releaseString);
    return effects()
        .model(ModelProvider.fromConfig())
        .systemMessage(SYSTEM_MESSAGE)
        .remoteMcpTools(RemoteMcpTools.fromServer("http://localhost:8000/sse").allowToolNames(name ->
            name.equals("get_pull_request") || name.equals("get_issue")
            ).withToolInterceptor(new RemoteMcpTools.ToolInterceptor() {
          @Override
          public String interceptRequest(String toolName, String payload) {
            System.out.println("Intercepting request for tool [" + toolName + "] with payload [" + payload + "]");
            return payload;
          }

          @Override
          public String interceptResponse(String toolName, String payload) {
            System.out.println("Intercepting response for tool [" + toolName + "] with payload [" + payload + "]");
            return payload;
          }
        }))
        .userMessage("""
           
            Here are the github release notes for
            """ + request.repositoryIdentifier.repo() + " " + request.releaseDetails.name() + " in markdown: \n<data>\n" +
            request.releaseDetails.body() + "\n</data>"
        ).responseAs(String.class)
        .map(summaryText -> {
          logger.info("Summary completed [{}]", releaseString);
            return new SummaryResult(request.releaseDetails.id(), request.releaseDetails.name(), request.repositoryIdentifier, summaryText);
        })
        .thenReply();
    }
}
