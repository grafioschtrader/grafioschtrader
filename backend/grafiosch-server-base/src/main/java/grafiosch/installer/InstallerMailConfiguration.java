package grafiosch.installer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.Environment;

import com.ulisesbocchio.jasyptspringboot.environment.StandardEncryptableEnvironment;

/**
 * Reads mail configuration from the deployed application's class path and working directory. Invoked explicitly through
 * Boot's PropertiesLauncher; never registered as an application bean. No application context, database or server
 * starts.
 */
public final class InstallerMailConfiguration {
  private InstallerMailConfiguration() {
  }

  /**
   * The decryption key arrives on stdin; the sole argument names a new file in an installer-owned private directory.
   */
  public static void main(String[] args) {
    try {
      if (args.length != 1) {
        throw new IllegalArgumentException();
      }
      String key = new String(System.in.readNBytes(65537), StandardCharsets.UTF_8);
      if (key.isEmpty() || key.length() > 65536 || key.indexOf('\0') >= 0) {
        throw new IllegalArgumentException();
      }
      System.setProperty("jasypt.encryptor.password", key);
      var environment = new StandardEncryptableEnvironment();
      ConfigDataEnvironmentPostProcessor.applyTo(environment);
      List<String> values = resolve(environment);
      Files.writeString(Path.of(args[0]), String.join("\0", values) + "\0", StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    } catch (Exception _) {
      // Property/decryption exceptions can contain secrets. The installer captures other diagnostics privately.
      System.err.println("Application mail configuration could not be resolved; details suppressed.");
      System.exit(2);
    } finally {
      System.clearProperty("jasypt.encryptor.password");
    }
  }

  static List<String> resolve(Environment environment) {
    String host = required(environment, "spring.mail.host");
    String port = required(environment, "spring.mail.port");
    String user = required(environment, "spring.mail.username");
    String password = environment.getRequiredProperty("spring.mail.password");
    boolean auth = flag(environment, "auth");
    boolean starttls = flag(environment, "starttls.enable");
    boolean required = flag(environment, "starttls.required");
    boolean tls = flag(environment, "ssl.enable");
    int number = Integer.parseInt(port);
    if (number < 1 || number > 65535 || starttls != required || starttls && tls || auth && !starttls && !tls
        || auth && password.isEmpty() || password.startsWith("ENC(") || password.indexOf('\0') >= 0) {
      throw new IllegalArgumentException();
    }
    return List.of(host, port, user, password, auth ? "yes" : "no", tls ? "tls" : starttls ? "starttls" : "none",
        required(environment, "g.main.user.admin.mail"));
  }

  private static String required(Environment environment, String key) {
    String value = environment.getRequiredProperty(key);
    if (value.isBlank() || value.indexOf('\0') >= 0) {
      throw new IllegalArgumentException();
    }
    return value;
  }

  private static boolean flag(Environment environment, String suffix) {
    String value = required(environment, "spring.mail.properties.mail.smtp." + suffix);
    if (!value.equals("true") && !value.equals("false")) {
      throw new IllegalArgumentException();
    }
    return Boolean.parseBoolean(value);
  }
}
