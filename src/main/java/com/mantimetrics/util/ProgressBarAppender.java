package com.mantimetrics.util;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.Layout;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginAttribute;
import org.apache.logging.log4j.core.config.plugins.PluginElement;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;

import java.io.Serializable;

/**
 * Log4j2 appender that writes to {@code System.out} while keeping any active
 * {@link ProgressBar} intact.
 *
 * <p>Before each log line it acquires {@link ProgressBar#CONSOLE_LOCK}, erases the
 * current progress-bar line ({@link ProgressBar#clearLine()}), prints the formatted
 * log event, then redraws the bar ({@link ProgressBar#redraw()}). The whole sequence
 * is atomic with respect to progress-bar updates, so log messages and bar renders can
 * never appear on the same terminal line.
 *
 * <p>Register this appender in {@code log4j2.xml} by adding
 * {@code packages="com.mantimetrics.util"} to the {@code <Configuration>} element and
 * replacing the standard {@code <Console>} element with {@code <ProgressBarAppender>}.
 */
@Plugin(name = "ProgressBarAppender", category = "Core", elementType = "appender")
public final class ProgressBarAppender extends AbstractAppender {

 private ProgressBarAppender(
 String name,
 Filter filter,
 Layout<? extends Serializable> layout) {
 super(name, filter, layout, /* ignoreExceptions */ true, Property.EMPTY_ARRAY);
 }

 /**
 * Factory method invoked by the Log4j2 plugin system when the configuration is loaded.
 *
 * @param name appender name (from the {@code name} XML attribute)
 * @param filter optional filter chain
 * @param layout log-event layout (typically a {@code PatternLayout})
 * @return configured appender instance
 */
 @PluginFactory
 public static ProgressBarAppender createAppender(
 @PluginAttribute("name") String name,
 @PluginElement("Filter") Filter filter,
 @PluginElement("Layout") Layout<? extends Serializable> layout) {
 return new ProgressBarAppender(name, filter, layout);
 }

 /**
 * Writes the log event to {@code System.out}, bracketed by progress-bar clear/redraw
 * calls so that the active bar is never clobbered.
 *
 * @param event the log event to serialize and print
 */
 @Override
 public void append(LogEvent event) {
 byte[] bytes = getLayout().toByteArray(event);
 synchronized (ProgressBar.CONSOLE_LOCK) {
 ProgressBar.clearLine();
 System.out.write(bytes, 0, bytes.length);
 System.out.flush();
 ProgressBar.redraw();
 }
 }
}
