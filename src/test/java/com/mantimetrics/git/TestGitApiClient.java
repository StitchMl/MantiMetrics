package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

final class TestGitApiClient extends GitApiClient {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, JsonNode> responses = new HashMap<>();
    private final Map<String, Integer> calls = new HashMap<>();

    TestGitApiClient() {
        super("test-token");
    }

    void when(String url, String json) throws IOException {
        responses.put(url, mapper.readTree(json));
    }

    int calls(String url) {
        return calls.getOrDefault(url, 0);
    }

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
