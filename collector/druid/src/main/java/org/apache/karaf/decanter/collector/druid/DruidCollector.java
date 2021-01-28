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
package org.apache.karaf.decanter.collector.druid;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Decanter Druid collector, periodically execute queries on Apache Druid
 */
@Component(
        service = Runnable.class,
        name = "org.apache.karaf.decanter.collector.druid",
        immediate = true,
        property = {
                "decanter.collector.name=druid",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-druid"
        }
)
public class DruidCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Unmarshaller unmarshaller;

    private Dictionary<String, Object> config;

    private final static Logger LOGGER = LoggerFactory.getLogger(DruidCollector.class);

    @Activate
    public void activate(ComponentContext componentContext) {
        config = componentContext.getProperties();
    }

    @Override
    public void run() {
        // get Druid broker location
        String druidBroker = (config.get("druid.broker.location") != null) ? (String) config.get("druid.broker.location") : "http://localhost:8888/druid/v2/sql/";

        Enumeration<String> keys = config.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            if (key.startsWith("query.")) {
                try {
                    Map<String, Object> data = new HashMap<>();
                    data.put("query", key.substring("query.".length()));
                    data.putAll(executeQuery(druidBroker, (String) config.get(key)));
                    PropertiesPreparator.prepare(data, config);
                    String topic = (config.get("topic") != null) ? (String) config.get("topic") : "decanter/collect/druid";
                    dispatcher.postEvent(new Event(topic, data));
                } catch (Exception e) {
                    LOGGER.warn("Can't execute query {}", key.substring("query.".length()), e);
                }
            }
        }
    }

    private Map<String, Object> executeQuery(String broker, String query) throws Exception {
        LOGGER.debug("Executing {} on {}", query, broker);
        HttpURLConnection connection = (HttpURLConnection) new URL(broker).openConnection();
        connection.setRequestMethod("POST");
        connection.addRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        connection.setDoInput(true);
        String queryJson = "{ \"query\": \"" + query + "\", \"resultFormat\": \"object\"}";
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
            writer.write(queryJson);
        }
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
        }
        String jsonResult = "{ \"result\": " + result.toString() + "}";
        return unmarshaller.unmarshal(new ByteArrayInputStream(jsonResult.getBytes()));
    }

}
