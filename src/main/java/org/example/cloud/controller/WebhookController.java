package org.example.cloud.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.App;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class WebhookController {

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/webhook")
    public ResponseEntity<String> handle(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestBody String rawBody) throws Exception {

        // only care about PRs being opened or updated
        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok("ignored: " + event);
        }

        JsonNode payload = mapper.readTree(rawBody);
        String action = payload.get("action").asText();
        if (!(action.equals("opened") || action.equals("synchronize"))) {
            return ResponseEntity.ok("ignored action: " + action);
        }

        String owner = payload.at("/repository/owner/login").asText();
        String repo  = payload.at("/repository/name").asText();
        int pr       = payload.at("/pull_request/number").asInt();

        App.run("https://api.github.com", "https://api.anthropic.com", owner, repo, pr);

        return ResponseEntity.ok("reviewed " + owner + "/" + repo + "#" + pr + "\n");
    }
}