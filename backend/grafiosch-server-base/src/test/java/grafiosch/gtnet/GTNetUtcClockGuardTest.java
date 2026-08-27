package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The GTNet message path has one clock, and it is UTC.
 *
 * <p>
 * {@code MessageEnvelope.timestamp} is defined as a UTC instant and the receiver enforces it against
 * {@code g.gnet.max.clock.skew.minutes}, five minutes by default. A sender stamping its own zone is therefore refused
 * outright on any host that is not running in UTC — and that is not a hypothetical: the senders stamped local time
 * while the validator compared against UTC, which put every envelope two hours in the future in Central European summer
 * time. {@link GTNetTime} is the only clock those files may read; everything that is not the protocol keeps the local
 * one and is listed below with its reason.
 * </p>
 */
class GTNetUtcClockGuardTest {

  /** Files in the scanned tree that legitimately read the local clock, because what they time is not the protocol. */
  private static final Set<String> LOCAL_CLOCK_ALLOWED = Set.of(
      // when a start-up task becomes due
      "GTNetLifecycleListener.java",
      // whether a maintenance window is open right now, which is a local schedule
      "GTNetStatusCheckService.java");

  @Test
  void noGtNetSourceReadsTheLocalClock() throws IOException {
    List<String> offenders = new ArrayList<>();
    for (Path file : gtNetSources()) {
      if (LOCAL_CLOCK_ALLOWED.contains(file.getFileName().toString())) {
        continue;
      }
      List<String> lines = Files.readAllLines(file);
      for (int i = 0; i < lines.size(); i++) {
        String code = lines.get(i).trim();
        if (code.startsWith("*") || code.startsWith("//")) {
          continue;
        }
        if (code.contains("LocalDateTime.now(")) {
          offenders.add(file.getFileName() + ":" + (i + 1) + " " + code);
        }
      }
    }
    assertThat(offenders).as("use GTNetTime.now() so the wire timestamp stays UTC").isEmpty();
  }

  private static List<Path> gtNetSources() throws IOException {
    Path main = Path.of("src", "main", "java", "grafiosch");
    assertThat(Files.isDirectory(main)).as("the guard must actually see the sources at %s", main.toAbsolutePath())
        .isTrue();
    List<Path> files = new ArrayList<>();
    for (Path root : List.of(main.resolve("gtnet"), main.resolve("m2m"))) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(path -> path.toString().endsWith(".java")).forEach(files::add);
      }
    }
    for (Path root : List.of(main.resolve("repository"), main.resolve("task").resolve("exec"))) {
      try (Stream<Path> walk = Files.walk(root)) {
        walk.filter(path -> path.getFileName().toString().matches("(GTNet|GNet).*[.]java")).forEach(files::add);
      }
    }
    assertThat(files).hasSizeGreaterThan(20);
    return files;
  }
}
