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
package org.apache.karaf.decanter.processor.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.core.osgi.OsgiClassResolver;
import org.apache.camel.core.osgi.OsgiDataFormatResolver;
import org.apache.camel.core.osgi.OsgiDefaultCamelContext;
import org.apache.camel.core.osgi.OsgiLanguageResolver;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.model.RouteDefinition;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.processor.camel",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class CamelProcessor implements EventHandler {

    @Reference
    private EventAdmin dispatcher;

    private String targetTopics;
    private String callbackUri;
    private String delegateUri;

    private ModelCamelContext camelContext;
    private ServiceRegistration<CamelContext> serviceRegistration;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties(), componentContext.getBundleContext());
    }

    public void activate(Dictionary<String, Object> configuration, BundleContext bundleContext) throws Exception {
        targetTopics = (configuration.get("target.topics") != null) ? configuration.get("target.topics").toString() : "decanter/process/camel";
        callbackUri = (configuration.get("callback.uri") != null) ? configuration.get("callback.uri").toString() : "direct-vm:decanter-callback";
        delegateUri = (configuration.get("delegate.uri") != null) ? configuration.get("delegate.uri").toString() : "direct-vm:decanter-delegate";

        if (bundleContext != null) {
            OsgiDefaultCamelContext osgiCamelContext = new OsgiDefaultCamelContext(bundleContext);
            osgiCamelContext.setClassResolver(new OsgiClassResolver(camelContext, bundleContext));
            osgiCamelContext.setDataFormatResolver(new OsgiDataFormatResolver(bundleContext));
            osgiCamelContext.setLanguageResolver(new OsgiLanguageResolver(bundleContext));
            camelContext = osgiCamelContext;
            serviceRegistration = bundleContext.registerService(CamelContext.class, camelContext, null);
        } else {
            camelContext = new DefaultCamelContext();
        }
        camelContext.start();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(callbackUri)
                        .id("decanter-processor-callback")
                        .process(new Processor() {
                            @Override
                            public void process(Exchange exchange) throws Exception {
                                Map<String, Object> body = exchange.getIn().getBody(Map.class);
                                body.put("processor", "camel");
                                String[] topics = targetTopics.split(",");
                                for (String topic : topics) {
                                    dispatcher.postEvent(new Event(topic, body));
                                }
                            }
                        }).end();
            }
        });
    }

    @Deactivate
    public void deactivate() {
        try {
            camelContext.stop();
            camelContext.removeRouteDefinitions(new ArrayList<RouteDefinition>(camelContext.getRouteDefinitions()));
            if (serviceRegistration != null) {
                serviceRegistration.unregister();
            }
        } catch (Exception e) {
            // no-op
        }
    }

    @Override
    public void handleEvent(Event event) {
        HashMap<String, Object> data = new HashMap<>();
        for (String name : event.getPropertyNames()) {
            data.put(name, event.getProperty(name));
        }
        ProducerTemplate producerTemplate = camelContext.createProducerTemplate();
        producerTemplate.sendBody(delegateUri, data);
    }

    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
