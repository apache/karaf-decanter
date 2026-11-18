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
import org.apache.velocity.util.introspection.SecureUberspector;
import org.osgi.service.event.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class EmailFormatter {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailFormatter.class);

    private final VelocityEngine velocityEngine;
    private final Path templatesDirectory;
    private final boolean allowEventTemplateLocation;

    public EmailFormatter() {
        this(null, false);
    }

    public EmailFormatter(String templatesDirectory, boolean allowEventTemplateLocation) {
        this.allowEventTemplateLocation = allowEventTemplateLocation;
        this.templatesDirectory = resolveBaseDirectory(templatesDirectory);

        Properties properties = new Properties();
        properties.setProperty(RuntimeConstants.UBERSPECT_CLASSNAME, SecureUberspector.class.getName());
        if (this.templatesDirectory != null) {
            properties.setProperty(RuntimeConstants.RESOURCE_LOADERS, "file,classpath");
            properties.setProperty(RuntimeConstants.FILE_RESOURCE_LOADER_PATH, this.templatesDirectory.toString());
        } else {
            properties.setProperty(RuntimeConstants.RESOURCE_LOADERS, "classpath");
        }
        properties.setProperty("resource.loader.classpath.class", ClasspathResourceLoader.class.getName());
        this.velocityEngine = new VelocityEngine(properties);
    }

    private static Path resolveBaseDirectory(String templatesDirectory) {
        if (templatesDirectory == null || templatesDirectory.trim().isEmpty()) {
            return null;
        }
        Path path = Paths.get(templatesDirectory.trim()).toAbsolutePath().normalize();
        if (Files.exists(path)) {
            try {
                return path.toRealPath();
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to resolve templates.directory: " + templatesDirectory, e);
            }
        }
        return path;
    }

    private Map<String, Object> getEventAsData(Event event) {
        Map<String, Object> map = new HashMap<>();
        for (String propertyName : event.getPropertyNames()) {
            map.put(propertyName, event.getProperty(propertyName));
        }
        return map;
    }

    public String formatSubject(String subjectTemplateLocation, Event event) {
        return formatTemplate("subject.template.location", subjectTemplateLocation, "defaultSubjectTemplate.vm", event);
    }

    public String formatBody(String bodyTemplateLocation, Event event) {
        return formatTemplate("body.template.location", bodyTemplateLocation, "defaultBodyTemplate.vm", event);
    }

    private String formatTemplate(String eventPropertyKey, String configuredLocation, String defaultTemplateName, Event event) {
        VelocityContext velocityContext = new VelocityContext();
        velocityContext.put("event", getEventAsData(event));
        StringWriter writer = new StringWriter();

        Object eventLocation = event.getProperty(eventPropertyKey);
        if (allowEventTemplateLocation && eventLocation != null) {
            String location = eventLocation.toString().trim();
            if (!location.isEmpty()) {
                if (templatesDirectory == null) {
                    LOGGER.warn("Ignoring event property '{}' because templates.directory is not configured", eventPropertyKey);
                } else {
                    String relativeTemplate = resolveSafeTemplateName(location);
                    Template template = velocityEngine.getTemplate(relativeTemplate, StandardCharsets.UTF_8.name());
                    template.merge(velocityContext, writer);
                    return writer.toString();
                }
            }
        }

        if (configuredLocation != null && !configuredLocation.trim().isEmpty()) {
            String trimmedLocation = configuredLocation.trim();
            if (renderConfiguredTemplate(trimmedLocation, velocityContext, writer)) {
                return writer.toString();
            }
        }

        Template defaultTemplate = velocityEngine.getTemplate(defaultTemplateName, StandardCharsets.UTF_8.name());
        defaultTemplate.merge(velocityContext, writer);
        return writer.toString();
    }

    private boolean renderConfiguredTemplate(String configuredLocation, VelocityContext velocityContext, StringWriter writer) {
        Path configuredPath = Paths.get(configuredLocation);
        if (templatesDirectory != null) {
            Path resolved = configuredPath.isAbsolute()
                    ? configuredPath.normalize()
                    : templatesDirectory.resolve(configuredPath).normalize();
            if (resolved.startsWith(templatesDirectory) && Files.isRegularFile(resolved)) {
                String relative = templatesDirectory.relativize(resolved).toString().replace('\\', '/');
                Template template = velocityEngine.getTemplate(relative, StandardCharsets.UTF_8.name());
                template.merge(velocityContext, writer);
                return true;
            }
        }
        if (Files.isRegularFile(configuredPath)) {
            try (Reader reader = Files.newBufferedReader(configuredPath, StandardCharsets.UTF_8)) {
                velocityEngine.evaluate(velocityContext, writer, configuredPath.getFileName().toString(), reader);
                return true;
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read template file: " + configuredLocation, e);
            }
        }
        Template template = velocityEngine.getTemplate(configuredLocation, StandardCharsets.UTF_8.name());
        template.merge(velocityContext, writer);
        return true;
    }

    private String resolveSafeTemplateName(String location) {
        if (location.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Invalid template location containing null byte");
        }
        Path requested = Paths.get(location);
        if (requested.isAbsolute()) {
            throw new IllegalArgumentException("Event template location must be relative to templates.directory: " + location);
        }
        if (!Files.isDirectory(templatesDirectory)) {
            throw new IllegalArgumentException("Configured templates.directory does not exist: " + templatesDirectory);
        }
        try {
            Path baseReal = templatesDirectory.toRealPath();
            Path candidate = baseReal.resolve(requested).normalize();
            if (!candidate.startsWith(baseReal)) {
                throw new IllegalArgumentException("Template location escapes templates.directory: " + location);
            }
            if (!Files.isRegularFile(candidate)) {
                throw new IllegalArgumentException("Template file does not exist: " + location);
            }
            Path candidateReal = candidate.toRealPath();
            if (!candidateReal.startsWith(baseReal)) {
                throw new IllegalArgumentException("Template location resolves outside templates.directory: " + location);
            }
            return baseReal.relativize(candidateReal).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to resolve template location: " + location, e);
        }
    }

}
