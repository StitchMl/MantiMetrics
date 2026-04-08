package com.mantimetrics.git;

import java.util.List;
import java.util.Map;

public record ReleaseCommitData(
        Map<String, List<String>> touchMap,
        Map<String, List<String>> fileToIssueKeys
) {
}
