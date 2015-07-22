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
package org.apache.karaf.decanter.sla.email;

import org.osgi.service.event.Event;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.Properties;

public class EmailAlerter implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmailAlerter.class);

    private String from;
    private String to;
    private String host;
    private String port;
    private String auth;
    private String starttls;
    private String ssl;
    private String username;
    private String password;

    public EmailAlerter(String from, String to, String host, String port, String auth, String starttls, String ssl, String username, String password) {
        this.from = from;
        this.to = to;
        this.host = host;
        this.port = port;
        this.auth = auth;
        this.starttls = starttls;
        this.ssl = ssl;
        this.username = username;
        this.password = password;
    }

    @Override
    public void handleEvent(Event event) {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.port", port);
        properties.put("mail.smtp.auth", auth);
        properties.put("mail.smtp.starttls.enable", starttls);
        properties.put("mail.smtp.ssl.enable", ssl);
        if (username != null)
            properties.put("mail.user", username);
        if (password != null)
            properties.put("mail.password", password);
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
            message.setFrom(new InternetAddress(from));
            message.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            String alertLevel = (String) event.getProperty("alertLevel");
            String alertAttribute = (String) event.getProperty("alertAttribute");
            String alertPattern = (String) event.getProperty("alertPattern");
            message.setSubject("[" + alertLevel + "] Alert on " + alertAttribute);
            StringBuilder builder = new StringBuilder();
            builder.append(alertLevel + " alert: " + alertAttribute + " is out of the pattern " + alertPattern + "\n");
            builder.append("\n");
            builder.append("Details:\n");
            for (String name : event.getPropertyNames()) {
                builder.append("\t").append(name).append(": ").append(event.getProperty(name)).append("\n");
            }
            message.setText(builder.toString());
            Transport.send(message);
        } catch (Exception e) {
            LOGGER.error("Can't send the alert e-mail", e);
        }
    }

}
