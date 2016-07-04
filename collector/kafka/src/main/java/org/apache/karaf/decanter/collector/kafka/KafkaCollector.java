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
package org.apache.karaf.decanter.collector.kafka;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Executors;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
        name = "org.apache.karaf.decanter.collector.kafka",
        immediate = true
)
public class KafkaCollector implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaCollector.class);

    private Dictionary<String, Object> properties;
    private KafkaConsumer<String, String> consumer;
    private String topic;
    private String eventAdminTopic;
    private boolean consuming = false;

    private EventAdmin dispatcher;
    private Unmarshaller unmarshaller;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext componentContext) {
        properties = componentContext.getProperties();

        topic = getValue(properties, "topic", "decanter");
        eventAdminTopic = getValue(properties, EventConstants.EVENT_TOPIC, "decanter/collect/kafka/decanter");

        Properties config = new Properties();

        String bootstrapServers = getValue(properties, "bootstrap.servers", "localhost:9092");
        config.put("bootstrap.servers", bootstrapServers);

        String groupId = getValue(properties, "group.id", "decanter");
        config.put("group.id", groupId);

        String enableAutoCommit = getValue(properties, "enable.auto.commit", "true");
        config.put("enable.auto.commit", enableAutoCommit);

        String autoCommitIntervalMs = getValue(properties, "auto.commit.interval.ms", "1000");
        config.put("auto.commit.interval.ms", autoCommitIntervalMs);

        String sessionTimeoutMs = getValue(properties, "session.timeout.ms", "30000");
        config.put("session.timeout.ms", sessionTimeoutMs);

        String keyDeserializer = getValue(properties, "key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("key.deserializer", keyDeserializer);

        String valueDeserializer = getValue(properties, "value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        config.put("value.deserializer", valueDeserializer);

        String securityProtocol = getValue(properties, "security.protocol", null);
        if (securityProtocol != null)
            config.put("security.protocol", securityProtocol);

        String sslTruststoreLocation = getValue(properties, "ssl.truststore.location", null);
        if (sslTruststoreLocation != null)
            config.put("ssl.truststore.location", sslTruststoreLocation);

        String sslTruststorePassword = getValue(properties, "ssl.truststore.password", null);
        if (sslTruststorePassword != null)
            config.put("ssl.truststore.password", sslTruststorePassword);

        String sslKeystoreLocation = getValue(properties, "ssl.keystore.location", null);
        if (sslKeystoreLocation != null)
            config.put("ssl.keystore.location", sslKeystoreLocation);

        String sslKeystorePassword = getValue(properties, "ssl.keystore.password", null);
        if (sslKeystorePassword != null)
            config.put("ssl.keystore.password", sslKeystorePassword);

        String sslKeyPassword = getValue(properties, "ssl.key.password", null);
        if (sslKeyPassword != null)
            config.put("ssl.key.password", sslKeyPassword);

        String sslProvider = getValue(properties, "ssl.provider", null);
        if (sslProvider != null)
            config.put("ssl.provider", sslProvider);

        String sslCipherSuites = getValue(properties, "ssl.cipher.suites", null);
        if (sslCipherSuites != null)
            config.put("ssl.cipher.suites", sslCipherSuites);

        String sslEnabledProtocols = getValue(properties, "ssl.enabled.protocols", null);
        if (sslEnabledProtocols != null)
            config.put("ssl.enabled.protocols", sslEnabledProtocols);

        String sslTruststoreType = getValue(properties, "ssl.truststore.type", null);
        if (sslTruststoreType != null)
            config.put("ssl.truststore.type", sslTruststoreType);

        String sslKeystoreType = getValue(properties, "ssl.keystore.type", null);
        if (sslKeystoreType != null)
            config.put("ssl.keystore.type", sslKeystoreType);

        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            consumer = new KafkaConsumer<String, String>(config);
            consumer.subscribe(Arrays.asList(topic));
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
        consuming = true;
        Executors.newSingleThreadExecutor().execute(this);
    }

    @Deactivate
    public void deactivate(ComponentContext componentContext) {
        consuming = false;
        consumer.close();
    }

    @Override
    public void run() {
        while (consuming) {
            try {
                consume();
            } catch (Exception e) {
                LOGGER.warn(e.getMessage(), e);
            }
        }
    }

    private void consume() throws UnsupportedEncodingException {
        ConsumerRecords<String, String> records = consumer.poll(10000);
        if (records.isEmpty()) {
            return;
        }
        Map<String, Object> data = new HashMap<>();
        try {
            data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
            data.put("hostName", InetAddress.getLocalHost().getHostName());
        } catch (Exception e) {
            LOGGER.warn("Can't populate local host name and address", e);
        }

        // custom fields
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            data.put(key, properties.get(key));
        }
        
        for (ConsumerRecord<String, String> record : records) {
            String value = record.value();
            ByteArrayInputStream is = new ByteArrayInputStream(value.getBytes("utf-8"));
            data.putAll(unmarshaller.unmarshal(is));
        }

        data.put("type", "kafka");
        String karafName = System.getProperty("karaf.name");
        if (karafName != null) {
            data.put("karafName", karafName);
        }
        Event event = new Event(eventAdminTopic, data);
        dispatcher.postEvent(event);
    }

    @Reference
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }
    
    @Reference
    public void setUnmarshaller(Unmarshaller unmarshaller) {
        this.unmarshaller = unmarshaller;
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

}
