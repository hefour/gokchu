package com.gokchu.gokchu.video;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "youtube")
public record YoutubeProperties(String apiKey, String baseUrl) {
}
