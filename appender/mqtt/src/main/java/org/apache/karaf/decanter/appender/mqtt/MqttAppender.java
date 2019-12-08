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

import java.nio.charset.StandardCharsets;
import java.util.Dictionary;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
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

    public static final String SERVER_PROPERTY = "server";
    public static final String CLIENT_ID_PROPERTY = "clientId";
    public static final String TOPIC_PROPERTY = "topic";

    public static final String SERVER_DEFAULT = "tcp://localhost:1883";
    public static final String CLIENT_ID_DEFAULT = "d:decanter:appender:default";
    public static final String TOPIC_DEFAULT = "decanter";

    @Reference
    public Marshaller marshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(MqttAppender.class);

    private MqttClient client;

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) throws Exception {
        this.config = config;
        client = new MqttClient(
                getValue(config, SERVER_PROPERTY, SERVER_DEFAULT),
                getValue(config, CLIENT_ID_PROPERTY, CLIENT_ID_DEFAULT),
                new MemoryPersistence());
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        String username = getValue(config, "username", null);
        String password = getValue(config, "password", null);
        if (username != null) {
            options.setUserName(username);
        }
        if (password != null) {
            options.setPassword(password.toCharArray());
        }
        client.connect(options);
    }

    private String getValue(Dictionary<String, Object> config, String key, String defaultValue) {
        return (config.get(key) != null) ? (String) config.get(key) : defaultValue;
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            try {
                MqttMessage message = new MqttMessage();
                String jsonSt = marshaller.marshal(event);
                message.setPayload(jsonSt.getBytes(StandardCharsets.UTF_8));
                client.publish(
                        getValue(config, TOPIC_PROPERTY, TOPIC_DEFAULT),
                        message);
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
    }

    @Deactivate
    public void deactivate() throws MqttException {
        client.disconnect();
        client.close();
    }

    
}
