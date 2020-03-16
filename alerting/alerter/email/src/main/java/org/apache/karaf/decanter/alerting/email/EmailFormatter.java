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

import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.osgi.service.event.Event;

import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EmailFormatter {

    private VelocityEngine velocityEngine;

    public EmailFormatter() {
        Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.RESOURCE_LOADERS, "file,classpath");
        properties.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, "/");
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        velocityEngine = new VelocityEngine(properties);
    }

    private Map<String, Object> getEventAsData(Event event) {
        Map<String, Object> map = new HashMap<>();
        for (String propertyName : event.getPropertyNames()) {
            map.put(propertyName, event.getProperty(propertyName));
        }
        return map;
    }

    public String formatSubject(String subjectTemplateLocation, Event event) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("event", getEventAsData(event));
        Template subjectTemplate;
        if (event.getProperty("subject.template.location") != null) {
            subjectTemplate = velocityEngine.getTemplate(event.getProperty("subject.template.location").toString());
        } else if (subjectTemplateLocation != null) {
            subjectTemplate = velocityEngine.getTemplate(subjectTemplateLocation);
        } else {
            subjectTemplate = velocityEngine.getTemplate("defaultSubjectTemplate.vm");
        }
        StringWriter writer = new StringWriter();
        subjectTemplate.merge(velocityContext, writer);
        return writer.toString();
    }

    public String formatBody(String bodyTemplateLocation, Event event) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("event", getEventAsData(event));
        Template bodyTemplate;
        if (event.getProperty("body.template.location") != null) {
            bodyTemplate = velocityEngine.getTemplate(event.getProperty("body.template.location").toString());
        } else if (bodyTemplateLocation != null) {
            bodyTemplate = velocityEngine.getTemplate(bodyTemplateLocation);
        } else {
            bodyTemplate = velocityEngine.getTemplate("/defaultBodyTemplate.vm");
        }
        StringWriter writer = new StringWriter();
        bodyTemplate.merge(velocityContext, writer);
        return writer.toString();
    }

}
