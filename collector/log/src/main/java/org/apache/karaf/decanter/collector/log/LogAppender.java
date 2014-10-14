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

import org.apache.karaf.decanter.api.Dispatcher;
import org.apache.karaf.decanter.api.Collector;
import org.ops4j.pax.logging.spi.PaxAppender;
import org.ops4j.pax.logging.spi.PaxLoggingEvent;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Decanter log collector, event driven implementing a PaxAppender
 */
public class LogAppender implements PaxAppender, Collector {

    private final static Logger LOGGER = LoggerFactory.getLogger(LogAppender.class);

    private BundleContext bundleContext;

    public LogAppender(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

    public void doAppend(PaxLoggingEvent event) {
        LOGGER.debug("Karaf Decanter Log Collector hooked ...");

        Map<Long, Map<String, Object>> collected = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("loggerClass", event.getFQNOfLoggerClass());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("renderedMessage", event.getRenderedMessage());

        collected.put(event.getTimeStamp(), data);

        // it's an event driven collector, calling the appender controller
        LOGGER.debug("Calling the Karaf Decanter Appender Controller ...");
        ServiceReference reference = bundleContext.getServiceReference(Dispatcher.class);
        if (reference != null) {
            Dispatcher controller = (Dispatcher) bundleContext.getService(reference);
            if (controller != null) {
                try {
                    controller.dispatch(collected);
                } catch (Exception e) {
                    LOGGER.warn("Can't dispatch collected data", e);
                }
            }
            bundleContext.ungetService(reference);
        }
    }

    public BundleContext getBundleContext() {
        return bundleContext;
    }

    public void setBundleContext(BundleContext bundleContext) {
        this.bundleContext = bundleContext;
    }

}
