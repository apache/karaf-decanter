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
package org.apache.karaf.decanter.processor.aggregate;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component(
        name = "org.apache.karaf.decanter.processor.aggregate",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class AggregateProcessor implements EventHandler {

    @Reference
    private EventAdmin dispatcher;

    private String targetTopics;
    private boolean overwrite = false;

    private int index = 0;
    private ConcurrentHashMap<String, Object> mergedData = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduledExecutorService;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) {
        targetTopics = (configuration.get("target.topics") != null) ? configuration.get("target.topics").toString() : "decanter/process/aggregate";
        long period = (configuration.get("period") != null) ? Long.parseLong(configuration.get("period").toString()) : 60L;
        overwrite = (configuration.get("overwrite") != null) ? Boolean.parseBoolean(configuration.get("overwrite").toString()) : false;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(new AggregateTask(), 0, period, TimeUnit.SECONDS);
    }

    @Deactivate
    public void deactivate() {
        scheduledExecutorService.shutdownNow();
    }

    @Override
    public void handleEvent(Event event) {
        // merge data
        for (String propertyName : event.getPropertyNames()) {
            if (overwrite) {
                mergedData.put(propertyName, event.getProperty(propertyName));
            } else {
                mergedData.put(index + "." + propertyName, event.getProperty(propertyName));
            }
        }
        index++;
    }

    class AggregateTask implements Runnable {

        @Override
        public void run() {
            // create event and send
            if (mergedData.size() > 0) {
                mergedData.put("processor", "aggregate");
                String[] topics = targetTopics.split(",");
                for (String topic : topics) {
                    dispatcher.postEvent(new Event(topic, mergedData));
                }
                // reset the merged data
                mergedData.clear();
                index = 0;
            }
        }

    }

    /**
     * Visible for testing
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
