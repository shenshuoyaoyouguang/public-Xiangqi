package com.sojourners.chess.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * HTTP 工具，基于 java.net.http.HttpClient (Java 11+)。
 */
public class HttpUtils {

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * GET 请求，timeoutMs 控制整体超时。
     * 返回响应体（2xx）或 null（非 2xx / 异常）。
     */
    public static String sendByGet(String url, String content, int timeoutMs) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "?" + content))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .GET()
                    .build();
            HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() / 100 == 2 ? resp.body() : null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
