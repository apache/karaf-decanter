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
package org.apache.karaf.decanter.collector.socket;

import org.apache.karaf.decanter.marshaller.json.JsonUnmarshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;

import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class SocketCollectorTest {

    @Test(timeout = 60000)
    public void singleMessageTest() throws Exception {
        DispatcherMock dispatcher = new DispatcherMock();

        SocketCollector collector = new SocketCollector();
        collector.unmarshaller = new JsonUnmarshaller();
        collector.dispatcher = dispatcher;
        collector.activate(new Hashtable<>());

        Socket socket = new Socket("localhost", 34343);

        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        writer.println("{\"foo\":\"bar\"}");
        writer.flush();

        while (dispatcher.postedEvents.size() < 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, dispatcher.postedEvents.size());

        Assert.assertEquals("socket", dispatcher.postedEvents.get(0).getProperty("type"));
        Assert.assertEquals("decanter/collect/socket", dispatcher.postedEvents.get(0).getProperty("event.topics"));
        Assert.assertEquals("bar", dispatcher.postedEvents.get(0).getProperty("foo"));

        collector.close();
    }

    @Test(timeout = 60000)
    public void testStreaming() throws Exception {
        DispatcherMock dispatcher = new DispatcherMock();

        SocketCollector collector = new SocketCollector();
        collector.dispatcher = dispatcher;
        collector.unmarshaller = new JsonUnmarshaller();
        collector.activate(new Hashtable<>());

        Socket socket = new Socket("localhost", 34343);

        PrintWriter writer = new PrintWriter(socket.getOutputStream(), true);
        writer.println("{\"first\":1}");
        writer.flush();
        writer.println("{\"second\":2}");
        writer.flush();

        while (dispatcher.postedEvents.size() < 2) {
            Thread.sleep(200);
        }

        Assert.assertEquals(2, dispatcher.postedEvents.size());

        Assert.assertEquals(1L, dispatcher.postedEvents.get(0).getProperty("first"));
        Assert.assertEquals(2L, dispatcher.postedEvents.get(1).getProperty("second"));

        collector.close();
    }

    @Test(timeout = 60000)
    public void testMultiClients() throws Exception {
        DispatcherMock dispatcher = new DispatcherMock();

        SocketCollector collector = new SocketCollector();
        collector.dispatcher = dispatcher;
        collector.unmarshaller = new JsonUnmarshaller();
        collector.activate(new Hashtable<>());

        Socket client1 = new Socket("localhost", 34343);
        Socket client2 = new Socket("localhost", 34343);

        PrintWriter writer1 = new PrintWriter(client1.getOutputStream(), true);
        writer1.println("{\"client\":\"client1\"}");
        writer1.flush();

        PrintWriter writer2 = new PrintWriter(client2.getOutputStream(), true);
        writer2.println("{\"client\":\"client2\"}");
        writer2.flush();

        while (dispatcher.postedEvents.size() < 2) {
            Thread.sleep(200);
        }

        Assert.assertEquals(2, dispatcher.postedEvents.size());

        collector.close();
    }

    class DispatcherMock implements EventAdmin {

        public List<Event> postedEvents = new ArrayList<>();
        public List<Event> sentEvents = new ArrayList<>();

        @Override
        synchronized public void postEvent(Event event) {
            postedEvents.add(event);
        }

        @Override
        synchronized public void sendEvent(Event event) {
            sentEvents.add(event);
        }

    }

}
