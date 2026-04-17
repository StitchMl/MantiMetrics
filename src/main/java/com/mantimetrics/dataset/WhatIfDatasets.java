package com.mantimetrics.dataset;

/**
 * Immutable container for the milestone what-if dataset variants.
 *
 * @param datasetA classifier-ready dataset without identifier columns
 * @param datasetBPlus smelly subset of dataset A
 * @param datasetB version of dataset B+ with actionable columns zeroed
 * @param datasetC clean subset of dataset A
 */
record WhatIfDatasets(
        DatasetTable datasetA,
        DatasetTable datasetBPlus,
        DatasetTable datasetB,
        DatasetTable datasetC
) {
}
