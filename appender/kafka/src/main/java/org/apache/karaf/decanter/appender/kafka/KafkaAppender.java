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

import java.io.Closeable;
import java.util.Properties;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KafkaAppender implements EventHandler, Closeable {

    private final static Logger LOGGER = LoggerFactory.getLogger(KafkaAppender.class);

    private Properties connection;
    private String topic;
    private Marshaller marshaller;
    private KafkaProducer<String, String> producer;

    public KafkaAppender(Properties connnection, String topic, Marshaller marshaller) {
        this.connection = connnection;
        this.topic = topic;
        this.marshaller = marshaller;
        this.producer = new KafkaProducer<>(connection);
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

    @Override
    public void close() {
        producer.close();
    }

}
