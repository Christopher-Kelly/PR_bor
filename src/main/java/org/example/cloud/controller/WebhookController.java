package org.example.cloud.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.App;
import org.example.cloud.config.AnthropicConfig;
import org.example.cloud.config.GithubConfig;
import org.example.cloud.service.ReviewQueue;
import org.example.cloud.service.VerifyRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@RestController
public class WebhookController {
    public  ObjectMapper mapper = new ObjectMapper();
    public AnthropicConfig anthropicConfig;
    public ReviewQueue reviewQueue;              // inject the bean
    public  VerifyRequest verifyRequest;         // inject the bean
    public  GithubConfig githubConfig;

    WebhookController(AnthropicConfig anthropicConfig,
                      ReviewQueue reviewQueue,
                      VerifyRequest verifyRequest, GithubConfig githubConfig) {    // Spring supplies all three
        this.anthropicConfig = anthropicConfig;
        this.reviewQueue = reviewQueue;
        this.verifyRequest = verifyRequest;
        this.githubConfig = githubConfig;
    }

    @PostMapping("/webhook")
    public ResponseEntity<String> webhook(
            @RequestHeader("X-GitHub-Event") String event,
            @RequestHeader("X-Hub-Signature-256") String sig,
            @RequestBody String rawBody) throws Exception {

        // 1. SECURITY FIRST — prove it's really GitHub before touching the body
        verifyRequest.verifyRequest(sig, rawBody);   // throws if bad

        // 2. event filtering — only PRs we care about
        if (!"pull_request".equals(event)) {
            return ResponseEntity.ok("ignored event: " + event);
        }

        JsonNode payload = mapper.readTree(rawBody);
        String action = payload.get("action").asText();
        if (!action.equals("opened") && !action.equals("synchronize")) {
            return ResponseEntity.ok("ignored action: " + action);
        }

        String owner = payload.at("/repository/owner/login").asText();
        if (!owner.equals("Christopher-Kelly")) {
            return ResponseEntity.ok("ignored repo owner: " + owner);
        }

        // 3. it passed all gates — queue it
        reviewQueue.publish(rawBody);
        return ResponseEntity.ok("queued");
    }

    // Pub/Sub calls this — slow path
    @PostMapping("/tasks/review")
    public ResponseEntity<String> review(@RequestBody String pubsubEnvelope) throws Exception {
        // 1. parse the Pub/Sub envelope
        JsonNode envelope = mapper.readTree(pubsubEnvelope);

        // 2. extract and base64-decode the data field → your GitHub payload
        String encoded = envelope.at("/message/data").asText();
        String githubPayload = new String(
                Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);

        // 3. now parse the actual GitHub JSON
        JsonNode p = mapper.readTree(githubPayload);
        String owner = p.at("/repository/owner/login").asText();
        String repo  = p.at("/repository/name").asText();
        int pr       = p.at("/pull_request/number").asInt();

        // 4. run the pipeline
        App.run("https://api.github.com", "https://api.anthropic.com",
                owner, repo, pr,                    // owner, repo, pr — in that order
                anthropicConfig.apiKey(),           // apiKey
                githubConfig.token());              // githubAuthKey

        return ResponseEntity.ok("done");
    }
}