package com.example.investment.service.moex;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Тонкая обёртка над {@link java.net.http.HttpClient} для MOEX ISS.
 *
 * Зачем выделен отдельный класс:
 *   1. HttpClient тяжёлый — создаётся один раз и переиспользуется.
 *   2. У всех вызовов MOEX ISS одинаковый базовый URL — прячем его сюда.
 *   3. Обработка ошибок (не-200 статус) делается в одном месте.
 *
 * Учебный момент для студентов:
 *   - HttpClient с JDK 11+ умеет sync ({@code send}) и async ({@code sendAsync}).
 *   - Async возвращает {@link CompletableFuture}, который потом удобно композить:
 *     {@code client.getAsync(...).thenApply(parser::parse).thenAccept(ui::render)}.
 *   - UI-поток JavaFX блокировать нельзя, поэтому мы используем именно async-вариант.
 */
public class MoexClient {

    private static final String BASE_URL = "https://iss.moex.com";

    // Один HttpClient на всё приложение. Можно безопасно делить между потоками.
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * Асинхронно выполняет GET запрос.
     *
     * @param path относительный путь вида "/iss/engines/stock/.../securities.json"
     * @return Future с телом ответа. При HTTP != 200 — {@link CompletionException} с понятным сообщением.
     */
    public CompletableFuture<String> getAsync(String path) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + path))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .header("User-Agent", "investment-app-edu/1.0")
                .GET()
                .build();

        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (status / 100 != 2) {
                        throw new CompletionException(new MoexApiException(
                                "MOEX ISS вернул статус " + status + " для " + path));
                    }
                    return response.body();
                });
    }

    /** Исключение, которое мы бросаем на некорректные HTTP ответы. */
    public static class MoexApiException extends RuntimeException {
        public MoexApiException(String msg) { super(msg); }
        public MoexApiException(String msg, Throwable cause) { super(msg, cause); }
    }
}
