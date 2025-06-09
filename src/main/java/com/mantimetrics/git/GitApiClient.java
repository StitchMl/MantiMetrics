package com.mantimetrics.git;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

class GitApiClient {
    private static final int     MAX_R = 5;
    private static final Logger  LOG   = LoggerFactory.getLogger(GitApiClient.class);
    final OkHttpClient           http;
    private final ObjectMapper   json  = new ObjectMapper();
    private final String         token;
    private static final String  API   = "https://api.github.com";
    private static final String  REPOS = "/repos/";

    GitApiClient(String token) {
        this.token = token;
        this.http  = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(30))
                .readTimeout(Duration.ofSeconds(60))
                .writeTimeout(Duration.ofSeconds(60))
                .callTimeout(Duration.ofSeconds(90))
                .retryOnConnectionFailure(true)
                .build();
    }

    /**
     * Returns the list of commits between two tags, or from the first tag
     * to the second tag if the first is null or empty.
     *
     * @param owner    GitHub repository owner
     * @param repo     GitHub repository name
     * @param baseTag  base tag (null or empty for first release)
     * @param headTag  head tag (must not be null or empty)
     * @return list of commit SHAs
     */
    List<String> listCommitsBetween(String owner, String repo,
                                    String baseTag, String headTag)
            throws IOException, InterruptedException {
        String spec = (baseTag == null || baseTag.isBlank())
                ? headTag      // prima release → chiamo /commits?sha=tag
                : baseTag + "..." + headTag;
        String url = (baseTag == null || baseTag.isBlank())
                ? API + REPOS + owner + "/" + repo +
                "/commits?sha=" + headTag + "&per_page=100"
                : API + REPOS + owner + "/" + repo + "/compare/" + spec;

        JsonNode node = getApi(url);
        List<String> out = new ArrayList<>();

        if (baseTag == null || baseTag.isBlank()) {          // prima release
            node.forEach(c -> out.add(c.path("sha").asText()));
        } else {                                             // releases successive
            node.path("commits").forEach(c -> out.add(c.path("sha").asText()));
        }
        return out;
    }

    /** Performs a GET request on the given URL, retrying on rate-limit errors. */
    JsonNode getApi(String path) throws IOException, InterruptedException {
        for (int attempt = 0; attempt < MAX_R; attempt++) {
            Request req = new Request.Builder()
                    .url(path)
                    .header("Authorization", "token " + token)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build();
            try (Response resp = http.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    return json.readTree(resp.body().string());
                }
                if (resp.code() == 403 || resp.code() == 429 || resp.code() == 502 || resp.code() == 503 || resp.code() == 504) {
                    long wait = backoff(resp, attempt);
                    LOG.warn("Rate-limit {}, retry {}/{} in {} s – {}", resp.code(), attempt + 1, MAX_R, wait/1_000, path);
                    TimeUnit.MILLISECONDS.sleep(wait);
                    continue;
                }
                throw new IOException("HTTP " + resp.code() + " for " + path);
            } catch (SocketTimeoutException ste) {
                if (attempt == MAX_R - 1) throw ste;
                LOG.warn("Socket timeout, retry {}/{}", attempt+1, MAX_R);
                TimeUnit.SECONDS.sleep(5);
            }
        }
        throw new IOException("Retries exhausted for " + path);
    }

    /** Exponential backoff for rate-limit errors. */
    private static long backoff(Response resp,int attempt){
        if (resp != null) {
            String reset=resp.header("X-RateLimit-Reset");
            if(reset!=null)
                return Math.max(Long.parseLong(reset)*1_000-System.currentTimeMillis(),5_000);
        }
        return (long)(3_000*Math.pow(2, attempt));
    }
}