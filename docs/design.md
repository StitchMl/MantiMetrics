## Project Architecture

The **MantiMetrics** project is structured in separate modules, each responsible for a specific aspect of the code metrics collection, analysis and labeling flow:

1. **GitService**
    - Interface with the GitHub API to list files (`listJavaFiles`/`listTags`), fetch raw content (`fetchFileContent`) and retrieve SHA commits (`getLatestCommitSha`).
    - It handles authentication via Personal Access Token, caching of the default branch and parsing of JSON responses.

2. **CodeParser**
    - It parses the Java source directly from GitHub (for a given tag or SHA) via JavaParser.
    - It extracts all `MethodDeclarations`, calculates the metrics (`MetricsCalculator`) and collects the JIRA issue keys associated with the commits of each file.

3. **MetricsCalculator**
    - Calculate a set of metrics for each method:
        - **Basic metrics**: LOC, statement count, cyclomatic and cognitive complexity.
        - **Halstead metrics**: n₁, n₂, N₁, N₂, vocabulary, length, volume, difficulty, effort.
        - **Nested depth**: maximum nesting depth.
        - **Code smells**: Simple detection of `Long method`, `Feature envy`, `God class`, `Duplicate code`.

4. **JiraClient**
    - It interacts with JIRA's REST API to execute configurable JQL queries (e.g. `issuetype = Bug AND status in (Closed,Resolved) AND resolution = Fixed`).
    - It retrieves bug-keys and provides `isMethodBuggy(...)` to tag methods according to the issue keys mentioned in the commits.

5. **ReleaseSelector**
    - It filters Git versions (tags) by selecting only the first configurable percentage (e.g., first 33%) and discarding the rest to ensure more stable data.

6. **CSVWriter**
    - It exports the collected data in a CSV with header:  
      `Project,Path,Method,ReleaseID,VersionId,CommitId,LOC,StmtCount,Cyclomatic,Cognitive,…,isDuplicatedCode,Buggy`

7. **MantiMetrics (Main)**
    - Orchestrator that loads the configuration from JSON files, initializes the services, cycles through the projects, selected versions, collects the data and finally writes the CSV.

---

## Diagramma del Flusso

```text
+----------------+      +----------------+      +----------------------+
| MantiMetrics   |----->| GitService     |----->| GitHub REST API      |
| (main)         |      | (listTags,     |      | (/tags, /git/trees,  |
|                |      |  listJavaFiles,|      |  /commits, /contents)|
|                |      |  fetchFile,    |      |                      |
|                |      |  getLatestSHA) |      +----------------------+
+----------------+      +----------------+
         |                        |
         |                        v
         |                +---------------+
         |                | CodeParser    |
         |                | (JavaParser,  |
         |                |  getIssueKeys)|
         |                +---------------+
         |                        |
         |                        v
         |                +---------------+
         |                | MetricsCalc   |
         |                +---------------+
         |                        |
         v                        v
+----------------+        +---------------+
| JiraClient     |<-------| Repository of |
| (fetchBugKeys, |        | MethodData    |
|  isMethodBuggy)|        +---------------+
+----------------+                |
         |                        v
         |                    +---------------+
         +---------------++-->| CSVWriter     |
                              +---------------+
