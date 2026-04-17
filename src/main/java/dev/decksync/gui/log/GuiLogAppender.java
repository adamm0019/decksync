package dev.decksync.gui.log;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.time.Instant;
import org.slf4j.LoggerFactory;

/**
 * Logback appender that feeds the in-process {@link GuiLogBuffer}. Installed programmatically by
 * {@link #install()} at GUI startup rather than via {@code logback.xml} so the non-GUI entry points
 * (CLI {@code serve}, tests) don't carry a pointless extra reference to the buffer.
 */
public final class GuiLogAppender extends AppenderBase<ILoggingEvent> {

  private static boolean installed;

  /** Attach the appender to the root logger. Idempotent — a second call is a no-op. */
  public static synchronized void install() {
    if (installed) {
      return;
    }
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    GuiLogAppender appender = new GuiLogAppender();
    appender.setContext(root.getLoggerContext());
    ThresholdFilter filter = new ThresholdFilter();
    filter.setLevel(Level.INFO.levelStr);
    filter.setContext(root.getLoggerContext());
    filter.start();
    appender.addFilter(filter);
    appender.start();
    root.addAppender(appender);
    installed = true;
  }

  @Override
  protected void append(ILoggingEvent event) {
    GuiLogBuffer.shared()
        .add(
            new GuiLogEvent(
                Instant.ofEpochMilli(event.getTimeStamp()),
                event.getLevel().toString(),
                shortLoggerName(event.getLoggerName()),
                event.getFormattedMessage()));
  }

  private static String shortLoggerName(String full) {
    if (full == null) {
      return "";
    }
    int dot = full.lastIndexOf('.');
    return dot < 0 ? full : full.substring(dot + 1);
  }
}
