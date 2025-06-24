package grafiosch;


import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
  
  public static void main(final String[] args) {
    // ApplicationContext context =
    SpringApplication.run(GrafioschApplication.class, args);
  }
}
