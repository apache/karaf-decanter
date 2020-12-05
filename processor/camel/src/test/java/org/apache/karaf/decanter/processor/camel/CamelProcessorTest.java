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
package org.apache.karaf.decanter.processor.camel;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.util.*;

public class CamelProcessorTest {

    private DefaultCamelContext camelContext;

    @Before
    public void setup() throws Exception {
        camelContext = new DefaultCamelContext();
        camelContext.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct-vm:decanter-delegate")
                        .id("client-route")
                .log("Delegate body: ${body}")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        Map<String, Object> body = exchange.getIn().getBody(Map.class);
                        body.put("processed", "yes");
                        exchange.getIn().setBody(body, Map.class);
                    }
                })
                        .log("Callback body: ${body}")
                        .to("direct-vm:decanter-callback");
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
        DispatcherMock dispatcher = new DispatcherMock();
        CamelProcessor processor = new CamelProcessor();
        processor.setDispatcher(dispatcher);
        Hashtable<String, Object> configuration = new Hashtable<>();
        configuration.put("target.topic", "decanter/process/test");
        configuration.put("delegate.uri", "direct-vm:decanter-delegate");
        configuration.put("callback.uri", "direct-vm:decanter-callback");
        processor.activate(configuration, null);

        Map<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("decanter/collector/test", data);
        processor.handleEvent(event);

        Assert.assertEquals(1, dispatcher.postedEvents.size());

        Assert.assertEquals("yes", dispatcher.postedEvents.get(0).getProperty("processed"));
    }

    class DispatcherMock implements EventAdmin {

        public List<Event> postedEvents = new ArrayList<>();
        public List<Event> sentEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            postedEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            sentEvents.add(event);
        }
    }

}
