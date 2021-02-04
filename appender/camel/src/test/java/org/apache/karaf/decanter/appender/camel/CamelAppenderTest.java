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
package org.apache.karaf.decanter.appender.camel;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class CamelAppenderTest {

    private static final String TOPIC = "decanter/collect/jmx";
    private static final long TIMESTAMP = 1454428780634L;

    private DefaultCamelContext camelContext;

    @Before
    public void setup() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:decanter")
                        .id("decanter-test")
                        .log("Received ${body}")
                        .to("mock:assert");
            }
        });
        camelContext.start();
    }

    @After
    public void teardown() throws Exception {
        camelContext.stop();
    }

    @Test
    public void test() throws Exception {
        CamelAppender appender = new CamelAppender();
        Hashtable<String, Object> config = new Hashtable<>();
        config.put(CamelAppender.DESTINATION_URI_KEY, "direct-vm:decanter");
        appender.open(config, null);

        Map<String, Object> data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("testKey", "testValue");
        Event event = new Event(TOPIC, data);

        appender.handleEvent(event);

        Map<String, Object> expected = new HashMap<>();
        expected.put("event.topics", "decanter/collect/jmx");
        expected.putAll(data);
        MockEndpoint mock = (MockEndpoint) camelContext.getEndpoint("mock:assert");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo(expected);
        mock.assertIsSatisfied();
    }

    @Test
    public void testWithFilter() throws Exception {
        CamelAppender appender = new CamelAppender();
        Hashtable<String, Object> config = new Hashtable<>();
        config.put(CamelAppender.DESTINATION_URI_KEY, "direct-vm:decanter");
        config.put(EventFilter.PROPERTY_NAME_EXCLUDE_CONFIG, ".*refused.*");
        config.put(EventFilter.PROPERTY_VALUE_EXCLUDE_CONFIG, ".*refused.*");
        appender.open(config, null);

        Map<String, Object> data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("this is refused property name", "testValue");
        data.put("key", "value");
        Event event = new Event(TOPIC, data);
        appender.handleEvent(event);

        data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("property", "this is a refused value");
        data.put("key", "value");
        event = new Event(TOPIC, data);
        appender.handleEvent(event);

        data = new HashMap<>();
        data.put(EventConstants.TIMESTAMP, TIMESTAMP);
        data.put("accepted", "value");
        event = new Event(TOPIC, data);
        appender.handleEvent(event);

        Map<String, Object> expected = new HashMap<>();
        expected.put("event.topics", "decanter/collect/jmx");
        expected.putAll(data);
        MockEndpoint mock = (MockEndpoint) camelContext.getEndpoint("mock:assert");
        mock.expectedMessageCount(1);
        mock.message(0).body().isEqualTo(expected);
        mock.assertIsSatisfied();
    }

}
