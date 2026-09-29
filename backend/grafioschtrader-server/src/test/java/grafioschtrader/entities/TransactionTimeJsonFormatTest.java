package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Guards the JSON shape of the transaction time (GitHub issue #56). The value is the local wall-clock time of the
 * statement and must leave the server without a zone designator: a literal {@code 'Z'} declares it UTC, the browser
 * then shifts it by its own offset, and every unchanged save of the edit dialog moved the stored time again.
 *
 * <p>
 * A mix-in restricts the serialization to {@code transactionTime}, so the test depends on that field's
 * {@code @JsonFormat} alone and not on the other getters of the entities.
 * </p>
 */
class TransactionTimeJsonFormatTest {

  private static final LocalDateTime STATEMENT_TIME = LocalDateTime.of(2026, 9, 15, 16, 0);

  private static final String EXPECTED_JSON = "{\"transactionTime\":\"2026-09-15T16:00:00\"}";

  @JsonIncludeProperties("transactionTime")
  private abstract static class OnlyTransactionTime {
  }

  private final ObjectMapper mapper = JsonMapper.builder().addMixIn(Transaction.class, OnlyTransactionTime.class)
      .addMixIn(ImportTransactionPos.class, OnlyTransactionTime.class).build();

  @Test
  @DisplayName("Transaction serializes its time as local wall-clock time without 'Z'")
  void transactionTimeHasNoZone() {
    Transaction transaction = new Transaction();
    transaction.setTransactionTime(STATEMENT_TIME);
    assertThat(mapper.writeValueAsString(transaction)).isEqualTo(EXPECTED_JSON);
  }

  @Test
  @DisplayName("Import position serializes its time as local wall-clock time without 'Z'")
  void importTransactionPosTimeHasNoZone() {
    ImportTransactionPos importTransactionPos = new ImportTransactionPos();
    importTransactionPos.setTransactionTime(STATEMENT_TIME);
    assertThat(mapper.writeValueAsString(importTransactionPos)).isEqualTo(EXPECTED_JSON);
  }

}
