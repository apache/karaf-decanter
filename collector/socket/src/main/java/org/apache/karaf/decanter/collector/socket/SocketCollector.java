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

import java.io.*;
import java.net.*;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component (
        name = "org.apache.karaf.decanter.collector.socket",
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        immediate = true)
public class SocketCollector implements Closeable, Runnable {

    @Reference
    public EventAdmin dispatcher;

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketCollector.class);

    private ServerSocket serverSocket;
    private DatagramSocket datagramSocket;
    private Protocol protocol;
    private boolean open;
    private ExecutorService executor;
    private Dictionary<String, Object> properties;
    private String topic;
    private long maxRequestSize = 100000;
    
    @Reference
    public Unmarshaller unmarshaller;
    
    private enum Protocol {
        TCP,
        UDP;
    }

    @Activate
    public void activate(ComponentContext context) throws IOException {
        activate(context.getProperties());
    }

    public void activate(Dictionary<String, Object> properties) throws IOException {
        this.properties = properties;
        int port = Integer.parseInt(getProperty(this.properties, "port", "34343"));
        String host = getProperty(this.properties, "host", "0.0.0.0");
        int workers = Integer.parseInt(getProperty(this.properties, "workers", "10"));

        this.protocol = Protocol.valueOf(getProperty(this.properties, "protocol", "tcp").toUpperCase());
        // force TCP protocol if value not in Enum
        if (this.protocol == null) {
            this.protocol = Protocol.TCP;
        }

        topic = getProperty(this.properties, EventConstants.EVENT_TOPIC, "decanter/collect/socket");

        switch (protocol) {
            case TCP:
                this.serverSocket = new ServerSocket(port, 50, InetAddress.getByName(host));
                break;
            case UDP:
                this.datagramSocket = new DatagramSocket(port, InetAddress.getByName(host));
                break;
        }

        if (this.properties.get("max.request.size") != null) {
            maxRequestSize = Long.parseLong((String)this.properties.get("max.request.size"));
        }

        // adding 1 for serverSocket handling
        this.executor = Executors.newFixedThreadPool(workers + 1);
        this.executor.execute(this);
        this.open = true;
    }

    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String)properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        while (open) {
            try {
                switch (protocol) {
                    case TCP:
                        Socket socket = serverSocket.accept();
                        LOGGER.debug("Connected to TCP client at {}", socket.getInetAddress());
                        this.executor.execute(new SocketRunnable(socket, maxRequestSize));
                        break;
                        
                    case UDP:
                        byte[] buffer = new byte[1024];
                        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                        LOGGER.debug("Connected to UDP client at {}", datagramSocket.getLocalSocketAddress());
                        datagramSocket.receive(packet);
                        this.executor.execute(new DatagramRunnable(packet));
                        break;
                }
            } catch (IOException e) {
                LOGGER.warn("Exception receiving log.", e);
            }
        }
    }

    @Deactivate
    @Override
    public void close() throws IOException {
        this.open = false;
        try {
            this.executor.shutdown();
            try {
                this.executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // nothing to do
            }
            this.executor.shutdownNow();
        } catch (Exception e) {
            // nothing to do
        }
        switch (protocol) {
            case TCP:
                serverSocket.close();
                break;
                
            case UDP:
                datagramSocket.close();
                break;
        }
    }

    private class SocketRunnable implements Runnable {

        private Socket clientSocket;
        private final long maxRequestSize;

        public SocketRunnable(Socket clientSocket, long maxRequestSize) {
            this.clientSocket = clientSocket;
            this.maxRequestSize = maxRequestSize;
        }

        public void run() {
            try {
                try (BoundedInputStream boundedInputStream = new BoundedInputStream(clientSocket.getInputStream(), maxRequestSize);
                     BufferedReader reader = new BufferedReader(new InputStreamReader(boundedInputStream))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Map<String, Object> data = new HashMap<>();
                        data.put("type", "socket");
                        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(line.getBytes())));
                        PropertiesPreparator.prepare(data, properties);
                        Event event = new Event(topic, data);
                        dispatcher.postEvent(event);
                    }
                }
                clientSocket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    private class DatagramRunnable implements Runnable {

        private DatagramPacket packet;

        public DatagramRunnable(DatagramPacket packet) {
            this.packet = packet;
        }

        public void run() {
            
            try (ByteArrayInputStream bais = new ByteArrayInputStream(packet.getData())) {
                Map<String, Object> data = new HashMap<>();
                data.put("type", "socket");
                try {
                    data.putAll(unmarshaller.unmarshal(bais));
                } catch (Exception e) {
                    // nothing to do
                }

                try {
                    PropertiesPreparator.prepare(data, properties);
                } catch (Exception e) {
                    LOGGER.warn("Can't prepare data for the dispatcher", e);
                }

                Event event = new Event(topic, data);
                dispatcher.postEvent(event);
                datagramSocket.send(packet);
            } catch (EOFException e) {
                LOGGER.warn("Client closed the connection", e);
            } catch (IOException e) {
                LOGGER.warn("Exception receiving data", e);
            }
        }
    }

}