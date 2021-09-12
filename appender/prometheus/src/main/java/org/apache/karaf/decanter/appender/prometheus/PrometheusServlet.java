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
package org.apache.karaf.decanter.appender.prometheus;

import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.MetricsServlet;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.osgi.service.http.HttpService;

import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.appender.prometheus",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class PrometheusServlet implements EventHandler {

    @Reference
    HttpService httpService;

    private Dictionary<String, Object> config;

    private String alias;
    private boolean filtered = false;

    private Map<String, Gauge> gauges = new HashMap<>();

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        config = componentContext.getProperties();
        setFiltered();
        alias = (config.get("alias") != null) ? (String) config.get("alias") : "/decanter/prometheus";
        httpService.registerServlet(alias, new MetricsServlet(), null, null);
    }

    @Deactivate
    public void deactivate() throws Exception {
        httpService.unregister(alias);
    }

    @Override
    public void handleEvent(Event event) {
        for (String property : event.getPropertyNames()) {
            if (!filtered || (filtered && config.get("prometheus.key." + property) != null)) {
                if (event.getProperty(property) instanceof Map) {
                    Map<String, Object> map = (Map) event.getProperty(property);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if (entry.getValue() instanceof Long || entry.getValue() instanceof Integer) {
                            String convertedProperty = (property + "." + entry.getKey()).replace(".", "_");
                            Gauge gauge = gauges.get(convertedProperty);
                            if (gauge == null) {
                                gauge = Gauge.build().name(convertedProperty).help(property + "." + entry.getKey()).register();
                                gauges.put(convertedProperty, gauge);
                            }
                            if (entry.getValue() instanceof Long) {
                                gauge.set((Long) entry.getValue());
                            } else if (entry.getValue() instanceof Integer) {
                                gauge.set((Integer) entry.getValue());
                            }
                        }
                    }
                }
                if (event.getProperty(property) instanceof Long || event.getProperty(property) instanceof Integer) {
                    String convertedProperty = property.replace(".", "_");
                    Gauge gauge = gauges.get(convertedProperty);
                    if (gauge == null) {
                        gauge = Gauge.build().name(convertedProperty).help(property).register();
                        gauges.put(convertedProperty, gauge);
                    }
                    if (event.getProperty(property) instanceof Long) {
                        gauge.set((Long) event.getProperty(property));
                    } else if (event.getProperty(property) instanceof Integer) {
                        gauge.set((Integer) event.getProperty(property));
                    }
                }
            }
        }
    }

    private void setFiltered() {
        Enumeration<String> keys = config.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith("prometheus.key")) {
                filtered = true;
                return;
            }
        }
    }

}
