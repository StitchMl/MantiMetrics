package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * Low-level GitHub API client with retry and rate-limit handling.
 */
class GitApiClient {
    private static final int MAX_R = 5;
    private static final Logger LOG = LoggerFactory.getLogger(GitApiClient.class);

    final OkHttpClient http;
    private final ObjectMapper json = new ObjectMapper();
    private final String token;

    /**
     * Creates a GitHub API client configured with the provided personal access token.
     *
     * @param token GitHub personal access token
     */
    GitApiClient(String token) {
        this.token = token;
        this.http = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(90))
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Performs a GitHub API GET request and parses the JSON response.
     *
     * @param path fully qualified GitHub API URL
     * @return parsed JSON response
     * @throws IOException when the request fails permanently or returns a non-retriable error
     * @throws InterruptedException when the thread is interrupted while waiting to retry
     */
    JsonNode getApi(String path) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_R; attempt++) {
            Request request = new Request.Builder()
                    .url(path)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
            try (Response response = http.newCall(request).execute()) {
                if (response.isSuccessful() && response.body() != null) {
                    return json.readTree(response.body().string());
                }
                if (isRetriable(response.code())) {
                    long wait = backoff(response, attempt);
                    LOG.warn("Rate-limit {}, retry {}/{} in {} s - {}",
                            response.code(), attempt + 1, MAX_R, wait / 1_000, path);
                    TimeUnit.MILLISECONDS.sleep(wait);
                    continue;
                }
                throw new IOException("HTTP " + response.code() + " for " + path);
            } catch (SocketTimeoutException exception) {
                if (attempt == MAX_R - 1) {
                    throw exception;
                }
                LOG.warn("Socket timeout, retry {}/{}", attempt + 1, MAX_R);
                TimeUnit.SECONDS.sleep(5);
            }
        }
        throw new IOException("Retries exhausted for " + path);
    }

    /**
     * Reports whether an HTTP status code should trigger a retry.
     *
     * @param statusCode HTTP status code returned by GitHub
     * @return {@code true} when the request should be retried
     */
    private boolean isRetriable(int statusCode) {
        return statusCode == 403 || statusCode == 429
                || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * Computes the next retry delay using the GitHub rate-limit reset header when available.
     *
     * @param response HTTP response that triggered the retry
     * @param attempt zero-based retry attempt index
     * @return backoff delay in milliseconds
     */
    private static long backoff(Response response, int attempt) {
        if (response != null) {
            String reset = response.header("X-RateLimit-Reset");
            if (reset != null) {
                return Math.max(Long.parseLong(reset) * 1_000 - System.currentTimeMillis(), 5_000);
            }
        }
        return (long) (3_000 * Math.pow(2, attempt));
    }
}
