package grafiosch.service;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.StandardEnvironment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import grafiosch.BaseConstants;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Disposable Docker acceptance fixture using the real login counters and Boot environment binding. Starts only an HTTP
 * adapter, never a Spring application context, database or authentication endpoint.
 */
public class LoginProxyCheckServer {
  public static void main(String[] args) throws Exception {
    if (!Files.exists(Path.of("/.dockerenv")) || !"yes".equals(System.getenv("GT_LOGIN_PROXY_TEST"))) {
      throw new IllegalStateException("Only run inside the disposable login-proxy test container");
    }
    var environment = new StandardEnvironment();
    ConfigurationPropertySources.attach(environment);
    var service = new LoginAttemptServiceIpAddress(environment.getRequiredProperty("g.security.login.trusted-proxies"));
    var server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.createContext("/api/", exchange -> {
      var request = request(exchange);
      switch (exchange.getRequestURI().getPath()) {
      case "/api/fail" -> service.loginFailed(request);
      case "/api/succeed" -> service.loginSucceeded(request);
      default -> {
      }
      }
      String result = service.isBlocked(request) ? "blocked" : "allowed";
      byte[] response = (result + ":" + BaseConstants.MAX_LOGIN_ATTEMPT).getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      try (var output = exchange.getResponseBody()) {
        output.write(response);
      }
    });
    server.start();
  }

  private static HttpServletRequest request(HttpExchange exchange) {
    return (HttpServletRequest) Proxy.newProxyInstance(HttpServletRequest.class.getClassLoader(),
        new Class<?>[] { HttpServletRequest.class }, (_, method, args) -> switch (method.getName()) {
        case "getRemoteAddr" -> exchange.getRemoteAddress().getAddress().getHostAddress();
        case "getHeaders" -> Collections
            .enumeration(exchange.getRequestHeaders().getOrDefault((String) args[0], List.of()));
        default -> throw new UnsupportedOperationException(method.getName());
        });
  }
}
