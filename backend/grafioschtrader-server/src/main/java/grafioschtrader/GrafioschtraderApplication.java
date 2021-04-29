package grafioschtrader;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.coyote.ajp.AbstractAjpProtocol;
import org.apache.coyote.ajp.AjpNioProtocol;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication()
@EnableAsync
@EnableScheduling
@EntityScan(basePackages = { "grafioschtrader.entities" })
// Spring ehcache is not working, 
// @EnableCaching
public class GrafioschtraderApplication {

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GrafioschtraderApplication.class, args);
  }

  @Bean
  public TomcatServletWebServerFactory servletContainer() throws UnknownHostException {
    final TomcatServletWebServerFactory tomcat = new TomcatServletWebServerFactory();
    tomcat.addAdditionalTomcatConnectors(this.addTomcatConnector(9090));
    return tomcat;
  }

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
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

}
