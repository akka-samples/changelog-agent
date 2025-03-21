package domain;

import integration.GitHubApiClient;

public record SummaryState(RepositoryIdentifier repositoryIdentifier, GitHubApiClient.ReleaseDetails releaseDetails) {
}
