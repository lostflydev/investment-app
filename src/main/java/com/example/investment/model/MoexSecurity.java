package com.example.investment.model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Описание бумаги с Московской биржи (упрощённо).
 * Заполняется из ответа MOEX ISS (блоки "securities" + "marketdata").
 *
 * JavaFX Properties нужны чтобы TableView автоматически перерисовывался
 * при обновлении цен — см. MoexBrowserController.
 */
public class MoexSecurity {

    private final StringProperty ticker    = new SimpleStringProperty();
    private final StringProperty shortName = new SimpleStringProperty();
    /** Последняя сделка (LAST). Может отсутствовать у неликвидных бумаг. */
    private final DoubleProperty lastPrice = new SimpleDoubleProperty();
    /** Цена предыдущего торгового дня (PREVPRICE) — fallback для lastPrice. */
    private final DoubleProperty prevPrice = new SimpleDoubleProperty();

    public MoexSecurity(String ticker, String shortName, double lastPrice, double prevPrice) {
        this.ticker.set(ticker);
        this.shortName.set(shortName);
        this.lastPrice.set(lastPrice);
        this.prevPrice.set(prevPrice);
    }

    /** Цена, которую разумно показать пользователю: LAST, иначе PREVPRICE. */
    public double effectivePrice() {
        double last = lastPrice.get();
        return last > 0 ? last : prevPrice.get();
    }

    public StringProperty tickerProperty()    { return ticker; }
    public StringProperty shortNameProperty() { return shortName; }
    public DoubleProperty lastPriceProperty() { return lastPrice; }
    public DoubleProperty prevPriceProperty() { return prevPrice; }

    public String getTicker()    { return ticker.get(); }
    public String getShortName() { return shortName.get(); }
    public double getLastPrice() { return lastPrice.get(); }
    public double getPrevPrice() { return prevPrice.get(); }

    @Override
    public String toString() {
        return String.format("%s (%s) %.2f ₽", getShortName(), getTicker(), effectivePrice());
    }
}
