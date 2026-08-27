package grafiosch.gtnet;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessage.GTNetMessageParam;

/**
 * The two producers of GTNet message parameters do not agree on a date format: the message dialog submits the raw value
 * of a date form control, which serialises as an ISO instant with a trailing {@code Z}, while a peer driving the
 * protocol directly sends what {@code LocalDate.toString()} produces. Both have to be readable — the bare form used to
 * be rejected, which left a discontinuation announcement forever unexpired and its message forever undeletable.
 */
class MessageParamDateParserTest {

  @Test
  @DisplayName("Reads the ISO instant the message dialog produces")
  void readsIsoInstantWithZoneSuffix() {
    Map<String, GTNetMessageParam> params = params("closeStartDate", "2026-09-01T00:00:00.000Z");

    assertThat(MessageParamDateParser.parseDate(params, "closeStartDate")).isEqualTo(LocalDate.of(2026, 9, 1));
    assertThat(MessageParamDateParser.parseDateTime(params, "closeStartDate"))
        .isEqualTo(LocalDateTime.of(2026, 9, 1, 0, 0));
  }

  @Test
  @DisplayName("Reads the bare date a peer sends over the protocol")
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
