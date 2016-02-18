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

import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import java.util.Dictionary;
import java.util.Properties;

@Component(
    name = "org.apache.karaf.decanter.sla.email",
    property = EventConstants.EVENT_TOPIC + "=decanter/alert/*"
)
public class EmailAlerter implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmailAlerter.class);

    private String from;
    private String to;

    private Properties properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) throws ConfigurationException {
        Dictionary<String, String> config = context.getProperties();
        requireProperty(config, "from");
        requireProperty(config, "to");
        requireProperty(config, "host");

        this.from = config.get("from");
        this.to = config.get("to");

        properties = new Properties();
        properties.put("mail.smtp.host", config.get("host"));
        properties.put("mail.smtp.port", config.get("port"));
        properties.put("mail.smtp.auth", config.get("auth"));
        properties.put("mail.smtp.starttls.enable", config.get("starttls"));
        properties.put("mail.smtp.ssl.enable", config.get("ssl"));
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        if (username != null) {
            properties.put("mail.user", username);
        }
        if (password != null) {
            properties.put("mail.password", password);
        }
    }

    private void requireProperty(Dictionary<String, ?> config, String key) throws ConfigurationException {
        if (config.get(key) == null) {
            throw new ConfigurationException(key, key + " property is not defined");
        }
    }

    @Override
    public void handleEvent(Event event) {
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
