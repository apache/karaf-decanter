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
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.MavenUtils;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class JmsCollectorTest extends KarafTestSupport {

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
                        "mvn:org.apache.karaf.decanter/apache-karaf-decanter/" + System.getProperty("decanter.version") + "/xml/features")
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    @Test
    public void test() throws Exception {
        // install activemq
        featureService.installFeature("aries-blueprint");
        featureService.installFeature("activemq-broker-noweb");

        // install jms
        featureService.installFeature("jms");
        featureService.installFeature("pax-jms-activemq");

        // create connection factory
        System.out.println(executeCommand("jms:create decanter"));
        Thread.sleep(2000);
        System.out.println(executeCommand("jms:connectionfactories"));
        System.out.println(executeCommand("jms:info jms/decanter"));

        // install decanter
        featureService.installFeature("decanter-collector-jms");

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

        // send message to JMS queue
        System.out.println(executeCommand("jms:send jms/decanter decanter '{\"foo\":\"bar\"}'"));

        while (received.size() == 0) {
            Thread.sleep(500);
        }

        Assert.assertEquals(1, received.size());

        Assert.assertEquals("decanter/collect/jms/decanter", received.get(0).getTopic());
        Assert.assertEquals("bar", received.get(0).getProperty("foo"));
        Assert.assertEquals("jms", received.get(0).getProperty("type"));
        Assert.assertEquals("root", received.get(0).getProperty("karafName"));
    }

}
