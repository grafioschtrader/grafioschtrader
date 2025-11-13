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
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import grafioschtrader.test.start.GTforTest;

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

  @Bean
  TomcatServletWebServerFactory servletContainer() throws UnknownHostException {
    final TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();

    // Add AJP connector if enabled (default for Apache2)
    if (ajpEnabled) {
      tomcat.addAdditionalTomcatConnectors(this.addAjpConnector(ajpPort));
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
