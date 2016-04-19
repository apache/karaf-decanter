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
package org.apache.karaf.decanter.collector.log.socket;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
    name = "org.apache.karaf.decanter.collector.log.socket",
    immediate = true
)
public class SocketCollector implements Closeable, Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketCollector.class);
    private ServerSocket serverSocket;
    private EventAdmin eventAdmin;
    private boolean open;
    private ExecutorService executor;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws IOException {
        this.properties = context.getProperties();
        int port = Integer.parseInt(getProperty(this.properties, "port", "4560"));
        LOGGER.info("Starting Log4j Socket collector on port {}", port);
        this.serverSocket = new ServerSocket(port);
        this.executor = Executors.newFixedThreadPool(1);
        this.executor.execute(this);
        this.open = true;
    }
    
    private String getProperty(Dictionary<String, Object> properties, String key, String defaultValue) {
        return (properties.get(key) != null) ? (String) properties.get(key) : defaultValue;
    }

    @Override
    public void run() {
        while (open) {
            try (Socket socket = serverSocket.accept();
                ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(socket
                    .getInputStream()));) {
                while (open) {
                    try {
                        Object event = ois.readObject();
                        if (event instanceof LoggingEvent) {
                            handleLog4j((LoggingEvent)event);
                        }
                    } catch (ClassNotFoundException e) {
                        LOGGER.warn("Unable to deserialize event from " + socket.getInetAddress(), e);
                    }
                }
            } catch (IOException e) {
                LOGGER.warn("Exception receiving log.", e);
            }
        }
    }

    private void handleLog4j(LoggingEvent event) throws UnknownHostException {
        Map<String, Object> data = new HashMap<>();
        data.put("hostAddress", InetAddress.getLocalHost().getHostAddress());
        data.put("hostName", InetAddress.getLocalHost().getHostName());

        // custom fields
        Enumeration<String> keys = properties.keys();
        while (keys.hasMoreElements()) {
            String key = keys.nextElement();
            data.put(key, properties.get(key));
        }

        data.put("timestamp", event.getTimeStamp());
        data.put("loggerClass", event.getFQNOfLoggerClass());
        data.put("loggerName", event.getLoggerName());
        data.put("threadName", event.getThreadName());
        data.put("message", event.getMessage());
        data.put("level", event.getLevel().toString());
        data.put("renderedMessage", event.getRenderedMessage());
        data.put("MDC", event.getProperties());
        putLocation(data, event.getLocationInformation());
        String[] throwableAr = event.getThrowableStrRep();
        if (throwableAr != null) {
            data.put("throwable", join(throwableAr));
        }
        sendEvent(event.getLoggerName(), data);
    }

    private void sendEvent(String loggerName, Map<String, Object> data) {
        String topic = loggerName2Topic(loggerName);
        data.put("type", "log");
        String karafName = System.getProperty("karaf.name");
        if (karafName != null) {
            data.put("karafName", karafName);
        }
        Event event = new Event(topic, data);
        eventAdmin.postEvent(event);
    }

    static String loggerName2Topic(String loggerName) {
        StringBuilder out = new StringBuilder();
        for (int c=0; c<loggerName.length(); c++) {
            Character ch = loggerName.charAt(c);
            if (Character.isDigit(ch) || Character.isLowerCase(ch) || Character.isUpperCase(ch)) {
                out.append(ch);
            } else if (ch == '.') {
                out.append("/");
            }
        }
        String outSt = out.toString();
        while (outSt.length() > 1 && outSt.endsWith("/")) {
            outSt = outSt.substring(0, outSt.length() - 1);
        }
        return "decanter/collect/log/" + outSt.replace(".", "/");
    }
    
    private void putLocation(Map<String, Object> data, LocationInfo loc) {
        data.put("loc.class", loc.getClassName());
        data.put("loc.file", loc.getFileName());
        data.put("loc.line", loc.getLineNumber());
        data.put("loc.method", loc.getMethodName());
    }

    private Object join(String[] throwableAr) {
        StringBuilder builder = new StringBuilder();
        for (String line : throwableAr) {
            builder.append(line).append("\n");
        }
        return builder.toString();
    }

    @Override
    public void close() throws IOException {
        try {
            this.executor.shutdown();
            try {
                this.executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // Ignore
            }
            this.executor.shutdownNow();
        } catch (Exception e) {
            LOGGER.warn("Error shutting down Socket");
        }
        serverSocket.close();
    }
    
    @Reference
    public void setEventAdmin(EventAdmin eventAdmin) {
        this.eventAdmin = eventAdmin;
    }

}
