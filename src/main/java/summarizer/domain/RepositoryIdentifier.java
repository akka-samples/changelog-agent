package summarizer.domain;

public record RepositoryIdentifier(String owner, String repo) {
  @Override
  public String toString() {
    return owner + "/" + repo;
  }
}
