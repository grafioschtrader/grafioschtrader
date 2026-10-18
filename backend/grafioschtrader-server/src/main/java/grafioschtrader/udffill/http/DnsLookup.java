package grafioschtrader.udffill.http;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Resolves a host name to all its addresses. Injected into {@link UDFFillHttp}, so that tests run without network.
 */
@FunctionalInterface
public interface DnsLookup {

  /**
   * Returns every address of the host.
   *
   * @param host a host name, never an IP literal
   * @return all addresses, never empty
   * @throws UnknownHostException when the name does not resolve
   */
  InetAddress[] lookup(String host) throws UnknownHostException;
}
