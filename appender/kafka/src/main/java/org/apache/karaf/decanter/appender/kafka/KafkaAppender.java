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
package org.apache.karaf.decanter.appender.kafka;

import java.util.Dictionary;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    name = "org.apache.karaf.decanter.appender.kafka",
    immediate = true,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class KafkaAppender implements EventHandler {
    private final static Logger LOGGER = LoggerFactory.getLogger(KafkaAppender.class);

    private Properties properties;
    private String topic;
    private Marshaller marshaller;
    private KafkaProducer<String, String> producer;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        Dictionary<String, Object> config = context.getProperties();
        this.properties = new Properties();

        String bootstrapServers = getValue(config, "bootstrap.servers", "localhost:9092");
        properties.put("bootstrap.servers", bootstrapServers);

        String clientId = getValue(config, "client.id", "");
        properties.put("client.id", clientId);

        String compressionType = getValue(config, "compression.type", "none");
        properties.put("compression.type", compressionType);

        String acks = getValue(config, "acks", "all");
        properties.put("acks", acks);

        String retries = getValue(config, "retries", "0");
        properties.put("retries", Integer.parseInt(retries));

        String batchSize = getValue(config, "batch.size", "16384");
        properties.put("batch.size", Integer.parseInt(batchSize));

        String bufferMemory = getValue(config, "buffer.memory", "33554432");
        properties.put("buffer.memory", Long.parseLong(bufferMemory));

        String keySerializer = getValue(config, "key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("key.serializer", keySerializer);

        String valueSerializer = getValue(config, "value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        properties.put("value.serializer", valueSerializer);

        this.topic = getValue(config, "topic", "decanter");
        this.producer = new KafkaProducer<>(properties);
    }
    
    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        String value = (String)config.get(key);
        return (value != null) ? value :  defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        try {
            String type = (String)event.getProperty("type");
            String data = marshaller.marshal(event);
            producer.send(new ProducerRecord<>(topic, type, data));
        } catch (RuntimeException e) {
            LOGGER.warn("Error sending event to kafka", e);
        }
    }
    
    @Deactivate
    public void close() {
        producer.close();
    }

    @Reference
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }
}
