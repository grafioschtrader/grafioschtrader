package grafiosch.installer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.MapPropertySource;

import com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment;

/**
 * Configuration-only tests: deliberately creates no Spring application context and never contacts a database or SMTP.
 */
class InstallerMailConfigurationTest {
  @TempDir
  Path directory;

  private StandardEncryptableEnvironment environment(String ciphertext, String key, String extra) throws Exception {
    Files.writeString(directory.resolve("application.properties"), """
        spring.profiles.active=production
        spring.mail.host=wrong.example.test
        spring.mail.port=587
        spring.mail.username=sender@example.test
        spring.mail.password=%s
        spring.mail.properties.mail.smtp.auth=true
        spring.mail.properties.mail.smtp.starttls.enable=true
        spring.mail.properties.mail.smtp.ssl.enable=false
        g.main.user.admin.mail=admin@example.test
        """.formatted(ciphertext));
    Files.writeString(directory.resolve("application-production.properties"), """
        spring.mail.host=smtp.example.test
        spring.mail.properties.mail.smtp.starttls.required=true
        """ + extra);
    var environment = new StandardEncryptableEnvironment();
    environment.getPropertySources()
        .addFirst(new MapPropertySource("fixture",
            Map.of("spring.config.location", directory.toUri().toString(), "jasypt.encryptor.password", key,
                "jasypt.encryptor.algorithm", "PBEWithMD5AndDES", "jasypt.encryptor.iv-generator-classname",
                "org.jasypt.iv.NoIvGenerator")));
    ConfigDataEnvironmentPostProcessor.applyTo(environment);
    return environment;
  }

  private String encrypted(String text) {
    var encryptor = new StandardPBEStringEncryptor();
    encryptor.setPassword("fixture-key");
    encryptor.setAlgorithm("PBEWithMD5AndDES");
    return "ENC(" + encryptor.encrypt(text) + ")";
  }

  @Test
  @DisplayName("Production overrides and the application's Jasypt settings resolve literal mail credentials")
  void productionAndDecryption() throws Exception {
    String password = "  fixture $`\\! = Grüsse 密码  ";
    var values = InstallerMailConfiguration.resolve(environment(encrypted(password), "fixture-key", ""));
    assertEquals("smtp.example.test", values.get(0));
    assertEquals(password, values.get(3));
    assertEquals("starttls", values.get(5));
    assertEquals("admin@example.test", values.get(6));
  }

  @Test
  @DisplayName("Wrong encryption key or malformed ciphertext fails before SMTP")
  void invalidEncryption() throws Exception {
    var wrongKey = environment(encrypted("smtp-accepted-password"), "wrong-key", "");
    assertThrows(RuntimeException.class, () -> InstallerMailConfiguration.resolve(wrongKey));
    var malformed = environment("ENC(invalid)", "fixture-key", "");
    assertThrows(RuntimeException.class, () -> InstallerMailConfiguration.resolve(malformed));
  }

  @Test
  @DisplayName("Dropped properties and inconsistent transport flags cannot earn a mail milestone")
  void invalidProperties() throws Exception {
    var both = environment(encrypted("password"), "fixture-key", "spring.mail.properties.mail.smtp.ssl.enable=true\n");
    assertThrows(IllegalArgumentException.class, () -> InstallerMailConfiguration.resolve(both));
    var missing = environment(encrypted("password"), "fixture-key", "spring.mail.host=\n");
    assertThrows(IllegalArgumentException.class, () -> InstallerMailConfiguration.resolve(missing));
    var downgrade = environment(encrypted("password"), "fixture-key",
        "spring.mail.properties.mail.smtp.starttls.required=false\n");
    assertThrows(IllegalArgumentException.class, () -> InstallerMailConfiguration.resolve(downgrade));
  }
}
