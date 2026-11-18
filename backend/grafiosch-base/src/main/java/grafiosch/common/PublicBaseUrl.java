package grafiosch.common;

import java.net.URI;
import java.util.Locale;

/** Validated public backend address, including an optional reverse-proxy path prefix. */
public record PublicBaseUrl(String origin, String host, String prefix) {
  /** Returns null for an unset or unusable address. Incoming HTTP headers never contribute to this address. */
  public static PublicBaseUrl parse(String value) {
    if (value == null || value.isBlank() || "none".equals(value.trim())) {
      return null;
    }
    try {
      URI uri = URI.create(value.trim().replaceAll("/+$", ""));
      String host = uri.getHost();
      if (host == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null
          || uri.getPort() > 65535 || !("https".equals(uri.getScheme())
              || "http".equals(uri.getScheme()) && ("localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host)))) {
        return null;
      }
      return new PublicBaseUrl(uri.getScheme() + "://" + uri.getRawAuthority(), host.toLowerCase(Locale.ROOT),
          uri.getRawPath() == null ? "" : uri.getRawPath());
    } catch (IllegalArgumentException _) {
      return null;
    }
  }

  public boolean isDnsName() {
    return !host.contains(":") && !host.matches("[0-9.]+");
  }

  @Override
  public String toString() {
    return origin + prefix;
  }
}
