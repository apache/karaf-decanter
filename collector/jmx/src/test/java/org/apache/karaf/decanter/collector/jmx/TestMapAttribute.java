package org.apache.karaf.decanter.collector.jmx;

import java.lang.management.ManagementFactory;
import java.util.*;

import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.osgi.service.component.ComponentContext;

public class TestMapAttribute {

    @Test
    public void testOperatingSystemMBean() throws MalformedObjectNameException, Exception {
        MBeanServerConnection server = ManagementFactory.getPlatformMBeanServer();
        BeanHarvester harvester = new BeanHarvester(server, "local", "local", "localhost");
        Map<String, Object> data = harvester.harvestBean(new ObjectName("java.lang:type=OperatingSystem"));
        Assert.assertTrue(data.size() >= 15);
        Object freeMem = data.get("FreePhysicalMemorySize");
        Assert.assertTrue(freeMem != null);
        Assert.assertTrue(freeMem instanceof Long);
        Assert.assertTrue((Long) freeMem > 10000);
        System.out.println(data);
    }

    @Test
    public void testSingleObjectName() throws Exception {
        ComponentContext ctx = Mockito.mock(ComponentContext.class);
        Dictionary props = new Hashtable<>();
        Mockito.when(ctx.getProperties()).thenReturn(props);

        props.put("object.name", "java.lang:*");
        JmxCollector collector = new JmxCollector();
        Assert.assertNull(collector.getObjectNames());
        collector.activate(ctx);

        Set<String> objectNames = collector.getObjectNames();
        Assert.assertEquals(1, objectNames.size());
        Assert.assertEquals("java.lang:*", objectNames.iterator().next());
    }

    @Test
    public void testSeveralObjectNames() throws Exception {
        ComponentContext ctx = Mockito.mock(ComponentContext.class);
        Dictionary props = new Hashtable();
        Mockito.when(ctx.getProperties()).thenReturn(props);

        props.put("object.name.system", "java.lang:*");
        props.put("object.name", "org.something.else:*");
        props.put("object.name.karaf", "org.apache.karaf:type=http");
        props.put("object.name.2", "whatever");
        props.put("object.name-invalid", "not expected");

        JmxCollector collector = new JmxCollector();
        Assert.assertNull(collector.getObjectNames());
        collector.activate(ctx);

        List<String> objectNames = new ArrayList<>(collector.getObjectNames());
        Collections.sort(objectNames);
        Assert.assertEquals(4, objectNames.size());
        Assert.assertEquals("java.lang:*", objectNames.get(0));
        Assert.assertEquals("org.apache.karaf:type=http", objectNames.get(1));
        Assert.assertEquals("org.something.else:*", objectNames.get(2));
        Assert.assertEquals("whatever", objectNames.get(3));
    }
}
