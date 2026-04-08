package com.mantimetrics.analysis;

import java.util.List;

record ProjectReleasePlan(
        String owner,
        String repo,
        List<String> selectedTags,
        List<String> bugKeys
) {
}
