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
package org.apache.karaf.decanter.processor.passthrough;

import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.Dictionary;
import java.util.HashMap;

@Component(
        name = "org.apache.karaf.decanter.processor.passthrough",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class PassThroughProcessor implements EventHandler {

    private String targetTopic;

    @Reference
    private EventAdmin dispatcher;

    @Activate
    public void activate(ComponentContext componentContext) {
        Dictionary<String, Object> properties = componentContext.getProperties();
        String targetTopic = "decanter/process/passthrough";
        if (properties.get("target.topics") != null) {
            targetTopic = properties.get("target.topics").toString();
        }
        activate(targetTopic);
    }

    public void activate(String targetTopic) {
        this.targetTopic = targetTopic;
    }

    @Override
    public void handleEvent(Event event) {
        HashMap<String, Object> data = new HashMap<>();
        data.put("processor", "passthrough");
        data.put("source.topics", event.getProperty("event.topics"));
        for (String propertyName : event.getPropertyNames()) {
            data.put(propertyName, event.getProperty(propertyName));
        }
        String[] targetTopics = targetTopic.split(",");
        for (String topic : targetTopics) {
            dispatcher.postEvent(new Event(topic, data));
        }
    }

    /**
     * Visible for testing.
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
