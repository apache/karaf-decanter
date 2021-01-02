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
package org.apache.karaf.decanter.appender.file;

import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.Dictionary;

@Component(
        name = "org.apache.karaf.decanter.appender.file",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class FileAppender implements EventHandler {

    public static final String FILENAME_PROPERTY = "filename";
    public static final String APPEND_PROPERTY = "append";

    @Reference
    public Marshaller marshaller;

    private BufferedWriter writer;

    private Dictionary<String, Object> config;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        Dictionary<String, Object> config = componentContext.getProperties();
        open(config);
    }

    public void open(Dictionary<String, Object> config) throws Exception {
        this.config = config;

        String filename = (config.get(FILENAME_PROPERTY) != null) ? (String) config.get(FILENAME_PROPERTY) : System.getProperty("karaf.data") + File.separator + "decanter" + File.separator + "appender.csv";
        boolean append = (config.get(APPEND_PROPERTY) != null) ? Boolean.parseBoolean((String) config.get(APPEND_PROPERTY)) : true;

        File file = new File(filename);
        file.getParentFile().mkdirs();
        file.createNewFile();
        this.writer = new BufferedWriter(new FileWriter(file, append));
    }

    @Override
    public void handleEvent(Event event) {
        if (EventFilter.match(event, config)) {
            try {
                String marshalled = marshaller.marshal(event);
                writer.write(marshalled);
                writer.newLine();
                writer.flush();
            } catch (Exception e) {
                // nothing to do
            }
        }
    }

    @Deactivate
    public void deactivate() throws Exception {
        this.writer.flush();
        this.writer.close();
    }

}
