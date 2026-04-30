# REST API в Java: методичка

> Материалы для пары курса ООП. Сопровождает код `investment-app` — работу с MOEX ISS.

---

## 1. Что такое REST

**REST** (Representational State Transfer) — это стиль построения веб-сервисов. У него простые правила:

1. Всё в системе — **ресурсы** (акция, список акций, профиль пользователя).
2. У каждого ресурса есть **URL** — уникальный адрес.
3. Действия с ресурсом выражаются стандартными **HTTP-методами**.
4. Данные в запросах и ответах — обычно **JSON**.

Пример:
```
GET  /iss/securities           → список всех бумаг
GET  /iss/securities/SBER      → одна бумага
POST /orders                   → создать заявку
PUT  /orders/42                → обновить заявку
DELETE /orders/42              → отменить
```

Никакой магии — просто договорённость, как называть URLы.

---

## 2. HTTP в двух словах

**Запрос** состоит из:
- **Метод**: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `HEAD`, `OPTIONS`.
- **URL**: `https://iss.moex.com/iss/securities.json`.
- **Заголовки**: `Accept: application/json`, `User-Agent: ...`.
- **Тело** (не у GET/DELETE): JSON, form-data, что-то ещё.

**Ответ**:
- **Статус-код**: `200`, `404`, `500` и др.
- **Заголовки**: `Content-Type: application/json; charset=utf-8`.
- **Тело**: JSON-документ.

### Идемпотентность и безопасность

| Метод | Безопасный? | Идемпотентный? |
|-------|------------|----------------|
| GET    | ✅ да (не меняет данные) | ✅ да |
| POST   | ❌ | ❌ (повторный POST создаст второй ресурс) |
| PUT    | ❌ | ✅ (повторный PUT даёт тот же результат) |
| DELETE | ❌ | ✅ (удалить уже удалённое — тоже «ок») |

Запомнить: **GET только для чтения.** Если ваш GET что-то меняет — это ошибка дизайна.

### Статус-коды

| Диапазон | Смысл | Примеры |
|----------|-------|---------|
| `1xx`    | Информация | `100 Continue` |
| `2xx`    | Успех      | `200 OK`, `201 Created`, `204 No Content` |
| `3xx`    | Редирект   | `301 Moved Permanently`, `304 Not Modified` |
| `4xx`    | Ошибка клиента | `400 Bad Request`, `401 Unauthorized`, `403 Forbidden`, `404 Not Found`, `429 Too Many Requests` |
| `5xx`    | Ошибка сервера | `500 Internal Server Error`, `502 Bad Gateway`, `503 Service Unavailable`, `504 Gateway Timeout` |

**Правило большого пальца**: `4xx` — виноват клиент (исправь запрос), `5xx` — виноват сервер (попробуй позже).

---

## 3. Боевой пример: MOEX ISS

Московская биржа предоставляет публичный API для получения данных торгов — **MOEX ISS**. Авторизация не нужна, ограничений на частоту практически нет.

### Эндпоинт

```
GET https://iss.moex.com/iss/engines/stock/markets/shares/boards/TQBR/securities.json
```

Он возвращает список бумаг, торгующихся в режиме TQBR (основной режим фондового рынка).

### Особенность формата MOEX

Ответ приходит в компактном виде:

```json
{
  "securities": {
    "columns": ["SECID", "SHORTNAME", "PREVPRICE"],
    "data": [
      ["SBER", "Сбербанк", 310.5],
      ["GAZP", "ГАЗПРОМ",  168.2]
    ]
  },
  "marketdata": {
    "columns": ["SECID", "LAST"],
    "data":    [["SBER", 311.0]]
  }
}
```

Почему так? Эффективно по размеру (имена колонок не повторяются для каждой строки). Но неудобно работать. Поэтому первое, что мы пишем — адаптер, который превращает это в `List<Map<String, Object>>`.

См. [`MoexJsonParser`](../src/main/java/com/example/investment/service/moex/MoexJsonParser.java).

---

## 4. HttpClient из JDK 11+

Начиная с Java 11, в стандартной библиотеке есть современный `java.net.http.HttpClient` — можно не тащить OkHttp/Apache.

### Синхронный вариант (плохой для UI)

```java
HttpClient client = HttpClient.newHttpClient();
HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create("https://iss.moex.com/iss/securities.json"))
        .GET()
        .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());
```

Метод `send` **блокирует вызывающий поток** до получения ответа.

### Асинхронный вариант (правильный)

```java
CompletableFuture<String> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
        .thenApply(HttpResponse::body);

future.thenAccept(body -> System.out.println(body.substring(0, 200)));
// вызывающий поток не ждёт — продолжает работу
```

Ключевая разница: `sendAsync` сразу возвращает `CompletableFuture`. Результат появится позже, в потоке внутреннего executor'а HttpClient.

### Собираем по кусочкам: CompletableFuture

```java
moexClient.getAsync("/iss/.../securities.json")      // CompletableFuture<String>
        .thenApply(parser::parseBlock)                // → CompletableFuture<List<...>>
        .thenApply(this::mergeWithMarketdata)         // → CompletableFuture<List<MoexSecurity>>
        .thenAccept(this::renderToTable)              // → CompletableFuture<Void>
        .exceptionally(ex -> {                        // обработка ошибок
            showAlert(ex.getMessage());
            return null;
        });
```

Каждый `thenXxx` ничего не блокирует — он вешает колбэк. Цепочка выполняется, как только предыдущий шаг завершился.

---

## 5. Ошибки и таймауты

**Три вещи, о которых забывают новички:**

1. **Connect timeout** и **request timeout** — задайте явно:
   ```java
   HttpClient.newBuilder()
           .connectTimeout(Duration.ofSeconds(5))    // не дольше 5 сек на подключение
           .build();
   // + для каждого запроса:
   HttpRequest.newBuilder().uri(...).timeout(Duration.ofSeconds(10)).build();
   ```
   По умолчанию — бесконечность. Если удалённый сервер недоступен, поток может висеть часами.

2. **Проверяйте статус**:
   ```java
   if (response.statusCode() / 100 != 2) {
       throw new MyApiException("HTTP " + response.statusCode());
   }
   ```
   JDK не кидает исключение на 4xx/5xx — это ваша ответственность.

3. **Не декодируйте JSON без try-catch**:
   ```java
   try {
       return objectMapper.readTree(body);
   } catch (JsonProcessingException e) {
       throw new MyApiException("Некорректный JSON: " + e.getMessage(), e);
   }
   ```

---

## 6. Парсинг JSON: Jackson

**Добавьте зависимость**:
```groovy
implementation 'com.fasterxml.jackson.core:jackson-databind:2.17.2'
```

**Два способа использования**:

### 6.1. Маппинг на POJO (хорошо, когда JSON «нормальный»)

```java
public class MoexResponse {
    @JsonProperty("securities") public Block securities;
    @JsonProperty("marketdata") public Block marketdata;

    public static class Block {
        public List<String> columns;
        public List<List<Object>> data;
    }
}

ObjectMapper mapper = new ObjectMapper();
MoexResponse resp = mapper.readValue(body, MoexResponse.class);
```

### 6.2. Дерево `JsonNode` (когда структура нестандартная)

```java
JsonNode root = mapper.readTree(body);
JsonNode data = root.path("securities").path("data");
for (JsonNode row : data) {
    System.out.println(row.get(0).asText());
}
```

Мы используем смешанный подход: `JsonNode` для разбора `columns + data`, POJO (`MoexSecurity`) для предметной области. См. `MoexJsonParser`.

---

## 7. UI-поток JavaFX и HTTP

### Что такое UI-поток

JavaFX однопоточный относительно UI: всё, что касается сцены (`Scene`, `Node`, `TableView`) может трогать только **JavaFX Application Thread**. Он же обрабатывает клики и клавиатуру.

Если этот поток занят — всё окно замерзает.

### Антипример

```java
@FXML
public void onLoad() {
    String body = httpClient.send(req, ...).body();  // ← блокирует UI-поток
    List<MoexSecurity> data = parse(body);
    table.setItems(FXCollections.observableArrayList(data));
}
```

Пока идёт запрос (1–10 сек) — окно не реагирует, курсор «крутится», кнопка «зависла».

### Правильно — `Task<T>`

```java
@FXML
public void onLoad() {
    Task<List<MoexSecurity>> task = new Task<>() {
        @Override
        protected List<MoexSecurity> call() throws Exception {
            return service.loadTopShares(10).get();  // выполняется в фоне
        }
    };

    task.setOnRunning   (e -> statusLabel.setText("Загружаю…"));
    task.setOnSucceeded (e -> table.setItems(
            FXCollections.observableArrayList(task.getValue())));
    task.setOnFailed    (e -> showAlert(task.getException().getMessage()));

    new Thread(task).start();   // запуск в ФОНОВОМ потоке
}
```

**Важно**: колбэки `setOnXxx` автоматически вызываются в UI-потоке — там можно трогать виджеты.

### `Platform.runLater` — если `Task` не подходит

Когда вы уже в чужом потоке (например, колбэк Jackson) и надо обновить UI:

```java
Platform.runLater(() -> statusLabel.setText("Готово"));
```

`runLater` кладёт задачу в очередь UI-потока; он её выполнит когда освободится.

---

## 8. Чек-лист «HTTP-запрос из JavaFX»

- [ ] Задан **connect timeout** (5–10 сек).
- [ ] Задан **request timeout** на уровне `HttpRequest`.
- [ ] Проверен **статус-код**, не-2xx → исключение с внятным текстом.
- [ ] JSON-парсинг в `try/catch`.
- [ ] Запрос запущен в **`Task` + `new Thread().start()`**, не `task.run()`.
- [ ] Обновления UI в `setOnSucceeded` / `setOnFailed` или через `Platform.runLater`.
- [ ] Кнопка «Загрузить» **disable**-ится на время запроса, чтобы пользователь не нажал её 10 раз.

---

## 9. Что читать дальше

- [MOEX ISS Reference](https://iss.moex.com/iss/reference/) — полная дока по всем эндпоинтам.
- JEP 321: HTTP Client (стандартизация `HttpClient`).
- [Jackson Databind wiki](https://github.com/FasterXML/jackson-databind) — аннотации, кастомные десериализаторы.
- [JavaFX Task](https://openjfx.io/javadoc/17/javafx.graphics/javafx/concurrent/Task.html) — документация.
