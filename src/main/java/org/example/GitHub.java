package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class GitHub {
    private final String owner, repo;
    private final int pr;
    private final String apiBase;

    public GitHub(String prUrl, String apiBase) {
        // path is /OWNER/REPO/pull/NUMBER, so split gives ["", owner, repo, "pull", number]
        String[] parts = URI.create(prUrl).getPath().split("/");
        if (parts.length < 5 || !"pull".equals(parts[3])) {
            throw new IllegalArgumentException("Not a pull request url: " + prUrl);
        }
        this.owner = parts[1];
        this.repo  = parts[2];
        this.pr    = Integer.parseInt(parts[4]);
        this.apiBase = trimTrailingSlash(apiBase);
    }

    /** Used when owner/repo/pr are already known — and by tests, to aim at a fake server. */
    public GitHub(String owner, String repo, int pr, String apiBase) {
        this.owner = owner;
        this.repo = repo;
        this.pr = pr;
        this.apiBase = trimTrailingSlash(apiBase);
    }

    private static String trimTrailingSlash(String base) {
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String filesUrl() {
        return "%s/repos/%s/%s/pulls/%d/files"
                .formatted(apiBase, owner, repo, pr);
    }

    private String commentUrl() {
        return "%s/repos/%s/%s/issues/%d/comments"
                .formatted(apiBase, owner, repo, pr);
    }
    public String generatePRDiff() throws URISyntaxException, IOException, InterruptedException {
        HttpClient client = HttpClient.newHttpClient();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(filesUrl()))
                .header("Authorization", "Bearer " + Config.get("oauth"))
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .GET()
                .build();

        System.out.print("sending http request" + request);

        HttpResponse<String> response =
                client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("response " + response);

        ObjectMapper mapper = new ObjectMapper();
        System.out.println(response.body());
        JsonNode files = mapper.readTree(response.body());   // array of changed files

        List<String> skip = List.of(
                "package-lock.json", "poetry.lock", "yarn.lock", "Cargo.lock");

        StringBuilder combined = new StringBuilder();
        for (JsonNode f : files) {
            JsonNode patchNode = f.get("patch");
            if (patchNode == null) continue;
            String filename = f.get("filename").asText();
            if (skip.stream().anyMatch(filename::endsWith)) continue;

            combined.append("### ").append(filename).append("\n")
                    .append(patchNode.asText()).append("\n\n");
        }
        return combined.toString();

    }
    public HttpResponse<?> postComment(String review)
        throws IOException, InterruptedException {

            // build the JSON with Jackson so newlines/quotes/backticks in the
            // review get escaped properly — don't hand-concatenate a JSON string
            String json = new ObjectMapper()
                    .writeValueAsString(Map.of("body", review));

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(
                            commentUrl()
                    ))
                    .header("Authorization", "Bearer " +Config.get("oauth"))
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());

        }
    }
