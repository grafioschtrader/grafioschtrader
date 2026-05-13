package grafiosch.schemagen;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Profile("schemagen")
@Component
public class SchemaGenExitRunner implements CommandLineRunner {

  @Autowired
  private ConfigurableApplicationContext ctx;

  @Override
  public void run(final String... args) {
    System.exit(SpringApplication.exit(ctx, () -> 0));
  }
}
