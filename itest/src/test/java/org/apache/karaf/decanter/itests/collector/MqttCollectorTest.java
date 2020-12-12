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
package org.apache.karaf.decanter.itests.collector;

import org.apache.karaf.itests.KarafTestSupport;
import org.apache.karaf.jaas.boot.principal.RolePrincipal;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.ProbeBuilder;
import org.ops4j.pax.exam.TestProbeBuilder;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionBaseConfigurationOption;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.framework.Constants;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

import static org.ops4j.pax.exam.CoreOptions.mavenBundle;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class MqttCollectorTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        String karafVersion = MavenUtils.getArtifactVersion("org.apache.karaf", "apache-karaf");
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "activemq.version", System.getProperty("activemq.version")),
                KarafDistributionOption.editConfigurationFilePut("etc/org.apache.karaf.features.cfg", "featuresRepositories",
                        "mvn:org.apache.karaf.features/framework/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/spring/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/spring-legacy/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/enterprise/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/enterprise-legacy/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.karaf.features/standard/" + karafVersion + "/xml/features," +
                                "mvn:org.apache.activemq/activemq-karaf/" + System.getProperty("activemq.version") + "/xml/features," +
                                "mvn:org.apache.karaf.decanter/apache-karaf-decanter/" + System.getProperty("decanter.version") + "/xml/features"),
                KarafDistributionOption.editConfigurationFileExtend("etc/org.apache.karaf.features.cfg", "featuresBoot", "aries-blueprint,activemq-broker-noweb"),
                KarafDistributionOption.replaceConfigurationFile("etc/activemq.xml", getConfigFile("/etc/activemq.xml")),
                mavenBundle().groupId("org.eclipse.paho").artifactId("org.eclipse.paho.client.mqttv3").version(System.getProperty("paho.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test(timeout = 60000)
    @Ignore("Flaky test")
    public void test() throws Exception {
        System.out.println("Waiting ActiveMQ MQTT connector ...");
        while (true) {
            Thread.sleep(200);
            try {
                Socket socket = new Socket(InetAddress.getLocalHost(), 1883);
                break;
            } catch (IOException ioException) {
                // no-op
            }
        }

        // install decanter
        System.out.println(executeCommand("feature:install decanter-collector-mqtt", new RolePrincipal("admin")));

        // create event handler
        List<Event> received = new ArrayList();
        EventHandler eventHandler = new EventHandler() {
            @Override
            public void handleEvent(Event event) {
                received.add(event);
            }
        };
        Hashtable serviceProperties = new Hashtable();
        serviceProperties.put(EventConstants.EVENT_TOPIC, "decanter/collect/*");
        bundleContext.registerService(EventHandler.class, eventHandler, serviceProperties);

        // send MQTT message
        MqttClient client = new MqttClient("tcp://localhost:1883", "d:decanter:collector:test");
        client.connect();
        MqttMessage message = new MqttMessage();
        message.setQos(0);
        message.setRetained(true);
        message.setPayload("This is a test".getBytes(StandardCharsets.UTF_8));
        client.publish("decanter", message);

        System.out.println("Waiting messages ...");
        while (received.size() == 0) {
            Thread.sleep(500);
        }

        System.out.println("");

        for (int i = 0; i < received.size(); i++) {
            for (String property : received.get(i).getPropertyNames()) {
                System.out.println(property + " = " + received.get(i).getProperty(property));
            }
            System.out.println("========");
        }

        System.out.println("");

        Assert.assertEquals(1, received.size());

        Assert.assertEquals("decanter/collect/mqtt/decanter", received.get(0).getTopic());
        Assert.assertTrue(((String) received.get(0).getProperty("payload")).contains("This is a test"));
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
        Assert.assertEquals("mqtt", received.get(0).getProperty("type"));

        client.disconnect();
    }

}
