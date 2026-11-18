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
    private String subjectTemplate = null;
    private String bodyTemplate = null;
    private String bodyType = null;
    private boolean allowEventEmailProperties = false;

    private Properties properties;

    private EmailFormatter formatter;

    @SuppressWarnings("unchecked")
    public void activate(ComponentContext context) throws ConfigurationException {
        activate(context.getProperties());
    }

    protected void activate(Dictionary<String, Object> config) throws ConfigurationException {
        from = getString(config, "from");
        to = getString(config, "to");
        subjectTemplate = getString(config, "subject.template");
        bodyTemplate = getString(config, "body.template");
        String configuredBodyType = getString(config, "body.type");
        bodyType = (configuredBodyType != null) ? configuredBodyType : "text/plain";
        cc = getString(config, "cc");
        bcc = getString(config, "bcc");

        allowEventEmailProperties = parseBoolean(config.get("allow.event.email.properties"), false);
        boolean allowEventTemplateLocation = parseBoolean(config.get("allow.event.template.location"), false);
        String templatesDirectory = getString(config, "templates.directory");
        if (allowEventTemplateLocation && (templatesDirectory == null || templatesDirectory.trim().isEmpty())) {
            LOGGER.warn("allow.event.template.location is enabled without templates.directory; event template overrides will be ignored");
        }

        properties = new Properties();
        putIfNotNull(properties, "mail.smtp.host", config.get("host"));
        putIfNotNull(properties, "mail.smtp.port", config.get("port"));
        putIfNotNull(properties, "mail.smtp.auth", config.get("auth"));
        putIfNotNull(properties, "mail.smtp.starttls.enable", config.get("starttls"));
        putIfNotNull(properties, "mail.smtp.ssl.enable", config.get("ssl"));
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        if (username != null) {
            properties.put("mail.smtp.user", username);
        }
        if (password != null) {
            properties.put("mail.smtp.password", password);
        }

        formatter = new EmailFormatter(templatesDirectory, allowEventTemplateLocation);
    }

    @Override
    public void handleEvent(Event event) {
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        try {
            // set from
            String resolvedFrom = resolveEventOrConfigured(event, "from", "alert.email.from", from);
            if (resolvedFrom != null && !resolvedFrom.trim().isEmpty()) {
                message.setFrom(new InternetAddress(validateHeaderValue("from", resolvedFrom)));
            } else {
                message.setFrom("decanter@karaf.apache.org");
            }
            // set to
            String resolvedTo = resolveEventOrConfigured(event, "to", "alert.email.to", to);
            if (resolvedTo != null && !resolvedTo.trim().isEmpty()) {
                message.addRecipients(Message.RecipientType.TO, validateHeaderValue("to", resolvedTo));
            } else {
                LOGGER.warn("to destination is not defined");
                return;
            }
            // set cc
            String resolvedCc = resolveEventOrConfigured(event, "cc", "alert.email.cc", cc);
            if (resolvedCc != null && !resolvedCc.trim().isEmpty()) {
                message.addRecipients(Message.RecipientType.CC, validateHeaderValue("cc", resolvedCc));
            }
            // set bcc
            String resolvedBcc = resolveEventOrConfigured(event, "bcc", "alert.email.bcc", bcc);
            if (resolvedBcc != null && !resolvedBcc.trim().isEmpty()) {
                message.addRecipients(Message.RecipientType.BCC, validateHeaderValue("bcc", resolvedBcc));
            }
            // set subject
            String subject = formatter.formatSubject(subjectTemplate, event);
            if (subject != null) {
                subject = subject.replace("\r", " ").replace("\n", " ").trim();
            }
            message.setSubject(subject);
            // set body
            String contentType = bodyType;
            if (allowEventEmailProperties) {
                contentType = resolveEventOrConfigured(event, "alert.email.body.type", "body.type", bodyType);
            }
            contentType = validateHeaderValue("body.type", contentType);
            message.setContent(formatter.formatBody(bodyTemplate, event), contentType);
            message.saveChanges();
            // send email
            sendMessage(message);
        } catch (Exception e) {
            LOGGER.error("Can't send the alert e-mail", e);
        }
    }

    protected void sendMessage(MimeMessage message) throws Exception {
        if (properties.get("mail.smtp.user") != null) {
            Transport.send(message, (String) properties.get("mail.smtp.user"), (String) properties.get("mail.smtp.password"));
        } else {
            Transport.send(message);
        }
    }

    private String resolveEventOrConfigured(Event event, String primaryEventKey, String secondaryEventKey, String configuredValue) {
        if (allowEventEmailProperties) {
            Object primary = event.getProperty(primaryEventKey);
            if (primary != null) {
                return primary.toString();
            }
            Object secondary = event.getProperty(secondaryEventKey);
            if (secondary != null) {
                return secondary.toString();
            }
        }
        return configuredValue;
    }

    private static String validateHeaderValue(String headerName, String value) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException("Invalid CR/LF characters in email header: " + headerName);
        }
        return value;
    }

    private static String getString(Dictionary<String, Object> config, String key) {
        Object value = config.get(key);
        return (value != null) ? value.toString() : null;
    }

    private static boolean parseBoolean(Object value, boolean defaultValue) {
        return (value != null) ? Boolean.parseBoolean(value.toString().trim()) : defaultValue;
    }

    private static void putIfNotNull(Properties target, String key, Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

}
