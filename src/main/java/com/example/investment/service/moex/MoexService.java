package com.example.investment.service.moex;

import com.example.investment.model.MoexSecurity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static com.example.investment.service.moex.MoexJsonParser.asDouble;
import static com.example.investment.service.moex.MoexJsonParser.asString;

/**
 * Доменное API для работы с MOEX ISS.
 *
 * Слой выше {@link MoexClient}: знает конкретные эндпоинты и структуру ответов,
 * превращает их в объекты предметной области ({@link MoexSecurity}).
 *
 * Все методы асинхронные — возвращают {@link CompletableFuture}. Это даёт
 * возможность JavaFX-контроллеру запускать их из UI-потока, не блокируя его.
 */
public class MoexService {

    /** Режим торгов TQBR — основной режим фондового рынка MOEX (акции T+2). */
    private static final String TQBR_SECURITIES_PATH =
            "/iss/engines/stock/markets/shares/boards/TQBR/securities.json";

    private final MoexClient     client = new MoexClient();
    private final MoexJsonParser parser = new MoexJsonParser();

    /**
     * Загружает список бумаг режима TQBR и возвращает первые {@code limit} штук
     * с заполненной текущей ценой.
     *
     * Что внутри:
     *   1. GET /iss/.../TQBR/securities.json
     *   2. Разобрать два блока: "securities" (статика: тикер, название, PREVPRICE)
     *      и "marketdata" (динамика: LAST). Связать их по SECID.
     *   3. Вернуть первые limit записей.
     */
    public CompletableFuture<List<MoexSecurity>> loadTopShares(int limit) {
        return client.getAsync(TQBR_SECURITIES_PATH)
                .thenApply(body -> mergeBlocks(body, limit));
    }

    /**
     * Загружает текущую цену (LAST или PREVPRICE) для одной бумаги.
     * Используется в кнопке "Обновить цены" на главном экране.
     */
    public CompletableFuture<Double> loadCurrentPrice(String ticker) {
        String path = "/iss/engines/stock/markets/shares/securities/" + ticker + ".json";
        return client.getAsync(path).thenApply(body -> {
            // Ищем сначала в marketdata (LAST), потом fallback на securities (PREVPRICE)
            List<Map<String, Object>> marketdata = parser.parseBlock(body, "marketdata");
            for (Map<String, Object> row : marketdata) {
                double last = asDouble(row, "LAST");
                if (last > 0) return last;
            }
            List<Map<String, Object>> securities = parser.parseBlock(body, "securities");
            for (Map<String, Object> row : securities) {
                double prev = asDouble(row, "PREVPRICE");
                if (prev > 0) return prev;
            }
            throw new MoexClient.MoexApiException("Не удалось определить цену для " + ticker);
        });
    }

    // ---- internals ----

    private List<MoexSecurity> mergeBlocks(String body, int limit) {
        List<Map<String, Object>> securities = parser.parseBlock(body, "securities");
        List<Map<String, Object>> marketdata = parser.parseBlock(body, "marketdata");

        // Индексируем marketdata по SECID для O(1) поиска
        Map<String, Double> lastBySecId = new HashMap<>();
        for (Map<String, Object> row : marketdata) {
            String secId = asString(row, "SECID");
            lastBySecId.put(secId, asDouble(row, "LAST"));
        }

        List<MoexSecurity> result = new ArrayList<>(Math.min(limit, securities.size()));
        for (Map<String, Object> row : securities) {
            if (result.size() >= limit) break;
            String secId    = asString(row, "SECID");
            String shortNm  = asString(row, "SHORTNAME");
            double prevPr   = asDouble(row, "PREVPRICE");
            double lastPr   = lastBySecId.getOrDefault(secId, 0.0);
            // Отсекаем «пустые» бумаги без цены — их бесполезно показывать
            if (prevPr <= 0 && lastPr <= 0) continue;
            result.add(new MoexSecurity(secId, shortNm, lastPr, prevPr));
        }
        return result;
    }
}
