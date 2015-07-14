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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.LockSupport;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.bulk.BulkResponse;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter appender which inserts into Elasticsearch
 */
public class ElasticsearchAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);
    
    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");
    private final AtomicLong pendingBulkItemCount = new AtomicLong();
    private final int concurrentRequests = 1;
    private BulkProcessor bulkProcessor;
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
            bulkProcessor = BulkProcessor.builder(client, new BulkProcessor.Listener() {
                
                @Override
                public void beforeBulk(long executionId, BulkRequest request) {
                    pendingBulkItemCount.addAndGet(request.numberOfActions());
                }
                
                @Override
                public void afterBulk(long executionId, BulkRequest request, Throwable failure) {
                    LOGGER.warn("Can't append into Elasticsearch", failure);
                    pendingBulkItemCount.addAndGet(-request.numberOfActions());
                }
                
                @Override
                public void afterBulk(long executionId, BulkRequest request, BulkResponse response) {
                    pendingBulkItemCount.addAndGet(-response.getItems().length);
                }
            })
            .setBulkActions(1000)
            .setConcurrentRequests(concurrentRequests)
            .setBulkSize(ByteSizeValue.parseBytesSizeValue("5mb"))
            .setFlushInterval(TimeValue.timeValueSeconds(5))
            .build();
            LOGGER.info("Starting Elasticsearch appender writing to {}", address.address());
        } catch (Exception e) {
            LOGGER.error("Error connecting to elastic search", e);
        }
    }

    public void close() {
        LOGGER.info("Stopping Elasticsearch appender");
        
        if(bulkProcessor != null) {
            bulkProcessor.close();
        }

        //if ConcurrentRequests > 0 we ll wait here because close() triggers a flush which is async
        while(concurrentRequests > 0 && pendingBulkItemCount.get() > 0) {
            LockSupport.parkNanos(1000*50);
        }
        
        if(client != null) {
            client.close();
        }
    }

    @Override
    public void handleEvent(Event event) {
        try {
            send(event);
        } catch (Exception e) {
            LOGGER.warn("Can't append into Elasticsearch", e);
        }
    }

    @SuppressWarnings("unchecked")
    private void send(Event event) {
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date(ts) : new Date();
        
        JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
        jsonObjectBuilder.add("@timestamp", tsFormat.format(date));
        for (String key : event.getPropertyNames()) {
            Object value = event.getProperty(key);
            if (value instanceof Map) {
                jsonObjectBuilder.add(key, build((Map<String, Object>) value));
            } else {
                addProperty(jsonObjectBuilder, key, value);
            }
        }
        JsonObject jsonObject = jsonObjectBuilder.build();
        String indexName = getIndexName("karaf", date);
        String jsonSt = jsonObject.toString();
        
        if(LOGGER.isDebugEnabled()) {
            LOGGER.debug("Sending event to elastic search with content: {}", jsonSt);
        }
        
        bulkProcessor.add(new IndexRequest(indexName, getType(event)).source(jsonSt));
    }

    private String getType(Event event) {
        String type = (String)event.getProperty("type");
        return type != null ? type : "karaf_event";
    }

    private JsonObject build(Map<String, Object> value) {
        JsonObjectBuilder innerBuilder = Json.createObjectBuilder();
        for (Entry<String, Object> innerEntrySet : value.entrySet()) {
            addProperty(innerBuilder, innerEntrySet.getKey(), innerEntrySet.getValue());
        }
        return innerBuilder.build();
    }

    private void addProperty(JsonObjectBuilder builder, String key, Object value) {
        if (value instanceof BigDecimal)
            builder.add(key, (BigDecimal) value);
        else if (value instanceof BigInteger)
            builder.add(key, (BigInteger) value);
        else if (value instanceof String)
            builder.add(key, (String) value);
        else if (value instanceof Long)
            builder.add(key, (Long) value);
        else if (value instanceof Integer)
            builder.add(key, (Integer) value);
        else if (value instanceof Float)
            builder.add(key, (Float) value);
        else if (value instanceof Double)
            builder.add(key, (Double) value);
        else if (value instanceof Boolean)
            builder.add(key, (Boolean) value);
    }

    private String getIndexName(String prefix, Date date) {
        return prefix + "-" + indexDateFormat.format(date);
    }

}
