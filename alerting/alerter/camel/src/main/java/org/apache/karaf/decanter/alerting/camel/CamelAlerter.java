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
package org.apache.karaf.decanter.alerting.camel;

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

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
    name = "org.apache.karaf.decanter.alerting.camel",
    property = EventConstants.EVENT_TOPIC + "=decanter/alert/*"
)
public class CamelAlerter implements EventHandler {

    private CamelContext camelContext;
    private String alertDestinationUri;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws ConfigurationException {
        Dictionary<String, Object> config = context.getProperties();
        this.alertDestinationUri = (String) config.get("alert.destination.uri");
        if (alertDestinationUri == null) {
            throw new ConfigurationException("alert.destination.uri", "alert.destination.uri property is not defined");
        }
        this.camelContext = new DefaultCamelContext();
    }

    @Override
    public void handleEvent(Event event) {
        HashMap<String, Object> data = new HashMap<>();
        for (String name : event.getPropertyNames()) {
            data.put(name, event.getProperty(name));
        }
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        Map<String, Object> headers = new HashMap<>();
        headers.put("alertLevel", event.getProperty("alertLevel"));
        headers.put("alertAttribute", event.getProperty("alertAttribute"));
        headers.put("alertPattern", event.getProperty("alertPattern"));
        headers.put("alertBackToNormal", event.getProperty("alertBackToNormal"));
        producerTemplate.sendBodyAndHeaders(alertDestinationUri, data, headers);
    }

    @Deactivate
    public void deactivate() throws Exception {
        this.camelContext.stop();
    }
}
