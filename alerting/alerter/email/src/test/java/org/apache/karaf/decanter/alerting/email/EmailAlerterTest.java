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

import org.junit.Assert;
import org.junit.Test;
import org.osgi.service.event.Event;

import javax.mail.Message;
import javax.mail.internet.MimeMessage;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

public class EmailAlerterTest {

    private static class TestEmailAlerter extends EmailAlerter {
        final List<MimeMessage> sentMessages = new ArrayList<>();

        @Override
        protected void sendMessage(MimeMessage message) {
            sentMessages.add(message);
        }
    }

    @Test
    public void testEventOverridesIgnoredByDefault() throws Exception {
        TestEmailAlerter alerter = new TestEmailAlerter();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("from", "decanter@example.org");
        config.put("to", "ops@example.org");
        config.put("cc", "cc@example.org");
        config.put("bcc", "bcc@example.org");
        config.put("body.type", "text/plain");
        alerter.activate(config);

        Map<String, Object> data = new HashMap<>();
        data.put("from", "attacker@evil.org");
        data.put("to", "attacker@evil.org");
        data.put("cc", "attacker-cc@evil.org");
        data.put("bcc", "attacker-bcc@evil.org");
        data.put("alert.email.to", "attacker2@evil.org");
        data.put("body.type", "text/html");
        data.put("subject.template.location", "/etc/passwd");
        data.put("body.template.location", "/etc/passwd");
        data.put("alertLevel", "SEVERE");
        data.put("alertPattern", "TestPattern");
        alerter.handleEvent(new Event("decanter/alert/SEVERE", data));

        Assert.assertEquals(1, alerter.sentMessages.size());
        MimeMessage message = alerter.sentMessages.get(0);
        Assert.assertEquals("decanter@example.org", message.getFrom()[0].toString());
        Assert.assertEquals("ops@example.org", message.getRecipients(Message.RecipientType.TO)[0].toString());
        Assert.assertEquals("cc@example.org", message.getRecipients(Message.RecipientType.CC)[0].toString());
        Assert.assertEquals("bcc@example.org", message.getRecipients(Message.RecipientType.BCC)[0].toString());
        Assert.assertTrue(message.getContentType().startsWith("text/plain"));
        Assert.assertEquals("[SEVERE] Alert TestPattern", message.getSubject());
        Assert.assertTrue(message.getContent().toString().contains("SEVERE alert: condition TestPattern alert"));
    }

    @Test
    public void testMissingConfiguredToIgnoresEventToByDefault() throws Exception {
        TestEmailAlerter alerter = new TestEmailAlerter();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("from", "decanter@example.org");
        alerter.activate(config);

        Map<String, Object> data = new HashMap<>();
        data.put("to", "attacker@evil.org");
        data.put("alert.email.to", "attacker@evil.org");
        data.put("alertLevel", "SEVERE");
        data.put("alertPattern", "TestPattern");
        alerter.handleEvent(new Event("decanter/alert/SEVERE", data));

        Assert.assertTrue(alerter.sentMessages.isEmpty());
    }

    @Test
    public void testEventOverridesWhenExplicitlyEnabled() throws Exception {
        String templatesDir = Paths.get(this.getClass().getClassLoader().getResource("propertySubjectTemplate.vm").toURI()).getParent().toString();

        TestEmailAlerter alerter = new TestEmailAlerter();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("from", "decanter@example.org");
        config.put("to", "ops@example.org");
        config.put("templates.directory", templatesDir);
        config.put("allow.event.email.properties", "true");
        config.put("allow.event.template.location", "true");
        alerter.activate(config);

        Map<String, Object> data = new HashMap<>();
        data.put("from", "custom-from@example.org");
        data.put("to", "custom-to@example.org");
        data.put("body.type", "text/html");
        data.put("subject.template.location", "propertySubjectTemplate.vm");
        data.put("body.template.location", "propertyBodyTemplate.vm");
        data.put("alertLevel", "SEVERE");
        data.put("alertAttribute", "TestAttribute");
        data.put("test.name", "test");
        data.put("threadCount", 100);
        alerter.handleEvent(new Event("decanter/alert/SEVERE", data));

        Assert.assertEquals(1, alerter.sentMessages.size());
        MimeMessage message = alerter.sentMessages.get(0);
        Assert.assertEquals("custom-from@example.org", message.getFrom()[0].toString());
        Assert.assertEquals("custom-to@example.org", message.getRecipients(Message.RecipientType.TO)[0].toString());
        Assert.assertTrue(message.getContentType().startsWith("text/html"));
        Assert.assertEquals("Property defined TestAttribute with SEVERE", message.getSubject());
        Assert.assertTrue(message.getContent().toString().contains("This is test using property defined template."));
    }

    @Test
    public void testHeaderInjectionRejectedAndSubjectSanitized() throws Exception {
        TestEmailAlerter alerter = new TestEmailAlerter();
        Dictionary<String, Object> config = new Hashtable<>();
        config.put("from", "decanter@example.org");
        config.put("to", "ops@example.org");
        config.put("allow.event.email.properties", "true");
        alerter.activate(config);

        Map<String, Object> injectedHeaderData = new HashMap<>();
        injectedHeaderData.put("to", "ops@example.org\r\nBcc: attacker@evil.org");
        injectedHeaderData.put("alertLevel", "SEVERE");
        injectedHeaderData.put("alertPattern", "TestPattern");
        alerter.handleEvent(new Event("decanter/alert/SEVERE", injectedHeaderData));
        Assert.assertTrue(alerter.sentMessages.isEmpty());

        Map<String, Object> crlfSubjectData = new HashMap<>();
        crlfSubjectData.put("alertLevel", "SEVERE");
        crlfSubjectData.put("alertPattern", "Pattern\r\nInjectedHeader: value");
        alerter.handleEvent(new Event("decanter/alert/SEVERE", crlfSubjectData));
        Assert.assertEquals(1, alerter.sentMessages.size());
        String subject = alerter.sentMessages.get(0).getSubject();
        Assert.assertFalse(subject.contains("\r"));
        Assert.assertFalse(subject.contains("\n"));
    }

}
