package org.apache.karaf.decanter.collector.log.socket;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.Random;

import javax.net.ServerSocketFactory;

/**
 * Utility class to determine if TCP port is available on localhost.
 * 
 * @author agonzalez
 */
public final class SocketUtils {

    private static final Random random = new Random();
    public static final int MIN_PORT = 40000;
    public static final int MAX_PORT = 65535;

    /**
     * Returns the first available port between {@link #MIN_PORT} and
     * {@link #MAX_PORT}
     */
    public static int findAvailablePort() {
        int port;
        do {
            port = findRandomPort(MIN_PORT, MAX_PORT);

        } while (!isPortAvailable(port));
        return port;
    }

    private static boolean isPortAvailable(int port) {
        try {
            ServerSocket serverSocker = ServerSocketFactory.getDefault().createServerSocket(port, 1,
                    InetAddress.getByName("localhost"));
            serverSocker.close();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static int findRandomPort(int minPort, int maxPort) {
        if (maxPort < minPort) {
            throw new IllegalArgumentException("maxPort should be >= minPort");
        }
        int portRange = maxPort - minPort + 1;
        return random.nextInt(portRange) + minPort;
    }
}
