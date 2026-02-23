package fr.agile.service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JiraHttpClient {

    @Value("${jira.username}")
    private String username;

    @Value("${jira.apiToken}")
    private String apiToken;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .proxy(ProxySelector.of(new InetSocketAddress("proxy-web.cnamts.fr", 3128)))
            .build();

    public HttpRequest createPostJsonRequest(String url, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + basicAuthHeader())
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                .build();
    }

    public HttpRequest createRequest(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + basicAuthHeader())
                .header("Accept", "application/json")
                .GET()
                .build();
    }

    public String sendRequest(HttpRequest request) throws IOException, InterruptedException {
        int attempts = 0;
        while (true) {
            attempts++;
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int statusCode = resp.statusCode();
            if (statusCode / 100 == 2) {
                return resp.body();
            }

            boolean retryable = statusCode == 429 || (statusCode >= 500 && statusCode < 600);
            if (retryable && attempts < 5) {
                long retrySec = resp.headers().firstValue("Retry-After").map(Long::parseLong).orElse(1L);
                long backoffMs = (long) (retrySec * 1000 + Math.pow(2, attempts - 1) * 200 + (Math.random() * 100));
                try {
                    Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                continue;
            }
            throw new IOException("HTTP " + statusCode + " for " + request.uri() + " – body: " + resp.body());
        }
    }

    private String basicAuthHeader() {
        String auth = username + ":" + apiToken;
        return Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }
}
