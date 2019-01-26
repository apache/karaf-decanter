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

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.junit.*;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class KafkaAppenderTest {

    @ClassRule
    public static EmbeddedZooKeeper zookeeper = new EmbeddedZooKeeper(PortFinder.getNextAvailable(23000));

    @ClassRule
    public static EmbeddedKafkaBroker kafkaBroker =
            new EmbeddedKafkaBroker(0,
                    PortFinder.getNextAvailable(24000),
                    zookeeper.getConnection(),
                    new Properties());

    private static final Logger LOG = LoggerFactory.getLogger(KafkaAppenderTest.class);

    @BeforeClass
    public static void beforeClass() {
        LOG.info("Embedded Zookeeper connection: " + zookeeper.getConnection());
        LOG.info("Embedded Kafka cluster broker list: " + kafkaBroker.getBrokerList());
    }

    @Test
    @Ignore
    public void test() throws Exception {
        KafkaAppender appender = new KafkaAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("topic", "test");
        config.put("bootstrap.servers", kafkaBroker.getBrokerList());
        appender.activate(config);

        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collect", data);
        appender.handleEvent(event);

        Properties kafkaConfig = new Properties();
        kafkaConfig.put("topic", "test");
        kafkaConfig.put("bootstrap.servers", kafkaBroker.getBrokerList());
        KafkaConsumer consumer = new KafkaConsumer<String, String>(kafkaConfig);
        consumer.subscribe(Arrays.asList("test"));
        ConsumerRecords<String, String> records = consumer.poll(1000);
        Assert.assertFalse(records.isEmpty());
        Assert.assertEquals(1, records.count());
    }

}
