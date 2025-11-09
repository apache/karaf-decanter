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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
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
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(
  name = "org.apache.karaf.decanter.collector.log.socket",
  configurationPolicy = ConfigurationPolicy.REQUIRE,
  immediate = true
)
public class SocketCollector implements Closeable, Runnable {

    public static final String HOSTNAME = "hostname";
    public static final String PORT_NAME = "port";
    public static final String BACKLOG = "backlog";
    public static final String WORKERS_NAME = "workers";
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    @Reference
    public EventAdmin dispatcher;

    private static final Logger LOGGER = LoggerFactory.getLogger(SocketCollector.class);
    private ServerSocket serverSocket;
    private boolean open;
    private ExecutorService executor;
    private Dictionary<String, Object> properties;

    @Activate
    public void activate(ComponentContext context) throws IOException {
        this.properties = context.getProperties();
        String hostname = getProperty(this.properties, HOSTNAME, "localhost");
        int port = Integer.parseInt(getProperty(this.properties, PORT_NAME, "4560"));
        int backlog = Integer.parseInt(getProperty(this.properties, BACKLOG, "50"));
        int workers = Integer.parseInt(getProperty(this.properties, WORKERS_NAME, "10"));
        LOGGER.info("Starting Log4j Socket collector on {}:{}", hostname, port);
        InetAddress host = InetAddress.getByName(hostname);
        this.serverSocket = new ServerSocket(port, backlog, host);
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

        String topic = (properties.get(EventConstants.EVENT_TOPIC) != null) ? (String) properties.get(EventConstants.EVENT_TOPIC) : "decanter/collect/log/";
        Event event = new Event(loggerName2Topic(topic, loggingEvent.getLoggerName()), data);
        dispatcher.postEvent(event);
    }

    static String loggerName2Topic(String topic, String loggerName) {
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
        return topic + outSt.replace(".", "/");
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
            try {
                InputStream socketInputStream = new BufferedInputStream(clientSocket.getInputStream());
                
                // Perform authentication if configured
                if (!authenticate(socketInputStream)) {
                    LOGGER.warn("Authentication failed for client at {}", clientSocket.getInetAddress());
                    return;
                }

                // After successful authentication, proceed with normal log event processing
                try (ObjectInputStream ois = new LoggingEventObjectInputStream(socketInputStream)) {
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

        /**
         * Authenticates the client connection.
         * Authentication protocol:
         * 1. Client sends username length (int) followed by username (UTF-8 bytes)
         * 2. Client sends password length (int) followed by password (UTF-8 bytes)
         * 3. Server validates and sends acknowledgment: 1 (success) or 0 (failure)
         * 
         * @param inputStream the input stream to read authentication data from
         * @return true if authentication succeeds or is not required, false otherwise
         */
        private boolean authenticate(InputStream inputStream) throws IOException {
            String configuredUsername = getProperty(properties, USERNAME, null);
            String configuredPassword = getProperty(properties, PASSWORD, null);

            // If no authentication is configured, allow connection
            if (configuredUsername == null && configuredPassword == null) {
                return true;
            }

            // If only one is configured, require both
            if (configuredUsername == null || configuredPassword == null) {
                LOGGER.warn("Both username and password must be configured for authentication");
                return false;
            }

            DataInputStream dis = new DataInputStream(inputStream);
            DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
            
            try {
                // Read username
                int usernameLength = dis.readInt();
                if (usernameLength < 0 || usernameLength > 1024) {
                    LOGGER.warn("Invalid username length from {}", clientSocket.getInetAddress());
                    dos.writeByte(0); // Send failure
                    dos.flush();
                    return false;
                }
                byte[] usernameBytes = new byte[usernameLength];
                dis.readFully(usernameBytes);
                String username = new String(usernameBytes, StandardCharsets.UTF_8);

                // Read password
                int passwordLength = dis.readInt();
                if (passwordLength < 0 || passwordLength > 1024) {
                    LOGGER.warn("Invalid password length from {}", clientSocket.getInetAddress());
                    dos.writeByte(0); // Send failure
                    dos.flush();
                    return false;
                }
                byte[] passwordBytes = new byte[passwordLength];
                dis.readFully(passwordBytes);
                String password = new String(passwordBytes, StandardCharsets.UTF_8);

                // Validate credentials
                boolean authenticated = configuredUsername.equals(username) && configuredPassword.equals(password);
                
                // Send acknowledgment
                dos.writeByte(authenticated ? 1 : 0);
                dos.flush();

                if (authenticated) {
                    LOGGER.debug("Client authenticated successfully: {}", username);
                } else {
                    LOGGER.warn("Authentication failed for user '{}' from {}", username, clientSocket.getInetAddress());
                }

                return authenticated;
            } catch (EOFException e) {
                LOGGER.debug("Client disconnected during authentication");
                return false;
            }
            // Note: We don't close dis/dos here as the underlying streams are still needed
        }
    }

    private static class LoggingEventObjectInputStream extends ObjectInputStream {

        public LoggingEventObjectInputStream(InputStream is) throws IOException {
            super(is);
            // JEP 290: Set ObjectInputFilter to filter incoming serialization data
            setObjectInputFilter(createLoggingEventFilter());
        }

        /**
         * Creates an ObjectInputFilter for JEP 290 that allows only the classes
         * necessary for Log4j LoggingEvent deserialization.
         * 
         * Note: Based off the internals of LoggingEvent. Will need to be
         * adjusted for Log4J 2
         */
        private static ObjectInputFilter createLoggingEventFilter() {
            return new ObjectInputFilter() {
                @Override
                public Status checkInput(FilterInfo filterInfo) {
                    Class<?> clazz = filterInfo.serialClass();
                    if (clazz != null) {
                        String className = clazz.getName();
                        if (isAllowedByDefault(className)) {
                            return Status.ALLOWED;
                        } else {
                            return Status.REJECTED;
                        }
                    }
                    // Allow array depth and references checks
                    long arrayLength = filterInfo.arrayLength();
                    if (arrayLength >= 0 && arrayLength > Integer.MAX_VALUE) {
                        return Status.REJECTED;
                    }
                    return Status.UNDECIDED;
                }
            };
        }

        private static boolean isAllowedByDefault(final String name) {
            return name.startsWith("java.lang.")
                || name.startsWith("[Ljava.lang.")
                || name.startsWith("org.apache.log4j.")
                || name.startsWith("java.util.Hashtable")
                || name.startsWith("[Ljava.util.Map");
        }
    }
}
