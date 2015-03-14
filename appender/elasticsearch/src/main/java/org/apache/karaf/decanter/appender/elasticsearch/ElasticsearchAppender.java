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
package org.apache.karaf.decanter.appender.elasticsearch;

import static org.elasticsearch.common.settings.ImmutableSettings.settingsBuilder;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter appender which inserts into Elasticsearch
 */
public class ElasticsearchAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");
    Client client;

    private String host;
    private int port;

    public ElasticsearchAppender(String host, int port) {
        this.host = host;
        this.port = port;
        TimeZone tz = TimeZone.getTimeZone( "UTC" );
        tsFormat.setTimeZone(tz);
        indexDateFormat.setTimeZone(tz);
    }

    @SuppressWarnings("resource")
    public void open() {
        try {
            Settings settings = settingsBuilder().classLoader(Settings.class.getClassLoader()).build();
            InetSocketTransportAddress address = new InetSocketTransportAddress(host, port);
            client = new TransportClient(settings).addTransportAddress(address);
            LOGGER.info("Starting Elasticsearch appender writing to {}", address.address());
        } catch (Exception e) {
            LOGGER.error("Error connecting to elastic search", e);
        }
    }

    public void close() {
        LOGGER.info("Stopping Elasticsearch appender");
        client.close();
    }

    @Override
    public void handleEvent(Event event) {
        try {
            send(client, event);
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void send(Client client, Event event) {
        Map<String, Object> props = new HashMap<>();
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date((Long)ts) : new Date();
        
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add("@timestamp", tsFormat.format(date));
        for (String key : event.getPropertyNames()) {
            Object value = event.getProperty(key);
            if (value instanceof String) {
                jsonObjectBuilder.add(key, (String) value);
            } else if (value instanceof Map) {
                jsonObjectBuilder.add(key, build((Map<String, Object>) value));
            }
        }
        JsonObject jsonObject = jsonObjectBuilder.build();
        String indexName = getIndexName("karaf", date);
        client.prepareIndex(indexName, "karaf_event").setSource(jsonObject.toString()).execute().actionGet();
    }

    private JsonObject build(Map<String, Object> value) {
        JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
        for (Entry<String, Object> innerEntrySet : value.entrySet()) {
            addProperty(innerBuilder, innerEntrySet.getKey(), innerEntrySet.getValue());
        }
        return innerBuilder.build();
    }

    private void addProperty(JsonObjectBuilder innerBuilder, String innerKey, Object innerValue) {
        if (innerValue instanceof String)
            innerBuilder.add(innerKey, (String) innerValue);
        else if (innerValue instanceof Long)
            innerBuilder.add(innerKey, (Long) innerValue);
        else if (innerValue instanceof Integer)
            innerBuilder.add(innerKey, (Integer) innerValue);
        else if (innerValue instanceof Float)
            innerBuilder.add(innerKey, (Float) innerValue);
    }

    private String getIndexName(String prefix, Date date) {
        return prefix + "-" + indexDateFormat.format(date);
    }

}
