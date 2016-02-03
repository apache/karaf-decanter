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

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.store.memory.MemoryPersistenceAdapter;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttSecurityException;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

public class TestMqttAppender  {
    private static final String SERRVER = "tcp://localhost:11883";
    private static final String TOPIC = "decanter";
    private static final long TIMESTAMP = 1454428780634L;

    @Test
    public void testSend() throws URISyntaxException, Exception {
        BrokerService brokerService = new BrokerService();
        brokerService.setUseJmx(false);
        brokerService.setPersistenceAdapter(new MemoryPersistenceAdapter());
        brokerService.addConnector(new URI("mqtt://localhost:11883"));
        brokerService.start();
        
        List<MqttMessage> received = new ArrayList<>();
        MqttClient client = receive(received);
        
        
        Marshaller marshaller = new JsonMarshaller();
        MqttAppender appender = new MqttAppender(SERRVER, "my", TOPIC, marshaller);
        
        Map<String, Object> properties = new HashMap<>();
        properties.put(EventConstants.TIMESTAMP, TIMESTAMP);
        Event event = new Event(TOPIC, properties);
        appender.send(event);
        Thread.sleep(100);
        Assert.assertEquals(1, received.size());
        
        String jsonSt = received.iterator().next().toString();
        JsonReader reader = Json.createReader(new StringReader(jsonSt));
        JsonObject jsonO = reader.readObject();
        Assert.assertEquals("2016-02-02T15:59:40,634Z", jsonO.getString("@timestamp"));
        Assert.assertEquals(TIMESTAMP, jsonO.getJsonNumber(EventConstants.TIMESTAMP).longValue());
        Assert.assertEquals("decanter", jsonO.getString(EventConstants.EVENT_TOPIC));
        
        appender.close();
        client.disconnect();
        client.close();
        brokerService.stop();
    }

    private MqttClient receive(final List<MqttMessage> received) throws MqttException, MqttSecurityException {
        MqttClient client = new MqttClient(SERRVER, "test");
        MqttCallback callback = new MqttCallback() {
            
            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                received.add(message);
            }
            
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {
            }
            
            @Override
            public void connectionLost(Throwable cause) {
                cause.printStackTrace();
            }
        };
        client.connect();
        client.subscribe(TOPIC);
        client.setCallback(callback);
        return client;
    }
    
}
