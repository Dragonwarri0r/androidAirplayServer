package com.github.serezhka.airplay.lib;


import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.util.logging.Logger;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Registers airplay/airtunes service mdns
 */
public class AirPlayBonjour {
    private static final Logger log = Logger.getLogger(AirPlayBonjour.class.getName());


    private static final String AIRPLAY_SERVICE_TYPE = "._airplay._tcp.local";
    private static final String AIRTUNES_SERVICE_TYPE = "._raop._tcp.local";

    private final String serverName;

    private final List<JmDNS> jmDNSList = new ArrayList<>();
    
    public AirPlayBonjour(String serverName) {
        this.serverName = serverName;
    }

    public void start(int airTunesPort) throws Exception {
        // 使用Java 8兼容的方式获取网络接口
        try {
            java.util.Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                
                // 应用网络接口过滤器
                if (!networkInterfaceFilter().test(networkInterface)) {
                    continue;
                }
                
                // 获取网络接口的所有IP地址
                java.util.Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
                while (inetAddresses.hasMoreElements()) {
                    InetAddress inetAddress = inetAddresses.nextElement();
                    
                    // 应用IP地址过滤器
                    if (!inetAddressFilter().test(inetAddress)) {
                        continue;
                    }
                    
                    try {
                        byte[] hardwareAddress = NetworkInterface.getByInetAddress(inetAddress).getHardwareAddress();
                        String mac = hardwareAddressBytesToString(hardwareAddress);

                        JmDNS jmDNS = JmDNS.create(inetAddress);
                        jmDNS.registerService(ServiceInfo.create(serverName + AIRPLAY_SERVICE_TYPE,
                                serverName, airTunesPort, 0, 0, airPlayMDNSProps(mac)));
                        log.info(serverName + AIRPLAY_SERVICE_TYPE + " service is registered on address " + inetAddress.getHostAddress() + ", port " + airTunesPort);

                        String airTunesServerName = mac.replaceAll(":", "") + "@" + serverName;
                        jmDNS.registerService(ServiceInfo.create(airTunesServerName + AIRTUNES_SERVICE_TYPE,
                                airTunesServerName, airTunesPort, 0, 0, airTunesMDNSProps()));
                        log.info(airTunesServerName + AIRTUNES_SERVICE_TYPE + " service is registered on address " + inetAddress.getHostAddress() + ", port " + airTunesPort);

                        jmDNSList.add(jmDNS);
                    } catch (IOException e) {
                        log.severe("Error creating JmDNS service: " + e.getMessage());
                    }
                }
            }
        } catch (SocketException e) {
            log.severe("Error getting network interfaces: " + e.getMessage());
            throw new Exception("Failed to start AirPlay Bonjour service", e);
        }
    }

    public void stop() {
        for (final JmDNS jmDNS : jmDNSList) {
            jmDNS.unregisterAllServices();
        }
    }

    private Map<String, String> airPlayMDNSProps(String deviceId) {
        HashMap<String, String> airPlayMDNSProps = new HashMap<>();
        airPlayMDNSProps.put("deviceid", deviceId);
        airPlayMDNSProps.put("features", "0x5A7FFFF7,0x1E"); // 0x5A7FFFF7 E4
        airPlayMDNSProps.put("srcvers", "220.68");
        airPlayMDNSProps.put("flags", "0x44");
        airPlayMDNSProps.put("vv", "2");
        airPlayMDNSProps.put("model", "AppleTV3,2C");
        airPlayMDNSProps.put("rhd", "5.6.0.0");
        airPlayMDNSProps.put("pw", "false");
        airPlayMDNSProps.put("pk", "f3769a660475d27b4f6040381d784645e13e21c53e6d2da6a8c3d757086fc336");
        //airPlayMDNSProps.put("pi", "2e388006-13ba-4041-9a67-25dd4a43d536");
        airPlayMDNSProps.put("rmodel", "PC1.0");
        airPlayMDNSProps.put("rrv", "1.01");
        airPlayMDNSProps.put("rsv", "1.00");
        airPlayMDNSProps.put("pcversion", "1715");
        return airPlayMDNSProps;
    }

    private Map<String, String> airTunesMDNSProps() {
        HashMap<String, String> airTunesMDNSProps = new HashMap<>();
        airTunesMDNSProps.put("ch", "2");
        airTunesMDNSProps.put("cn", "1,3");
        airTunesMDNSProps.put("da", "true");
        airTunesMDNSProps.put("et", "0,3,5");
        airTunesMDNSProps.put("ek", "1");
        //airTunesMDNSProps.put("vv", "2");
        airTunesMDNSProps.put("ft", "0x5A7FFFF7,0x1E");
        airTunesMDNSProps.put("am", "AppleTV3,2C");
        airTunesMDNSProps.put("md", "0,1,2");
        //airTunesMDNSProps.put("rhd", "5.6.0.0");
        //airTunesMDNSProps.put("pw", "false");
        airTunesMDNSProps.put("sr", "44100");
        airTunesMDNSProps.put("ss", "16");
        airTunesMDNSProps.put("sv", "false");
        airTunesMDNSProps.put("sm", "false");
        airTunesMDNSProps.put("tp", "UDP");
        airTunesMDNSProps.put("txtvers", "1");
        airTunesMDNSProps.put("sf", "0x44");
        airTunesMDNSProps.put("vs", "220.68");
        airTunesMDNSProps.put("vn", "65537");
        airTunesMDNSProps.put("pk", "f3769a660475d27b4f6040381d784645e13e21c53e6d2da6a8c3d757086fc336");
        return airTunesMDNSProps;
    }

    private Predicate<NetworkInterface> networkInterfaceFilter() {
        return networkInterface -> {
            try {
                return /*!networkInterface.isLoopback() &&*/ !networkInterface.isPointToPoint() && networkInterface.isUp();
            } catch (SocketException e) {
                return false;
            }
        };
    }

    private Predicate<InetAddress> inetAddressFilter() {
        return inetAddress -> inetAddress instanceof Inet4Address /*|| inetAddress instanceof Inet6Address*/;
    }

    private String hardwareAddressBytesToString(byte[] mac) {
        if (mac == null) {
            return "00:00:00:00:00:00"; // loopback
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? ":" : ""));
        }
        return sb.toString().toUpperCase();
    }
}
