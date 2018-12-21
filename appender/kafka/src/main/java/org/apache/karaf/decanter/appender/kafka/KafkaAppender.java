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

import java.util.Properties;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
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
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class KafkaAppender implements EventHandler {

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(KafkaAppender.class);

    private Properties properties;
    private String topic;
    private KafkaProducer<String, String> producer;

    @Activate
    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) {
        this.properties = ConfigMapper.map(context.getProperties());
        this.topic = properties.getProperty("topic");
        properties.remove("topic");

        // workaround for KAFKA-3218
        ClassLoader originClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(null);
            this.producer = new KafkaProducer<>(properties);
        } finally {
            Thread.currentThread().setContextClassLoader(originClassLoader);
        }
    }

    @Override
    public void handleEvent(Event event) {
        try {
            String type = (String)event.getProperty("type");
            String data = marshaller.marshal(event);
            producer.send(new ProducerRecord<>(topic, type, data), new Callback() {
                @Override
                public void onCompletion(RecordMetadata recordMetadata, Exception e) {
                    if (e != null) {
                        LOGGER.warn("Can't send event to Kafka broker", e);
                    }
                }
            }).get();
            producer.flush();
        } catch (Exception e) {
            LOGGER.warn("Error sending event to kafka", e);
        }
    }
    
    @Deactivate
    public void close() {
        producer.close();
    }

}
