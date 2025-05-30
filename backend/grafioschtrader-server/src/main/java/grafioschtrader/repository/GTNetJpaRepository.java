package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNet;

public interface GTNetJpaRepository
    extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom, UpdateCreateJpaRepository<GTNet> {

  List<GTNet> findByAcceptEntityRequestOrAcceptLastpriceRequest(boolean acceptEntityRequest,
      boolean acceptLastpriceRequest);

  List<GTNet> findByLastpriceConsumerUsageAndLastpriceServerState(byte lastpriceConsumerUsage,
      byte lastpriceServerState);

  GTNet findByDomainRemoteName(String domainRemoteName);

}
