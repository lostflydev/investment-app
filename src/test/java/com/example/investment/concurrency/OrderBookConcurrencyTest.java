package com.example.investment.concurrency;

import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Тесты, показывающие race condition.
 *
 * Сценарий «классический»: пул 10 акций, 2 клиента, каждый хочет купить 7.
 * Итого 14 — больше пула → один должен получить отказ.
 *
 * При некорректной реализации ({@link UnsafeOrderBook}) оба клиента
 * регулярно «покупают» полностью, и счётчик переторговки становится > 0.
 *
 * Для стабильности:
 *   - chunk=1 (по одной штуке) — максимизирует число переходов через границу
 *   - maxAttempts много — клиент не сдаётся после первой неудачи
 *   - Thread.yield() в UnsafeOrderBook подстраховывает воспроизводимость
 *
 * @RepeatedTest позволяет увидеть недетерминизм: тест бежит N раз и мы считаем
 * сколько прогонов показали баг. Для учебного материала — самое то.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderBookConcurrencyTest {

    private static final int POOL      = 10;
    private static final int TARGET    = 7;
    private static final int CHUNK     = 1;
    private static final int CLIENTS   = 2;

    /**
     * Прогон одной торговой сессии. Два клиента стартуют одновременно через
     * CountDownLatch и пытаются купить TARGET акций порциями CHUNK.
     */
    private void runScenario(OrderBook book) throws InterruptedException {
        book.reset(POOL);
        CountDownLatch gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(CLIENTS);
        for (int i = 0; i < CLIENTS; i++) {
            pool.submit(new TradeClient(
                    String.valueOf((char)('A' + i)), book, TARGET, CHUNK, TARGET * 4, gate));
        }
        gate.countDown();
        pool.shutdown();
        boolean done = pool.awaitTermination(10, TimeUnit.SECONDS);
        assertTrue(done, "сценарий не завершился за 10 секунд");
    }

    // ============== Unsafe: баг ДОЛЖЕН воспроизводиться ==============

    /**
     * Показываем студентам недетерминизм: один и тот же код даёт разный результат.
     * Прогоняем Unsafe 100 раз и считаем, в скольких прогонах случилась переторговка.
     * Тест не падает — он просто печатает статистику.
     */
    @Test
    void unsafe_statistics_overoversold() throws InterruptedException {
        AtomicInteger oversoldRuns = new AtomicInteger();
        AtomicInteger maxOversold  = new AtomicInteger();
        int runs = 100;
        for (int i = 0; i < runs; i++) {
            OrderBook book = new UnsafeOrderBook();
            runScenario(book);
            if (book.oversold() > 0) oversoldRuns.incrementAndGet();
            maxOversold.updateAndGet(m -> Math.max(m, book.oversold()));
        }
        System.out.printf(
                "[Unsafe] прогонов=%d, с переторговкой=%d, макс. сверх пула=%d%n",
                runs, oversoldRuns.get(), maxOversold.get());
        // Мягкое утверждение: баг обязан проявиться хотя бы раз на 100 прогонов.
        assertTrue(oversoldRuns.get() > 0,
                "Unsafe должен хотя бы иногда переторговывать — " +
                "проверь, что Thread.yield() на месте и тесты не падают из-за слишком быстрой машины");
    }

    // ============== Корректные реализации: oversold всегда 0 ==============

    @RepeatedTest(30)
    void synchronized_neverOversold() throws InterruptedException {
        OrderBook book = new SynchronizedOrderBook();
        runScenario(book);
        assertEquals(0, book.oversold(), () ->
                "Synchronized не должен переторговывать, а получили " + book.oversold());
        assertEquals(POOL, book.sold() + book.available(),
                "инвариант: sold + available == initial");
    }

    @RepeatedTest(30)
    void reentrantLock_neverOversold() throws InterruptedException {
        OrderBook book = new LockOrderBook();
        runScenario(book);
        assertEquals(0, book.oversold());
    }

    @RepeatedTest(30)
    void atomicCAS_neverOversold() throws InterruptedException {
        OrderBook book = new AtomicOrderBook();
        runScenario(book);
        assertEquals(0, book.oversold());
    }

    @RepeatedTest(30)
    void semaphore_neverOversold() throws InterruptedException {
        OrderBook book = new SemaphoreOrderBook();
        runScenario(book);
        assertEquals(0, book.oversold());
    }
}
