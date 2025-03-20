# github-changelog-summarizer


## Design sketch

Entity representing each github repo to track, and what target audiences to provide summaries for.

timer checking releases at some interval, when there is a release, trigger LLM workflow

feed release note to LLM for summary, provide tool to read more details in issue/ and PR descriptions, 
LLM should make a summary of most important features, separate lists of all features, all bugfixes and dependency bumps. 
It should filter out changes to the build infrastructure and CI workflows.


## What the sample does

Periodically polls the latest release from a github repository, when there is a new release
a summary of the changes is created.

## Design overview

## Running the sample

To interact with the Anthropic LLM the environment variables ANTHROPIC_API_KEY and or ANTHROPIC_AUTH_TOKEN must be set
to tokens gotten from the 