package grafiosch.common;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class NetworkHelper {
  
  public static String getIpAddressToOutside() {
    try (Socket socket = new Socket()) {
      socket.connect(new InetSocketAddress("google.com", 80));
      return socket.getLocalAddress().getHostAddress();
    } catch (Exception e) {
      try {
        return InetAddress.getLocalHost().getHostAddress();
      } catch (UnknownHostException e1) {
        return "Could not determine IP address";
      }
    }
  }
}
