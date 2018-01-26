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

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class ManualReceive {

    private static final String TOPIC_FILTER = "decanter";
    private static final String SERVER = "tcp://localhost:1883";

    private void receive() throws Exception {
        MqttClient client = new MqttClient(SERVER, "test");
        MqttCallback callback = new MqttCallback() {

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                System.out.println(message);
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
        client.subscribe(TOPIC_FILTER);
        client.setCallback(callback);
        System.in.read();
        client.disconnect();
        client.close();
    }

    public static void main(String[] args) throws Exception {
        new ManualReceive().receive();
    }
}
