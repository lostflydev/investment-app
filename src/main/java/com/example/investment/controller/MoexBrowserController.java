package com.example.investment.controller;

import com.example.investment.model.MoexSecurity;
import com.example.investment.service.moex.MoexService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.util.List;
import java.util.function.Consumer;

/**
 * Контроллер окна "Обзор биржи MOEX".
 *
 * Ключевой учебный момент — правильная работа с сетью из JavaFX:
 *   1. Обработчик кнопки выполняется в UI-потоке.
 *   2. Мы СОЗДАЁМ {@link Task}, но его {@code call()} выполняется
 *      в фоновом потоке ({@code new Thread(task).start()}).
 *   3. Колбэки Task ({@code setOnSucceeded}, {@code setOnFailed}) Fх выполняет
 *      уже в UI-потоке — в них можно безопасно трогать TableView.
 *   4. Если забыть пункт 2 и вызвать {@code service.loadTopShares(10).get()}
 *      прямо в обработчике — замёрзнет всё окно на время запроса.
 */
public class MoexBrowserController {

    @FXML private Button btnLoad;
    @FXML private ProgressIndicator progress;
    @FXML private TableView<MoexSecurity> table;
    @FXML private TableColumn<MoexSecurity, String> colTicker;
    @FXML private TableColumn<MoexSecurity, String> colShortName;
    @FXML private TableColumn<MoexSecurity, Double> colLast;
    @FXML private TableColumn<MoexSecurity, Double> colPrev;
    @FXML private Label statusLabel;

    private final ObservableList<MoexSecurity> rows = FXCollections.observableArrayList();
    private final MoexService moex = new MoexService();

    /** Сколько бумаг грузить за один запрос. */
    private static final int LIMIT = 10;

    /** Колбэк для передачи выбранных бумаг обратно в главный экран. */
    private Consumer<List<MoexSecurity>> onAddToPortfolio = list -> { };

    public void setOnAddToPortfolio(Consumer<List<MoexSecurity>> cb) {
        this.onAddToPortfolio = cb == null ? list -> { } : cb;
    }

    @FXML
    public void initialize() {
        colTicker.setCellValueFactory(new PropertyValueFactory<>("ticker"));
        colShortName.setCellValueFactory(new PropertyValueFactory<>("shortName"));
        colLast.setCellValueFactory(new PropertyValueFactory<>("lastPrice"));
        colPrev.setCellValueFactory(new PropertyValueFactory<>("prevPrice"));

        setPriceFormat(colLast);
        setPriceFormat(colPrev);

        table.setItems(rows);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void setPriceFormat(TableColumn<MoexSecurity, Double> col) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double v, boolean empty) {
                super.updateItem(v, empty);
                if (empty || v == null || v == 0.0) { setText("—"); }
                else { setText(String.format("%.2f", v)); }
                setStyle("-fx-text-fill: #cdd6f4;");
            }
        });
    }

    @FXML
    public void onLoad() {
        // 1. Готовим Task. call() будет выполнен в ФОНОВОМ потоке.
        Task<List<MoexSecurity>> task = new Task<>() {
            @Override
            protected List<MoexSecurity> call() throws Exception {
                return moex.loadTopShares(LIMIT).get(); // блокируется, но не в UI
            }
        };

        // 2. Подписываемся на жизненный цикл. Все эти колбэки — в UI-потоке.
        task.setOnRunning(e -> {
            btnLoad.setDisable(true);
            progress.setVisible(true);
            statusLabel.setText("Загружаю данные с MOEX…");
        });
        task.setOnSucceeded(e -> {
            List<MoexSecurity> result = task.getValue();
            rows.setAll(result);
            statusLabel.setText("Загружено: " + result.size() + " бумаг");
            btnLoad.setDisable(false);
            progress.setVisible(false);
        });
        task.setOnFailed(e -> {
            Throwable ex = task.getException();
            String msg = ex == null ? "неизвестная ошибка" : ex.getMessage();
            statusLabel.setText("Ошибка: " + msg);
            btnLoad.setDisable(false);
            progress.setVisible(false);
            Alert alert = new Alert(Alert.AlertType.ERROR,
                    "Не удалось загрузить данные MOEX.\n\n" + msg);
            alert.setHeaderText("Ошибка сети или парсинга");
            alert.showAndWait();
        });

        // 3. Запускаем в фоновом потоке. Без этого ШАГА всё выполнится
        //    в UI-потоке и окно замёрзнет.
        Thread t = new Thread(task, "moex-loader");
        t.setDaemon(true);
        t.start();

        // (для учебной демонстрации можно раскомментировать альтернативу:)
        // task.run();  // ← ПЛОХО: блокирует UI до завершения запроса
    }

    @FXML
    public void onAddSelected() {
        List<MoexSecurity> selected = List.copyOf(table.getSelectionModel().getSelectedItems());
        if (selected.isEmpty()) {
            statusLabel.setText("Ничего не выбрано — выделите строки (Ctrl/Shift + клик)");
            return;
        }
        onAddToPortfolio.accept(selected);
        statusLabel.setText("Добавлено в портфель: " + selected.size());
    }

    /** Явная демонстрация Platform.runLater — для учебного примера. */
    @SuppressWarnings("unused")
    private void demoPlatformRunLater(String text) {
        // Если мы В фоновом потоке и хотим изменить UI — только через runLater.
        Platform.runLater(() -> statusLabel.setText(text));
    }
}
