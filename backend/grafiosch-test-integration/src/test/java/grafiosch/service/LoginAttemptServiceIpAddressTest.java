package grafiosch.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import grafiosch.BaseConstants;

class LoginAttemptServiceIpAddressTest {

  @Test
  @DisplayName("Docker Caddy keeps separate client counters and resets only the successful client")
  void dockerProxyKeepsClientCountersSeparate() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1,172.30.231.2");
    var first = request("172.30.231.2", "198.51.100.20");
    var second = request("172.30.231.2", "198.51.100.21");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(first);
    }
    assertTrue(service.isBlocked(first));
    assertFalse(service.isBlocked(second));
    service.loginSucceeded(second);
    assertTrue(service.isBlocked(first));
    service.loginSucceeded(first);
    assertFalse(service.isBlocked(first));
  }

  @Test
  @DisplayName("Trusting Docker Caddy does not trust other peers on its subnet")
  void otherContainerCannotForgeClientAddresses() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1,172.30.231.2");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("172.30.231.3", "198.51.100." + i));
    }
    assertTrue(service.isBlocked(request("172.30.231.3", "203.0.113.50")));
    assertFalse(service.isBlocked(request("172.30.231.2", "198.51.100.20")));
  }

  @Test
  @DisplayName("A trusted proxy chain through Docker stops at the nearest untrusted client")
  void containerProxyChainIgnoresForgedPrefix() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1,172.30.231.2,192.0.2.10");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("172.30.231.2", "203.0.113." + i + ", 198.51.100.20, 192.0.2.10"));
    }
    assertTrue(service.isBlocked(request("172.30.231.2", "198.51.100.20, 192.0.2.10")));
    assertFalse(service.isBlocked(request("172.30.231.2", "198.51.100.21, 192.0.2.10")));
  }

  @Test
  void directAndAjpClientsCannotEvadeLockoutWithForgedHeaders() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("198.51.100.20", "203.0.113." + i));
    }
    // AJP exposes the client address as remoteAddr too.
    assertTrue(service.isBlocked(request("198.51.100.20", "203.0.113.250")));
    assertFalse(service.isBlocked(request("198.51.100.21", "198.51.100.20")));
  }

  @Test
  void loopbackProxyUsesClientAndSkipsOnlyConfiguredUpstreamProxies() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1,192.0.2.10");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("127.0.0.1", "203.0.113." + i + ", 198.51.100.20, 192.0.2.10"));
    }
    assertTrue(service.isBlocked(request("::1", "198.51.100.20")));
    assertTrue(service.isBlocked(request("192.0.2.10", "198.51.100.20")));
    assertFalse(service.isBlocked(request("127.0.0.1", "198.51.100.21")));
    service.loginSucceeded(request("127.0.0.1", "198.51.100.20"));
    assertFalse(service.isBlocked(request("::1", "198.51.100.20")));
  }

  @Test
  void repeatedHeaderFieldsCannotHideTheAppendedClientAddress() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      var request = request("127.0.0.1", "203.0.113." + i);
      request.addHeader("X-Forwarded-For", "198.51.100.20");
      service.loginFailed(request);
    }
    assertTrue(service.isBlocked(request("::1", "198.51.100.20")));
  }

  @Test
  void privatePeersAreNotImplicitlyTrustedAndTrustCanBeDisabled() {
    for (String trusted : new String[] { "127.0.0.1,::1", "" }) {
      var service = new LoginAttemptServiceIpAddress(trusted);
      for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
        service.loginFailed(request("192.168.1.10", "198.51.100." + i));
      }
      assertTrue(service.isBlocked(request("192.168.1.10", "198.51.100.250")));
    }
    var service = new LoginAttemptServiceIpAddress("");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("127.0.0.1", "198.51.100." + i));
    }
    assertTrue(service.isBlocked(request("127.0.0.1", "198.51.100.250")));
  }

  @Test
  void ipv6SpellingsShareOneCounterAndMalformedHopFallsBackToPeer() {
    var service = new LoginAttemptServiceIpAddress("127.0.0.1,::1");
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("127.0.0.1", "2001:0db8:0:0:0:0:0:1"));
    }
    assertTrue(service.isBlocked(request("::1", "2001:db8::1")));
    for (int i = 0; i < BaseConstants.MAX_LOGIN_ATTEMPT; i++) {
      service.loginFailed(request("127.0.0.1", "198.51.100." + i + ", unknown"));
    }
    assertTrue(service.isBlocked(request("127.0.0.1", null)));
    assertTrue(service.isBlocked(request("127.0.0.1", "198.51.100.20,")));
    assertThrows(IllegalArgumentException.class, () -> new LoginAttemptServiceIpAddress("proxy.example.org"));
  }

  private MockHttpServletRequest request(String peer, String forwarded) {
    var request = new MockHttpServletRequest();
    request.setRemoteAddr(peer);
    if (forwarded != null) {
      request.addHeader("X-Forwarded-For", forwarded);
    }
    return request;
  }
}
