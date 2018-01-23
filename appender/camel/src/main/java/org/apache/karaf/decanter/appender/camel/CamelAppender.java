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

    private CamelContext camelContext;
    private String destinationUri;

    private final static Logger LOGGER = LoggerFactory.getLogger(CamelAppender.class);

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws ConfigurationException {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) throws ConfigurationException {
        LOGGER.debug("Creating CamelContext, and use the {} URI", destinationUri);
        this.camelContext = new DefaultCamelContext();
        this.destinationUri = (String) config.get("destination.uri");
        if (this.destinationUri == null) {
            throw new ConfigurationException("destination.uri", "destination.uri is not defined");
        }
    }

    @Override
    public void handleEvent(Event event) {
        HashMap<String, Object> data = new HashMap<>();
        for (String name : event.getPropertyNames()) {
            data.put(name, event.getProperty(name));
        }
        LOGGER.debug("Creating producer template");
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        LOGGER.debug("Sending event data on {}", destinationUri);
        producerTemplate.sendBody(destinationUri, data);
    }

    @Deactivate
    public void deactivate() throws Exception {
        this.camelContext.stop();
    }
}
