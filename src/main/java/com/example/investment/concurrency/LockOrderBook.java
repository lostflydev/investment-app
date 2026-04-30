package com.example.investment.concurrency;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Исправление через явный {@link ReentrantLock}.
 *
 * Почему вообще нужен Lock, если есть synchronized?
 *   - Гибкость: можно ждать с таймаутом ({@link ReentrantLock#tryLock(long, TimeUnit)}).
 *   - Можно прервать поток, который стоит в ожидании ({@code lockInterruptibly}).
 *   - «Справедливый» режим {@code new ReentrantLock(true)} — потоки получают лок в порядке прибытия.
 *   - Удобнее, когда lock нужен для нескольких методов/объектов.
 *
 * Обязательно {@code try/finally} — если забыть отпустить lock, ничто не спасёт.
 * {@code synchronized} освобождает монитор автоматически, это его большое
 * преимущество в «простых» случаях.
 */
public class LockOrderBook implements OrderBook {

    /** Fair=true: потоки получают lock в FIFO-порядке. Справедливо, но медленнее. */
    private final ReentrantLock lock = new ReentrantLock(true);

    private int initial;
    private int available;
    private int sold;
    private final TradeLog log = new TradeLog();

    public LockOrderBook() { reset(0); }

    @Override
    public boolean buy(int qty, String clientId) {
        lock.lock();
        try {
            if (available >= qty) {
                Thread.yield();
                available -= qty;
                sold      += qty;
                log.record(clientId, qty, true, available, null);
                return true;
            }
            log.record(clientId, qty, false, available, "не хватило");
            return false;
        } finally {
            lock.unlock(); // ОБЯЗАТЕЛЬНО в finally — иначе deadlock на исключении
        }
    }

    /**
     * Вариант с таймаутом: «если не удалось захватить lock за timeoutMs — сдаёмся».
     * В реальных системах это способ избежать бесконечного ожидания и deadlock.
     * Для учебной демонстрации: показать, что блокирующая операция не обязана
     * быть бесконечной.
     */
    public boolean buyOrGiveUp(int qty, String clientId, long timeoutMs) {
        try {
            if (!lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                log.record(clientId, qty, false, available, "не дождался lock");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
        try {
            if (available >= qty) {
                available -= qty;
                sold      += qty;
                log.record(clientId, qty, true, available, "через tryLock");
                return true;
            }
            log.record(clientId, qty, false, available, "не хватило");
            return false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int available() {
        lock.lock();
        try { return available; } finally { lock.unlock(); }
    }

    @Override
    public int sold() {
        lock.lock();
        try { return sold; } finally { lock.unlock(); }
    }

    @Override
    public int oversold() {
        lock.lock();
        try { return Math.max(0, sold - initial); } finally { lock.unlock(); }
    }

    @Override
    public void reset(int initial) {
        lock.lock();
        try {
            this.initial   = initial;
            this.available = initial;
            this.sold      = 0;
            this.log.clear();
        } finally { lock.unlock(); }
    }

    @Override public String   name() { return "ReentrantLock (fair) + tryLock"; }
    @Override public TradeLog log()  { return log; }
}
