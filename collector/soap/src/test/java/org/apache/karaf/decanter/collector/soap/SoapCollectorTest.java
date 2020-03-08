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
package org.apache.karaf.decanter.collector.soap;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.feature.LoggingFeature;
import org.apache.cxf.jaxws.JaxWsServerFactoryBean;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.util.Dictionary;
import java.util.Hashtable;

public class SoapCollectorTest {

    private Server cxfServer;

    @Before
    public void setup() throws Exception {
        JaxWsServerFactoryBean factory = new JaxWsServerFactoryBean();

        LoggingFeature loggingFeature = new LoggingFeature();
        loggingFeature.setPrettyLogging(true);
        factory.getFeatures().add(loggingFeature);

        TestServiceImpl testService = new TestServiceImpl();
        factory.setServiceBean(testService);
        factory.setAddress("http://localhost:9090/test");
        cxfServer = factory.create();
        cxfServer.start();

        while (!cxfServer.isStarted()) {
            Thread.sleep(200);
        }
    }

    @After
    public void teardown() throws Exception {
        if (cxfServer != null) {
            cxfServer.stop();
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testBadConfiguration() throws Exception {
        SoapCollector collector = new SoapCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        collector.activate(config);
    }

    @Test
    public void testWithInvalidRequest() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();

        SoapCollector collector = new SoapCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("soap.request", "test");
        config.put("url", "http://localhost:9090/test");
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());

        Event event = eventAdminMock.postedEvents.get(0);

        Assert.assertEquals(500, event.getProperty("http.response.code"));
        Assert.assertTrue(((String) event.getProperty("error")).contains("java.io.IOException: Server returned HTTP response code: 500 for URL: http://localhost:9090/test"));
    }

    @Test
    public void testWithBadUrl() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();

        SoapCollector collector = new SoapCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("soap.request", "test");
        config.put("url", "http://foo.bar/foo");
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());

        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertEquals("java.net.UnknownHostException: foo.bar", event.getProperty("error"));
    }

    @Test
    public void testWithValidRequest() throws Exception {
        EventAdminMock eventAdminMock = new EventAdminMock();

        SoapCollector collector = new SoapCollector();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("url", "http://localhost:9090/test");
        config.put("soap.request", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:soap=\"http://soap.collector.decanter.karaf.apache.org/\">\n" +
                "   <soapenv:Header/>\n" +
                "   <soapenv:Body>\n" +
                "      <soap:echo>\n" +
                "         <!--Optional:-->\n" +
                "         <arg0>This is a test</arg0>\n" +
                "      </soap:echo>\n" +
                "   </soapenv:Body>\n" +
                "</soapenv:Envelope>");
        collector.dispatcher = eventAdminMock;
        collector.activate(config);
        collector.run();

        Assert.assertEquals(1, eventAdminMock.postedEvents.size());

        Event event = eventAdminMock.postedEvents.get(0);
        Assert.assertNull(event.getProperty("error"));
        Assert.assertEquals(200, event.getProperty("http.response.code"));
        Assert.assertEquals("OK", event.getProperty("http.response.message"));
        Assert.assertTrue("http.response.time should be greater or equal to 0", Long.class.cast(event.getProperty("http.response.time")) >= 0L);
        Assert.assertTrue(((String) event.getProperty("soap.response")).contains("hello This is a test"));
    }

}
