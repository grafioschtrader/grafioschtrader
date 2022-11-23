package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNet;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface GTNetJpaRepository
    extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom, UpdateCreateJpaRepository<GTNet> {
 
  List<GTNet> findByLastpriceConsumerUsageAndLastpriceServerState(byte lastpriceConsumerUsage,
      byte lastpriceServerState);
  
  GTNet findByDomainRemoteName(String domainRemoteName);

}
