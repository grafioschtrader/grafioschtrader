package grafiosch.gtnet.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.repository.GTNetMessageJpaRepository;

class GTNetCoolingOffServiceTest {

  private final GTNetMessageJpaRepository messageRepository = mock(GTNetMessageJpaRepository.class);
  private final GTNetMessageCodeRegistry codeRegistry = new GTNetMessageCodeRegistry();
  private final GTNetCoolingOffService service = new GTNetCoolingOffService(messageRepository, codeRegistry);

  @Test
  void returnsOriginalRejectionAndCeilingOfRemainingDays() {
    GTNet remote = remote(7);
    GTNetMessage response = response(LocalDateTime.now(ZoneOffset.UTC).minusHours(12), (short) 2);
    when(messageRepository.findLatestCoolingOffResponse(7, GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue(),
        (byte) 1, (byte) 0)).thenReturn(response);

    var period = service.findActive(remote, GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue());

    assertThat(period).isPresent();
    assertThat(period.orElseThrow().responseCode()).isEqualTo(GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S);
    assertThat(period.orElseThrow().remainingDays()).isEqualTo(2);
  }

  @Test
  void ignoresExpiredPeriod() {
    GTNet remote = remote(7);
    GTNetMessage response = response(LocalDateTime.now(ZoneOffset.UTC).minusDays(2), (short) 1);
    when(messageRepository.findLatestCoolingOffResponse(7, GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue(),
        (byte) 1, (byte) 0)).thenReturn(response);

    assertThat(service.findActive(remote, GNetCoreMessageCode.GT_NET_DATA_REQUEST_SEL_RR_C.getValue())).isEmpty();
  }

  private GTNet remote(Integer id) {
    GTNet gtNet = new GTNet();
    gtNet.setIdGtNet(id);
    return gtNet;
  }

  private GTNetMessage response(LocalDateTime timestamp, short waitDays) {
    GTNetMessage response = new GTNetMessage();
    response.setTimestamp(timestamp);
    response.setMessageCode(GNetCoreMessageCode.GT_NET_DATA_REQUEST_REJECTED_S);
    response.setWaitDaysApply(waitDays);
    return response;
  }
}
