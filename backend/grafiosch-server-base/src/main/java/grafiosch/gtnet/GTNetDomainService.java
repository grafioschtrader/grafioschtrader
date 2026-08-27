package grafiosch.gtnet;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Canonicalizes and validates the domain a GTNet peer identifies itself with.
 *
 * <p>
 * A peer is addressed by {@code GTNet.domainRemoteName}, and the same string arrives on the wire as
 * {@code MessageEnvelope.sourceDomain}. Both are free text supplied by the caller, so before either can be used as an
 * identity they must be reduced to one comparable form. Without that, {@code https://Peer.Example:443/} and
 * {@code https://peer.example} are two different peers to an exact-match lookup, and the first handshake can be talked
 * into acting on a relationship that belongs to somebody else.
 * </p>
 *
 * <p>
 * The value is also the target of later outbound calls in {@code BaseDataClient.getWebClientForDomain}, which is why
 * a malformed or hostless value is refused outright rather than stored.
 * </p>
 */
@Component
public class GTNetDomainService {

  /**
   * Whether a peer may identify itself with a plain {@code http} URL or an address in a loopback, link-local or private
   * range. Deployments where the peers are machines on the same LAN need this; an instance exposed to the public
   * network should turn it off, so that a peer identity is always a routable {@code https} address.
   */
  @Value("${g.gnet.allow.private.peers:true}")
  private boolean allowPrivatePeers;

  /**
   * Reduces a peer domain to its comparable form: lower-case scheme and host, the default port of the scheme removed,
   * and no trailing slash. Everything else is kept as it is, because a peer that publishes a path prefix is addressed
   * through it.
   *
   * @param domain the domain as it arrived, may be null or blank
   * @return the canonical form, or null when the value is not an absolute URL with a host
   */
  public String canonicalize(String domain) {
    if (domain == null || domain.isBlank()) {
      return null;
    }
    try {
      URI uri = new URI(domain.trim());
      if (!uri.isAbsolute() || uri.getHost() == null || uri.getScheme() == null) {
        return null;
      }
      String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
      StringBuilder canonical = new StringBuilder(scheme).append("://")
          .append(uri.getHost().toLowerCase(Locale.ROOT));
      if (uri.getPort() != -1 && uri.getPort() != defaultPortOf(scheme)) {
        canonical.append(':').append(uri.getPort());
      }
      String path = uri.getRawPath() == null ? "" : uri.getRawPath();
      while (path.endsWith("/")) {
        path = path.substring(0, path.length() - 1);
      }
      return canonical.append(path).toString();
    } catch (URISyntaxException e) {
      return null;
    }
  }

  /**
   * Whether two domains name the same peer. Both are canonicalized first; a value that cannot be canonicalized never
   * equals anything, so a malformed domain cannot be argued into matching a well-formed one.
   *
   * @param first  the first domain
   * @param second the second domain
   * @return true when both canonicalize to the same non-null value
   */
  public boolean isSameDomain(String first, String second) {
    String canonicalFirst = canonicalize(first);
    return canonicalFirst != null && canonicalFirst.equals(canonicalize(second));
  }

  /**
   * Whether a canonical domain may be stored as a peer identity. Rejects a scheme other than {@code http} / {@code
   * https}, and — unless {@code g.gnet.allow.private.peers} is set — a plain {@code http} address, an unresolvable
   * host, and any host that resolves into a loopback, link-local or private range.
   *
   * @param canonicalDomain a domain already put through {@link #canonicalize(String)}
   * @return true when the domain is acceptable as a peer identity
   */
  public boolean isAcceptablePeerDomain(String canonicalDomain) {
    if (canonicalDomain == null) {
      return false;
    }
    String scheme = canonicalDomain.substring(0, Math.max(canonicalDomain.indexOf(':'), 0))
        .toLowerCase(Locale.ROOT);
    if (!"http".equals(scheme) && !"https".equals(scheme)) {
      return false;
    }
    if (allowPrivatePeers) {
      return true;
    }
    if (!"https".equals(scheme)) {
      return false;
    }
    return resolvesToRoutableAddress(canonicalDomain);
  }

  /**
   * Resolves the host and requires every returned address to be outside the loopback, link-local and private ranges.
   * Only reached when private peers are disallowed, so the DNS lookup is not on the path of a LAN deployment.
   *
   * @param canonicalDomain the canonical domain whose host is resolved
   * @return true when the host resolves and no address is loopback, link-local or site-local
   */
  private boolean resolvesToRoutableAddress(String canonicalDomain) {
    try {
      String host = new URI(canonicalDomain).getHost();
      for (InetAddress address : InetAddress.getAllByName(host)) {
        if (address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()
            || address.isAnyLocalAddress()) {
          return false;
        }
      }
      return true;
    } catch (URISyntaxException | UnknownHostException e) {
      return false;
    }
  }

  private static int defaultPortOf(String scheme) {
    return switch (scheme) {
    case "http" -> 80;
    case "https" -> 443;
    default -> -1;
    };
  }
}
