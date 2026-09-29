package grafiosch;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication()
@EnableAsync
@EnableConfigurationProperties
@Configuration
@EntityScan(basePackages = { "grafiosch.entities", "grafiosch.integration.entities" })
@ComponentScan(basePackages = { "grafiosch" })
public class GrafioschApplication {

  static {
    // Spring tests also create this application without invoking main().
    TimeZone.setDefault(TimeZone.getTimeZone(BaseConstants.TIME_ZONE));
  }

  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GrafioschApplication.class, args);
  }
}
