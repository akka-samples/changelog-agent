package application;

import akka.javasdk.workflow.Workflow;
import domain.SummaryState;

public final class ReleaseSummaryWorkflow extends Workflow<SummaryState> {
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
