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

    private Marshaller marshaller;
    private BufferedWriter writer;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        Dictionary<String, Object> config = componentContext.getProperties();
        String filename = (config.get("filename") != null) ? (String) config.get("filename") : System.getProperty("karaf.data") + File.separator + "decanter";
        open(filename);
    }

    public void open(String filename) throws Exception {
        File file = new File(filename);
        file.getParentFile().mkdirs();
        file.createNewFile();
        this.writer = new BufferedWriter(new FileWriter(file));
    }

    @Override
    public void handleEvent(Event event) {
        try {
            String marshalled = marshaller.marshal(event);
            writer.write(marshalled);
            writer.newLine();
            writer.flush();
        } catch (Exception e) {
            // nothing to do
        }
    }

    @Deactivate
    public void deactivate() throws Exception {
        this.writer.flush();
        this.writer.close();
    }

    @Reference(target="(" + Marshaller.SERVICE_KEY_DATAFORMAT + "=csv)")
    public void setMarshaller(Marshaller marshaller) {
        this.marshaller = marshaller;
    }

}
