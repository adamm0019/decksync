package dev.decksync.gui.log;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Thread-safe ring buffer of recent log events, capped at {@link #CAPACITY} entries. The Logback
 * appender writes from application threads; the drawer controller reads from the JavaFX thread and
 * subscribes via {@link #addListener} to learn when new events arrive. Listeners are invoked on the
 * producing thread — it's the listener's job to hop onto the right UI thread.
 */
public final class GuiLogBuffer {

  public static final int CAPACITY = 250;

  private static final GuiLogBuffer INSTANCE = new GuiLogBuffer();

  public static GuiLogBuffer shared() {
    return INSTANCE;
  }

  private final Deque<GuiLogEvent> buffer = new ArrayDeque<>(CAPACITY);
  private final List<Consumer<GuiLogEvent>> listeners = new CopyOnWriteArrayList<>();

  GuiLogBuffer() {}

  public synchronized void add(GuiLogEvent event) {
    if (buffer.size() == CAPACITY) {
      buffer.removeFirst();
    }
    buffer.addLast(event);
    for (Consumer<GuiLogEvent> listener : listeners) {
      listener.accept(event);
    }
  }

  /** Snapshot of current contents, oldest first. Safe to retain. */
  public synchronized List<GuiLogEvent> snapshot() {
    return Collections.unmodifiableList(new ArrayList<>(buffer));
  }

  public void addListener(Consumer<GuiLogEvent> listener) {
    listeners.add(listener);
  }

  public void removeListener(Consumer<GuiLogEvent> listener) {
    listeners.remove(listener);
  }
}
