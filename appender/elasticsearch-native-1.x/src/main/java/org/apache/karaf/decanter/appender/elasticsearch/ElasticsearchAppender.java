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
import java.util.Dictionary;
import java.util.TimeZone;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.index.IndexRequest;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.unit.ByteSizeValue;
import org.elasticsearch.common.unit.TimeValue;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter appender which inserts into Elasticsearch
 */
@Component(
    name = "org.apache.karaf.decanter.appender.elasticsearch",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class ElasticsearchAppender implements EventHandler {

    public static String HOST_PROPERTY = "host";
    public static String PORT_PROPERTY = "port";
    public static String CLUSTER_NAME_PROPERTY = "clusterName";
    public static String INDEX_PREFIX_PROPERTY = "index.prefix";
    public static String INDEX_EVENT_TIMESTAMPED_PROPERTY = "index.event.timestamped";

    public static String HOST_DEFAULT = "localhost";
    public static String PORT_DEFAULT = "9300";
    public static String CLUSTER_NAME_DEFAULT = "elasticsearch";
    public static String INDEX_PREFIX_DEFAULT = "karaf";
    public static String INDEX_EVENT_TIMESTAMPED_DEFAULT = "true";

    final static Logger LOGGER = LoggerFactory.getLogger(ElasticsearchAppender.class);

    @Reference
    public Marshaller marshaller;

    private final SimpleDateFormat tsFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'");
    private final SimpleDateFormat indexDateFormat = new SimpleDateFormat("yyyy.MM.dd");

    private final int concurrentRequests = 1;
    private BulkProcessor bulkProcessor;
    TransportClient client;

    private WorkFinishedListener listener;

    private Dictionary<String, Object> config;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) {
        open(context.getProperties());
    }
    
    public void open(Dictionary<String, Object> config) {
        this.config = config;
        try {
            String host = getValue(config, HOST_PROPERTY, HOST_DEFAULT);
            int port = Integer.parseInt(getValue(config, PORT_PROPERTY, PORT_DEFAULT));
            String cluster = getValue(config, CLUSTER_NAME_PROPERTY, CLUSTER_NAME_DEFAULT);
            TimeZone tz = TimeZone.getTimeZone( "UTC" );
            tsFormat.setTimeZone(tz);
            indexDateFormat.setTimeZone(tz);
            Settings settings = settingsBuilder()
                .classLoader(Settings.class.getClassLoader())
                .put("cluster.name", cluster)
                .build();
            InetSocketTransportAddress address = new InetSocketTransportAddress(host, port);
            client = new TransportClient(settings);
            client.addTransportAddress(address);
            listener = new WorkFinishedListener(concurrentRequests);
            bulkProcessor = BulkProcessor.builder(client, listener)
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

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Deactivate
    public void close() {
        LOGGER.info("Stopping Elasticsearch appender");

        if (bulkProcessor != null) {
            bulkProcessor.close();
        }

        // Need to wait till all requests are processed as close would do this asynchronously
        if (listener != null) {
            listener.waitFinished();
        }

        if(client != null) {
            client.close();
        }
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            try {
                send(event);
            } catch (Exception e) {
                LOGGER.warn("Can't append into Elasticsearch", e);
            }
        }
    }

    private void send(Event event) {
        String indexName = getIndexName(getValue(config, INDEX_PREFIX_PROPERTY, INDEX_PREFIX_DEFAULT), getDate(event));
        String jsonSt = marshaller.marshal(event);
        LOGGER.debug("Sending event to elastic search with content: {}", jsonSt);
        bulkProcessor.add(new IndexRequest(indexName, getType(event)).source(jsonSt));
    }

    private Date getDate(Event event) {
        Long ts = (Long)event.getProperty("timestamp");
        Date date = ts != null ? new Date(ts) : new Date();
        return date;
    }

    private String getType(Event event) {
        String type = (String)event.getProperty("type");
        return type != null ? type : "karaf_event";
    }

    private String getIndexName(String prefix, Date date) {
        boolean indexTimestamped = Boolean.parseBoolean(getValue(config, INDEX_EVENT_TIMESTAMPED_PROPERTY, INDEX_EVENT_TIMESTAMPED_DEFAULT));
        if (indexTimestamped) {
            return prefix + "-" + indexDateFormat.format(date);
        } else {
            return prefix;
        }
    }

}
