package grafioschtrader;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.apache.coyote.ajp.AjpNioProtocol;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GTNetMessageCode;
import grafioschtrader.gtnet.GTNetMessageCodeType;
import grafioschtrader.test.start.GTforTest;
import jakarta.annotation.PostConstruct;

/// Main entry point of the GrafioschTrader server.
///
/// ## Command line arguments
///
/// There is no declared list of supported arguments, because Spring Boot does not work that way. Every
/// `--key=value` argument passed to [SpringApplication#run(Class,String...)] becomes a property source that takes
/// precedence over `application.properties` / `application.yaml`, so *any* property read anywhere in the application —
/// through `@Value`, `@ConfigurationProperties` or the `Environment` — can be overridden on the command line without
/// being registered anywhere. The same values may equally be passed as JVM system properties (`-Dkey=value`).
///
/// The full set of configurable properties is therefore the union of `application.properties`, `application.yaml` and
/// the profile specific files next to them. The arguments below are the ones most useful when starting the server by
/// hand; they are listed here for discoverability and are not a closed list.
///
/// - `--spring.profiles.active=e2e` — select the profile, which decides the target database. Defaults to `production`
///   and thus the live database, so always set this explicitly when starting against a test database.
/// - `--g.use.gtnet=false` — switch GTNet off completely for this run, regardless of the database parameter
///   `g.gnet.use`. No peer status check on startup, no offline broadcast on shutdown, no GTNet price or history
///   retrieval, and the feature is hidden in the frontend. Intended for development, so the database parameter does
///   not have to be toggled.
/// - `--g.gnet.offline.announce=false` — keep GTNet fully usable but skip the offline broadcast on shutdown. That
///   broadcast contacts every peer sequentially and blocks for up to `g.gnet.connection.timeout` seconds each, which
///   noticeably delays shutdown during development.
/// - `--gt.connector.ajp.enabled` / `--gt.connector.ajp.port` — AJP connector for an Apache2 reverse proxy, enabled by
///   default on port 9090. See [#servletContainer()].
/// - `--gt.connector.http.enabled` / `--gt.connector.http.port` — plain HTTP connector for nginx, disabled by default,
///   port 8080.
///
/// Note that the environment variable `JASYPT_ENCRYPTOR_PASSWORD` must be set before startup, because the encrypted
/// `ENC(...)` properties cannot be decrypted otherwise.
//Spring ehcache is not working,
//@EnableCaching
@EnableScheduling
@SpringBootApplication()
@EnableAsync
@EnableConfigurationProperties
@Configuration
@EntityScan(basePackages = { "grafiosch.entities", "grafioschtrader.entities" })
@ComponentScan(basePackages = { "grafiosch", "grafioschtrader" }, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = GTforTest.class) })
public class GrafioschtraderApplication {

  @Value("${gt.connector.ajp.enabled:true}")
  private boolean ajpEnabled;

  @Value("${gt.connector.ajp.port:9090}")
  private int ajpPort;

  @Value("${gt.connector.http.enabled:false}")
  private boolean httpEnabled;

  @Value("${gt.connector.http.port:8080}")
  private int httpPort;

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GrafioschtraderApplication.class, args);
  }

  /**
   * Configures the GTNetMessage code resolver to handle both core protocol codes (0-54) and application-specific codes
   * (60+). This enables the frontend to display translated message code labels for all message types.
   */
  @PostConstruct
  public void configureGTNetMessageCodeResolver() {
    GTNetMessage.setMessageCodeResolver(value -> {
      GTNetMessageCode code = GTNetMessageCodeType.getMessageCodeByValue(value);
      return code != null ? code.name() : null;
    });
  }

  @Bean
  TomcatServletWebServerFactory servletContainer() throws UnknownHostException {
    final TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();

    // Add AJP connector if enabled (default for Apache2)
    if (ajpEnabled) {
      tomcat.addAdditionalConnectors(this.addAjpConnector(ajpPort));
    }

    // Add HTTP connector if enabled (for nginx)
    if (httpEnabled) {
      tomcat.setPort(httpPort);
    }

    return tomcat;
  }

  @SuppressWarnings("rawtypes")
  private Connector addAjpConnector(Integer port) throws UnknownHostException {
    final Connector ajpConnector = new Connector("AJP/1.3");
    ajpConnector.setPort(port);
    ajpConnector.setSecure(false);
    ajpConnector.setAllowTrace(false);
    ((AbstractAjpProtocol) ajpConnector.getProtocolHandler()).setSecretRequired(false);
    ajpConnector.setScheme("http");

    ProtocolHandler handler = ajpConnector.getProtocolHandler();

    if (handler instanceof AjpNioProtocol) {
      ((AjpNioProtocol) handler).setAddress(InetAddress.getByName("127.0.0.1"));
    }
    return ajpConnector;
  }

}
