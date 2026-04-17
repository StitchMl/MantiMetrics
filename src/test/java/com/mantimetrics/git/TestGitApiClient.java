package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test double for {@link GitApiClient} that returns preconfigured JSON payloads.
 */
final class TestGitApiClient extends GitApiClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> responses = new HashMap<>();
    private final Map<String, Integer> calls = new HashMap<>();

    /**
     * Creates the fake client using a dummy token.
     */
    TestGitApiClient() {
        super("test-token");
    }

    /**
     * Registers the JSON response returned for one URL.
     *
     * @param url requested URL
     * @param json JSON payload to return
     * @throws IOException when the fake payload cannot be parsed
     */
    void when(String url, String json) throws IOException {
        responses.put(url, mapper.readTree(json));
    }

    /**
     * Returns how many times a URL was requested.
     *
     * @param url requested URL
     * @return invocation count
     */
    int calls(String url) {
        return calls.getOrDefault(url, 0);
    }

    /**
     * Returns the configured fake response for the requested path.
     *
     * @param path requested URL
     * @return configured JSON response
     * @throws IOException when no fake response was configured
     */
    @Override
    JsonNode getApi(String path) throws IOException {
        calls.merge(path, 1, Integer::sum);
        JsonNode node = responses.get(path);
        if (node == null) {
            throw new IOException("No fake response configured for " + path);
        }
        return node;
    }
}
