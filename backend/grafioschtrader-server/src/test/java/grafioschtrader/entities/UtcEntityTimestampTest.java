package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import jakarta.persistence.PrePersist;
import tools.jackson.databind.json.JsonMapper;

/** Required DATETIME values must exist before Hibernate inserts an explicit NULL. */
class UtcEntityTimestampTest {

  @JsonIncludeProperties("lastDirectPriceUpdate")
  private abstract static class OnlyLastDirectPriceUpdate {
  }

  @Test
  @DisplayName("Quotes and exchanges initialize required timestamps while preserving existing values")
  void initializesQuotesAndExchanges() throws Exception {
    var quote = new Historyquote();
    var exchange = new Stockexchange();
    LocalDateTime before = LocalDateTime.now();
    assertThat(Historyquote.class.getDeclaredMethod("initializeCreateModifyTime").isAnnotationPresent(PrePersist.class))
        .isTrue();
    assertThat(
        Stockexchange.class.getDeclaredMethod("initializeLastDirectPriceUpdate").isAnnotationPresent(PrePersist.class))
            .isTrue();
    quote.initializeCreateModifyTime();
    exchange.initializeLastDirectPriceUpdate();
    assertThat(quote.getCreateModifyTime()).isBetween(before, LocalDateTime.now());
    assertThat(exchange.getLastDirectPriceUpdate()).isBetween(before.minusHours(72),
        LocalDateTime.now().minusHours(72));

    LocalDateTime imported = LocalDateTime.of(2026, 1, 15, 20, 30);
    quote.setCreateModifyTime(imported);
    exchange.setLastDirectPriceUpdate(imported);
    quote.initializeCreateModifyTime();
    exchange.initializeLastDirectPriceUpdate();
    assertThat(quote.getCreateModifyTime()).isEqualTo(imported);
    assertThat(exchange.getLastDirectPriceUpdate()).isEqualTo(imported);
  }

  @Test
  @DisplayName("Both securities and currency pairs initialize missing price and GTNet instants independently")
  void initializesInstrumentTimestamps() throws Exception {
    assertThat(Securitycurrency.class.getDeclaredMethod("initializeTimestamps").isAnnotationPresent(PrePersist.class))
        .isTrue();
    for (Securitycurrency<?> instrument : List.of(new Security(), new Currencypair())) {
      LocalDateTime imported = LocalDateTime.of(2026, 1, 15, 20, 30);
      LocalDateTime before = LocalDateTime.now();
      instrument.setSTimestamp(imported);
      instrument.initializeTimestamps();
      assertThat(instrument.getSTimestamp()).isEqualTo(imported);
      assertThat(instrument.getGtNetLastModifiedTime()).isBetween(before, LocalDateTime.now());
      instrument.setSTimestamp(null);
      instrument.setGtNetLastModifiedTime(imported);
      instrument.initializeTimestamps();
      assertThat(instrument.getSTimestamp()).isBetween(before, LocalDateTime.now());
      assertThat(instrument.getGtNetLastModifiedTime()).isEqualTo(imported);
    }
  }

  @Test
  @DisplayName("The last direct price update serializes as a UTC instant")
  void exchangeWireFormatDeclaresUtc() {
    var exchange = new Stockexchange();
    exchange.setLastDirectPriceUpdate(LocalDateTime.of(2026, 9, 15, 20, 30, 45));
    var mapper = JsonMapper.builder().addMixIn(Stockexchange.class, OnlyLastDirectPriceUpdate.class).build();
    assertThat(mapper.writeValueAsString(exchange)).isEqualTo("{\"lastDirectPriceUpdate\":\"2026-09-15T20:30:45Z\"}");
  }
}
