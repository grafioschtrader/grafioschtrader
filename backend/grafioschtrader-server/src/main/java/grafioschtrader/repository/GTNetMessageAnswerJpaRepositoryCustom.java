package grafioschtrader.repository;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import grafiosch.repository.BaseRepositoryCustom;
import grafioschtrader.entities.GTNet;
import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.entities.GTNetMessageAnswer;
import grafioschtrader.gtnet.m2m.model.MessageEnvelope;

public interface GTNetMessageAnswerJpaRepositoryCustom extends BaseRepositoryCustom<GTNetMessageAnswer>{
  GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest);

  private long calculateTimeZoneDifferenceInMinutes(GTNet myGTNet, GTNet remoteGTNet) {
    ZonedDateTime now1 = ZonedDateTime.now(ZoneId.of(myGTNet.getTimeZone()));
    ZonedDateTime now2 = ZonedDateTime.now(ZoneId.of(remoteGTNet.getTimeZone()));
    Duration duration = Duration.between(now1, now2);
    return duration.toMinutes() % 60;
  }
}
