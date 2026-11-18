package grafiosch.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PublicBaseUrlTest {
  @Test
  @DisplayName("Public addresses retain proxy prefixes and ports, and normalize trailing slashes")
  void addresses() {
    var base = PublicBaseUrl.parse(" https://example.org:8443/gt/// ");
    assertEquals("https://example.org:8443", base.origin());
    assertEquals("example.org", base.host());
    assertEquals("/gt", base.prefix());
    assertEquals("https://example.org:8443/gt", base.toString());
    assertTrue(base.isDnsName());
    assertNotNull(PublicBaseUrl.parse("http://localhost:8081"));
    assertFalse(PublicBaseUrl.parse("https://127.0.0.1").isDnsName());
    assertFalse(PublicBaseUrl.parse("https://[::1]").isDnsName());
  }

  @Test
  @DisplayName("Unconfigured, insecure, malformed and requester-controlled authority values are unusable")
  void refusedAddresses() {
    for (String value : new String[] { null, "", "none", "https:///", "http://example.org", "ftp://example.org",
        "https://user:password@example.org", "https://example.org?x=1", "https://example.org#token",
        "https://example.org:99999", "//example.org", "https://exa mple.org" }) {
      assertNull(PublicBaseUrl.parse(value), value);
    }
  }
}
