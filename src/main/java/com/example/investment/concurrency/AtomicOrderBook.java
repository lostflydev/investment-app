package com.example.investment.concurrency;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Реализация без блокировок (lock-free) через {@link AtomicInteger} и CAS-цикл.
 *
 * CAS (Compare-And-Swap) — атомарная процессорная операция:
 * «если текущее значение равно {@code expected} — записать {@code newValue}
 * и вернуть true, иначе вернуть false». Всё это за одну инструкцию.
 *
 * Идея:
 * <pre>
 *   while (true) {
 *       int current = available.get();
 *       if (current &lt; qty) return false;
 *       int next = current - qty;
 *       if (available.compareAndSet(current, next)) return true;
 *       // CAS не прошёл — другой поток успел изменить available. Повторяем.
 *   }
 * </pre>
 *
 * Когда полезно:
 *   - Короткие операции над одним числом/ссылкой.
 *   - Высокая нагрузка: CAS намного дешевле lock при малом конфликте.
 *
 * Когда НЕ подходит:
 *   - Нужно атомарно менять несколько полей — придётся всё равно лок или
 *     обёртка типа {@link java.util.concurrent.atomic.AtomicReference}
 *     с immutable-состоянием.
 *   - Проблема ABA: если значение ушло и вернулось — CAS не заметит.
 */
public class AtomicOrderBook implements OrderBook {

    private final AtomicInteger available = new AtomicInteger();
    private final AtomicInteger sold      = new AtomicInteger();
    private volatile int initial;
    private final TradeLog log = new TradeLog();

    public AtomicOrderBook() { reset(0); }

    @Override
    public boolean buy(int qty, String clientId) {
        // CAS-цикл: продолжаем пытаться, пока не успеем или не увидим «не хватает».
        while (true) {
            int current = available.get();
            if (current < qty) {
                log.record(clientId, qty, false, current, "не хватило");
                return false;
            }
            int next = current - qty;
            // Thread.yield() тут уже не «ломает» — CAS атомарен,
            // конкурирующий поток вынудит нас ретрайнуть.
            Thread.yield();
            if (available.compareAndSet(current, next)) {
                sold.addAndGet(qty);
                log.record(clientId, qty, true, next, null);
                return true;
            }
            // CAS не прошёл → другой поток был быстрее. Крутим цикл.
        }
    }

    @Override public int available() { return available.get(); }
    @Override public int sold()      { return sold.get(); }
    @Override public int oversold()  { return Math.max(0, sold.get() - initial); }

    @Override
    public void reset(int initial) {
        this.initial = initial;
        this.available.set(initial);
        this.sold.set(0);
        this.log.clear();
    }

    @Override public String   name() { return "AtomicInteger (CAS)"; }
    @Override public TradeLog log()  { return log; }
}
