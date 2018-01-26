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
import java.nio.charset.StandardCharsets;
import java.util.Dictionary;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
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

@Component(
        name = "org.apache.karaf.decanter.appender.mqtt",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class MqttAppender implements EventHandler {

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(MqttAppender.class);

    private MqttClient client;
    private String server;
    private String clientId;
    private String topic;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> dictionary) throws Exception {
        this.server = getProperty(dictionary, "server", "tcp://localhost:9300");
        this.clientId = getProperty(dictionary, "clientId", "decanter");
        this.topic = getProperty(dictionary, "topic", "decanter");
        client = new MqttClient(server, clientId, new MemoryPersistence());
        client.connect();
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        try {
            MqttMessage message = new MqttMessage();
            String jsonSt = marshaller.marshal(event);
            message.setPayload(jsonSt.getBytes(StandardCharsets.UTF_8));
            client.publish(topic, message);
        } catch (Exception e) {
            LOGGER.warn("Error sending to MQTT server " + client.getServerURI(), e);
            try {
                client.disconnect();
                client.connect();
            } catch (MqttException e1) {
                e1.printStackTrace();
            }
        }
    }

    @Deactivate
    public void deactivate() throws MqttException {
        client.disconnect();
        client.close();
    }

    
}
