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
package org.apache.karaf.decanter.processor.groupby;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component(
        name = "org.apache.karaf.decanter.processor.groupby",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class GroupByProcessor implements EventHandler {

    @Reference
    private EventAdmin dispatcher;

    private String targetTopics;
    private String groupBy;
    private boolean flat;

    private ConcurrentHashMap<Integer, List<Event>> accumulation = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduledExecutorService;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) {
        targetTopics = (configuration.get("target.topics") != null) ? configuration.get("target.topics").toString() : "decanter/process/aggregate";
        long period = (configuration.get("period") != null) ? Long.parseLong(configuration.get("period").toString()) : 60L;
        groupBy = (configuration.get("groupBy") != null) ? configuration.get("groupBy").toString() : null;
        flat = (configuration.get("flat") != null) ? Boolean.parseBoolean(configuration.get("flat").toString()) : true;
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        scheduledExecutorService.scheduleAtFixedRate(new GroupByTask(), 0, period, TimeUnit.SECONDS);
    }

    @Deactivate
    public void deactivate() {
        scheduledExecutorService.shutdownNow();
    }

    @Override
    public void handleEvent(Event event) {
        String[] groups = groupBy.split(",");
        int hash = 0;
        for (String group : groups) {
            if (event.getProperty(group) == null) {
                return;
            } else {
                hash = hash + event.getProperty(group).hashCode();
            }
        }
        if (accumulation.get(hash) == null) {
            List<Event> events = new ArrayList<>();
            events.add(event);
            accumulation.put(hash, events);
        } else {
            accumulation.get(hash).add(event);
        }
    }

    class GroupByTask implements Runnable {

        @Override
        public void run() {
            if (accumulation.size() > 0) {
                for (Integer hash : accumulation.keySet()) {
                    Map merge = new HashMap();
                    merge.put("processor", "groupBy");
                    if (flat) {
                        for (Event event : accumulation.get(hash)) {
                            for (String propertyName : event.getPropertyNames()) {
                                merge.put(propertyName, event.getProperty(propertyName));
                            }
                        }
                    } else {
                        List<Map<String, Object>> events = new ArrayList<>();
                        merge.put("events", events);
                        for (Event event : accumulation.get(hash)) {
                            Map<String,Object> properties = new HashMap<>();
                            for (String propertyName : event.getPropertyNames()) {
                                properties.put(propertyName, event.getProperty(propertyName));
                            }
                            events.add(properties);
                        }
                    }
                    // send event
                    String[] topics = targetTopics.split(",");
                    for (String topic : topics) {
                        dispatcher.postEvent(new Event(topic, merge));
                    }
                }
                accumulation.clear();
            }
        }

    }

    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
