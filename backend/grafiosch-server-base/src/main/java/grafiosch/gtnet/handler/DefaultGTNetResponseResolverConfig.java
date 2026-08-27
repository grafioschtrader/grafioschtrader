package grafiosch.gtnet.handler;

import org.springframework.stereotype.Component;

import grafiosch.entities.GTNet;
import grafiosch.gtnet.GTNetMessageCode;
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.repository.GlobalparametersJpaRepository;

/** Default response-resolver configuration shared by every Grafiosch host. */
@Component
public class DefaultGTNetResponseResolverConfig implements GTNetResponseResolverConfig {

  private final GTNetMessageCodeRegistry messageCodeRegistry;
  private final GlobalparametersJpaRepository globalparametersJpaRepository;

  public DefaultGTNetResponseResolverConfig(GTNetMessageCodeRegistry messageCodeRegistry,
      GlobalparametersJpaRepository globalparametersJpaRepository) {
    this.messageCodeRegistry = messageCodeRegistry;
    this.globalparametersJpaRepository = globalparametersJpaRepository;
  }

  @Override
  public GTNetMessageCode lookupMessageCode(byte codeValue) {
    return messageCodeRegistry.getByValue(codeValue);
  }

  @Override
  public boolean isValidResponse(byte requestCodeValue, byte responseCodeValue) {
    return messageCodeRegistry.isValidResponse(requestCodeValue, responseCodeValue);
  }

  @Override
  public Integer getMyGTNetEntryId() {
    return globalparametersJpaRepository.getGTNetMyEntryID();
  }

  @Override
  public Short getMaxLimitForEntityKind(GTNet gtNet, byte entityKindValue) {
    return gtNet.getEntityByKind(entityKindValue).map(entity -> entity.getMaxLimit()).orElse(null);
  }
}
