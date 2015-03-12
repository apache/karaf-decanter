/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.karaf.decanter.collector.log;

import java.util.HashMap;
import java.util.Map;

import org.apache.karaf.decanter.api.Collector;
import org.apache.karaf.decanter.api.Dispatcher;
import org.apache.log4j.MDC;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter log collector, event driven implementing a PaxAppender
 */
public class LogAppender implements PaxAppender, Collector {
    private static final String MDC_IN_LOG_APPENDER = "inLogAppender";
    private final static String[] ignoredCategories = {"org.apache.karaf.decanter"};
    private final static Logger LOGGER = LoggerFactory.getLogger(LogAppender.class);
    private Dispatcher dispatcher;
    
    public LogAppender(Dispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void doAppend(PaxLoggingEvent event) {
        try {
            if (MDC.get(MDC_IN_LOG_APPENDER) != null) {
                // Avoid recursion
                return;
            }
            MDC.put(MDC_IN_LOG_APPENDER, "true");
            appendInternal(event);
        } catch (Exception e) {
            LOGGER.warn("Error while appending event", e);
            MDC.remove(MDC_IN_LOG_APPENDER);
        }
    }

    private void appendInternal(PaxLoggingEvent event) throws Exception {
        LOGGER.debug("Karaf Decanter Log Collector hooked ...");

        Map<Long, Map<String, Object>> collected = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("loggerClass", event.getFQNOfLoggerClass());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("renderedMessage", event.getRenderedMessage());
        data.put("MDC", event.getProperties());
        collected.put(event.getTimeStamp(), data);

        if (!isIgnored(event.getLoggerName())) {
            LOGGER.debug("Calling the Karaf Decanter Appender Controller ...");
            this.dispatcher.dispatch(collected);
        }
    }

    private boolean isIgnored(String loggerName) {
        for (String cat : ignoredCategories) {
            if (loggerName.startsWith(cat)) {
                return true;
            }
        }
        return false;
    }

}
