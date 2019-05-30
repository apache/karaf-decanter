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
package org.apache.karaf.decanter.collector.file;

import java.io.File;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.input.Tailer;
import org.apache.commons.io.input.TailerListenerAdapter;
import org.apache.karaf.decanter.api.parser.Parser;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.cm.ConfigurationException;
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
    name="org.apache.karaf.decanter.collector.file",
    configurationPolicy = ConfigurationPolicy.REQUIRE,
    immediate = true
)
public class DecanterTailerListener extends TailerListenerAdapter {

    @Reference
    public EventAdmin dispatcher;

    @Reference(target = "(" + Parser.SERVICE_KEY_ID + "=identity)")
    public Parser parser;

    private final static Logger LOGGER = LoggerFactory.getLogger(DecanterTailerListener.class);

    private String type;
    private String path;
    private String regex;
    private Pattern compiledRegex;
    
    /**
     * additional properties provided by the user
     */
    private Dictionary<String, Object> properties;
    private Tailer tailer;

    @SuppressWarnings("unchecked")
    @Activate
    public void activate(ComponentContext context) throws ConfigurationException {
        properties = context.getProperties();
        if (properties.get("type") == null) {
            throw new ConfigurationException("type", "type property is mandatory");
        }
        String type = (String) properties.get("type");
        if (properties.get("path") == null) {
            throw new ConfigurationException("path", "path property is mandatory");
        }
        String path = (String) properties.get("path");

        LOGGER.debug("Starting tail on {}", path);
        tailer = new Tailer(new File(path), this, 1000, true, true);
        Thread thread = new Thread(tailer, "File tailer for " + path);
        this.type = type;
        this.path = path;
        this.regex = (String) properties.get("regex");
        thread.start();
        if (regex != null) {
            compiledRegex  = Pattern.compile(regex);
        }
    }
    
    @Deactivate
    public void deactivate() {
        tailer.stop();
    }

    @Override
    public void handle(String line) {
        LOGGER.debug("Handle new line in {}", path);
        Map<String, Object> data = new HashMap<>();
        data.put("type", type);
        data.put("path", path);
        data.put("regex", regex);

        if (compiledRegex != null) {
            Matcher matcher = compiledRegex.matcher(line);
            if (matcher.matches()) {
                data.putAll(this.parser.parse("line_" + type, line));
            } else {
                return;
            }
        } else {
            data.putAll(this.parser.parse("line_" + type, line));
        }

        try {
            PropertiesPreparator.prepare(data, properties);
        } catch (Exception e) {
            LOGGER.warn("Can't fully prepare data for the dispatcher", e);
        }

        Event event = new Event("decanter/collect/file/" + type, data);
        dispatcher.postEvent(event);
    }

    @Override
    public void handle(Exception e) {
        LOGGER.warn("Handle exception on file {}", path, e);
        super.handle(e);
    }

    @Override
    public void fileNotFound() {
        LOGGER.warn("File {} is not found", path);
        super.fileNotFound();
    }

    @Override
    public void fileRotated() {
        LOGGER.debug("File {} rotated", path);
        super.fileRotated();
    }

    @Override
    public void endOfFileReached() {
        LOGGER.debug("Reached end of file {}", path);
        super.endOfFileReached();
    }

}
