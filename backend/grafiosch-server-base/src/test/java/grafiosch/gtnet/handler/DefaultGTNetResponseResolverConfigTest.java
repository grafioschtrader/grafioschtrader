package grafiosch.gtnet.handler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetEntity;
import grafiosch.gtnet.GNetCoreMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.repository.GlobalparametersJpaRepository;

class DefaultGTNetResponseResolverConfigTest {

  @Test
  void resolvesRegisteredCodesOwnEntryAndEntityLimits() {
    GlobalparametersJpaRepository globalparameters = mock(GlobalparametersJpaRepository.class);
    when(globalparameters.getGTNetMyEntryID()).thenReturn(17);
    DefaultGTNetResponseResolverConfig config = new DefaultGTNetResponseResolverConfig(new GTNetMessageCodeRegistry(),
        globalparameters);
    GTNet gtNet = new GTNet();
    GTNetEntity entity = new GTNetEntity();
    entity.setEntityKindValue((byte) 1);
    entity.setMaxLimit((short) 321);
    gtNet.getGtNetEntities().add(entity);

    assertThat(config.lookupMessageCode(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S.getValue()))
        .isEqualTo(GNetCoreMessageCode.GT_NET_DATA_REQUEST_ACCEPT_S);
    assertThat(config.getMyGTNetEntryId()).isEqualTo(17);
    assertThat(config.getMaxLimitForEntityKind(gtNet, (byte) 1)).isEqualTo((short) 321);
    assertThat(config.getMaxLimitForEntityKind(gtNet, (byte) 0)).isNull();
  }
}
