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
package org.apache.karaf.decanter.appender.socket;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.marshaller.csv.CsvMarshaller;
import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class SocketAppenderTest {

    @Test(timeout = 60000L)
    public void testNotConnected() throws Exception {
        SocketAppender appender = new SocketAppender();
        Marshaller marshaller = new CsvMarshaller();
        appender.marshaller = marshaller;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("connected", "false");
        config.put("host", "localhost");
        config.put("port", "44445");
        appender.activate(config);

        // no exception there as the socket is bound when sending message

        final List<String> received = new ArrayList<>();

        Runnable server = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket server = new ServerSocket(44445);
                    while (true) {
                        Socket socket = server.accept();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                received.add(line);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
          }
        };
        Thread serverThread = new Thread(server);
        serverThread.start();

        Map<String, String> data = new HashMap<>();
        data.put("type", "test");
        data.put("first", "1");
        appender.handleEvent(new Event("test", data));

        while (received.size() != 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, received.size());
        Assert.assertEquals("type=test,first=1,event.topics=test", received.get(0));

        serverThread.interrupt();
    }

    @Test
    public void testConnected() throws Exception {
        SocketAppender appender = new SocketAppender();
        Marshaller marshaller = new CsvMarshaller();
        appender.marshaller = marshaller;
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("connected", "true");
        config.put("host", "localhost");
        config.put("port", "44444");
        try {
            appender.activate(config);
            Assert.fail("Expect ConnectionRefused exception here");
        } catch (Exception e) {
            // expected
        }

        // no exception there as the socket is bound when sending message

        final List<String> received = new ArrayList<>();

        Runnable server = new Runnable() {
            @Override
            public void run() {
                try {
                    ServerSocket server = new ServerSocket(44444);
                    while (true) {
                        Socket socket = server.accept();
                        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                received.add(line);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        };
        Thread serverThread = new Thread(server);
        serverThread.start();

        // gives time to server thread to start
        Thread.sleep(500);

        appender.activate(config);

        Map<String, String> data = new HashMap<>();
        data.put("type", "test");
        data.put("first", "1");
        appender.handleEvent(new Event("test", data));

        while (received.size() != 1) {
            Thread.sleep(200);
        }

        Assert.assertEquals(1, received.size());
        Assert.assertEquals("type=test,first=1,event.topics=test", received.get(0));

        serverThread.interrupt();
    }

}
