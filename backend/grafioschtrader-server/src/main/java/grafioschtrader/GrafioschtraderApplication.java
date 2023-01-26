package grafioschtrader;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.apache.coyote.ajp.AjpNioProtocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import grafioschtrader.test.start.GTforTest;
import jakarta.annotation.PostConstruct;

@SpringBootApplication()
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties
// Spring ehcache is not working,
// @EnableCaching
@Configuration
@ComponentScan(basePackages = { "grafioschtrader" }, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = GTforTest.class) })
public class GrafioschtraderApplication {

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GrafioschtraderApplication.class, args);
  }

  @Bean
  TomcatServletWebServerFactory servletContainer() throws UnknownHostException {
    final TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
    tomcat.addAdditionalTomcatConnectors(this.addTomcatConnector(9090));
    return tomcat;
  }

  @SuppressWarnings("rawtypes")
  private Connector addTomcatConnector(Integer port) throws UnknownHostException {
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

  @PostConstruct
  void started() {
    TimeZone.setDefault(TimeZone.getTimeZone(GlobalConstants.TIME_ZONE));
  }

}
