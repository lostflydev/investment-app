package com.example.investment.concurrency;

/**
 * НАМЕРЕННО СЛОМАННАЯ реализация для демонстрации race condition.
 *
 * Баг ровно один и классический — «check-then-act» не атомарно:
 *
 * <pre>
 *   if (available &gt;= qty) {   // ← Thread A читает available=10, проверка ок
 *                               //   Thread B читает available=10, проверка ок
 *       available -= qty;      // ← А вычитает 7 → 3. Б вычитает 7 → -4.
 *   }                          //   Результат: продано 14 акций при пуле 10.
 * </pre>
 *
 * {@code Thread.yield()} между проверкой и изменением — чит для учебной демонстрации.
 * Он провоцирует переключение контекста именно в критической точке, чтобы баг
 * воспроизводился надёжно на любой машине. В реальном коде его не надо —
 * баг там всё равно есть, просто проявляется реже.
 */
public class UnsafeOrderBook implements OrderBook {

    private int initial;
    private int available;
    private int sold;
    private final TradeLog log = new TradeLog();

    public UnsafeOrderBook() { reset(0); }

    @Override
    public boolean buy(int qty, String clientId) {
        // Шаг 1. Проверяем, хватает ли акций.
        if (available >= qty) {
            // Шаг 2. ВНИМАНИЕ: между шагом 1 и шагом 3 другой поток может
            //        тоже пройти проверку на старом значении available.
            Thread.yield();

            // Шаг 3. Вычитаем. Операция "a -= b" это три операции:
            //        read a → subtract → write a. Не атомарна.
            available -= qty;
            sold      += qty;

            String note = available < 0 ? "OVERSOLD! пул ушёл в минус" : "";
            log.record(clientId, qty, true, available, note);
            return true;
        }
        log.record(clientId, qty, false, available, "не хватило");
        return false;
    }

    @Override public int available() { return available; }
    @Override public int sold()      { return sold; }

    @Override
    public int oversold() {
        // Сколько продали сверх начального пула
        return Math.max(0, sold - initial);
    }

    @Override
    public void reset(int initial) {
        this.initial   = initial;
        this.available = initial;
        this.sold      = 0;
        this.log.clear();
    }

    @Override public String   name() { return "Unsafe (без синхронизации)"; }
    @Override public TradeLog log()  { return log; }
}
