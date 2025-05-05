## JIRA Integration

This document describes how the **JiraClient** module of MantiMetrics integrates with JIRA to extract and filter resolved 'Bug' tickets, then labelling the methods as defective ('buggy') or not.

---

## 1. Configuration

All connection and query parameters are defined in **`application.properties`**:

```properties
# Basic URL of Apache JIRA Server/Data Centre (without a final slash)
jira.url=https://issues.apache.org/jira

# Personal Access Token (PAT) authentication - supported by Jira Server/DC 8.14+
jira.pat=YOUR_JIRA_PAT

# Template JQL to extract only fixed bugs: {projectKey} will be replaced upon initialisation
jira.query=project = {projectKey} AND issuetype = Bug AND status in (Closed,Resolved) AND resolution = Fixed
