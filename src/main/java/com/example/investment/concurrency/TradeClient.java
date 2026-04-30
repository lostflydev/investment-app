package com.example.investment.concurrency;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Клиент-трейдер. Запускается в отдельном потоке и в цикле пытается покупать
 * у {@link OrderBook} порциями по {@code chunkSize} штук, пока не купит
 * {@code targetQty} или не закончатся акции.
 *
 * Используется {@link CountDownLatch} как «стартовое окно»: все клиенты
 * ждут latch.await() и стартуют практически одновременно, максимизируя
 * вероятность состязания.
 */
public class TradeClient implements Runnable {

    private final String   id;
    private final OrderBook book;
    private final int      targetQty;
    private final int      chunkSize;
    private final int      maxAttempts;
    private final CountDownLatch startGate;
    private final AtomicInteger bought = new AtomicInteger();

    public TradeClient(String id, OrderBook book,
                       int targetQty, int chunkSize, int maxAttempts,
                       CountDownLatch startGate) {
        this.id          = id;
        this.book        = book;
        this.targetQty   = targetQty;
        this.chunkSize   = chunkSize;
        this.maxAttempts = maxAttempts;
        this.startGate   = startGate;
    }

    @Override
    public void run() {
        try {
            startGate.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        int attempts = 0;
        while (bought.get() < targetQty && attempts < maxAttempts) {
            attempts++;
            int qty = Math.min(chunkSize, targetQty - bought.get());
            if (book.buy(qty, id)) {
                bought.addAndGet(qty);
            }
            // Крошечная пауза, чтобы не монополизировать CPU одним потоком.
            try {
                Thread.sleep(ThreadLocalRandom.current().nextLong(0, 2));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public String id()     { return id; }
    public int    bought() { return bought.get(); }
    public int    target() { return targetQty; }
}
