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
package org.apache.karaf.decanter.appender.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;
import java.util.HashMap;

@Component(
    name = "org.apache.karaf.decanter.appender.camel",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class CamelAppender implements EventHandler {

    public static final String DESTINATION_URI_KEY = "destination.uri";

    private CamelContext camelContext;
    private Dictionary<String, Object> config;

    private final static Logger LOGGER = LoggerFactory.getLogger(CamelAppender.class);

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws ConfigurationException {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) throws ConfigurationException {
        this.config = config;
        if (config.get(DESTINATION_URI_KEY) == null) {
            throw new ConfigurationException(DESTINATION_URI_KEY, DESTINATION_URI_KEY + " is not defined");
        }
        LOGGER.debug("Creating CamelContext, and use the {} URI", config.get(DESTINATION_URI_KEY));
        this.camelContext = new DefaultCamelContext();
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            HashMap<String, Object> data = new HashMap<>();
            for (String name : event.getPropertyNames()) {
                data.put(name, event.getProperty(name));
            }
            LOGGER.debug("Creating producer template");
            ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
            LOGGER.debug("Sending event data on {}", config.get(DESTINATION_URI_KEY));
            producerTemplate.sendBody((String) config.get(DESTINATION_URI_KEY), data);
        }
    }

    @Deactivate
    public void deactivate() throws Exception {
        this.camelContext.stop();
    }
}
