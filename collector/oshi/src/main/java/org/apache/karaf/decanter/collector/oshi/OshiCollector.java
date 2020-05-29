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
package org.apache.karaf.decanter.collector.oshi;

import org.apache.karaf.decanter.collector.utils.PropertiesPreparator;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.event.Event;
import org.osgi.service.event.EventAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import oshi.SystemInfo;
import oshi.hardware.*;
import oshi.software.os.*;

import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

@Component(
        name = "org.apache.karaf.decanter.collector.oshi",
        immediate = true,
        property = { "decanter.collector.name=oshi",
                "scheduler.period:Long=60",
                "scheduler.concurrent:Boolean=false",
                "scheduler.name=decanter-collector-oshi"}
)
public class OshiCollector implements Runnable {

    private final static Logger LOGGER = LoggerFactory.getLogger(OshiCollector.class);

    @Reference
    EventAdmin dispatcher;

    private Dictionary<String, Object> properties;

    @Activate
    public void activate(ComponentContext componentContext) {
        activate(componentContext.getProperties());
    }

    public void activate(Dictionary<String, Object> properties) {
        this.properties = properties;
    }

    @Override
    public void run() {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("type", "oshi");

            SystemInfo systemInfo = new SystemInfo();

            HardwareAbstractionLayer hardwareAbstractionLayer = systemInfo.getHardware();
            // * computer system
            boolean computerSystem = properties.get("computerSystem") != null ? Boolean.parseBoolean(properties.get("computerSystem").toString()) : true;
            if (computerSystem) {
                data.put("computerSystem.manufacturer", hardwareAbstractionLayer.getComputerSystem().getManufacturer());
                data.put("computerSystem.model", hardwareAbstractionLayer.getComputerSystem().getModel());
                data.put("computerSystem.serialNumber", hardwareAbstractionLayer.getComputerSystem().getSerialNumber());
                // ** baseboard
                boolean computerSystemBaseboard = properties.get("computerSystem.baseboard") != null  ? Boolean.parseBoolean(properties.get("computerSystem.baseboard").toString()) : true;
                if (computerSystemBaseboard) {
                    data.put("computerSystem.baseboard.manufacturer", hardwareAbstractionLayer.getComputerSystem().getBaseboard().getManufacturer());
                    data.put("computerSystem.baseboard.model", hardwareAbstractionLayer.getComputerSystem().getBaseboard().getModel());
                    data.put("computerSystem.baseboard.serialNumber", hardwareAbstractionLayer.getComputerSystem().getBaseboard().getSerialNumber());
                    data.put("computerSystem.baseboard.version", hardwareAbstractionLayer.getComputerSystem().getBaseboard().getVersion());
                }
                // ** firmware
                boolean computerSystemFirmware = properties.get("computerSystem.firmware") != null ? Boolean.parseBoolean(properties.get("computerSystem.firmware").toString()) : true;
                if (computerSystemFirmware) {
                    data.put("computerSystem.firmware.description", hardwareAbstractionLayer.getComputerSystem().getFirmware().getDescription());
                    data.put("computerSystem.firmware.manufacturer", hardwareAbstractionLayer.getComputerSystem().getFirmware().getManufacturer());
                    data.put("computerSystem.firmware.name", hardwareAbstractionLayer.getComputerSystem().getFirmware().getName());
                    data.put("computerSystem.firmware.releaseDate", hardwareAbstractionLayer.getComputerSystem().getFirmware().getReleaseDate());
                    data.put("computerSystem.firmware.version", hardwareAbstractionLayer.getComputerSystem().getFirmware().getVersion());
                }
            }
            // * memory
            boolean memory = properties.get("memory") != null ? Boolean.parseBoolean(properties.get("memory").toString()) : true;
            if (memory) {
                data.put("memory.available", hardwareAbstractionLayer.getMemory().getAvailable());
                data.put("memory.pageSize", hardwareAbstractionLayer.getMemory().getPageSize());
                data.put("memory.physicalMemory", hardwareAbstractionLayer.getMemory().getPhysicalMemory());
                data.put("memory.total", hardwareAbstractionLayer.getMemory().getTotal());
                data.put("memory.virtualMemory", hardwareAbstractionLayer.getMemory().getVirtualMemory());
            }
            // * processor
            boolean processors = properties.get("processors") != null ? Boolean.parseBoolean(properties.get("processors").toString()) : true;
            if (processors) {
                data.put("processor.contextSwitched", hardwareAbstractionLayer.getProcessor().getContextSwitches());
                data.put("processor.currentFreq", Arrays.toString(hardwareAbstractionLayer.getProcessor().getCurrentFreq()));
                data.put("processor.interrupts", hardwareAbstractionLayer.getProcessor().getInterrupts());
                data.put("processor.logicalProcessorCount", hardwareAbstractionLayer.getProcessor().getLogicalProcessorCount());
                data.put("processor.maxFreq", hardwareAbstractionLayer.getProcessor().getMaxFreq());
                boolean processorsLogical = properties.get("processors.logical") != null ? Boolean.parseBoolean(properties.get("processors.logical").toString()) : true;
                if (processorsLogical) {
                    int i = 0;
                    for (CentralProcessor.LogicalProcessor processor : hardwareAbstractionLayer.getProcessor().getLogicalProcessors()) {
                        data.put("processor.logical." + i + ".numaNode", processor.getNumaNode());
                        data.put("processor.logical." + i + ".physicalPackageNumber", processor.getPhysicalPackageNumber());
                        data.put("processor.logical." + i + ".physicalProcessorNumber", processor.getPhysicalProcessorNumber());
                        data.put("processor.logical." + i + ".processorGroup", processor.getProcessorGroup());
                        data.put("processor.logical." + i + ".processorNumber", processor.getProcessorNumber());
                        i++;
                    }
                }
            }
            // * displays
            boolean displays = properties.get("displays") != null ? Boolean.parseBoolean(properties.get("displays").toString()) : true;
            if (displays) {
                int i = 0;
                for (Display display : hardwareAbstractionLayer.getDisplays()) {
                    data.put("display." + i + ".edid", display.getEdid());
                    i++;
                }
            }
            boolean disks = properties.get("disks") != null ? Boolean.parseBoolean(properties.get("disks").toString()) : true;
            if (disks) {
                // * disk stores
                int i = 0;
                for (HWDiskStore diskStore : hardwareAbstractionLayer.getDiskStores()) {
                    data.put("diskStore." + i + ".name", diskStore.getName());
                    data.put("diskStore." + i + ".model", diskStore.getModel());
                    data.put("diskStore." + i + ".currentQueueLength", diskStore.getCurrentQueueLength());
                    data.put("diskStore." + i + ".readBytes", diskStore.getReadBytes());
                    data.put("diskStore." + i + ".reads", diskStore.getReads());
                    data.put("diskStore." + i + ".serial", diskStore.getSerial());
                    data.put("diskStore." + i + ".size", diskStore.getSize());
                    data.put("diskStore." + i + ".timeStamp", diskStore.getTimeStamp());
                    data.put("diskStore." + i + ".transferTime", diskStore.getTransferTime());
                    data.put("diskStore." + i + ".writeBytes", diskStore.getWriteBytes());
                    data.put("diskStore." + i + ".writes", diskStore.getWrites());
                    // partitions
                    boolean disksPartitions = properties.get("disks.partitions") != null ? Boolean.parseBoolean(properties.get("disks.partitions").toString()) : true;
                    if (disksPartitions) {
                        int j = 0;
                        for (HWPartition partition : diskStore.getPartitions()) {
                            data.put("diskStore." + i + ".partition." + j + ".name", partition.getName());
                            data.put("diskStore." + i + ".partition." + j + ".identification", partition.getIdentification());
                            data.put("diskStore." + i + ".partition." + j + ".major", partition.getMajor());
                            data.put("diskStore." + i + ".partition." + j + ".minor", partition.getMinor());
                            data.put("diskStore." + i + ".partition." + j + ".mountPoint", partition.getMountPoint());
                            data.put("diskStore." + i + ".partition." + j + ".size", partition.getSize());
                            data.put("diskStore." + i + ".partition." + j + ".type", partition.getType());
                            data.put("diskStore." + i + ".partition." + j + ".uuid", partition.getUuid());
                            j++;
                        }
                    }
                    i++;
                }
            }
            // * graphics cards
            boolean graphicsCards = properties.get("graphicsCards") != null ? Boolean.parseBoolean(properties.get("graphicsCards").toString()) : true;
            if (graphicsCards) {
                int i = 0;
                for (GraphicsCard graphicsCard : hardwareAbstractionLayer.getGraphicsCards()) {
                    data.put("graphicsCard." + i + ".deviceId", graphicsCard.getDeviceId());
                    data.put("graphicsCard." + i + ".name", graphicsCard.getName());
                    data.put("graphicsCard." + i + ".vendor", graphicsCard.getVendor());
                    data.put("graphicsCard." + i + ".versionInfo", graphicsCard.getVersionInfo());
                    data.put("graphicsCard." + i + ".vram", graphicsCard.getVRam());
                    i++;
                }
            }
            boolean networkIFs = properties.get("networkIFs") != null ? Boolean.parseBoolean(properties.get("networkIFs").toString()) : true;
            if (networkIFs) {
                // * network interfaces
                int i = 0;
                for (NetworkIF networkIF : hardwareAbstractionLayer.getNetworkIFs()) {
                    data.put("networkIF." + i + ".bytesRecv", networkIF.getBytesRecv());
                    data.put("networkIF." + i + ".bytesSent", networkIF.getBytesSent());
                    data.put("networkIF." + i + ".collisions", networkIF.getCollisions());
                    data.put("networkIF." + i + ".displayName", networkIF.getDisplayName());
                    data.put("networkIF." + i + ".ifType", networkIF.getIfType());
                    data.put("networkIF." + i + ".inDrops", networkIF.getInDrops());
                    data.put("networkIF." + i + ".inErrors", networkIF.getInErrors());
                    data.put("networkIF." + i + ".IPv4addr", Arrays.toString(networkIF.getIPv4addr()));
                    data.put("networkIF." + i + ".IPv6addr", Arrays.toString(networkIF.getIPv6addr()));
                    data.put("networkIF." + i + ".MACaddr", networkIF.getMacaddr());
                    data.put("networkIF." + i + ".MTU", networkIF.getMTU());
                    data.put("networkIF." + i + ".name", networkIF.getName());
                    data.put("networkIF." + i + ".ndisPhysicalMediumType", networkIF.getNdisPhysicalMediumType());
                    data.put("networkIF." + i + ".outErrors", networkIF.getOutErrors());
                    data.put("networkIF." + i + ".packetsRecv", networkIF.getPacketsRecv());
                    data.put("networkIF." + i + ".packetsSent", networkIF.getPacketsSent());
                    data.put("networkIF." + i + ".speed", networkIF.getSpeed());
                    data.put("networkIF." + i + ".prefixLengths", Arrays.toString(networkIF.getPrefixLengths()));
                    data.put("networkIF." + i + ".subnetMasks", Arrays.toString(networkIF.getSubnetMasks()));
                    data.put("networkIF." + i + ".timeStamp", networkIF.getTimeStamp());
                    i++;
                }
            }
            // * power sources
            boolean powerSources = properties.get("powerSources") != null ? Boolean.parseBoolean(properties.get("powerSources").toString()) : true;
            if (powerSources) {
                int i = 0;
                for (PowerSource powerSource : hardwareAbstractionLayer.getPowerSources()) {
                    data.put("powerSource." + i + ".amperage", powerSource.getAmperage());
                    data.put("powerSource." + i + ".capacityUnits", powerSource.getCapacityUnits());
                    data.put("powerSource." + i + ".chemistry", powerSource.getChemistry());
                    data.put("powerSource." + i + ".currentCapacity", powerSource.getCurrentCapacity());
                    data.put("powerSource." + i + ".cycleCount", powerSource.getCycleCount());
                    data.put("powerSource." + i + ".designCapacity", powerSource.getDesignCapacity());
                    data.put("powerSource." + i + ".deviceName", powerSource.getDeviceName());
                    data.put("powerSource." + i + ".manufactureDate", powerSource.getManufactureDate());
                    data.put("powerSource." + i + ".manufacturer", powerSource.getManufacturer());
                    data.put("powerSource." + i + ".maxCapacity", powerSource.getMaxCapacity());
                    data.put("powerSource." + i + ".name", powerSource.getName());
                    data.put("powerSource." + i + ".powerUsageRate", powerSource.getPowerUsageRate());
                    data.put("powerSource." + i + ".remainingCapacityPercent", powerSource.getRemainingCapacityPercent());
                    data.put("powerSource." + i + ".serialNumber", powerSource.getSerialNumber());
                    data.put("powerSource." + i + ".temperature", powerSource.getTemperature());
                    data.put("powerSource." + i + ".timeRemainingEstimated", powerSource.getTimeRemainingEstimated());
                    data.put("powerSource." + i + ".powerUsageRate", powerSource.getPowerUsageRate());
                    data.put("powerSource." + i + ".timeRemainingInstance", powerSource.getTimeRemainingInstant());
                    data.put("powerSource." + i + ".voltage", powerSource.getVoltage());
                    i++;
                }
            }
            // * sound cards
            boolean soundCards = properties.get("soundCards") != null ? Boolean.parseBoolean(properties.get("soundCards").toString()) : true;
            if (soundCards) {
                int i = 0;
                for (SoundCard soundCard : hardwareAbstractionLayer.getSoundCards()) {
                    data.put("soundCard." + i + ".codec", soundCard.getCodec());
                    data.put("soundCard." + i + ".driverVersion", soundCard.getDriverVersion());
                    data.put("soundCard." + i + ".name", soundCard.getName());
                    i++;
                }
            }
            // * sensors
            boolean sensors = properties.get("sensors") != null ? Boolean.parseBoolean(properties.get("sensors").toString()) : true;
            if (sensors) {
                data.put("sensors.cpuTemperature", hardwareAbstractionLayer.getSensors().getCpuTemperature());
                data.put("sensors.cpuVoltage", hardwareAbstractionLayer.getSensors().getCpuVoltage());
                data.put("sensors.fanSpeeds", Arrays.toString(hardwareAbstractionLayer.getSensors().getFanSpeeds()));
            }
            // * USB
            boolean usbDevices = properties.get("usbDevices") != null ? Boolean.parseBoolean(properties.get("usbDevices").toString()) : true;
            if (usbDevices) {
                int i = 0;
                for (UsbDevice usbDevice : hardwareAbstractionLayer.getUsbDevices(false)) {
                    data.put("usbDevice." + i + ".name", usbDevice.getName());
                    data.put("usbDevice." + i + ".productId", usbDevice.getProductId());
                    data.put("usbDevice." + i + ".serialNumber", usbDevice.getSerialNumber());
                    data.put("usbDevice." + i + ".uniqueDeviceId", usbDevice.getUniqueDeviceId());
                    data.put("usbDevice." + i + ".vendor", usbDevice.getVendor());
                    data.put("usbDevice." + i + ".vendorId", usbDevice.getVendorId());
                    i++;
                }
            }

            // OS
            boolean operatingSystem = properties.get("operatingSystem") != null ? Boolean.parseBoolean(properties.get("operatingSystem").toString()) : true;
            if (operatingSystem) {
                OperatingSystem os = systemInfo.getOperatingSystem();
                data.put("operatingSystem.bitness", os.getBitness());
                data.put("operatingSystem.family", os.getFamily());
                data.put("operatingSystem.manufacturer", os.getManufacturer());
                data.put("operatingSystem.processCount", os.getProcessCount());
                data.put("operatingSystem.processId", os.getProcessId());
                data.put("operatingSystem.systemBootTime", os.getSystemBootTime());
                data.put("operatingSystem.systemUptime", os.getSystemUptime());
                data.put("operatingSystem.threadCount", os.getThreadCount());
                data.put("operatingSystem.versionInfo", os.getVersionInfo());
                data.put("operatingSystem.elevated", os.isElevated());
                data.put("operatingSystem.versionInfo.codeName", os.getVersionInfo().getCodeName());
                data.put("operatingSystem.versionInfo.buildNumber", os.getVersionInfo().getBuildNumber());
                data.put("operatingSystem.versionInfo.version", os.getVersionInfo().getVersion());
                // filesystem
                boolean fileSystems = properties.get("operatingSystem.fileSystems") != null ? Boolean.parseBoolean(properties.get("operatingSystem.fileSystems").toString()) : true;
                if (fileSystems) {
                    data.put("operatingSystem.fileSystem.maxFileDescriptors", os.getFileSystem().getMaxFileDescriptors());
                    data.put("operatingSystem.fileSystem.openFileDescriptors", os.getFileSystem().getOpenFileDescriptors());
                    int i = 0;
                    for (OSFileStore fileStore : os.getFileSystem().getFileStores()) {
                        data.put("operatingSystem.fileSystem." + i + ".description", fileStore.getDescription());
                        data.put("operatingSystem.fileSystem." + i + ".freeInodes", fileStore.getFreeInodes());
                        data.put("operatingSystem.fileSystem." + i + ".freeSpace", fileStore.getFreeSpace());
                        data.put("operatingSystem.fileSystem." + i + ".label", fileStore.getLabel());
                        data.put("operatingSystem.fileSystem." + i + ".logicalVolume", fileStore.getLogicalVolume());
                        data.put("operatingSystem.fileSystem." + i + ".mount", fileStore.getMount());
                        data.put("operatingSystem.fileSystem." + i + ".name", fileStore.getName());
                        data.put("operatingSystem.fileSystem." + i + ".options", fileStore.getOptions());
                        data.put("operatingSystem.fileSystem." + i + ".totalInodes", fileStore.getTotalInodes());
                        data.put("operatingSystem.fileSystem." + i + ".totalSpace", fileStore.getTotalSpace());
                        data.put("operatingSystem.fileSystem." + i + ".type", fileStore.getType());
                        data.put("operatingSystem.fileSystem." + i + ".usableSpace", fileStore.getUsableSpace());
                        data.put("operatingSystem.fileSystem." + i + ".uuid", fileStore.getUUID());
                        data.put("operatingSystem.fileSystem." + i + ".volume", fileStore.getVolume());
                        i++;
                    }
                }
                // network params
                boolean networkParams = properties.get("operatingSystem.networkParams") != null ? Boolean.parseBoolean(properties.get("operatingSystem.networkParams").toString()) : true;
                if (networkParams) {
                    data.put("operatingSystem.networkParams.dnsServers", Arrays.toString(os.getNetworkParams().getDnsServers()));
                    data.put("operatingSystem.networkParams.domainName", os.getNetworkParams().getDomainName());
                    data.put("operatingSystem.networkParams.hostName", os.getNetworkParams().getHostName());
                    data.put("operatingSystem.networkParams.IPv4DefaultGateway", os.getNetworkParams().getIpv4DefaultGateway());
                    data.put("operatingSystem.networkParams.IPv6DefaultGateway", os.getNetworkParams().getIpv6DefaultGateway());
                }
                // processes
                boolean processes = properties.get("operatingSystem.processes") != null ? Boolean.parseBoolean(properties.get("operatingSystem.processes").toString()) : true;
                if (processes) {
                    for (OSProcess process : os.getProcesses()) {
                        int pid = process.getProcessID();
                        data.put("operatingSystem.process." + pid + ".pid", pid);
                        data.put("operatingSystem.process." + pid + ".bitness", process.getBitness());
                        data.put("operatingSystem.process." + pid + ".bytesRead", process.getBytesRead());
                        data.put("operatingSystem.process." + pid + ".bytesWritten", process.getBytesWritten());
                        data.put("operatingSystem.process." + pid + ".commandLine", process.getCommandLine());
                        data.put("operatingSystem.process." + pid + ".currentWorkingDirectory", process.getCurrentWorkingDirectory());
                        data.put("operatingSystem.process." + pid + ".group", process.getGroup());
                        data.put("operatingSystem.process." + pid + ".groupID", process.getGroupID());
                        data.put("operatingSystem.process." + pid + ".kernelTime", process.getKernelTime());
                        data.put("operatingSystem.process." + pid + ".name", process.getName());
                        data.put("operatingSystem.process." + pid + ".openFiles", process.getOpenFiles());
                        data.put("operatingSystem.process." + pid + ".parentProcessID", process.getParentProcessID());
                        data.put("operatingSystem.process." + pid + ".path", process.getPath());
                        data.put("operatingSystem.process." + pid + ".priority", process.getPriority());
                        data.put("operatingSystem.process." + pid + ".processCpuLoadCumulative", process.getProcessCpuLoadCumulative());
                        data.put("operatingSystem.process." + pid + ".residentSetSize", process.getResidentSetSize());
                        data.put("operatingSystem.process." + pid + ".startTime", process.getStartTime());
                        data.put("operatingSystem.process." + pid + ".state", process.getState());
                        data.put("operatingSystem.process." + pid + ".threadCount", process.getThreadCount());
                        data.put("operatingSystem.process." + pid + ".upTime", process.getUpTime());
                        data.put("operatingSystem.process." + pid + ".user", process.getUser());
                        data.put("operatingSystem.process." + pid + ".userID", process.getUserID());
                        data.put("operatingSystem.process." + pid + ".userTime", process.getUserTime());
                        data.put("operatingSystem.process." + pid + ".virtualSize", process.getVirtualSize());
                    }
                }
                // services
                boolean services = properties.get("operatingSystem.services") != null ? Boolean.parseBoolean(properties.get("operatingSystem.services").toString()) : true;
                if (services) {
                    for (OSService service : os.getServices()) {
                        int pid = service.getProcessID();
                        data.put("operatingSystem.service." + pid + ".pid", pid);
                        data.put("operatingSystem.service." + pid + ".name", service.getName());
                        data.put("operatingSystem.service." + pid + ".state", service.getState());
                    }
                }
            }

            PropertiesPreparator.prepare(data, properties);

            dispatcher.postEvent(new Event("decanter/collect/oshi", data));
        } catch (Exception e) {
            LOGGER.warn("Can't get oshi system metrics", e);
        }
    }

    /**
     * Visible for testing.
     */
    public void setDispatcher(EventAdmin dispatcher) {
        this.dispatcher = dispatcher;
    }

}
