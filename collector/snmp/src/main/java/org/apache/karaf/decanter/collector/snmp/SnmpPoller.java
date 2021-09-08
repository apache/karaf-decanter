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
package org.apache.karaf.decanter.collector.snmp;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.*;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.osgi.service.event.EventConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.event.ResponseListener;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.DefaultPDUFactory;
import org.snmp4j.util.TreeEvent;
import org.snmp4j.util.TreeUtils;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.snmp.poll",
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE,
        property = { "decanter.collector.name=snmp",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-snmp"
        }
)
public class SnmpPoller implements ResponseListener, Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(SnmpPoller.class);

    @Reference
    private EventAdmin dispatcher;

    private Dictionary<String, Object> configuration;
    private boolean treeList;
    private String oids;

    private Address address;
    private TransportMapping<? extends Address> transport;
    private Snmp snmp;

    private Target target;
    private PDU pdu;

    @Activate
    public void activate(ComponentContext componentContext) throws Exception {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> configuration) throws Exception {
        this.configuration = configuration;

        String addressConfig = (configuration.get("address") != null) ? (String) configuration.get("address") : "127.0.0.1/161";
        this.address = GenericAddress.parse(addressConfig);

        String protocol = (configuration.get("protocol") != null) ? (String) configuration.get("protocol") : "tcp";
        if (protocol.equalsIgnoreCase("tcp")) {
            this.transport = new DefaultTcpTransportMapping();
        } else if (protocol.equalsIgnoreCase("udp")) {
            this.transport = new DefaultUdpTransportMapping();
        } else {
            throw new IllegalStateException("Unknown SNMP protocol: " + protocol + " (udp and tcp are supported)");
        }
        int retries = (configuration.get("retries") != null) ? Integer.parseInt((String) configuration.get("retries")) : 2;
        long timeout = (configuration.get("timeout") != null) ? Long.parseLong((String) configuration.get("timeout")) : 1500;
        treeList = (configuration.get("treelist") != null) ? Boolean.parseBoolean((String) configuration.get("treelist")) : false;
        oids = (configuration.get("oids") != null) ? (String) configuration.get("oids") : "";

        this.snmp = new Snmp(transport);

        int snmpVersion = (configuration.get("snmp.version") != null) ? Integer.parseInt((String) configuration.get("snmp.version")) : SnmpConstants.version3;
        if (snmpVersion == SnmpConstants.version3) {
            UserTarget userTarget = new UserTarget();

            int securityLevel = (configuration.get("security.level") != null) ? Integer.parseInt((String) configuration.get("security.level")) : SecurityLevel.AUTH_PRIV;
            userTarget.setSecurityLevel(securityLevel);
            String securityName = (configuration.get("security.name") != null) ? (String) configuration.get("security.name") : "";
            userTarget.setSecurityName(convertToOctetString(securityName));
            userTarget.setAddress(address);
            userTarget.setRetries(2);
            userTarget.setTimeout(timeout);
            userTarget.setVersion(snmpVersion);

            this.target = userTarget;

            USM usm = new USM(SecurityProtocols.getInstance(), new OctetString(MPv3.createLocalEngineID()), 0);
            SecurityModels.getInstance().addSecurityModel(usm);

            String authenticationProtocolConfig = (configuration.get("authentication.protocol") != null) ? (String) configuration.get("authentication.protocol") : "MD5";
            OID authenticationProtocol = convertAuthenticationProtocol(authenticationProtocolConfig);

            String authenticationPassphraseConfig = (configuration.get("authentication.passphrase") != null) ? (String) configuration.get("authentication.passphrase") : "";
            OctetString authenticationPassphrase = convertToOctetString(authenticationPassphraseConfig);

            String privacyProtocolConfig = (configuration.get("privacy.protocol") != null) ? (String) configuration.get("privacy.protocol") : "DES";
            OID privacyProtocol = convertPrivacyProtocol(privacyProtocolConfig);

            String privacyPassphraseConfig = (configuration.get("privacy.passphrase") != null) ? (String) configuration.get("privacy.passphrase") : "";
            OctetString privacyPassphrase = convertToOctetString(privacyPassphraseConfig);

            UsmUser user = new UsmUser(convertToOctetString(securityName), authenticationProtocol,
                    authenticationPassphrase, privacyProtocol, privacyPassphrase);
            usm.addUser(convertToOctetString(securityName), user);

            ScopedPDU scopedPDU = new ScopedPDU();

            String snmpContextEngineId = (configuration.get("snmp.context.engine.id") != null) ? (String) configuration.get("snmp.context.engine.id") : null;
            if (snmpContextEngineId != null) {
                scopedPDU.setContextEngineID(new OctetString(snmpContextEngineId));
            }

            String snmpContextName = (configuration.get("snmp.context.name") != null) ? (String) configuration.get("snmp.context.name") : null;
            if (snmpContextName != null) {
                scopedPDU.setContextName(new OctetString(snmpContextName));
            }

            this.pdu = scopedPDU;
        } else {
            CommunityTarget communityTarget = new CommunityTarget();
            String snmpCommunity = (configuration.get("snmp.community") != null) ? (String) configuration.get("snmp.community") : "public";
            communityTarget.setCommunity(convertToOctetString(snmpCommunity));
            communityTarget.setAddress(address);
            communityTarget.setRetries(retries);
            communityTarget.setTimeout(timeout);
            communityTarget.setVersion(snmpVersion);
            this.target = communityTarget;
            this.pdu = new PDU();
        }
        this.transport.listen();
    }

    @Deactivate
    public void deactivate() throws Exception {
        if (this.transport != null && this.transport.isListening()) {
            this.transport.close();
        }
    }

    @Override
    public void run() {
        this.pdu.clear();

        this.pdu.setType(PDU.GETNEXT);

        if (!treeList) {
            for (String oid : oids.split(",")) {
                this.pdu.add(new VariableBinding(new OID(oid)));
            }
        } else {
            TreeUtils treeUtils = new TreeUtils(snmp, new DefaultPDUFactory());
            for (String oid : oids.split(",")) {
                List events = treeUtils.getSubtree(target, new OID(oid));
                for (Object event : events) {
                    TreeEvent treeEvent = (TreeEvent) event;
                    if (treeEvent == null) {
                        LOGGER.warn("SNMP event is null");
                        continue;
                    }
                    if (treeEvent.isError()) {
                        LOGGER.error("Error in SNMP event: {}", treeEvent.getErrorMessage());
                        continue;
                    }
                    VariableBinding[] variableBindings = treeEvent.getVariableBindings();
                    if (variableBindings == null || variableBindings.length == 0) {
                        continue;
                    }
                    for (VariableBinding variableBinding : variableBindings) {
                        if (variableBinding == null) {
                            continue;
                        }
                        this.pdu.add(variableBinding);
                    }
                }
            }
        }
        try {
            snmp.send(pdu, target, null, this);
        } catch (Exception e) {
            LOGGER.warn("Can't send SNMP request", e);
        }
    }

    @Override
    public void onResponse(ResponseEvent event) {
        ((Snmp) event.getSource()).cancel(event.getRequest(), this);

        if (event.getRequest() == null || event.getResponse() == null) {
            return;
        }

        PDU pdu = event.getResponse();
        Map<String, Object> data = new HashMap<>();
        data.put("type", "snmp.poll");
        data.put("peerAddress", event.getPeerAddress());
        try {
            PropertiesPreparator.prepare(data, configuration);
        } catch (Exception e) {
            LOGGER.warn("Can't prepare event data", e);
        }
        // PDU v1 specific variables
        if (pdu.getType() == PDU.V1TRAP) {
            PDUv1 v1pdu = (PDUv1) pdu;
            data.put("enterprise", v1pdu.getEnterprise().toString());
            data.put("agentAddress", v1pdu.getAgentAddress().toString());
            data.put("genericTrap", v1pdu.getGenericTrap());
            data.put("specificTrap", v1pdu.getSpecificTrap());
            data.put("timestamp", v1pdu.getTimestamp());
        }
        // all variables
        for (VariableBinding variableBinding : pdu.getVariableBindings()) {
            data.put(variableBinding.getOid().toString(), variableBinding.getVariable().toString());
        }
        // send event
        String topic = (configuration.get(EventConstants.EVENT_TOPIC) != null) ? (String) configuration.get(EventConstants.EVENT_TOPIC) : "decanter/collector/snmp";
        dispatcher.postEvent(new Event(topic, data));
    }

    private OctetString convertToOctetString(String value) {
        if (value == null) {
            return null;
        }
        return new OctetString(value);
    }

    private OID convertAuthenticationProtocol(String authenticationProtocol) {
        if (authenticationProtocol == null) {
            return null;
        }
        if (authenticationProtocol.equals("MD5")) {
            return AuthMD5.ID;
        } else if (authenticationProtocol.equals("SHA1")) {
            return AuthSHA.ID;
        } else {
            throw new IllegalArgumentException("Unknown authentication protocol: " + authenticationProtocol);
        }
    }

    private OID convertPrivacyProtocol(String privacyProtocol) {
        if (privacyProtocol == null) {
            return null;
        }
        if (privacyProtocol.equals("DES")) {
            return PrivDES.ID;
        } else if (privacyProtocol.equals("TRIDES")) {
            return Priv3DES.ID;
        } else if (privacyProtocol.equals("AES128")) {
            return PrivAES128.ID;
        } else if (privacyProtocol.equals("AES192")) {
            return PrivAES192.ID;
        } else if (privacyProtocol.equals("AES256")) {
            return PrivAES256.ID;
        } else {
            throw new IllegalArgumentException("Unknown privacy protocol: " + privacyProtocol);
        }
    }

}
