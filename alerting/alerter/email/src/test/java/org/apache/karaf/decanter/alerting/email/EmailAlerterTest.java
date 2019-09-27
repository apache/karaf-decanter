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

import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.Properties;

public class EmailAlerterTest {

    @Test
    public void testInterpolation() throws Exception {
        String source = "This is ${test} of the ${other} processing";
        Map<String, Object> data = new HashMap<>();
        data.put("test", "a test");
        data.put("other", "interpolation");
        data.put("not_used", "not_used");
        Event event = new Event("topic", data);
        String result = EmailAlerter.interpolation(source, event);
        Assert.assertEquals("This is a test of the interpolation processing", result);
    }

    @Test
    public void testSetSubjectWithEventProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("subject", "Test subject");
        Event event = new Event("topic", data);
        emailAlerter.setSubject(message, event);
        Assert.assertEquals("Test subject", message.getSubject());
    }

    @Test
    public void testSetSubjectWithEventInterpolatedProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("subject", "not used subject");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("subject", "Test ${alert} subject");
        data.put("alert", "alerting");
        Event event = new Event("topic", data);
        emailAlerter.setSubject(message, event);
        Assert.assertEquals("Test alerting subject", message.getSubject());
    }

    @Test
    public void testSetSubjectWithComponentProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("subject", "This is my subject");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        Event event = new Event("topic", new HashMap<>());
        emailAlerter.setSubject(message, event);
        Assert.assertEquals("This is my subject", message.getSubject());
    }

    @Test
    public void testSetSubjectWithComponentInterpolatedProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("subject", "This is my ${my.subject}");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("my.subject", "subject");
        Event event = new Event("topic", data);
        emailAlerter.setSubject(message, event);
        Assert.assertEquals("This is my subject", message.getSubject());
    }

    @Test
    public void testSetSubjectFallback() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        Event event = new Event("topic", data);
        emailAlerter.setSubject(message, event);
        Assert.assertTrue(message.getSubject().contains("Alert on null"));
    }

    @Test
    public void testSetBodyWithEventProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("body", "Test body");
        Event event = new Event("topic", data);
        emailAlerter.setBody(message, event);
        Assert.assertEquals("Test body", message.getContent().toString());
    }

    @Test
    public void testSetBodyWithEventInterpolatedProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("body", "not used body");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("body", "Test ${alert} body");
        data.put("alert", "alerting");
        Event event = new Event("topic", data);
        emailAlerter.setBody(message, event);
        Assert.assertEquals("Test alerting body", message.getContent().toString());
    }

    @Test
    public void testSetBodyWithComponentProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("body", "This is the email body");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        Event event = new Event("topic", new HashMap<>());
        emailAlerter.setBody(message, event);
        Assert.assertEquals("This is the email body", message.getContent().toString());
    }

    @Test
    public void testSetBodyWithComponentInterpolatedProperties() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        componentConfig.put("body", "This is the email ${my.body}");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("my.body", "body");
        Event event = new Event("topic", data);
        emailAlerter.setBody(message, event);
        Assert.assertEquals("This is the email body", message.getContent().toString());
    }

    @Test
    public void testSetBodyWithFallback() throws Exception {
        EmailAlerter emailAlerter = new EmailAlerter();
        Hashtable<String, Object> componentConfig = new Hashtable<>();
        componentConfig.put("host", "");
        componentConfig.put("port", "8888");
        componentConfig.put("auth", "false");
        componentConfig.put("starttls", "false");
        componentConfig.put("ssl", "false");
        emailAlerter.activate(componentConfig);
        Properties properties = new Properties();
        Session session = Session.getDefaultInstance(properties);
        MimeMessage message = new MimeMessage(session);
        HashMap<String, Object> data = new HashMap<>();
        data.put("my.body", "unused");
        Event event = new Event("topic", data);
        emailAlerter.setBody(message, event);
        Assert.assertTrue(message.getContent().toString().contains("out of the pattern"));
    }

}
