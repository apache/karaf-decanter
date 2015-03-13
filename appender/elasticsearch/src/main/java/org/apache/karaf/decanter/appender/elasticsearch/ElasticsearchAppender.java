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
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.apache.karaf.decanter.api.Appender;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter appender which inserts into Elasticsearch
 */
public class ElasticsearchAppender implements Appender {

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

    public void append(Map<Long, Map<String, Object>> data) throws Exception {
        try {
            for (Entry<Long, Map<String, Object>> entry : data.entrySet()) {
                send(client, new Date(entry.getKey()), entry.getValue());
            }
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        }
    }

    private void send(Client client, Date date, Map<String, Object> props) {
        props.put("@timestamp", tsFormat.format(date));
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        for (Entry<String, Object> valueEntry : props.entrySet()) {
            Object value = valueEntry.getValue();
            if (value instanceof String) {
                jsonObjectBuilder.add(valueEntry.getKey(), (String)value);
            } else if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                JsonObject jsonO = asJson(jsonObjectBuilder, (Map<String, Object>)value);
                jsonObjectBuilder.add(valueEntry.getKey(), jsonO);
            }
        }
        JsonObject jsonObject = jsonObjectBuilder.build();
        String indexName = getIndexName("karaf", date);
        client.prepareIndex(indexName, "karaf_event").setSource(jsonObject.toString()).execute().actionGet();
    }

    private String getIndexName(String prefix, Date date) {
        return prefix + "-" + indexDateFormat.format(date);
    }

    private JsonObject asJson(JsonObjectBuilder jsonObjectBuilder, Map<String, Object> value) {
        JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
        for (Entry<String, Object> innerEntrySet : value.entrySet()) {
            String key = innerEntrySet.getKey();
            Object object = innerEntrySet.getValue();
            if (object instanceof String)
                innerBuilder.add(key, (String)object);
            else if (object instanceof Long)
                innerBuilder.add(key, (Long)object);
            else if (object instanceof Integer)
                innerBuilder.add(key, (Integer)object);
            else if (object instanceof Float)
                innerBuilder.add(key, (Float)object);
        }
        return innerBuilder.build();
    }

}
