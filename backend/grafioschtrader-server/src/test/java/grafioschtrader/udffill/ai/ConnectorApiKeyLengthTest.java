package grafioschtrader.udffill.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.entities.ConnectorApiKey;
import grafiosch.repository.ConnectorApiKeyJpaRepository;
import grafioschtrader.rest.GTIntegrationTestContext;
import jakarta.validation.Validator;

/**
 * A key as long as the input field admits must survive encryption and the column. An OpenAI project key of about 164
 * characters was once cut to 64 by the input field, and encrypted it would not have fitted the former 255 characters
 * either; OpenAI then answered 401 for a key the administrator had entered correctly.
 */
@GTIntegrationTestContext
class ConnectorApiKeyLengthTest {

  /** Same as the maximum length of the input field of the connector API key. */
  private static final int MAX_INPUT_LENGTH = 256;

  @Autowired
  private ConnectorApiKeyJpaRepository connectorApiKeyJpaRepository;
  @Autowired
  private JdbcTemplate jdbc;
  @Autowired
  private PlatformTransactionManager transactionManager;
  @Autowired
  private Validator validator;

  @Test
  @DisplayName("A key of the maximum input length is stored encrypted and decrypted unchanged")
  void longKeyRoundTrip() {
    assumeTrue(System.getenv("JASYPT_ENCRYPTOR_PASSWORD") != null, "the encryption needs JASYPT_ENCRYPTOR_PASSWORD");
    String key = "sk-proj-" + "x".repeat(MAX_INPUT_LENGTH - 8);
    new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
      ConnectorApiKey connectorApiKey = new ConnectorApiKey();
      connectorApiKey.setIdProvider("length-test");
      connectorApiKey.setApiKey(key);
      assertThat(validator.validate(connectorApiKey)).isEmpty();
      connectorApiKeyJpaRepository.saveAndFlush(connectorApiKey);
      String stored = jdbc.queryForObject("SELECT api_key FROM connector_apikey WHERE id_provider = 'length-test'",
          String.class);
      assertThat(ConnectorApiKey.stringEncryptor().decrypt(stored)).isEqualTo(key);
      status.setRollbackOnly();
    });
  }
}
