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
package org.apache.karaf.decanter.appender.hdfs;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.karaf.decanter.api.marshaller.Marshaller;
import org.apache.karaf.decanter.appender.utils.EventFilter;
import org.osgi.service.cm.ConfigurationException;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventConstants;
import org.osgi.service.event.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Dictionary;

@Component(
        name = "org.apache.karaf.decanter.appender.hdfs",
        immediate = true,
        property = EventConstants.EVENT_TOPIC + "=decanter/collect/*"
)
public class HdfsAppender implements EventHandler {

    private final static Logger LOGGER = LoggerFactory.getLogger(HdfsAppender.class);

    private Dictionary<String, Object> config;
    private Configuration configuration;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) {
        this.config = config;
        configuration = new Configuration();
        if (config.get("hdfs.configuration") != null) {
            configuration.addResource((String) config.get("hdfs.configuration"));
        }
    }

    @Override
    public void handleEvent(Event event) {
        try {
            if (EventFilter.match(event, config)) {
                FileSystem fileSystem = FileSystem.get(configuration);
                if (config.get("hdfs.path") == null) {
                    throw new IllegalArgumentException("hdfs.path is not set");
                }
                Path path = new Path((String) config.get("hdfs.path"));
                FSDataOutputStream outputStream;
                if (config.get("hdfs.mode") == null) {
                    outputStream = fileSystem.create(path, true);
                } else {
                    if (((String) config.get("hdfs.mode")).equalsIgnoreCase("append")) {
                        outputStream = fileSystem.append(path);
                    } else if (((String) config.get("hdfs.mode")).equalsIgnoreCase("overwrite")) {
                        outputStream = fileSystem.create(path, true);
                    } else {
                        outputStream = fileSystem.create(path, false);
                    }
                }
                try {
                    String marshalled = marshaller.marshal(event);
                    outputStream.writeChars(marshalled);
                } catch (Exception e) {
                    // nothing to do
                }
                outputStream.flush();
                outputStream.close();
                fileSystem.close();
            }
        } catch (Exception e) {
            LOGGER.warn("Can't write on HDFS", e);
        }
    }

    @Reference
    public Marshaller marshaller;

}
