package org.example.cloud.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicConfig(String apiKey) {}   // binds anthropic.api.key -> apiKey