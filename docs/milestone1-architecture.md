# Milestone 1 Architecture

This document maps the main directories and classes involved in the Milestone 1 pipeline.
The goal is to keep the application explicit, modular and reviewable: no god class, no god file, and each package with a clear responsibility.

## End-to-end flow

1. `app` and `cli` resolve, which Apache project to analyze.
2. `analysis` builds the release timeline and preloads the Git history needed for snoring and historical labels.
3. `jira` fetches resolved bug tickets and project versions.
4. `labeling` turns Jira metadata plus fix commits into a release-aware historical bug oracle.
5. `parser` and `metrics` analyse the full source tree of each selected release.
6. `history` enriches each entity with cumulative metrics across releases.
7. `csv`, `dataset` and `audit` write the raw CSV, derived WEKA datasets, and the milestone verification report.

## Package roles

`com.mantimetrics.app`
- `ApplicationBootstrap`: wiring root of the application. Creates services and launches the selected project analysis.

`com.mantimetrics.analysis`
- `ProjectReleasePlanner`: computes the valid chronological release timeline shared by Git and Jira.
- `ProjectProcessor`: project-level orchestrator. Preloads release history, builds the label index and writes the datasets.
- `ReleaseExecutionService`: per-release executor. Downloads sources, runs PMD and delegates row collection.
- `ReleaseDatasetCollector`: converts a parsed release into class-level or method-level rows.
- `DatasetRowEnricher`: applies release-local history, cumulative history and historical bug labels to parsed rows.
- `ReleaseSnapshot`: immutable release history checkpoint used by both dataset generation and labeling.
- `ReleaseDatasetRequest`: narrow request object that keeps collector method signatures small and explicit.

`com.mantimetrics.audit`
- `MilestoneAuditService`: writes `milestone1-audit.json`, summarizing feature count, snoring window and labeling policy.

`com.mantimetrics.cli`
- `CliOptions` and `CliOptionsParser`: CLI input parsing.
- `ProjectSelectionPrompt`: interactive project selection when no repository is provided from the CLI.

`com.mantimetrics.csv`
- `CSVWriter`: raw CSV writer for the dataset rows.

`com.mantimetrics.dataset`
- `DatasetArtifactService`: generates classifier-ready CSV/ARFF artifacts.
- `DatasetMetadataWriter`: documents the derived artifacts.
- `WhatIfDatasetBuilder`: creates A/B/BPlus/C datasets for the exam workflow.

`com.mantimetrics.git`
- `GitService`: GitHub-facing facade for tags, sources and release commit data.
- `GitHubCommitRangeClient` and `GitHubCommitDetailsClient`: collect the commit range and per-commit file details.
- `ReleaseCommitData`: per-release Git history snapshot enriched with touches, issue-linked touches, authors and churn.

`com.mantimetrics.history`
- `RowHistoryStore`: mutable in-memory store used while traversing releases chronologically.
- `RowHistoryState`: cumulative metrics carried across releases for a single dataset entity.

`com.mantimetrics.jira`
- `JiraClient`: JIRA facade for versions and resolved bug tickets.
- `JiraProjectReader`: paginated reader for project versions and bug ticket metadata.
- `JiraBugTicket`: minimal immutable ticket snapshot for historical labeling.

`com.mantimetrics.labeling`
- `ReleaseTimeline`: chronological release index shared across pipeline.
- `HistoricalBugLabelIndexBuilder`: builds the historical bug oracle.
- `HistoricalBugLabelIndex`: release-aware lookup used by the row enricher.

`com.mantimetrics.metrics`
- Static metric calculators and smell detectors. They are intentionally kept small and specialized.

`com.mantimetrics.model`
- `ClassData`, `MethodData` and `MetricDatasetRowData`: immutable row models for the raw dataset.

`com.mantimetrics.parser`
- Source scanning and JavaParser-based extraction of classes and methods.

## Labeling policy

The implemented labeling policy is:

- Use the full historical release timeline to collect bug-fix commits.
- Use Jira affected versions when they are consistent with the fix release.
- Fall back to the simplified `Total` policy when affected versions are missing or inconsistent.
- Emit rows only for the oldest selected release window (`33%` by default), while still using the remaining timeline for labels.

This matches the simplified course guidance and makes the historical oracle explicit in the audit report.
