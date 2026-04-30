package com.example.investment.controller;

import com.example.investment.concurrency.*;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Контроллер окна «Торговый стакан».
 *
 * Сценарий :
 *   1. Выбрать реализацию OrderBook (Unsafe → и 4 исправленные).
 *   2. Нажать "▶ Запустить" — несколько клиентов стартуют одновременно
 *      (CountDownLatch гарантирует одновременный старт).
 *   3. Наблюдать за счётчиками в реальном времени (AnimationTimer).
 *   4. При Unsafe увидеть overselling (продано больше, чем было в пуле).
 *   5. "⏩ Прогнать 20 раз" — стресс-тест для статистики: сколько прогонов
 *      из 20 показали overselling.
 */
public class OrderBookController {

    @FXML private ComboBox<String> implCombo;
    @FXML private Spinner<Integer> poolSpinner;
    @FXML private Spinner<Integer> clientsSpinner;
    @FXML private Spinner<Integer> targetSpinner;
    @FXML private Spinner<Integer> chunkSpinner;

    @FXML private Label availLabel;
    @FXML private Label soldLabel;
    @FXML private Label oversoldLabel;
    @FXML private Label clientsStatus;
    @FXML private Label statusLabel;
    @FXML private Label batchLabel;

    @FXML private ListView<String> logView;

    private final ObservableList<String> logItems = FXCollections.observableArrayList();

    /** Текущая реализация OrderBook, создаётся перед каждым запуском. */
    private OrderBook book;
    private List<TradeClient> clients = List.of();

    /** Фоновый ticker обновляет счётчики UI независимо от потока, который их меняет. */
    private AnimationTimer ticker;

    @FXML
    public void initialize() {
        implCombo.setItems(FXCollections.observableArrayList(
                "Unsafe (без синхронизации)",
                "Synchronized",
                "ReentrantLock (fair) + tryLock",
                "AtomicInteger (CAS)",
                "Semaphore (fair)"
        ));
        implCombo.getSelectionModel().selectFirst();

        poolSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 10));
        clientsSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(2, 20, 2));
        targetSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1000, 7));
        chunkSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 100, 1));

        logView.setItems(logItems);

        // Тикер: 20 раз в секунду обновляет счётчики в UI.
        ticker = new AnimationTimer() {
            @Override public void handle(long now) { refreshCounters(); }
        };
        ticker.start();

        resetState();
    }

    private OrderBook createBook(String name) {
        return switch (name) {
            case "Synchronized"                   -> new SynchronizedOrderBook();
            case "ReentrantLock (fair) + tryLock" -> new LockOrderBook();
            case "AtomicInteger (CAS)"            -> new AtomicOrderBook();
            case "Semaphore (fair)"               -> new SemaphoreOrderBook();
            default                               -> new UnsafeOrderBook();
        };
    }

    @FXML
    public void onReset() {
        resetState();
        statusLabel.setText("Сброшено");
    }

    private void resetState() {
        book = createBook(implCombo.getValue());
        book.reset(poolSpinner.getValue());
        clients = List.of();
        logItems.clear();
        batchLabel.setText("");
        // Подписка на новые события журнала — попадают в UI через Platform.runLater
        book.log().subscribe(entry -> Platform.runLater(() -> {
            logItems.add(entry.toString());
            // Ограничиваем размер лога, чтобы UI не тонул
            if (logItems.size() > 500) logItems.remove(0, 100);
            logView.scrollTo(logItems.size() - 1);
        }));
        refreshCounters();
    }

    @FXML
    public void onRun() {
        resetState();
        int clientsCount = clientsSpinner.getValue();
        int target       = targetSpinner.getValue();
        int chunk        = chunkSpinner.getValue();
        int pool         = poolSpinner.getValue();

        statusLabel.setText(String.format(
                "Запуск: %s, пул=%d, клиентов=%d × %d, чанк=%d",
                book.name(), pool, clientsCount, target, chunk));

        launchScenario(clientsCount, target, chunk, false);
    }

    @FXML
    public void onBatch() {
        int runs = 20;
        batchLabel.setText("");
        statusLabel.setText("Прогоняю " + runs + " раз…");
        Thread batch = new Thread(() -> {
            int oversoldRuns = 0;
            int maxOversold  = 0;
            for (int i = 0; i < runs; i++) {
                CountDownLatch done = new CountDownLatch(1);
                Platform.runLater(() -> { resetState(); done.countDown(); });
                try { done.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
                runScenarioBlocking(clientsSpinner.getValue(), targetSpinner.getValue(), chunkSpinner.getValue());
                if (book.oversold() > 0) oversoldRuns++;
                maxOversold = Math.max(maxOversold, book.oversold());
            }
            final int fo = oversoldRuns;
            final int fm = maxOversold;
            Platform.runLater(() -> batchLabel.setText(String.format(
                    "Из %d прогонов переторговка в %d, макс. %d сверх пула",
                    runs, fo, fm)));
        }, "batch-runner");
        batch.setDaemon(true);
        batch.start();
    }

    private void launchScenario(int clientsCount, int target, int chunk, boolean blocking) {
        CountDownLatch startGate = new CountDownLatch(1);
        List<TradeClient> list = new ArrayList<>(clientsCount);
        for (int i = 0; i < clientsCount; i++) {
            String id = String.valueOf((char) ('A' + i));
            // maxAttempts: с запасом, чтобы клиент успел купить свою долю
            list.add(new TradeClient(id, book, target, chunk, target * 4, startGate));
        }
        this.clients = list;
        ExecutorService pool = Executors.newFixedThreadPool(clientsCount,
                r -> { Thread t = new Thread(r); t.setDaemon(true); t.setName("trader-" + r.hashCode()); return t; });
        for (TradeClient c : list) pool.submit(c);
        startGate.countDown(); // одновременный старт

        Runnable waiter = () -> {
            pool.shutdown();
            try { pool.awaitTermination(10, TimeUnit.SECONDS); } catch (InterruptedException ignored) { }
            Platform.runLater(this::refreshCounters);
        };
        if (blocking) waiter.run();
        else {
            Thread t = new Thread(waiter, "scenario-waiter");
            t.setDaemon(true);
            t.start();
        }
    }

    private void runScenarioBlocking(int clientsCount, int target, int chunk) {
        launchScenario(clientsCount, target, chunk, true);
    }

    private void refreshCounters() {
        if (book == null) return;
        availLabel.setText(String.valueOf(book.available()));
        soldLabel.setText(String.valueOf(book.sold()));
        int oversold = book.oversold();
        oversoldLabel.setText(String.valueOf(oversold));
        oversoldLabel.setStyle(oversold > 0
                ? "-fx-text-fill: #f38ba8; -fx-font-size: 24; -fx-font-weight: bold;"
                : "-fx-text-fill: #a6e3a1; -fx-font-size: 24; -fx-font-weight: bold;");

        StringBuilder sb = new StringBuilder();
        for (TradeClient c : clients) {
            sb.append(String.format("Клиент %s: %d / %d%n", c.id(), c.bought(), c.target()));
        }
        clientsStatus.setText(sb.toString());
    }
}
