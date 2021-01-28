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
package org.apache.karaf.decanter.collector.rest;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.karaf.decanter.marshaller.raw.RawUnmarshaller;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.Dictionary;
import java.util.Hashtable;

public class RestCollectorTest {

    private Server cxfServer;

    @Before
    public void setup() throws Exception {
        JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
        TestService service = new TestService();
        jaxrsServerFactoryBean.setAddress("http://localhost:9090/test");
        jaxrsServerFactoryBean.setServiceBean(service);
        cxfServer = jaxrsServerFactoryBean.create();
        cxfServer.start();
    }

    @After
    public void teardown() throws Exception {
        cxfServer.stop();
    }

    @Test
    public void testBadUrl() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("url", "http://foo.bar/foo");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals("java.net.UnknownHostException: foo.bar", event.getProperty("error"));
    }

    @Test
    public void testExceptionWrapping() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("url", "http://foo.bar/foo");
        config.put("exception.as.http.response", "true");
        config.put("exception.http.response.code", "600");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(600, event.getProperty("http.response.code"));
        Assert.assertEquals("java.net.UnknownHostException: foo.bar", event.getProperty("http.exception"));
    }

    @Test
    public void testGet() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("url", "http://localhost:9090/test/echo");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("hello world\n", event.getProperty("payload"));
    }

    @Test
    public void testPost() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("request.method", "POST");
        config.put("request", "test");
        config.put("url", "http://localhost:9090/test/submit");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("hello post test\n", event.getProperty("payload"));
    }

    @Test
    public void testPut() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("request.method", "PUT");
        config.put("request", "test");
        config.put("url", "http://localhost:9090/test/submit");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("hello put test\n", event.getProperty("payload"));
    }

    @Test
    public void testDelete() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("request.method", "DELETE");
        config.put("url", "http://localhost:9090/test/delete");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("deleted\n", event.getProperty("payload"));
    }

    @Test
    public void testHeader() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();
        RestCollector collector = new RestCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("request.method", "POST");
        config.put("header.foo", "test");
        config.put("url", "http://localhost:9090/test/header");
        collector.unmarshaller = new RawUnmarshaller();
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());
        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("hello header test\n", event.getProperty("payload"));
    }

}
