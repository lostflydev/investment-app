# Инвестиционный Портфель (Investment Portfolio)

Учебное JavaFX-приложение для управления инвестиционным портфелем с симуляцией изменения цен.

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
├── build.gradle              # Конфигурация Gradle
├── settings.gradle           # Настройки проекта
├── gradle.properties         # Свойства Gradle
├── gradlew, gradlew.bat      # Gradle wrapper
├── .gitignore                # Игнорируемые файлы
├── README.md                 # Эта документация
└── src/
    └── main/
        ├── java/
        │   └── com/example/investment/
        │       ├── InvestmentApp.java      # Точка входа
        │       ├── model/
        │       │   └── Asset.java          # Модель актива
        │       ├── controller/
        │       │   └── MainController.java # Логика UI
        │       └── service/
        │           └── PriceSimulator.java # Бизнес-логика
        └── resources/
            └── com/example/investment/
                └── main-view.fxml          # FXML-разметка
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
2. **Реальный API:** Подключение к биржевому API
3. **Валидация:** Проверка введённых данных
4. **Тесты:** Unit-тесты для `PriceSimulator` и `Asset`
5. **Стили:** Вынести CSS в отдельный файл
6. **Экспорт:** Выгрузка отчёта в CSV/PDF

---

## 👤 Автор

**lostflydev**  
Email: lostfly.dev@gmail.com

---

## 📄 Лицензия

Учебный проект — свободное использование в образовательных целях.
