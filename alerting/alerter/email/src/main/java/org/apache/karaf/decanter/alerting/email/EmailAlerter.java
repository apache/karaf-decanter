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
package org.apache.karaf.decanter.alerting.email;

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
    name = "org.apache.karaf.decanter.alerting.email",
    property = EventConstants.EVENT_TOPIC + "=decanter/alert/*"
)
public class EmailAlerter implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(EmailAlerter.class);

    private String from = null;
    private String to = null;
    private String cc = null;
    private String bcc = null;
    private String subject = null;
    private String body = null;
    private String bodyType = null;

    private Properties properties;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) throws ConfigurationException {
        activate(context.getProperties());
    }

    protected void activate(Dictionary<String, Object> config) throws ConfigurationException {
        from = (config.get("from") != null) ? config.get("from").toString() : null;
        to = (config.get("to") != null) ? config.get("to").toString() : null;
        subject = (config.get("subject") != null) ? config.get("subject").toString() : null;
        body = (config.get("body") != null) ? config.get("body").toString() : null;
        bodyType = (config.get("body.type") != null) ? config.get("body.type").toString() : "text/plain";
        cc = (config.get("cc") != null) ? config.get("cc").toString() : null;
        bcc = (config.get("bcc")  != null) ? config.get("bcc").toString() : null;

        properties = new Properties();
        properties.put("mail.smtp.host", config.get("host"));
        properties.put("mail.smtp.port", config.get("port"));
        properties.put("mail.smtp.auth", config.get("auth"));
        properties.put("mail.smtp.starttls.enable", config.get("starttls"));
        properties.put("mail.smtp.ssl.enable", config.get("ssl"));
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        if (username != null) {
            properties.put("mail.smtp.user", username);
        }
        if (password != null) {
            properties.put("mail.smtp.password", password);
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
            // set from
            if (event.getProperty("from") != null) {
                message.setFrom(new InternetAddress(event.getProperty("from").toString()));
            } else if (event.getProperty("alert.email.from") != null) {
                message.setFrom(new InternetAddress(event.getProperty("alert.email.from").toString()));
            } else if (from != null){
                message.setFrom(from);
            } else {
                message.setFrom("decanter@karaf.apache.org");
            }
            // set to
            if (event.getProperty("to") != null) {
                message.addRecipients(Message.RecipientType.TO, event.getProperty("to").toString());
            } else if (event.getProperty("alert.email.to") != null) {
                message.addRecipients(Message.RecipientType.TO, event.getProperty("alert.email.to").toString());
            } else if (to != null) {
                message.addRecipients(Message.RecipientType.TO, to);
            } else {
                LOGGER.warn("to destination is not defined");
                return;
            }
            // set cc
            if (event.getProperty("cc") != null) {
                message.addRecipients(Message.RecipientType.CC, event.getProperty("cc").toString());
            } else if (event.getProperty("alert.email.cc") != null) {
                message.addRecipients(Message.RecipientType.CC, event.getProperty("alert.email.cc").toString());
            } else if (cc != null) {
                message.addRecipients(Message.RecipientType.CC, cc);
            }
            // set bcc
            if (event.getProperty("bcc") != null) {
                message.addRecipients(Message.RecipientType.BCC, event.getProperty("bcc").toString());
            } else if (event.getProperty("alert.email.bcc") != null) {
                message.addRecipients(Message.RecipientType.BCC, event.getProperty("alert.email.bcc").toString());
            } else if (bcc != null) {
                message.addRecipients(Message.RecipientType.BCC, bcc);
            }
            // set subject
            setSubject(message, event);
            // set body
            setBody(message, event);
            // send email
            if (properties.get("mail.smtp.user") != null) {
                Transport.send(message, (String) properties.get("mail.smtp.user"), (String) properties.get("mail.smtp.password"));
            } else {
                Transport.send(message);
            }
        } catch (Exception e) {
            LOGGER.error("Can't send the alert e-mail", e);
        }
    }

    /**
     * Visible for testing.
     */
    protected void setSubject(MimeMessage message, Event event) throws Exception {
        if (event.getProperty("subject") != null) {
            message.setSubject(interpolation(event.getProperty("subject").toString(), event));
        } else if (event.getProperty("alert.email.subject") != null) {
            message.setSubject(interpolation(event.getProperty("alert.email.subject").toString(), event));
        } else if (subject != null) {
            message.setSubject(interpolation(subject, event));
        } else {
            String alertLevel = (String) event.getProperty("alertLevel");
            String alertAttribute = (String) event.getProperty("alertAttribute");
            String alertPattern = (String) event.getProperty("alertPattern");
            boolean recovery = false;
            if (event.getProperty("alertBackToNormal") != null) {
                recovery = (boolean) event.getProperty("alertBackToNormal");
            }
            if (!recovery) {
                message.setSubject("[" + alertLevel + "] Alert on " + alertAttribute);
            } else {
                message.setSubject("Alert on " + alertAttribute + " back to normal");
            }
        }
    }

    /**
     * Visible for testing.
     */
    protected void setBody(MimeMessage message, Event event) throws Exception {
        String contentType = bodyType;
        contentType = (event.getProperty("body.type") != null) ? event.getProperty("body.type").toString() : contentType;
        contentType = (event.getProperty("alert.email.body.type") != null) ? event.getProperty("alert.email.body.type").toString() : contentType;
        StringBuilder builder = new StringBuilder();
        if (event.getProperty("body") != null) {
            builder.append(interpolation(event.getProperty("body").toString(), event));
        } else if (event.getProperty("alert.email.body") != null) {
            builder.append(interpolation(event.getProperty("alert.email.body").toString(), event));
        } else if (body != null) {
            builder.append(interpolation(body, event));
        } else {
            String alertLevel = (String) event.getProperty("alertLevel");
            String alertAttribute = (String) event.getProperty("alertAttribute");
            String alertPattern = (String) event.getProperty("alertPattern");
            boolean recovery = false;
            if (event.getProperty("alertBackToNormal") != null) {
                recovery = (boolean) event.getProperty("alertBackToNormal");
            }
            if (!recovery) {
                builder.append(alertLevel + " alert: " + alertAttribute + " is out of the pattern " + alertPattern + "\n");
            } else {
                builder.append(alertLevel + " alert: " + alertAttribute + " was out of the pattern " + alertPattern + ", but back to normal now\n");
            }
            builder.append("\n");
            builder.append("Details:\n");
            for (String name : event.getPropertyNames()) {
                builder.append("\t").append(name).append(": ").append(event.getProperty(name)).append("\n");
            }
        }
        message.setText(builder.toString(), contentType);
    }

    /**
     * Visible for testing
     * @return the interpolated string
     */
    protected static String interpolation(String source, Event event) {
        String interpolated = source;
        for (String propertyName : event.getPropertyNames()) {
            interpolated = interpolated.replaceAll("\\$\\{" + propertyName + "\\}", event.getProperty(propertyName).toString());
        }
        return interpolated;
    }

}
