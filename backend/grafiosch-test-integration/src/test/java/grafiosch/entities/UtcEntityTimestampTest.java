package grafiosch.entities;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;

import com.fasterxml.jackson.annotation.JsonIncludeProperties;

import grafiosch.dto.MailSendRecvDTO;
import jakarta.persistence.PrePersist;
import tools.jackson.databind.json.JsonMapper;

/** Guards timestamp defaults and the mail formats used by both entities and REST projections. */
class UtcEntityTimestampTest {

  @JsonIncludeProperties("sendRecvTime")
  private abstract static class OnlyMailTime {
  }

  @Test
  @DisplayName("New mail and users receive required timestamps without overwriting imported values")
  void initializesMissingValuesOnly() throws Exception {
    var mail = new MailSendRecv();
    var user = new User();
    LocalDateTime before = LocalDateTime.now();
    assertThat(MailSendRecv.class.getDeclaredMethod("initializeSendRecvTime").isAnnotationPresent(PrePersist.class))
        .isTrue();
    assertThat(User.class.getDeclaredMethod("initializeLastRoleModifiedTime").isAnnotationPresent(PrePersist.class))
        .isTrue();
    mail.initializeSendRecvTime();
    user.initializeLastRoleModifiedTime();
    assertThat(mail.getSendRecvTime()).isBetween(before, LocalDateTime.now());
    assertThat(user.getLastRoleModifiedTime()).isBetween(before, LocalDateTime.now());

    LocalDateTime imported = LocalDateTime.of(2026, 1, 15, 20, 30);
    mail.setSendRecvTime(imported);
    user.setLastRoleModifiedTime(imported);
    mail.initializeSendRecvTime();
    user.initializeLastRoleModifiedTime();
    assertThat(mail.getSendRecvTime()).isEqualTo(imported);
    assertThat(user.getLastRoleModifiedTime()).isEqualTo(imported);
  }

  @Test
  @DisplayName("Mail entity and the projection used by the mail tree serialize UTC instants with Z")
  void mailWireFormatsDeclareUtc() {
    LocalDateTime time = LocalDateTime.of(2026, 9, 15, 20, 30, 45);
    var mail = new MailSendRecv();
    mail.setSendRecvTime(time);
    var projection = new SpelAwareProxyProjectionFactory().createProjection(MailSendRecvDTO.class,
        Map.of("sendRecvTime", time));
    var mapper = JsonMapper.builder().addMixIn(MailSendRecv.class, OnlyMailTime.class)
        .addMixIn(MailSendRecvDTO.class, OnlyMailTime.class).build();
    String expected = "{\"sendRecvTime\":\"2026-09-15T20:30:45Z\"}";
    assertThat(mapper.writeValueAsString(mail)).isEqualTo(expected);
    assertThat(mapper.writeValueAsString(projection)).isEqualTo(expected);
  }
}
