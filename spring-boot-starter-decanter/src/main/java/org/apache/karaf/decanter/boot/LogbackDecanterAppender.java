/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.karaf.decanter.boot;

import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

/**
 * Logback appender for usage in non OSGi environments like spring boot.
 * Forwards all log messages to an EventAdmin instance that needs to be injected.
 */
public class LogbackDecanterAppender extends AppenderBase<ILoggingEvent> {
    private static final String MDC_IN_LOG_APPENDER = "inLogAppender";
    private final static String[] ignoredCategories = {"org.apache.karaf.decanter", "org.apache.kafka", "org.talend.decanter.connect"};
    private final static Logger LOGGER = LoggerFactory.getLogger(LogbackDecanterAppender.class);

    private static EventAdmin dispatcher;
    
    public static void setDispatcher(EventAdmin dispatcher) {
        LogbackDecanterAppender.dispatcher = dispatcher;
    }

    protected void append(ILoggingEvent event) {
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

    private void appendInternal(ILoggingEvent event) throws Exception {
        if (isIgnored(event.getLoggerName())) {
            LOGGER.trace("{} logger is ignored by the log collector", event.getLoggerName());
            return;
        }

        LOGGER.debug("Publishing log event to the appenders ...");

        Map<String, Object> data = new HashMap<>();
        data.put("type", "log");
        String karafName = System.getProperty("karaf.name");
        if (karafName != null) {
            data.put("karafName", karafName);
        }

        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());

        data.put("timestamp", event.getTimeStamp());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("MDC", event.getMDCPropertyMap());
        String loggerName = event.getLoggerName();
        if (loggerName == null || loggerName.isEmpty()) {
            loggerName = "default";
        }
        String topic = "decanter/collect/log/" + loggerName.replace(".", "/").replace(" ", "_").replace("{", "_").replace("}", "_").replace("$", "_");
        if (dispatcher != null) {
            dispatcher.postEvent(new Event(topic, data));
        }
    }

    private boolean isIgnored(String loggerName) {
        if (loggerName == null) {
            return true;
        }
        for (String cat : ignoredCategories) {
            if (loggerName.startsWith(cat)) {
                return true;
            }
        }
        return false;
    }

}
