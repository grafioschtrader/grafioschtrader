package grafioschtrader.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * No application sender may stamp its envelope with the local clock.
 *
 * <p>
 * The payload exchanges build their own {@code MessageEnvelope} instead of deriving it from a stored message, so each
 * one of them assigns the timestamp itself. The receiver reads it as UTC and refuses anything further away than
 * {@code g.gnet.max.clock.skew.minutes}; a sender on a Central European host was two hours out and had every request
 * refused. The counterpart of this guard in {@code grafiosch-server-base} covers the library senders.
 * </p>
 */
class GTNetUtcClockGuardTest {

  @Test
  void noSenderStampsAnEnvelopeWithTheLocalClock() throws IOException {
    Path main = Path.of("src", "main", "java", "grafioschtrader");
    assertThat(Files.isDirectory(main)).as("the guard must actually see the sources at %s", main.toAbsolutePath())
        .isTrue();

    List<String> offenders = new ArrayList<>();
    try (Stream<Path> walk = Files.walk(main)) {
      for (Path file : walk.filter(path -> path.toString().endsWith(".java")).toList()) {
        List<String> lines = Files.readAllLines(file);
        for (int i = 0; i < lines.size(); i++) {
          String code = lines.get(i).trim();
          if (code.contains(".timestamp = ") && code.contains("LocalDateTime.now(")) {
            offenders.add(file.getFileName() + ":" + (i + 1) + " " + code);
          }
        }
      }
    }
    assertThat(offenders).as("use GTNetTime.now() so the wire timestamp stays UTC").isEmpty();
  }
}
