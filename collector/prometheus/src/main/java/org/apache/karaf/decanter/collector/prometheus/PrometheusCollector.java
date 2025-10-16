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
package org.apache.karaf.decanter.collector.prometheus;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

@Component(
        name = "org.apache.karaf.decanter.collector.prometheus",
        immediate = true,
        property = { "decanter.collector.name=prometheus",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-prometheus"}
)
public class PrometheusCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(PrometheusCollector.class);

    @Reference
    public EventAdmin dispatcher;

    private Dictionary<String, Object> properties;

    private URL prometheusURL;
    private String type = "prometheus";

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> properties) throws Exception {
        this.properties = properties;
        if (properties.get("prometheus.url") == null) {
            throw new IllegalArgumentException("prometheus.url is mandatory in the configuration");
        }
        prometheusURL = new URL(properties.get("prometheus.url").toString());
    }

    @Override
    public void run() {
        try {
            URLConnection connection = prometheusURL.openConnection();
            String topic = (properties.get(EventConstants.EVENT_TOPIC) != null) ? (String) properties.get(EventConstants.EVENT_TOPIC) : "decanter/collect/prometheus";
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.matches("# .*")) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("type", type);
                        if (line.contains("{")) {
                            final String name = line.substring(0, line.indexOf("{"));
                            String[] labels = line.substring(line.indexOf("{")+1, line.lastIndexOf("}")).split(",");
                            Stream.of(labels).forEach(it -> {
                                try {
                                    // we don't want to store label without value
                                    if (it.contains("=")) {
                                        String labelName = it.substring(0, it.indexOf("=")).replace("\"", "");
                                        String labelValue = it.substring(it.indexOf("=") + 1).replace("\"", "");
                                        data.put(labelName, labelValue);
                                    }
                                } catch (Exception e) {
                                    LOGGER.error("[collector-prometheus] error while parsing label {} :: {}",it, e.getMessage());
                                }
                            });

                            String value = line.substring(line.lastIndexOf("}")+2);
                            Double parseValue = Double.parseDouble(value);
                            data.put(name, parseValue);
                        } else {
                            String[] split = line.split(" ");
                            if (split.length == 2) {
                                String property = split[0];
                                double value = Double.parseDouble(split[1]);
                                data.put(property, value);
                            }
                        }
                        PropertiesPreparator.prepare(data, properties);
                        dispatcher.postEvent(new Event(topic, data));
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Can't get Prometheus metrics", e);
            e.printStackTrace();
        }
    }

    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
