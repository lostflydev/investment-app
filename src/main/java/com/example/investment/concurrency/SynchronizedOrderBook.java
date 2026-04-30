package com.example.investment.concurrency;

/**
 * Исправленная версия через ключевое слово {@code synchronized}.
 *
 * {@code synchronized} на методе = «захватить монитор {@code this}» на время вызова.
 * Пока один поток внутри метода, остальные ждут перед входом.
 *
 * Что даёт:
 *   - Взаимное исключение (mutual exclusion): только один поток видит
 *     критическую секцию одновременно.
 *   - Happens-before: изменения, сделанные потоком A внутри synchronized-блока,
 *     гарантированно видны потоку B, который войдёт в этот же блок позже.
 *     Именно поэтому не нужен {@code volatile}.
 *
 * Минусы:
 *   - Блокировка на весь метод. Если метод долгий — падает параллелизм.
 *   - Нельзя прервать ожидание, нельзя с таймаутом (для этого есть ReentrantLock).
 */
public class SynchronizedOrderBook implements OrderBook {

    private int initial;
    private int available;
    private int sold;
    private final TradeLog log = new TradeLog();

    public SynchronizedOrderBook() { reset(0); }

    @Override
    public synchronized boolean buy(int qty, String clientId) {
        // Весь блок «проверка → изменение» теперь выполняется атомарно
        // с точки зрения других потоков: они не увидят промежуточного состояния.
        if (available >= qty) {
            Thread.yield(); // даже с yield баг не проявится — мы держим монитор
            available -= qty;
            sold      += qty;
            log.record(clientId, qty, true, available, null);
            return true;
        }
        log.record(clientId, qty, false, available, "не хватило");
        return false;
    }

    @Override public synchronized int available() { return available; }
    @Override public synchronized int sold()      { return sold; }
    @Override public synchronized int oversold()  { return Math.max(0, sold - initial); }

    @Override
    public synchronized void reset(int initial) {
        this.initial   = initial;
        this.available = initial;
        this.sold      = 0;
        this.log.clear();
    }

    @Override public String   name() { return "Synchronized"; }
    @Override public TradeLog log()  { return log; }
}
