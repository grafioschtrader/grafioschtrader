package grafioschtrader.test.start;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.scheduling.annotation.EnableAsync;

import grafiosch.BaseConstants;
import grafioschtrader.GrafioschtraderApplication;
import jakarta.annotation.PostConstruct;

/**
 * With tests use a different application context without a tomcat reconfiguration.
 *
 */
@SpringBootApplication()
@EnableAsync
@EntityScan(basePackages = { "grafiosch.entities", "grafioschtrader.entities" })
@ComponentScan(basePackages = { "grafiosch", "grafioschtrader" }, excludeFilters = {
    @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = GrafioschtraderApplication.class) })

// Since Spring Boot 3.2 it requires and read this properties
// @PropertySource("classpath:application-test.properties")
@EnableConfigurationProperties

public class GTforTest {

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GTforTest.class, args);
  }

  @PostConstruct
  void started() {
    TimeZone.setDefault(TimeZone.getTimeZone(BaseConstants.TIME_ZONE));
  }

}
