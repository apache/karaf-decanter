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
package org.apache.karaf.decanter.collector.mqtt;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
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

import java.io.ByteArrayInputStream;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
    name = "org.apache.karaf.decanter.collector.mqtt",
    immediate = true
)
public class MqttCollector {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Unmarshaller unmarshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(MqttCollector.class);

    private Dictionary<String, Object> properties;

    private MqttClient client;
    private String dispatcherTopic;
    private boolean consuming = false;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        properties = componentContext.getProperties();
        String serverUri = getProperty(properties, "server.uri", "tcp://localhost:1883");
        String clientId = getProperty(properties, "client.id", "d:decanter:collector:default");
        String topic = getProperty(properties, "topic", "decanter");
        String username = getProperty(properties, "userName", null);
        String password = getProperty(properties, "password", null);
        dispatcherTopic = getProperty(properties, EventConstants.EVENT_TOPIC, "decanter/collect/mqtt/decanter");
        client = new MqttClient(serverUri, clientId);
        MqttConnectOptions options = new MqttConnectOptions();
        options.setCleanSession(true);
        if (username != null) {
            options.setUserName(username);
        }
        if (password != null) {
            options.setPassword(password.toCharArray());
        }
        options.setConnectionTimeout(60);
        options.setAutomaticReconnect(true);
        options.setKeepAliveInterval(10);
        options.setExecutorServiceTimeout(30);
        client.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                LOGGER.debug("MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                if (message.getPayload() == null) {
                    return;
                }

                Map<String, Object> data = new HashMap<>();
                data.put("type", "mqtt");

                ByteArrayInputStream is = new ByteArrayInputStream(message.getPayload());
                data.putAll(unmarshaller.unmarshal(is));

                try {
                    PropertiesPreparator.prepare(data, properties);
                } catch (Exception e) {
                    LOGGER.warn("Can't prepare data for the dispatcher", e);
                }

                Event event = new Event(dispatcherTopic, data);
                dispatcher.postEvent(event);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
                // nothing to do
            }
        });
        client.connect(options);
        client.subscribe(topic);
    }

    @Deactivate
    public void deactivate() {
        if (client != null) {
            try {
                client.disconnect();
            } catch (Exception e) {
                // no-op
            }
        }
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

}
