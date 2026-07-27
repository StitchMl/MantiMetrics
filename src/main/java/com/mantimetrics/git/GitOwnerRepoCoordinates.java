package com.mantimetrics.git;

/**
 * Repository owner/name pair resolved from configuration or repository URLs.
 *
 * @param owner repository owner
 * @param name repository name
 */
record GitOwnerRepoCoordinates(String owner, String name) {
}
