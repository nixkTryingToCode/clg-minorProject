package com.nixk.recruit.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * JDK HttpClient implementation: 4s connect / 6s request timeout, no redirect following
 * (a redirect to a login wall must never count as "profile exists"), identifying User-Agent.
 */
@Component
public class JdkHttpGateway implements HttpGateway {
    private static final Logger log = LoggerFactory.getLogger(JdkHttpGateway.class);

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Override
    public Response get(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "snap-hire-verifier")
                    .header("Accept", "application/json, text/html;q=0.9, */*;q=0.8")
                    .timeout(Duration.ofSeconds(6))
                    .GET().build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(-1, null);
        } catch (Exception e) {
            log.warn("Evidence GET {} failed: {}", url, e.toString());
            return new Response(-1, null);
        }
    }

    @Override
    public Response postJson(String url, String jsonBody) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "snap-hire-verifier")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(6))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            return new Response(res.statusCode(), res.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Response(-1, null);
        } catch (Exception e) {
            log.warn("Evidence POST {} failed: {}", url, e.toString());
            return new Response(-1, null);
        }
    }
}
