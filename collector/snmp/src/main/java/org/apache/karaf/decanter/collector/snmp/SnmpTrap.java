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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.*;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.snmp.trap",
        immediate = true,
        configurationPolicy = ConfigurationPolicy.REQUIRE
)
public class SnmpTrap implements CommandResponder {

    private final static Logger LOGGER = LoggerFactory.getLogger(SnmpTrap.class);

    @Reference
    private EventAdmin dispatcher;

    private Dictionary<String, Object> configuration;

    private Address address;
    private Snmp snmp;
    private TransportMapping<? extends Address> transport;

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
            transport = new DefaultTcpTransportMapping((TcpAddress) address);
        } else if (protocol.equalsIgnoreCase("udp")) {
            transport = new DefaultUdpTransportMapping((UdpAddress) address);
        } else {
            throw new IllegalArgumentException("Unknown SNMP protocol: " + protocol + " (should be tcp or udp)");
        }
        this.snmp = new Snmp(transport);
        this.snmp.addCommandResponder(this);
        this.snmp.listen();
    }

    @Deactivate
    public void deactivate() throws Exception {
        transport.close();
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        PDU pdu = event.getPDU();
        if (pdu != null) {
            try {
                if ((pdu.getType() != PDU.TRAP) && (pdu.getType() != PDU.V1TRAP) && (pdu.getType() != PDU.REPORT) && (pdu.getType() != PDU.RESPONSE)) {
                    // respond the inform message
                    pdu.setErrorIndex(0);
                    pdu.setErrorStatus(0);
                    pdu.setType(PDU.RESPONSE);
                    StatusInformation statusInformation = new StatusInformation();
                    StateReference reference = event.getStateReference();
                    event.getMessageDispatcher().returnResponsePdu(event.getMessageProcessingModel(),
                            event.getSecurityModel(),
                            event.getSecurityName(),
                            event.getSecurityModel(),
                            pdu,
                            event.getMaxSizeResponsePDU(),
                            reference,
                            statusInformation);
                }
                Map<String, Object> data = new HashMap<>();
                data.put("type", "snmp.trap");
                data.put("securityName", new OctetString(event.getSecurityName()));
                data.put("peerAddress", event.getPeerAddress());
                PropertiesPreparator.prepare(data, configuration);
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
                dispatcher.postEvent(new Event("decanter/collector/snmp", data));
            } catch (Exception e) {
                LOGGER.warn("Can't send SNMP event", e);
            }
        }
    }

}
