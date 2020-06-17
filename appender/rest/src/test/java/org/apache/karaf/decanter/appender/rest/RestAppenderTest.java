/*
 * Licen	sed to the Apache Software Foundation (ASF) under one or more
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
package org.apache.karaf.decanter.appender.rest;

import java.net.URISyntaxException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.json.JsonMarshaller;
import org.junit.*;
import org.osgi.service.event.Event;

public class RestAppenderTest {
    
    private static final int NUM_MESSAGES = 100000;

    private Server cxfServer;
    private TestService testService;

    @Before
    public void setup() throws Exception {
        JAXRSServerFactoryBean jaxrsServerFactoryBean = new JAXRSServerFactoryBean();
        testService = new TestService();
        jaxrsServerFactoryBean.setAddress("http://localhost:9091/test");
        jaxrsServerFactoryBean.setServiceBean(testService);
        cxfServer = jaxrsServerFactoryBean.create();
        cxfServer.start();
    }

    @After
    public void teardown() throws Exception {
        cxfServer.stop();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmptyURI() throws URISyntaxException {
        RestAppender appender = new RestAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        appender.activate(config);
    }

    @Test
    public void testPost() throws URISyntaxException {
        RestAppender appender = new RestAppender();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("uri", "http://localhost:9091/test/echo");
        appender.marshaller = new JsonMarshaller();
        appender.activate(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("post", data);

        appender.handleEvent(event);

        Assert.assertEquals(1, testService.postMessages.size());
        Assert.assertTrue(testService.postMessages.get(0).contains("\"foo\":\"bar\""));
    }

    @Test
    public void testPut() throws URISyntaxException {
        RestAppender appender = new RestAppender();
        Dictionary<String, Object> config= new Hashtable<>();
        config.put("uri", "http://localhost:9091/test/echo");
        config.put("request.method", "PUT");
        appender.marshaller = new JsonMarshaller();
        appender.activate(config);

        HashMap<String, Object> data = new HashMap<>();
        data.put("foo", "bar");
        Event event = new Event("put", data);

        appender.handleEvent(event);

        Assert.assertEquals(1, testService.putMessages.size());
        Assert.assertTrue(testService.putMessages.get(0).contains("\"foo\":\"bar\""));
    }

}
