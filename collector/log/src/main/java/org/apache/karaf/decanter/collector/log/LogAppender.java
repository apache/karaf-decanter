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

import org.apache.log4j.MDC;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLocationInfo;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter log collector, event driven implementing a PaxAppender
 */
public class LogAppender implements PaxAppender {
    private static final String MDC_IN_LOG_APPENDER = "inLogAppender";
    private final static String[] ignoredCategories = {"org.apache.karaf.decanter"};
    private final static Logger LOGGER = LoggerFactory.getLogger(LogAppender.class);
    private EventAdmin dispatcher;
    
    public LogAppender(EventAdmin dispatcher) {
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
        } finally {
            MDC.remove(MDC_IN_LOG_APPENDER);
        }
    }

    private void appendInternal(PaxLoggingEvent event) throws Exception {
        LOGGER.debug("Karaf Decanter Log Collector hooked ...");

        Map<String, Object> data = new HashMap<>();
        data.put("type", "log");
        data.put("karafName", System.getProperty("karaf.name"));
        data.put("timeStamp", event.getTimeStamp());
        data.put("loggerClass", event.getFQNOfLoggerClass());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("renderedMessage", event.getRenderedMessage());
        data.put("MDC", event.getProperties());
        putLocation(data, event.getLocationInformation());
        String[] throwableAr = event.getThrowableStrRep(); 
        if (throwableAr != null) {
            data.put("throwable", join(throwableAr));
        }

        if (!isIgnored(event.getLoggerName())) {
            String topic = "decanter/log/" + event.getLoggerName().replace(".", "/");
            this.dispatcher.postEvent(new Event(topic, data));
        }
    }

    private void putLocation(Map<String, Object> data, PaxLocationInfo loc) {
        data.put("loc.class", loc.getClassName());
        data.put("loc.file", loc.getFileName());
        data.put("loc.line", loc.getLineNumber());
        data.put("loc.method", loc.getMethodName());
    }

    private Object join(String[] throwableAr) {
        StringBuilder builder = new StringBuilder();
        for (String line : throwableAr) {
            builder.append(line).append("\n");
        }
        return builder.toString();
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
