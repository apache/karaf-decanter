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

import java.io.StringReader;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

public class DecanterInterceptStrategyTest {

    @Test
    public void testTracer() throws Exception {
        JsonMarshaller marshaller = new JsonMarshaller();
        MockEventAdmin eventAdmin = new MockEventAdmin();
        DecanterInterceptStrategy tracer = new DecanterInterceptStrategy();
        tracer.setDispatcher(eventAdmin);
        DefaultCamelContext camelContext = createCamelContext(tracer);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader("direct:start", "TEST", "header", "test");

        for (Event event : eventAdmin.getPostEvents()) {
            String jsonString = marshaller.marshal(event);
            JsonReader jsonReader = Json.createReader(new StringReader(jsonString));
            JsonObject rootObject = jsonReader.readObject();
            System.out.println(jsonString);
            Assert.assertEquals("InOnly", rootObject.getString("exchangePattern"));
            Assert.assertEquals("camelTracer", rootObject.getString("type"));
            Assert.assertEquals("test-route", rootObject.getString("routeId"));
            Assert.assertEquals("test-context", rootObject.getString("camelContextName"));
            JsonObject headersObject = rootObject.getJsonObject("inHeaders");
            Assert.assertEquals("test", headersObject.getString("header"));
            Assert.assertEquals("direct://start", rootObject.getString("fromEndpointUri"));
            Assert.assertEquals("TEST", rootObject.getString("inBody"));
            JsonObject propertiesObject = rootObject.getJsonObject("properties");
            Assert.assertEquals("String", rootObject.getString("inBodyType"));
            Assert.assertEquals("decanter/collect/camel/tracer", rootObject.getString("event_topics"));
            System.out.println(marshaller.marshal(event));
        }
    }

    @Test
    public void testTracerWithExtender() throws Exception {
        MockEventAdmin eventAdmin = new MockEventAdmin();
        TestExtender extender = new TestExtender();
        DecanterInterceptStrategy tracer = new DecanterInterceptStrategy();
        tracer.setExtender(extender);
        tracer.setDispatcher(eventAdmin);
        DefaultCamelContext camelContext = createCamelContext(tracer);

        ProducerTemplate template = camelContext.createProducerTemplate();
        template.sendBodyAndHeader("direct:start", "TEST", "header", "test");

        Assert.assertEquals(1, eventAdmin.getPostEvents().size());

        Assert.assertEquals("test", eventAdmin.getPostEvents().get(0).getProperty("extender-test"));
    }

    private DefaultCamelContext createCamelContext(InterceptStrategy tracer) throws Exception {
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("test-route").to("log:foo");
            }
        };
        DefaultCamelContext camelContext = new DefaultCamelContext();
        camelContext.setName("test-context");
        camelContext.addRoutes(builder);
        camelContext.addInterceptStrategy(tracer);
        camelContext.start();
        return camelContext;
    }

    private class TestExtender implements DecanterCamelEventExtender {

        @Override
        public void extend(Map<String, Object> decanterData, Exchange camelExchange) {
            decanterData.put("extender-test", "test");
        }

    }

}
