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
package org.apache.karaf.decanter.collector.camel;

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

public class DecanterTraceEventHandlerTest {

    @Test
    public void testTracer() throws Exception {
        JsonMarshaller marshaller = new JsonMarshaller();

        DispatcherMock eventAdmin = new DispatcherMock();

        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").id("testRoute").to("log:foo");
            }
        };
        CamelContext camelContext = new DefaultCamelContext();
        camelContext.addRoutes(builder);
        Tracer tracer = new Tracer();
        tracer.setEnabled(true);
        tracer.setTraceOutExchanges(true);
        tracer.setLogLevel(LoggingLevel.OFF);
        DecanterTraceEventHandler handler = new DecanterTraceEventHandler();
        handler.setEventAdmin(eventAdmin);
        tracer.addTraceHandler(handler);
        camelContext.setTracing(true);
        camelContext.setDefaultTracer(tracer);
        camelContext.start();

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader("direct:start", "TEST", "header", "test");

        for (Event event : eventAdmin.getPostEvents()) {
            String jsonString = marshaller.marshal(event);
            JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
            JsonObject rootObject = jsonReader.readObject();
            Assert.assertEquals("InOnly", rootObject.getString("exchangePattern"));
            Assert.assertEquals("camelTracer", rootObject.getString("type"));
            Assert.assertEquals("log://foo", rootObject.getString("toNode"));
            Assert.assertEquals("testRoute", rootObject.getString("routeId"));
            JsonObject headersObject = rootObject.getJsonObject("inHeaders");
            Assert.assertEquals("test", headersObject.getString("header"));
            Assert.assertEquals("direct://start", rootObject.getString("fromEndpointUri"));
            Assert.assertEquals("to1", rootObject.getString("nodeId"));
            Assert.assertEquals("TEST", rootObject.getString("inBody"));
            JsonObject propertiesObject = rootObject.getJsonObject("properties");
            Assert.assertEquals("log://foo", propertiesObject.getString("CamelToEndpoint"));
            Assert.assertEquals("String", rootObject.getString("inBodyType"));
            Assert.assertEquals("decanter/collect/camel/tracer", rootObject.getString("event_topics"));
            System.out.println(marshaller.marshal(event));
        }
    }

    private class DispatcherMock implements EventAdmin {

        private List<Event> postEvents = new ArrayList<>();
        private List<Event> sendEvents = new ArrayList<>();

        @Override
        public void postEvent(Event event) {
            postEvents.add(event);
        }

        @Override
        public void sendEvent(Event event) {
            System.out.println("SEND EVENT");
            sendEvents.add(event);
        }

        public List<Event> getPostEvents() {
            return postEvents;
        }

        public List<Event> getSendEvents() {
            return sendEvents;
        }
    }

}
