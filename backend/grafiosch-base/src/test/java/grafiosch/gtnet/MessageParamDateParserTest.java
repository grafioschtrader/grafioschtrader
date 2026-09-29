package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.dynamic.model.DynamicFormPropertyHelps;
import grafiosch.dynamic.model.DynamicModelHelper;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;
import grafiosch.gtnet.model.msg.DiscontinuedMsg;
import jakarta.validation.Validation;

/**
 * Calendar dates from current peers and ISO instants from older message dialogs must both remain readable, including
 * when checking whether an announcement has expired.
 */
class MessageParamDateParserTest {

  @Test
  @DisplayName("Discontinuation accepts the sender's tomorrow when it is already today in UTC")
  void discontinuationAcceptsUtcToday() {
    Clock clock = Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC);
    try (var factory = Validation.byDefaultProvider().configure().clockProvider(() -> clock).buildValidatorFactory()) {
      var validator = factory.getValidator();
      var message = new DiscontinuedMsg();
      message.closeStartDate = LocalDate.of(2026, 9, 16);
      assertThat(validator.validate(message)).isEmpty();
      message.closeStartDate = LocalDate.of(2026, 9, 15);
      assertThat(validator.validate(message)).hasSize(1);
    }
  }

  @Test
  @DisplayName("Discontinuation retains the future-date constraint in its generated browser form")
  void discontinuationRetainsFutureDateHint() {
    var fields = DynamicModelHelper.getFormDefinitionOfModelClassMembers(DiscontinuedMsg.class);
    assertThat(fields).hasSize(1);
    assertThat(fields.getFirst().dynamicFormPropertyHelps).containsExactly(DynamicFormPropertyHelps.DATE_FUTURE);
  }

  @Test
  @DisplayName("Reads calendar dates sent as ISO instants by older message dialogs")
  void readsIsoInstantWithZoneSuffix() {
    Map<String, GTNetMessageParam> params = params("closeStartDate", "2026-09-01T00:00:00.000Z");

    assertThat(MessageParamDateParser.parseDate(params, "closeStartDate")).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(MessageParamDateParser.parseDateTime(params, "closeStartDate"))
        .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
  }

  @Test
  @DisplayName("Reads the bare date sent by message dialogs and peers")
  void readsBareDate() {
    Map<String, GTNetMessageParam> params = params("closeStartDate", "2026-09-01");

    assertThat(MessageParamDateParser.parseDate(params, "closeStartDate")).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(MessageParamDateParser.parseDateTime(params, "closeStartDate"))
        .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
  }

  @Test
  @DisplayName("Reads a local date-time with and without seconds")
  void readsLocalDateTime() {
    assertThat(MessageParamDateParser.parseDateTime(params("fromDateTime", "2026-09-01T22:30"), "fromDateTime"))
        .isEqualTo(LocalDateTime.of(2026, 9, 1, 22, 30));
    assertThat(MessageParamDateParser.parseDateTime(params("fromDateTime", "2026-09-01T22:30:15"), "fromDateTime"))
        .isEqualTo(LocalDateTime.of(2026, 9, 1, 22, 30, 15));
  }

  @Test
  @DisplayName("A missing, blank or unreadable value yields null rather than an exception")
  void toleratesUnusableValues() {
    assertThat(MessageParamDateParser.parseDateTime(null, "closeStartDate")).isNull();
    assertThat(MessageParamDateParser.parseDateTime(params("other", "2026-09-01"), "closeStartDate")).isNull();
    assertThat(MessageParamDateParser.parseDateTime(params("closeStartDate", "   "), "closeStartDate")).isNull();
    assertThat(MessageParamDateParser.parseDateTime(params("closeStartDate", "not a date"), "closeStartDate")).isNull();
  }

  @Test
  @DisplayName("A discontinuation is expired from the day after its close date, in either date format")
  void discontinuationExpiresOnItsCloseDate() {
    String past = LocalDate.now().minusDays(1).toString();
    String future = LocalDate.now().plusDays(1).toString();

    assertThat(MessageParamDateParser.isAnnouncementExpired(
        announcement(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, "closeStartDate", past))).isTrue();
    assertThat(MessageParamDateParser.isAnnouncementExpired(announcement(
        GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, "closeStartDate", past + "T00:00:00.000Z"))).isTrue();
    assertThat(MessageParamDateParser.isAnnouncementExpired(
        announcement(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, "closeStartDate", future))).isFalse();
  }

  @Test
  @DisplayName("A maintenance announcement is expired once its window has ended")
  void maintenanceExpiresAtTheEndOfItsWindow() {
    assertThat(MessageParamDateParser.isAnnouncementExpired(announcement(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C,
        "toDateTime", LocalDateTime.now().minusHours(1).toString()))).isTrue();
    assertThat(MessageParamDateParser.isAnnouncementExpired(announcement(GNetCoreMessageCode.GT_NET_MAINTENANCE_ALL_C,
        "toDateTime", LocalDateTime.now().plusHours(1).toString()))).isFalse();
  }

  @Test
  @DisplayName("An announcement whose date cannot be read counts as not expired, so it stays visible")
  void unreadableAnnouncementIsNotExpired() {
    assertThat(MessageParamDateParser.isAnnouncementExpired(
        announcement(GNetCoreMessageCode.GT_NET_OPERATION_DISCONTINUED_ALL_C, "closeStartDate", "rubbish"))).isFalse();
    assertThat(MessageParamDateParser
        .isAnnouncementExpired(announcement(GNetCoreMessageCode.GT_NET_OFFLINE_ALL_C, "closeStartDate", "2000-01-01")))
            .isFalse();
  }

  private Map<String, GTNetMessageParam> params(String name, String value) {
    Map<String, GTNetMessageParam> params = new HashMap<>();
    params.put(name, new GTNetMessageParam(value));
    return params;
  }

  private GTNetMessage announcement(GNetCoreMessageCode code, String paramName, String paramValue) {
    GTNetMessage message = new GTNetMessage();
    message.setMessageCode(code);
    message.setGtNetMessageParamMap(params(paramName, paramValue));
    return message;
  }
}
