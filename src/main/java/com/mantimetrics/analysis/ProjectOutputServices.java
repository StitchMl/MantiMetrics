package com.mantimetrics.analysis;

import com.mantimetrics.audit.MilestoneAuditService;
import com.mantimetrics.dataset.DatasetArtifactService;

/**
 * Groups the two output-oriented services passed to {@link ProjectProcessor}, reducing its
 * constructor parameter count to comply with the 7-parameter limit.
 *
 * @param datasetArtifactService service that generates derived dataset artifacts
 * @param milestoneAuditService service that writes the milestone audit JSON
 */
public record ProjectOutputServices(
 DatasetArtifactService datasetArtifactService,
 MilestoneAuditService milestoneAuditService
) {}
