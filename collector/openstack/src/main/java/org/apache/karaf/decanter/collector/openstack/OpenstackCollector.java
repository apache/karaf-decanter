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
package org.apache.karaf.decanter.collector.openstack;

import org.apache.karaf.decanter.api.marshaller.Unmarshaller;
import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component(
        service = Runnable.class,
        name = "org.apache.karaf.decanter.collector.openstack",
        immediate = true,
        property = {
                "decanter.collector.name=openstack",
                "scheduler.period:Long=300",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-openstack"
        }
)
public class OpenstackCollector implements Runnable {

    @Reference
    public EventAdmin dispatcher;

    @Reference
    public Unmarshaller unmarshaller;

    private final static Logger LOGGER = LoggerFactory.getLogger(OpenstackCollector.class);

    private Dictionary<String, Object> config;
    private String topic;

    private URL identity;
    private String username;
    private String password;
    private String domain;
    private String project;

    private boolean computeEnabled = true;
    private String compute = null;
    private boolean computeUsageFlatten = true;
    private boolean blockStorageEnabled = true;
    private String blockStorage = null;
    private boolean imageEnabled = true;
    private String image = null;
    private boolean metricEnabled = true;
    private String metric = null;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> config) throws Exception {
        this.config = config;
        if (config.get("topic") != null) {
            topic = (String) config.get("topic");
        } else {
            topic = "decanter/collect/openstack";
        }
        if (config.get("openstack.identity") == null) {
            throw new IllegalStateException("openstack.identity is not configured");
        }
        identity = new URL(config.get("openstack.identity") + "/v3/auth/tokens");
        if (config.get("openstack.username") == null) {
            throw new IllegalStateException("openstack.username is not configured");
        }
        username = (String) config.get("openstack.username");
        if (config.get("openstack.password") == null) {
            throw new IllegalStateException("openstack.password is not configured");
        }
        password = (String) config.get("openstack.password");
        if (config.get("openstack.domain") == null) {
            throw new IllegalStateException("openstack.domain is not configured");
        }
        domain = (String) config.get("openstack.domain");
        if (config.get("openstack.project") == null) {
            throw new IllegalStateException("openstack.project is not configured");
        }
        project = (String) config.get("openstack.project");

        if (config.get("openstack.compute.enabled") != null) {
            computeEnabled = Boolean.parseBoolean((String) config.get("openstack.compute.enabled"));
        }
        compute = (String) config.get("openstack.compute");
        if (config.get("openstack.compute.usage.flatten") != null) {
            computeUsageFlatten = Boolean.parseBoolean((String) config.get("openstack.compute.usage.flatten"));
        }

        if (config.get("openstack.block.storage.enabled") != null) {
            blockStorageEnabled = Boolean.parseBoolean((String) config.get("openstack.block.storage.enabled"));
        }
        blockStorage = (String) config.get("openstack.block.storage");

        if (config.get("openstack.image.enabled") != null) {
            imageEnabled = Boolean.parseBoolean((String) config.get("openstack.image.enabled"));
        }
        image = (String) config.get("openstack.image");

        if (config.get("openstack.metric.enabled") != null) {
            metricEnabled = Boolean.parseBoolean((String) config.get("openstack.metric.enabled"));
        }
        metric = (String) config.get("openstack.metric");
    }

    @Override
    public void run() {
        try {
            String token = auth();

            if (computeEnabled) {
                try {
                    computeUsage(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute usage", e);
                }
                try {
                    computeServers(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute servers", e);
                }
                try {
                    computeFlavors(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute flavors", e);
                }
                try {
                    computeOsKeypairs(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS keypairs", e);
                }
                try {
                    computeLimits(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute limits", e);
                }
                try {
                    computeOsAggregates(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS aggregates", e);
                }
                try {
                    computeAvailabilityZones(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute availability zones", e);
                }
                try {
                    computeOsHypervisors(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS hypervisors", e);
                }
                try {
                    computeOsInstanceUsageAuditLog(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS instance usage audit log", e);
                }
                try {
                    computeOsMigrations(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS migrations", e);
                }
                try {
                    computeOsServerGroups(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS server groups", e);
                }
                try {
                    computeOsServices(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get compute OS services", e);
                }
            }
            if (blockStorageEnabled) {
                try {
                    blockStorageVolumeTypes(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage volume types", e);
                }
                try {
                    blockStorageVolumesDetail(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage volumes detail", e);
                }
                try {
                    blockStorageManageableVolumes(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage manageable volumes", e);
                }
                try {
                    blockStorageSnapshotsDetail(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage snapshots detail", e);
                }
                try {
                    blockStorageVolumeTransfers(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage volume transfers", e);
                }
                try {
                    blockStorageAttachments(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage attachments", e);
                }
                try {
                    blockStorageBackups(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage backups", e);
                }
                try {
                    blockStorageOsServices(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage OS services", e);
                }
                try {
                    blockStorageGroups(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage groups", e);
                }
                try {
                    blockStorageGroupSnapshots(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage group snapshots", e);
                }
                try {
                    blockStorageGroupTypes(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage group types", e);
                }
                try {
                    blockStorageOsHosts(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage OS hosts", e);
                }
                try {
                    blockStorageLimits(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage limits", e);
                }
                try {
                    blockStorageResourceFilters(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage resource filters", e);
                }
                try {
                    blockStorageQosSpecs(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get block storage QoS specs", e);
                }
            }
            if (imageEnabled) {
                try {
                    images(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get images", e);
                }
                try {
                    imageStore(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get image store", e);
                }
                try {
                    imageTasks(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get image tasks", e);
                }
            }
            if (metricEnabled) {
                try {
                    metric(token, dispatcher);
                } catch (Exception e) {
                    LOGGER.warn("Can't get metric", e);
                }
            }

        } catch (Exception e) {
            LOGGER.warn("Can't get openstack details", e);
        }
    }

    protected void computeUsage(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-simple-tenant-usage", token);

        if (computeUsageFlatten) {
            String extract = response.substring(response.indexOf("[") +1, response.indexOf("]"));
            String[] split = extract.split("}, \\{");
            for (int i = 0; i < split.length; i++) {
                Map<String, Object> data = new HashMap<>();
                String use = split[i];
                if (!use.startsWith("{")) {
                    use = "{" + use;
                }
                if (!use.endsWith("}")) {
                    use = use + "}";
                }
                data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(use.getBytes())));
                PropertiesPreparator.prepare(data, config);
                dispatcher.postEvent(new Event(topic, data));
            }
        } else {
            Map<String, Object> data = new HashMap<>();
            data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
            PropertiesPreparator.prepare(data, config);
            dispatcher.postEvent(new Event(topic, data));
        }
    }

    protected void computeServers(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/servers/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeFlavors(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/flavors/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsKeypairs(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-keypairs", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeLimits(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/limits", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsAggregates(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-aggregates", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeAvailabilityZones(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-availability-zone/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsHypervisors(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-hypervisors/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsInstanceUsageAuditLog(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-instance_usage_audit_log", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsMigrations(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-migrations", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsServerGroups(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-server-groups", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void computeOsServices(String token, EventAdmin dispatcher) throws Exception {
        String response = request(compute + "/os-services", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageVolumeTypes(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/types", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageVolumesDetail(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/volumes/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageManageableVolumes(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/manageable_volumes/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageSnapshotsDetail(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/snapshots/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageVolumeTransfers(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/volume-transfers/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageAttachments(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/attachments/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageBackups(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/backups/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageOsServices(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/os-services", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageGroups(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/groups", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageGroupSnapshots(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/group_snapshots/detail", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageGroupTypes(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/group_types", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageOsHosts(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/os-hosts", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageLimits(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/limits", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageResourceFilters(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/resource_filters", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void blockStorageQosSpecs(String token, EventAdmin dispatcher) throws Exception {
        String response = request(blockStorage + "/" + project + "/qos-specs", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void images(String token, EventAdmin dispatcher) throws Exception {
        String response = request(image + "/v2/images", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void imageStore(String token, EventAdmin dispatcher) throws Exception {
        String response = request(image + "/v2/info/stores", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void imageTasks(String token, EventAdmin dispatcher) throws Exception {
        String response = request(image + "/v2/tasks", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    protected void metric(String token, EventAdmin dispatcher) throws Exception {
        String response = request(metric + "/v1/metric", token);
        Map<String, Object> data = new HashMap<>();
        data.putAll(unmarshaller.unmarshal(new ByteArrayInputStream(response.getBytes())));
        PropertiesPreparator.prepare(data, config);
        dispatcher.postEvent(new Event(topic, data));
    }

    private String request(String url, String token) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Auth-Token", token);
        connection.setDoInput(true);
        if (connection.getResponseCode() != 200) {
            throw new IllegalStateException("Can't get data from " + url + " (" + connection.getResponseCode() + "): " + connection.getResponseMessage());
        }
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line).append("\n");
            }
        }
        return response.toString();
    }

    protected String auth() throws Exception {
        LOGGER.debug("Authentication on {}", identity);
        HttpURLConnection connection = (HttpURLConnection) identity.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoInput(true);
        connection.setDoOutput(true);
        String authJson = "{ \"auth\": { \"identity\": { \"methods\": [\"password\"], \"password\": { \"user\": { \"name\": \"" + username + "\", \"domain\": { \"name\": \"" + domain + "\" }, \"password\": \"" + password + "\" }}}, \"scope\": { \"project\": { \"id\": \"" + project + "\" }}}}";
        LOGGER.debug("Authentication JSON request:");
        LOGGER.debug(authJson);
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(connection.getOutputStream()))) {
            writer.write(authJson);
            writer.flush();
        }
        if (connection.getResponseCode() != 201) {
            throw new IllegalStateException("Can't get token (" + connection.getResponseCode() + "): " + connection.getResponseMessage());
        }
        return connection.getHeaderField("X-Subject-Token");
    }

    /**
     * For testing purpose.
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
