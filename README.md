# Инвестиционный Портфель (Investment Portfolio)

Учебное JavaFX-приложение для курса ООП по Java. Служит живым материалом для пары по двум темам:

1. **REST API** — реальные запросы к Московской бирже (MOEX ISS), парсинг JSON, асинхронные вызовы и UI-поток JavaFX.
2. **Многопоточность** — демонстрация race condition на примере «два клиента покупают акции» и четырёх способов это починить (`synchronized`, `ReentrantLock`, `AtomicInteger`, `Semaphore`).


## 🚀 Быстрый старт

### Требования

- Java 17 или выше
- Gradle 8.x (встроенный `gradlew`)

### Запуск

```bash
./gradlew run
```

### Сборка

```bash
./gradlew build
./gradlew clean  # очистка артефактов
```

---

## 📚 Пошаговый гайд по реализации

Этот проект создан как учебный пример. Следуйте шагам ниже, чтобы понять архитектуру и реализовать приложение с нуля.

### Шаг 1: Настройка проекта

**Что делаем:** Создаём структуру Gradle-проекта с JavaFX.

**Файлы:**
- `build.gradle` — зависимости и плагины
- `settings.gradle` — имя проекта
- `gradle.properties` — настройки Gradle

**Ключевые моменты:**
```groovy
plugins {
    id 'application'
    id 'org.openjfx.javafxplugin' version '0.0.14'
}

javafx {
    version = '17'
    modules = ['javafx.controls', 'javafx.fxml']
}
```

**Команда для проверки:**
```bash
./gradlew dependencies
```

---

### Шаг 2: Точка входа приложения

**Что делаем:** Создаём главный класс, расширяющий `Application`.

**Файл:** `src/main/java/com/example/investment/InvestmentApp.java`

**Ключевые концепции:**
- `launch()` — запускает JavaFX жизненный цикл
- `start(Stage)` — метод инициализации UI
- `FXMLLoader` — загрузка FXML-разметки

**Жизненный цикл JavaFX:**
```
main() → launch() → init() → start(Stage) → stop()
```

---

### Шаг 3: Модель данных (Model)

**Что делаем:** Создаём класс `Asset` с JavaFX Properties.

**Файл:** `src/main/java/com/example/investment/model/Asset.java`

**Ключевые концепции:**

| Концепция | Зачем |
|-----------|-------|
| `StringProperty`, `DoubleProperty` | Привязка данных к UI |
| `property()` методы | Для `TableView` binding |
| Вычисляемые методы | `getGain()`, `getGainPercent()` |

**Пример привязки:**
```java
label.textProperty().bind(asset.nameProperty());
// Label автоматически обновляется при изменении name
```

---

### Шаг 4: FXML-разметка (View)

**Что делаем:** Создаём UI-разметку в FXML.

**Файл:** `src/main/resources/com/example/investment/main-view.fxml`

**Структура:**
```
BorderPane
├── top: Заголовок + итоговая стоимость
├── center: SplitPane
│   ├── TableView (список активов)
│   └── PieChart (диаграмма)
└── bottom: Кнопки + статус
```

**Ключевые элементы:**
- `fx:id` — связь с контроллером
- `onAction="#methodName"` — обработчики кнопок
- `fx:controller` — указание контроллера

---

### Шаг 5: Контроллер (Controller)

**Что делаем:** Создаём `MainController` для обработки логики UI.

**Файл:** `src/main/java/com/example/investment/controller/MainController.java`

**Ключевые концепции:**

| Концепция | Описание |
|-----------|----------|
| `@FXML` | Инъекция элементов из FXML |
| `ObservableList` | "Умный" список с уведомлениями |
| `PropertyValueFactory` | Привязка колонок к полям |
| `CellFactory` | Кастомизация ячеек таблицы |

**Настройка колонок:**
```java
colName.setCellValueFactory(new PropertyValueFactory<>("name"));
colGain.setCellValueFactory(data -> 
    new SimpleDoubleProperty(data.getValue().getGain()).asObject()
);
```

---

### Шаг 6: Сервисный слой (Service)

**Что делаем:** Выносим бизнес-логику в отдельный класс.

**Файл:** `src/main/java/com/example/investment/service/PriceSimulator.java`

**Принципы:**
- Контроллер не знает *как* работает симуляция
- Легко заменить на реальный API биржи
- Чистая функция: вход → выход

---

### Шаг 7: Диалоги и взаимодействие

**Что делаем:** Добавляем диалог добавления актива.

**Ключевые моменты:**
- `Dialog<Asset>` — типизированный диалог
- `GridPane` — форма ввода
- `ResultConverter` — преобразование результата

---

## 📊 Архитектура приложения

```
┌─────────────────────────────────────────────────────────┐
│                    InvestmentApp                        │
│                    (точка входа)                        │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                   MainController                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐ │
│  │ TableView   │  │ PieChart    │  │ Dialogs         │ │
│  └─────────────┘  └─────────────┘  └─────────────────┘ │
└───────────┬─────────────────────────────────┬───────────┘
            │                                 │
            ▼                                 ▼
┌───────────────────────┐         ┌───────────────────────┐
│      Asset (Model)    │         │  PriceSimulator       │
│  ┌─────────────────┐  │         │  (Service Layer)      │
│  │ JavaFX Props    │  │         └───────────────────────┘
│  │ Вычисляемые     │  │
│  │ поля            │  │
│  └─────────────────┘  │
└───────────────────────┘
```

---

## 🔍 Как смотреть коммиты

### Просмотр истории

```bash
# Все коммиты (кратко)
git log --oneline

# Подробно с изменениями
git log -p

# Последние 5 коммитов
git log -n 5

# Красивый формат
git log --graph --oneline --decorate
```

### Что было добавлено в каждом коммите

```bash
# Показать изменения в конкретном коммите
git show <commit-hash>

# Показать только имена изменённых файлов
git show --name-only <commit-hash>

# Статистика изменений
git show --stat <commit-hash>
```

### Сравнение версий

```bash
# Изменения с последнего коммита
git diff HEAD

# Изменения между коммитами
git diff abc123 def456

# Изменения в конкретном файле
git diff HEAD -- src/main/java/.../Asset.java
```

### Поиск по коммитам

```bash
# Найти коммиты по сообщению
git log --grep="fix"

# Найти коммиты по автору
git log --author="lostflydev"

# Найти коммиты, изменившие файл
git log -- src/main/java/.../Asset.java
```

---

## 📁 Структура проекта

```
investment-app/
├── build.gradle              # Конфигурация Gradle (+ Jackson, JUnit 5)
├── README.md                 # Эта документация
├── docs/                     # Учебные материалы для пары
│   ├── REST_API.md           # Методичка по REST
│   ├── CONCURRENCY.md        # Методичка по многопоточке
│   ├── rest-api.html         # HTML-версия (раздаточный материал)
│   ├── concurrency.html      # HTML-версия
│   └── assets/styles.css
└── src/
    ├── main/
    │   ├── java/com/example/investment/
    │   │   ├── InvestmentApp.java
    │   │   ├── model/
    │   │   │   ├── Asset.java
    │   │   │   └── MoexSecurity.java          # DTO биржи
    │   │   ├── controller/
    │   │   │   ├── MainController.java
    │   │   │   ├── MoexBrowserController.java # REST UI
    │   │   │   └── OrderBookController.java   # Race condition UI
    │   │   ├── service/
    │   │   │   ├── PriceSimulator.java
    │   │   │   └── moex/                      # REST слой
    │   │   │       ├── MoexClient.java        # HttpClient обёртка
    │   │   │       ├── MoexJsonParser.java    # columns+data → List<Map>
    │   │   │       └── MoexService.java       # Доменное API
    │   │   └── concurrency/                   # Race condition + решения
    │   │       ├── OrderBook.java             # Интерфейс
    │   │       ├── UnsafeOrderBook.java       # Сломанная версия
    │   │       ├── SynchronizedOrderBook.java
    │   │       ├── LockOrderBook.java         # ReentrantLock + tryLock
    │   │       ├── AtomicOrderBook.java       # CAS
    │   │       ├── SemaphoreOrderBook.java
    │   │       ├── TradeClient.java           # Runnable клиент
    │   │       └── TradeLog.java              # Thread-safe журнал
    │   └── resources/com/example/investment/
    │       ├── main-view.fxml
    │       ├── moex-browser.fxml
    │       └── order-book.fxml
    └── test/java/com/example/investment/
        ├── concurrency/OrderBookConcurrencyTest.java
        └── service/moex/MoexJsonParserTest.java
```

---

## 🛠 Полезные команды

| Команда | Описание |
|---------|----------|
| `./gradlew run` | Запуск приложения |
| `./gradlew build` | Сборка проекта |
| `./gradlew clean` | Очистка build-артефактов |
| `./gradlew dependencies` | Показать зависимости |
| `./gradlew test` | Запуск тестов |

---

## 📝 Следующие шаги для развития

1. **Добавить сохранение:** JSON/XML/База данных
2. **Реальный API:** ✅ сделано — см. `service/moex/` и окно «Обзор MOEX»
3. **Многопоточность:** ✅ сделано — см. `concurrency/` и окно «Торговый стакан»
4. **Тесты:** ✅ базовый набор — см. `src/test/java`
5. **Стили:** Вынести CSS в отдельный файл
6. **Экспорт:** Выгрузка отчёта в CSV/PDF
7. **График истории цен:** через `/iss/history/...` — ДЗ студентам

---

## 🎓 Что добавлено для учебной пары

### Обзор MOEX (кнопка «📈 Обзор MOEX»)
Загружает 10 бумаг с режима TQBR (акции T+2 фондового рынка) через **реальный REST API** MOEX ISS. Показывает работу с:

- `java.net.http.HttpClient` + `CompletableFuture`
- `Jackson` для парсинга JSON (формат `columns + data`)
- JavaFX `Task` + `Platform.runLater` — правильная работа с сетью из UI-потока

Файлы: `service/moex/*.java`, `controller/MoexBrowserController.java`, `resources/.../moex-browser.fxml`.

### Торговый стакан (кнопка «⚔ Торговый стакан»)
Демонстрирует проблему **race condition**: пул 10 акций, два клиента по 7 штук каждый. На реализации `Unsafe` видно overselling (продано 14), на четырёх правильных — всё корректно.

Реализации в пакете `concurrency/`:
- `UnsafeOrderBook` — намеренно сломанная (check-then-act race)
- `SynchronizedOrderBook` — `synchronized` метод
- `LockOrderBook` — `ReentrantLock` (fair) + `tryLock` с таймаутом
- `AtomicOrderBook` — lock-free CAS-цикл на `AtomicInteger`
- `SemaphoreOrderBook` — квотирование через `Semaphore`

Тесты `OrderBookConcurrencyTest` с `@RepeatedTest` показывают:
- `Unsafe` стабильно переторговывает (нужно для демо бага студентам);
- остальные 4 реализации — всегда `oversold == 0`.

### Обновление цен с биржи (кнопка «🔄 Обновить с биржи»)
Пример параллельных асинхронных запросов: `CompletableFuture.allOf(...)` — для всех активов портфеля одновременно запускаются HTTP-запросы к MOEX, цены обновляются по мере ответов.

---

## 👤 Автор

**lostflydev**  
Email: lostfly.dev@gmail.com

---

## 📄 Лицензия

Учебный проект — свободное использование в образовательных целях.
