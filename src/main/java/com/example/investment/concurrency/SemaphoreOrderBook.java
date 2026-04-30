package com.example.investment.concurrency;

import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализация через {@link Semaphore}.
 *
 * Семафор — это счётчик разрешений. {@code tryAcquire(n)} пытается забрать
 * {@code n} разрешений; если меньше осталось — возвращает false (мгновенно).
 * {@code acquire(n)} — блокирует поток до получения.
 *
 * Модель «ограниченный ресурс» ложится на семафор идеально:
 *   - Начальное количество разрешений = количество акций в пуле.
 *   - buy(qty) = tryAcquire(qty).
 *
 * Важно: семафор синхронизирует только «квоту», но если у класса есть
 * и другие поля ({@code sold} в нашем случае), их изменение надо делать
 * отдельно потокобезопасно — иначе получим второй race condition, но уже на
 * другой переменной. Поэтому {@code sold} — {@link AtomicInteger}.
 *
 * Fairness: {@code new Semaphore(n, true)} — выдавать разрешения в FIFO-порядке.
 */
public class SemaphoreOrderBook implements OrderBook {

    private Semaphore permits = new Semaphore(0, true);
    private final AtomicInteger sold = new AtomicInteger();
    private volatile int initial;
    private final TradeLog log = new TradeLog();

    public SemaphoreOrderBook() { reset(0); }

    @Override
    public boolean buy(int qty, String clientId) {
        if (!permits.tryAcquire(qty)) {
            log.record(clientId, qty, false, permits.availablePermits(), "не хватило");
            return false;
        }
        // Разрешения списаны атомарно. Обновляем статистику.
        sold.addAndGet(qty);
        log.record(clientId, qty, true, permits.availablePermits(), null);
        return true;
    }

    @Override public int available() { return permits.availablePermits(); }
    @Override public int sold()      { return sold.get(); }
    @Override public int oversold()  { return Math.max(0, sold.get() - initial); }

    @Override
    public void reset(int initial) {
        // Создаём свежий семафор — сбросить счётчик разрешений напрямую нельзя.
        this.initial = initial;
        this.permits = new Semaphore(initial, true);
        this.sold.set(0);
        this.log.clear();
    }

    @Override public String   name() { return "Semaphore (fair)"; }
    @Override public TradeLog log()  { return log; }
}
