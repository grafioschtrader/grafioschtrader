package grafiosch.gtnet.handler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetTime;
import grafiosch.gtnet.SendReceivedType;
import grafiosch.repository.GTNetMessageJpaRepository;

/** Resolves active cooling-off periods established by earlier GTNet responses. */
@Component
public class GTNetCoolingOffService {

  private final GTNetMessageJpaRepository gtNetMessageJpaRepository;
  private final GTNetMessageCodeRegistry messageCodeRegistry;

  public GTNetCoolingOffService(GTNetMessageJpaRepository gtNetMessageJpaRepository,
      GTNetMessageCodeRegistry messageCodeRegistry) {
    this.gtNetMessageJpaRepository = gtNetMessageJpaRepository;
    this.messageCodeRegistry = messageCodeRegistry;
  }

  public Optional<CoolingOffPeriod> findActive(GTNet remoteGTNet, byte requestCode) {
    if (remoteGTNet == null) {
      return Optional.empty();
    }
    GTNetMessage response = gtNetMessageJpaRepository.findLatestCoolingOffResponse(remoteGTNet.getIdGtNet(),
        requestCode, SendReceivedType.RECEIVED.getValue(), SendReceivedType.SEND.getValue());
    if (response == null || response.getWaitDaysApply() == null || response.getWaitDaysApply() <= 0) {
      return Optional.empty();
    }

    LocalDateTime now = GTNetTime.now();
    LocalDateTime expiresAt = response.getTimestamp().plusDays(response.getWaitDaysApply());
    if (!now.isBefore(expiresAt)) {
      return Optional.empty();
    }
    long secondsRemaining = Math.max(1, Duration.between(now, expiresAt).getSeconds());
    long remainingDays = Math.max(1, (secondsRemaining + 86_399) / 86_400);
    GTNetMessageCode responseCode = messageCodeRegistry.getByValue(response.getMessageCodeValue());
    return responseCode == null ? Optional.empty()
        : Optional.of(new CoolingOffPeriod(responseCode, remainingDays, expiresAt));
  }

  public record CoolingOffPeriod(GTNetMessageCode responseCode, long remainingDays, LocalDateTime expiresAt) {
  }
}
