package com.example.investment.controller;

import com.example.investment.InvestmentApp;
import com.example.investment.model.Asset;
import com.example.investment.model.MoexSecurity;
import com.example.investment.service.PriceSimulator;
import com.example.investment.service.moex.MoexService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.concurrent.CompletableFuture;

/**
 * FXML-контроллер главного экрана.
 *
 * Ключевые концепции на этом этапе:
 * - ObservableList: список с уведомлениями — TableView автоматически
 *   обновляется при добавлении/удалении элементов
 * - PropertyValueFactory: связывает колонку таблицы с Property поля Asset
 * - CellFactory: кастомизирует отображение ячеек (цвет для прибыли/убытка)
 */
public class MainController {

    @FXML private TableView<Asset>            assetsTable;
    @FXML private TableColumn<Asset, String>  colName;
    @FXML private TableColumn<Asset, String>  colTicker;
    @FXML private TableColumn<Asset, Double>  colQuantity;
    @FXML private TableColumn<Asset, Double>  colBuyPrice;
    @FXML private TableColumn<Asset, Double>  colCurrentPrice;
    @FXML private TableColumn<Asset, Double>  colValue;
    @FXML private TableColumn<Asset, Double>  colGain;
    @FXML private TableColumn<Asset, Double>  colGainPercent;
    @FXML private Label    statusLabel;
    @FXML private Label    totalLabel;
    @FXML private PieChart pieChart;

    // ObservableList — "умный" список: TableView слушает его изменения
    private final ObservableList<Asset> assets    = FXCollections.observableArrayList();
    private final PriceSimulator        simulator = new PriceSimulator();
    private final MoexService           moex      = new MoexService();

    @FXML
    public void initialize() {
        setupColumns();
        assetsTable.setItems(assets);

        // Тестовые данные: 3 российские акции
        assets.add(new Asset("Сбербанк", "SBER", 100, 250.0,  310.5));
        assets.add(new Asset("Яндекс",   "YDEX",  20, 2800.0, 3150.0));
        assets.add(new Asset("Т-Акции",  "T",     50, 2900.0, 3200.0));

        // Слушатель: автоматически перестраивает диаграмму при изменении списка
        assets.addListener((ListChangeListener<Asset>) c -> updatePieChart());

        updateTotal();
        updatePieChart();
        statusLabel.setText("Загружено " + assets.size() + " актива");
    }

    /** Настройка колонок таблицы через PropertyValueFactory. */
    private void setupColumns() {
        colName.setCellValueFactory(new PropertyValueFactory<>("name"));
        colTicker.setCellValueFactory(new PropertyValueFactory<>("ticker"));
        colQuantity.setCellValueFactory(new PropertyValueFactory<>("quantity"));
        colBuyPrice.setCellValueFactory(new PropertyValueFactory<>("buyPrice"));
        colCurrentPrice.setCellValueFactory(new PropertyValueFactory<>("currentPrice"));

        // Вычисляемые колонки — используем лямбду вместо PropertyValueFactory
        colValue.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(
                        data.getValue().getCurrentValue()).asObject());
        colGain.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(
                        data.getValue().getGain()).asObject());
        colGainPercent.setCellValueFactory(data ->
                new javafx.beans.property.SimpleDoubleProperty(
                        data.getValue().getGainPercent()).asObject());

        // Форматирование: деньги с 2 знаками
        setMoneyFormat(colBuyPrice);
        setMoneyFormat(colCurrentPrice);
        setMoneyFormat(colValue);

        // Прибыль с цветом: зелёный если > 0, красный если < 0
        setGainFormat(colGain, false);
        setGainFormat(colGainPercent, true);
    }

    private void setMoneyFormat(TableColumn<Asset, Double> col) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                setText(empty || val == null ? null
                        : String.format("%.2f р.", val));
                setStyle("-fx-text-fill: #cdd6f4;");
            }
        });
    }

    private void setGainFormat(TableColumn<Asset, Double> col, boolean isPercent) {
        col.setCellFactory(c -> new TableCell<>() {
            @Override
            protected void updateItem(Double val, boolean empty) {
                super.updateItem(val, empty);
                if (empty || val == null) { setText(null); return; }
                String text = isPercent
                        ? String.format("%.2f%%", val)
                        : String.format("%.2f р.", val);
                setText(text);
                // Цвет зависит от знака
                setStyle(val >= 0
                        ? "-fx-text-fill: #a6e3a1;"  // зелёный
                        : "-fx-text-fill: #f38ba8;"); // красный
            }
        });
    }

    /**
     * Перестроить PieChart: каждый сектор = доля актива в общей стоимости.
     * PieChart.Data принимает (название, числовое значение).
     */
    private void updatePieChart() {
        ObservableList<PieChart.Data> data = FXCollections.observableArrayList();
        for (Asset asset : assets) {
            data.add(new PieChart.Data(
                    asset.getTicker(),
                    asset.getCurrentValue()
            ));
        }
        pieChart.setData(data);
    }

    /** Пересчитать итоговую стоимость портфеля. */
    private void updateTotal() {
        double total = assets.stream().mapToDouble(Asset::getCurrentValue).sum();
        totalLabel.setText(String.format("Итого: %.2f р.", total));
    }

    @FXML
    public void onAddAsset() {
        // Dialog<Asset> — встроенный JavaFX диалог с результатом нужного типа
        Dialog<Asset> dialog = new Dialog<>();
        dialog.setTitle("Добавить актив");
        dialog.setHeaderText("Введите данные нового актива");

        // Кнопки
        ButtonType addButton = new ButtonType("Добавить", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(addButton, ButtonType.CANCEL);

        // Форма ввода — сетка из меток и полей
        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        TextField fName         = new TextField(); fName.setPromptText("Сбербанк");
        TextField fTicker       = new TextField(); fTicker.setPromptText("SBER");
        TextField fQuantity     = new TextField(); fQuantity.setPromptText("100");
        TextField fBuyPrice     = new TextField(); fBuyPrice.setPromptText("250.00");
        TextField fCurrentPrice = new TextField(); fCurrentPrice.setPromptText("310.50");

        grid.addRow(0, new Label("Название:"),    fName);
        grid.addRow(1, new Label("Тикер:"),        fTicker);
        grid.addRow(2, new Label("Количество:"),   fQuantity);
        grid.addRow(3, new Label("Цена покупки:"), fBuyPrice);
        grid.addRow(4, new Label("Тек. цена:"),    fCurrentPrice);

        dialog.getDialogPane().setContent(grid);

        // ResultConverter: преобразует нажатую кнопку в объект Asset (или null)
        dialog.setResultConverter(btn -> {
            if (btn != addButton) return null;
            try {
                return new Asset(
                        fName.getText().trim(),
                        fTicker.getText().trim().toUpperCase(),
                        Double.parseDouble(fQuantity.getText()),
                        Double.parseDouble(fBuyPrice.getText()),
                        Double.parseDouble(fCurrentPrice.getText())
                );
            } catch (NumberFormatException e) {
                return null; // некорректный ввод — игнорируем
            }
        });

        // showAndWait() блокирует поток UI до закрытия диалога
        dialog.showAndWait().ifPresent(asset -> {
            assets.add(asset);
            updateTotal();
            updatePieChart();
            statusLabel.setText("Добавлен: " + asset.getName());
        });
    }

    @FXML
    public void onSimulatePrices() {
        if (assets.isEmpty()) {
            statusLabel.setText("Нет активов для симуляции");
            return;
        }
        int gainers = simulator.simulate(assets);
        // TableView не знает что данные изменились (мы меняли через setCurrentPrice)
        // refresh() принудительно перерисовывает все ячейки
        assetsTable.refresh();
        updateTotal();
        updatePieChart();
        statusLabel.setText(String.format(
                "Обновлено: %d выросло, %d упало",
                gainers, assets.size() - gainers
        ));
    }

    @FXML
    public void onRemoveAsset() {
        Asset selected = assetsTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Выберите актив для удаления");
            return;
        }
        assets.remove(selected);
        updateTotal();
        updatePieChart();
        statusLabel.setText("Удалён: " + selected.getName());
    }

    /**
     * Открывает окно «Обзор MOEX». Пользователь может загрузить список
     * бумаг с биржи и добавить выбранные в портфель.
     */
    @FXML
    public void onOpenMoexBrowser() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    InvestmentApp.class.getResource("moex-browser.fxml"));
            javafx.scene.Parent root = loader.load();
            MoexBrowserController ctrl = loader.getController();
            // Callback: когда пользователь нажимает "Добавить выбранные" — мы получаем список
            ctrl.setOnAddToPortfolio(this::importFromMoex);

            Stage stage = new Stage();
            stage.setTitle("Обзор биржи MOEX");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.NONE); // не блокировать главное окно
            stage.show();
        } catch (Exception e) {
            statusLabel.setText("Не удалось открыть окно: " + e.getMessage());
        }
    }

    /** Добавляет бумаги, выбранные в MoexBrowser, в портфель. */
    private void importFromMoex(java.util.List<MoexSecurity> securities) {
        for (MoexSecurity s : securities) {
            double price = s.effectivePrice();
            assets.add(new Asset(s.getShortName(), s.getTicker(), 10, price, price));
        }
        updateTotal();
        updatePieChart();
        statusLabel.setText("Добавлено из MOEX: " + securities.size());
    }

    /**
     * Обновляет текущие цены всех активов в портфеле реальными данными с MOEX.
     * Учебный момент: запросы выполняются параллельно через CompletableFuture.allOf —
     * N активов = N одновременных HTTP-запросов, а не N подряд.
     */
    @FXML
    public void onRefreshFromMoex() {
        if (assets.isEmpty()) {
            statusLabel.setText("Нет активов для обновления");
            return;
        }
        statusLabel.setText("Запрашиваю цены с MOEX…");

        CompletableFuture<?>[] futures = assets.stream()
                .map(asset -> moex.loadCurrentPrice(asset.getTicker())
                        .thenAccept(price -> Platform.runLater(() -> {
                            asset.setCurrentPrice(price);
                            assetsTable.refresh();
                            updateTotal();
                            updatePieChart();
                        }))
                        .exceptionally(ex -> {
                            Platform.runLater(() -> statusLabel.setText(
                                    "Ошибка для " + asset.getTicker() + ": " + ex.getMessage()));
                            return null;
                        }))
                .toArray(CompletableFuture[]::new);

        CompletableFuture.allOf(futures)
                .whenComplete((v, err) -> Platform.runLater(() -> {
                    if (err == null) {
                        statusLabel.setText("Цены обновлены с биржи (" + assets.size() + " бумаг)");
                    } else {
                        statusLabel.setText("Обновлено с ошибками: " + err.getMessage());
                    }
                }));
    }

    /** Открывает окно демонстрации race condition. */
    @FXML
    public void onOpenOrderBook() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    InvestmentApp.class.getResource("order-book.fxml"));
            javafx.scene.Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Торговый стакан — демо многопоточки");
            stage.setScene(new Scene(root));
            stage.initModality(Modality.NONE);
            stage.show();
        } catch (Exception e) {
            statusLabel.setText("Не удалось открыть окно: " + e.getMessage());
        }
    }
}
