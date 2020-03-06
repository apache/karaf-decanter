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
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InvalidClassException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
  name = "org.apache.karaf.decanter.collector.log.socket",
  configurationPolicy = ConfigurationPolicy.REQUIRE,
  immediate = true
)
public class SocketCollector implements Closeable, Runnable {

    public static final String PORT_NAME = "port";
    public static final String WORKERS_NAME = "workers";

    @Reference
    public EventAdmin dispatcher;

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketCollector.class);
    private ServerSocket serverSocket;
    private boolean open;
    private ExecutorService executor;
    private Dictionary<String, Object> properties;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws IOException {
        this.properties = context.getProperties();
        int port = Integer.parseInt(getProperty(this.properties, PORT_NAME, "4560"));
        int workers = Integer.parseInt(getProperty(this.properties, WORKERS_NAME, "10"));
        LOGGER.info("Starting Log4j Socket collector on port {}", port);
        this.serverSocket = new ServerSocket(port);
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
                Socket socket = serverSocket.accept();
                LOGGER.debug("Connected to client at {}", socket.getInetAddress());
                this.executor.execute(new SocketRunnable(socket));
            } catch (IOException e) {
                LOGGER.warn("Exception receiving log.", e);
            }
        }
    }

    /**
     * Returns true if this class is active (waiting for client logging events)
     */
    boolean isOpen() {
        return open;
    }

    /**
     * returns executor service used for serverSocket and client socket handling
     */
    ExecutorService getExecutorService() {
        return executor;
    }

    private void handleLog4j(LoggingEvent loggingEvent) throws UnknownHostException {
        LOGGER.debug("Received log event {}", loggingEvent.getLoggerName());
        Map<String, Object> data = new HashMap<>();
        data.put("type", "log");

        data.put("timestamp", loggingEvent.getTimeStamp());
        data.put("loggerClass", loggingEvent.getFQNOfLoggerClass());
        data.put("loggerName", loggingEvent.getLoggerName());
        data.put("threadName", loggingEvent.getThreadName());
        data.put("message", loggingEvent.getMessage());
        data.put("level", loggingEvent.getLevel().toString());
        data.put("renderedMessage", loggingEvent.getRenderedMessage());
        data.put("MDC", loggingEvent.getProperties());
        putLocation(data, loggingEvent.getLocationInformation());
        String[] throwableAr = loggingEvent.getThrowableStrRep();
        if (throwableAr != null) {
            data.put("throwable", join(throwableAr));
        }

        try {
            PropertiesPreparator.prepare(data, properties);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare data for the dispatcher", e);
        }

        String topic = loggerName2Topic(loggingEvent.getLoggerName());
        Event event = new Event(topic, data);
        dispatcher.postEvent(event);
    }

    static String loggerName2Topic(String loggerName) {
        StringBuilder out = new StringBuilder();
        for (int c = 0; c < loggerName.length(); c++) {
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

    @Deactivate
    @Override
    public void close() throws IOException {
        this.open = false;
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

    private class SocketRunnable implements Runnable {
        private Socket clientSocket;

        public SocketRunnable(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
            try (ObjectInputStream ois = new LoggingEventObjectInputStream(new BufferedInputStream(clientSocket
                .getInputStream()))) {
                while (open) {
                    try {
                        Object event = ois.readObject();
                        if (event instanceof LoggingEvent) {
                            handleLog4j((LoggingEvent)event);
                        }
                    } catch (ClassNotFoundException e) {
                        LOGGER.warn("Unable to deserialize event from " + clientSocket.getInetAddress(), e);
                    }
                }
            } catch (EOFException e) {
                LOGGER.debug("Log client closed the connection.", e);
            } catch (IOException e) {
                LOGGER.warn("Exception receiving log.", e);
            }
            try {
                clientSocket.close();
            } catch (IOException e) {
                LOGGER.info("Error closing socket", e);
            }
        }
    }

    private static class LoggingEventObjectInputStream extends ObjectInputStream {

        public LoggingEventObjectInputStream(InputStream is) throws IOException {
            super(is);
        }

        @Override
        protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
            if (!isAllowedByDefault(desc.getName())) {
                throw new InvalidClassException("Unauthorized deserialization attempt", desc.getName());
            }
            return super.resolveClass(desc);
        }

        // Note: Based off the internals of LoggingEvent. Will need to be
        // adjusted for Log4J 2
        private static boolean isAllowedByDefault(final String name) {
            return name.startsWith("java.lang.")
                || name.startsWith("[Ljava.lang.")
                || name.startsWith("org.apache.log4j.")
                || name.equals("java.util.Hashtable");
        }
    }
}
