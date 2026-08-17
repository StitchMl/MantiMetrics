package com.mantimetrics.orchestrator;

import com.mantimetrics.datasetoutput.MilestoneAuditWriter;
import com.mantimetrics.datasetoutput.DatasetArtifactGenerator;

/**
 * Groups the two output-oriented services passed to {@link Orchestrator}, reducing its
 * constructor parameter count to comply with the 7-parameter limit.
 *
 * @param datasetArtifactService service that generates derived dataset artifacts
 * @param milestoneAuditService  service that writes the milestone audit JSON
 */
public record OutputServices(
        DatasetArtifactGenerator datasetArtifactService,
        MilestoneAuditWriter milestoneAuditService
) {}
