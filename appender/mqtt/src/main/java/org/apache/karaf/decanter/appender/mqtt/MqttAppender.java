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
package org.apache.karaf.decanter.appender.mqtt;

import java.nio.charset.Charset;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Karaf Decanter appender which sends to mqtt
 */
public class MqttAppender implements EventHandler, AutoCloseable {
    private final static Logger LOGGER = LoggerFactory.getLogger(MqttAppender.class);
    private MqttClient client;
    private Marshaller marshaller;
    private String topic;

    public MqttAppender(String serverURI, String clientId, String topic, Marshaller marshaller) throws MqttSecurityException, MqttException {
        this.topic = topic;
        this.marshaller = marshaller;
        client = new MqttClient(serverURI, clientId, new MemoryPersistence());
        client.connect();
    }

    @Override
    public void handleEvent(Event event) {
        try {
            send(event);
        } catch (Exception e) {
            LOGGER.warn("Error sending to mqtt server " + client.getServerURI(), e);
            try {
                client.disconnect();
                client.connect();
            } catch (MqttException e1) {
                e1.printStackTrace();
            }
        }
    }

    void send(Event event) throws MqttException, MqttPersistenceException {
        MqttMessage message = new MqttMessage();
        String jsonSt = marshaller.marshal(event);
        message.setPayload(jsonSt.getBytes(Charset.forName("UTF-8")));
        client.publish(topic, message);
    }

    @Override
    public void close() throws MqttException {
        client.disconnect();
        client.close();
    }

    
}
