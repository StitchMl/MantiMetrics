# MantiMetrics

MantiMetrics is a Java/Maven tool for building maintainability datasets from GitHub repositories and JIRA bug data.
It supports `class`, `method`, and `both` execution modes, can load projects from JSON or directly from the CLI, produces raw CSV datasets, and then generates exam-ready artifacts for WEKA and what-if analysis.

## What it does

For each configured project, MantiMetrics:

1. selects the target releases
2. builds the full historical release timeline shared by GitHub and JIRA.
3. streams each selected release from GitHub and keeps only the production Java sources in memory for the time needed by the analysis.
4. extracts static metrics, smell-related indicators and release-local Git history
5. enriches every class/method row with cumulative history features and historical JIRA-based bug labels.
6. writes a raw dataset CSV
7. generates derived artifacts for the exam workflow
8. writes a milestone-1 audit report that documents snoring coverage and labeling policy.

## Granularity

The tool supports two dataset granularities and one combined execution mode:

- `method`: one row per method
- `class`: one row per type, including classes, interfaces, records, enums and annotations.
- `both`: runs `class` first and then `method` in the same execution

Choose the granularity from the CLI:

```powershell
.\mvnw.cmd exec:java "-Dexec.args=--granularity=method"
.\mvnw.cmd exec:java "-Dexec.args=--granularity=class"
.\mvnw.cmd exec:java "-Dexec.args=--granularity=both"
```

The same works with global Maven:

```powershell
mvn exec:java "-Dexec.args=--granularity=method"
```

For the current Milestone 1 setup, `class` is the primary mode and `both` is useful when you want to keep the method-level dataset available for comparison or reuse.

If you omit `--granularity`, the default is `class`.
If you omit `--repo-url`, the CLI shows an interactive prompt, so you can choose one configured project or enter a custom GitHub repository.

## Configuration

Projects are loaded from [src/main/resources/projects-config.json](C:/Users/matte/IdeaProjects/MantiMetrics/src/main/resources/projects-config.json).
When you launch the tool without `--repo-url`, those projects are shown in the terminal, and the analysis starts only after you pick one of them.

The bundled catalog is aligned with the six Apache projects typically allowed for the exam:

- `AVRO`
- `OPENJPA`
- `STORM`
- `ZOOKEEPER`
- `SYNCOPE`
- `TAJO`

Example:

```json
[
  {
    "owner": "apache",
    "name": "Avro",
    "percentage": 33,
    "repoUrl": "https://github.com/apache/avro.git",
    "jiraKey": "AVRO"
  },
  {
    "owner": "apache",
    "name": "ZooKeeper",
    "percentage": 33,
    "repoUrl": "https://github.com/apache/zookeeper.git",
    "jiraKey": "ZOOKEEPER"
  }
]
```

Relevant fields:

- `owner`: GitHub organization or user
- `name`: repository name
- `repoUrl`: optional fallback source for `owner` and `name`; useful when you want a single canonical repository reference in the config.
- `percentage`: percentage of releases to analyze; for the exam, `33` matches the first milestone guidance.
- `jiraKey`: JIRA project key used to retrieve versions and fixed bugs

## Ad-Hoc CLI Project

If the exam changes the repository to analyze, you can skip the JSON file and launch a single project directly from the CLI:

```powershell
.\mvnw.cmd exec:java "-Dexec.args=--repo-url=https://github.com/apache/avro.git --jira-key=AVRO"
```

You can combine it with explicit granularity:

```powershell
.\mvnw.cmd exec:java "-Dexec.args=--granularity=both --repo-url=https://github.com/apache/zookeeper.git --jira-key=ZOOKEEPER --percentage=33"
```

Rules for ad-hoc mode:

- `--repo-url` enables single-project execution from the CLI
- `--jira-key` is required together with `--repo-url`
- `--percentage` is optional; if omitted in CLI ad-hoc mode, the default is `33`
- `owner` and `name` are derived automatically from the repository URL

If you do not pass `--repo-url`, the prompt also offers a `custom repository` option and asks for:

- repository URL
- JIRA key
- percentage of releases to analyze, defaulting to `33`

## Secrets Setup

Tracked files no longer contain real tokens. Configure credentials in one of these ways:

- create `config/github.local.properties` from [github.local.properties.example](C:/Users/matte/IdeaProjects/MantiMetrics/config/github.local.properties.example)
- create `config/jira.local.properties` from [jira.local.properties.example](C:/Users/matte/IdeaProjects/MantiMetrics/config/jira.local.properties.example)
- or pass secrets with `-Dmantimetrics.github.pat=...` and `-Dmantimetrics.jira.pat=...`
- or use `MANTIMETRICS_GITHUB_PAT` and `MANTIMETRICS_JIRA_PAT`

Local `*.local.properties` files are ignored by Git.

## Raw Dataset

The raw CSV is written under `output/`:

- `output/<repo>_dataset_method.csv`
- `output/<repo>_dataset_class.csv`

Each row contains identifiers, release information, static metrics, Git history metrics and bug labels.
The dataset includes smell-related features such as:

- `CodeSmells`
- `NSmells`
- `isLongMethod`
- `isGodClass`
- `isFeatureEnvy`
- `isDuplicatedCode`

`NSmells` is an explicit derived feature that combines PMD smell counts with the binary smell indicators exposed by the tool.

The raw dataset now also carries release-local and cumulative history features, for example:

- `Touches` and `TotalTouches`
- `IssueTouches` and `TotalIssueTouches`
- `Authors` and `TotalAuthors`
- `AddedLines`, `DeletedLines`, `Churn` and `TotalChurn`
- `AgeInReleases`

## Exam Artifacts

After the raw CSV is written, MantiMetrics creates a sibling directory:

- `output/<repo>_dataset_method_artifacts/`
- `output/<repo>_dataset_class_artifacts/`

Inside that directory the tool generates:

- `A.csv` and `A.arff`: classifier-ready dataset without identifier columns
- `BPlus.csv` and `BPlus.arff`: rows with at least one smell
- `B.csv` and `B.arff`: same rows as `BPlus`, but smell-related actionable features forced to zero.
- `C.csv` and `C.arff`: rows with no smells
- `metadata.json`: summary of columns, actionable features and produced artifacts
- `milestone1-audit.json`: audit of feature count, snoring coverage and historical labeling policy

This matches the exam workflow for the what-if analysis:

- `A`: base dataset
- `BPlus`: smelly subset
- `B`: de-smelled version of `BPlus`
- `C`: clean subset

## Build And Test

Compile:

```powershell
.\mvnw.cmd -DskipTests compile
```

Run tests:

```powershell
.\mvnw.cmd test
```

With global Maven:

```powershell
mvn -DskipTests compile
mvn test
```

## Output Summary

For each configured project and chosen granularity, the final outputs are:

- one raw CSV dataset
- four flat CSV datasets for classification and what-if analysis
- four ARFF datasets for WEKA
- one metadata JSON file
- one milestone audit JSON file

If you run with `both`, the tool generates the full output set twice:

- `output/<repo>_dataset_class.csv` plus its derived artifacts
- `output/<repo>_dataset_method.csv` plus its derived artifacts

## Notes

- The current pipeline is designed around GitHub plus JIRA because bug labels come from fixed JIRA tickets.
- Release selection is percentage-based and uses chronological tag order, so the first `33%` really means the oldest release window requested by the exam.
- The dataset is emitted for the oldest release window only, while the full available timeline is still used to label the past according to the simplified `Total` policy when Jira affected versions are incomplete.
- The runtime is web-first and zero-disk for the analyzed repository: no persistent clone and no extracted release tree are written locally during analysis.
- To keep GitHub pressure under control, API calls use pagination, retries and backoff, and `both` still reuses the same release extraction, PMD scan and commit history for class-level and method-level outputs.
- Tests currently cover dataset formatting, granularity handling, historical labeling, release commit mapping, path normalization and derived artifact generation.

Architecture details and package roles are documented in [docs/milestone1-architecture.md](C:/Users/matte/IdeaProjects/MantiMetrics/docs/milestone1-architecture.md).
