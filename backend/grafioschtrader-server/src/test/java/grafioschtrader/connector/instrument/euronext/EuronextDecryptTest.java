package grafioschtrader.connector.instrument.euronext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Decryption of Euronext answers without network access, against a recorded encrypted answer of the chart endpoint
 * (TotalEnergies on Paris, period 1M).
 */
class EuronextDecryptTest {

  private static String fixture(String name) throws IOException {
    try (InputStream in = EuronextDecryptTest.class.getResourceAsStream("/connector/euronext/" + name)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  @Test
  @DisplayName("A recorded CryptoJS envelope decrypts to the JSON array of daily closes")
  void decryptChart() throws IOException {
    String plain = EuronextFeedConnector.decrypt(fixture("chart-1m-encrypted.json"));
    assertTrue(plain.startsWith("["), plain.substring(0, Math.min(40, plain.length())));
    assertTrue(plain.contains("\"price\""));
  }

  @Test
  @DisplayName("An answer that is no envelope passes unchanged; a damaged envelope names the probable cause")
  void plainAndDamaged() {
    assertEquals("[{\"time\":\"2026-10-09\"}]", assertDoesNotThrowDecrypt("[{\"time\":\"2026-10-09\"}]"));
    IOException e = assertThrows(IOException.class,
        () -> EuronextFeedConnector.decrypt("{\"ct\":\"AAAA\",\"iv\":\"00\",\"s\":\"00\"}"));
    assertTrue(e.getMessage().contains("passphrase"));
  }

  private static String assertDoesNotThrowDecrypt(String response) {
    try {
      return EuronextFeedConnector.decrypt(response);
    } catch (IOException e) {
      throw new AssertionError(e);
    }
  }
}
