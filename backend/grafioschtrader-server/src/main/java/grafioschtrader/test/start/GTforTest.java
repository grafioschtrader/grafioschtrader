package grafioschtrader.test.start;

import java.util.TimeZone;

import javax.annotation.PostConstruct;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

import grafioschtrader.GrafioschtraderApplication;
/**
 * With tests use a different application context without a tomcat reconfiguration.
 * 
 * 
 * @author Hugo Graf
 *
 */
@SpringBootApplication()
@EnableAsync
@EntityScan(basePackages = { "grafioschtrader.entities" })
@ComponentScan(basePackages = { "grafioschtrader" }, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = GrafioschtraderApplication.class) })

public class GTforTest {

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GTforTest.class, args);
  }

  @PostConstruct
  void started() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
  }

}
