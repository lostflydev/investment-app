package com.example.investment.service;

import com.example.investment.model.Asset;
import javafx.collections.ObservableList;

import java.util.Random;

/**
 * Симулятор изменения цен активов.
 *
 * Демонстрирует принцип сервисного слоя (Service Layer):
 * - Бизнес-логика вынесена из контроллера в отдельный класс
 * - Контроллер вызывает simulate() — он не знает КАК это работает
 * - Позволяет легко заменить симуляцию на реальный API
 *
 * В реальном приложении здесь был бы HTTP-запрос к биржевому API.
 */
public class PriceSimulator {

    private static final double MAX_CHANGE = 0.07; // максимальное изменение ±7%
    private final Random random = new Random();

    /**
     * Изменяет цены всех активов в списке на случайную величину.
     * Возвращает количество активов у которых цена выросла.
     *
     * @param assets список активов для обновления
     * @return количество активов с ростом цены
     */
    public int simulate(ObservableList<Asset> assets) {
        int gainers = 0;
        for (Asset asset : assets) {
            double changePercent = (random.nextDouble() * 2 - 1) * MAX_CHANGE;
            double newPrice = asset.getCurrentPrice() * (1 + changePercent);
            // Цена не может быть отрицательной
            newPrice = Math.max(newPrice, 0.01);
            asset.setCurrentPrice(newPrice);
            if (changePercent > 0) gainers++;
        }
        return gainers;
    }
}
