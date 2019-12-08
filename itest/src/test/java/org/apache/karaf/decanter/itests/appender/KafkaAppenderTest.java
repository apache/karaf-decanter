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
package org.apache.karaf.decanter.itests.appender;

import org.apache.karaf.itests.KarafTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Configuration;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.karaf.options.KarafDistributionOption;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerClass;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.stream.Stream;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerClass.class)
public class KafkaAppenderTest extends KarafTestSupport {

    @Configuration
    public Option[] config() {
        Option[] options = new Option[]{
                KarafDistributionOption.editConfigurationFilePut("etc/system.properties", "decanter.version", System.getProperty("decanter.version"))
        };
        return Stream.of(super.config(), options).flatMap(Stream::of).toArray(Option[]::new);
    }

    /**
     * Require running Kafka broker.
     */
    @Test
    public void test() throws Exception {
        System.out.println("Please, first download and start Kafka:");
        System.out.println("\tbin/zookeeper-server-start.sh ../config/zookeeper.properties");
        System.out.println("\tbin/kafka-server-start.sh ../config/server.properties");
        System.out.println("Create decanter topic:");
        System.out.println("\tbin/kafka-topics.sh --create --partitions 1 --replication-factor 1 --zookeeper localhost:2181 --topic decanter");

        // install decanter
        addFeaturesRepository("mvn:org.apache.karaf.decanter/apache-karaf-decanter/" + System.getProperty("decanter.version") + "/xml/features");
        installAndAssertFeature("decanter-common");
        installAndAssertFeature("decanter-appender-kafka");

        // send event
        EventAdmin eventAdmin = getOsgiService(EventAdmin.class);
        HashMap<String, String> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect/test", data);
        eventAdmin.sendEvent(event);

        System.out.println("Check message in Kafka using:");
        System.out.println("\tbin/kafka-console-consumer.sh ");

        System.out.println("*********************************");
        System.out.println("* Latest Tested Version: 2.1.0  *");
        System.out.println("*********************************");
    }

}
