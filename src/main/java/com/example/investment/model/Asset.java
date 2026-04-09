package com.example.investment.model;

import javafx.beans.property.*;

/**
 * Модель финансового актива в портфеле.
 *
 * Использует JavaFX Properties вместо обычных полей.
 * Это ключевая концепция JavaFX: Property позволяет
 * привязывать (bind) значения к UI элементам — изменение
 * данных автоматически обновляет интерфейс.
 *
 * Пример:
 *   label.textProperty().bind(asset.nameProperty());
 *   // теперь Label автоматически показывает актуальное название
 */
public class Asset {

    // --- Основные поля (JavaFX Properties) ---

    private final StringProperty  name         = new SimpleStringProperty();
    private final StringProperty  ticker       = new SimpleStringProperty();
    private final DoubleProperty  quantity     = new SimpleDoubleProperty();
    private final DoubleProperty  buyPrice     = new SimpleDoubleProperty();
    private final DoubleProperty  currentPrice = new SimpleDoubleProperty();

    // --- Конструктор ---

    public Asset(String name, String ticker, double quantity, double buyPrice, double currentPrice) {
        this.name.set(name);
        this.ticker.set(ticker);
        this.quantity.set(quantity);
        this.buyPrice.set(buyPrice);
        this.currentPrice.set(currentPrice);
    }

    // --- Вычисляемые значения ---

    /** Текущая стоимость позиции = quantity × currentPrice */
    public double getCurrentValue() {
        return getQuantity() * getCurrentPrice();
    }

    /** Прибыль/убыток в рублях = (currentPrice - buyPrice) × quantity */
    public double getGain() {
        return (getCurrentPrice() - getBuyPrice()) * getQuantity();
    }

    /** Прибыль/убыток в процентах = (currentPrice - buyPrice) / buyPrice × 100 */
    public double getGainPercent() {
        if (getBuyPrice() == 0) return 0;
        return (getCurrentPrice() - getBuyPrice()) / getBuyPrice() * 100.0;
    }

    // --- Property accessors (нужны для TableView binding) ---

    public StringProperty  nameProperty()         { return name; }
    public StringProperty  tickerProperty()       { return ticker; }
    public DoubleProperty  quantityProperty()     { return quantity; }
    public DoubleProperty  buyPriceProperty()     { return buyPrice; }
    public DoubleProperty  currentPriceProperty() { return currentPrice; }

    // --- Обычные геттеры/сеттеры ---

    public String  getName()         { return name.get(); }
    public String  getTicker()       { return ticker.get(); }
    public double  getQuantity()     { return quantity.get(); }
    public double  getBuyPrice()     { return buyPrice.get(); }
    public double  getCurrentPrice() { return currentPrice.get(); }

    public void setCurrentPrice(double price) { this.currentPrice.set(price); }
    public void setQuantity(double qty)       { this.quantity.set(qty); }

    @Override
    public String toString() {
        return String.format("%s (%s): %.0f шт × %.2f ₽ = %.2f ₽",
                getName(), getTicker(), getQuantity(), getCurrentPrice(), getCurrentValue());
    }
}
