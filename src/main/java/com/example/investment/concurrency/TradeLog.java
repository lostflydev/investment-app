package com.example.investment.concurrency;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

/**
 * Потокобезопасный журнал событий торгов.
 *
 * {@link ConcurrentLinkedQueue} гарантирует корректное добавление из нескольких
 * потоков без блокировок. Подписчик (обычно JavaFX-контроллер) регистрируется
 * через {@link #subscribe(Consumer)} и получает каждое новое событие.
 *
 * Важно: callback вызывается из того потока, который добавил запись —
 * подписчик обязан сам обернуть обновление UI в {@code Platform.runLater}.
 * Это сделано намеренно — для наглядности в учебном материале.
 */
public class TradeLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    public record Entry(String time, String thread, String clientId, int qty,
                        boolean success, int remaining, String note) {
        @Override
        public String toString() {
            return String.format("[%s] [%s] %s buy %d %s  available=%d%s",
                    time, thread, clientId, qty,
                    success ? "OK    " : "DENIED",
                    remaining,
                    note == null || note.isEmpty() ? "" : "  ← " + note);
        }
    }

    private final ConcurrentLinkedQueue<Entry>   entries   = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Consumer<Entry>> listeners = new ConcurrentLinkedQueue<>();

    public void record(String clientId, int qty, boolean success, int remaining, String note) {
        Entry e = new Entry(
                LocalTime.now().format(TIME),
                Thread.currentThread().getName(),
                clientId, qty, success, remaining,
                note
        );
        entries.add(e);
        for (Consumer<Entry> listener : listeners) {
            try { listener.accept(e); } catch (Exception ignored) { }
        }
    }

    public void subscribe(Consumer<Entry> listener) { listeners.add(listener); }

    public void clear() { entries.clear(); }

    public ConcurrentLinkedQueue<Entry> snapshot() { return entries; }
}
