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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.apache.log4j.MDC;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLocationInfo;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decanter log collector, event driven implementing a PaxAppender
 */
@Component(
    name = "org.apache.karaf.decanter.collector.log",
    property = {"org.ops4j.pax.logging.appender.name=DecanterLogCollectorAppender",
              "name=log"},
    immediate = true
)
public class LogCollector implements PaxAppender {

    @Reference
    public EventAdmin dispatcher;

    private static final String MDC_IN_LOG_COLLECTOR = "inLogCollector";
    private final static Logger LOGGER = LoggerFactory.getLogger(LogCollector.class);
    private final static Pattern PATTERN = Pattern.compile("[^A-Za-z0-9]");

    private Dictionary<String, Object> properties;
    protected String[] ignoredCategories;
    protected String[] locationDisabledCategories;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        this.properties = context.getProperties();
        if (this.properties.get("ignored.categories") != null) {
            ignoredCategories = ((String)this.properties.get("ignored.categories")).split(",");
        }
        if (this.properties.get("location.disabled") != null) {
            locationDisabledCategories = ((String) this.properties.get("location.disabled")).split(",");
        }
    }
    
    public void doAppend(PaxLoggingEvent event) {
        try {
            if (MDC.get(MDC_IN_LOG_COLLECTOR) != null) {
                // Avoid recursion
                return;
            }
            MDC.put(MDC_IN_LOG_COLLECTOR, "true");
            appendInternal(event);
        } catch (Exception e) {
            LOGGER.warn("Error while appending event", e);
        } finally {
            MDC.remove(MDC_IN_LOG_COLLECTOR);
        }
    }

    private void appendInternal(PaxLoggingEvent event) throws Exception {
        if (isIgnored(event.getLoggerName(), ignoredCategories)) {
            LOGGER.debug("{} logger is ignored by the log collector", event.getLoggerName());
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("type", "log");

        data.put("timestamp", event.getTimeStamp());
        data.put("loggerClass", event.getFQNOfLoggerClass());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("renderedMessage", event.getRenderedMessage());
        data.put("MDC", event.getProperties());
        if (locationDisabledCategories == null || !isIgnored(event.getLoggerName(), locationDisabledCategories)) {
            putLocation(data, event.getLocationInformation());
        }
        String[] throwableAr = event.getThrowableStrRep();
        if (throwableAr != null) {
            data.put("throwable", join(throwableAr));
        }

        PropertiesPreparator.prepare(data, properties);

        String loggerName = event.getLoggerName();
        if (loggerName == null || loggerName.isEmpty()) {
            loggerName = "default";
        }
        String topic = "decanter/collect/log/" + cleanLoggerName(loggerName);
        this.dispatcher.postEvent(new Event(topic, data));
    }
    
    /*
     * only protected for testing. 
     */
    protected final String cleanLoggerName(String loggerName) {
        Matcher matcher = PATTERN.matcher(loggerName);
        
        if (matcher.find()) {
            return matcher.replaceAll("_");
        } else {
            return loggerName;
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

    protected boolean isIgnored(String loggerName, String[] ignoreList) {
        if (loggerName == null) {
            return true;
        }
        if (ignoreList != null) {
            for (String cat : ignoreList) {
                if (loggerName.matches(cat)) {
                    return true;
                }
            }
        }
        return false;
    }

}
