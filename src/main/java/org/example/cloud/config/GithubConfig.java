package org.example.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "github")
public record GithubConfig(String token, WebhookConfig webhook) {
    public record WebhookConfig(String secret) {}   // github.webhook.secret
}