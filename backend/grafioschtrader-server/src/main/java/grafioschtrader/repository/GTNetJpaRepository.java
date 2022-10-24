package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNet;

public interface GTNetJpaRepository extends JpaRepository<GTNet, Integer>, GTNetJpaRepositoryCustom {
  List<GTNet> findByLastpriceConsumerUsageAndLastpriceServerState(byte lastpriceConsumerUsage,
      byte lastpriceServerState);

}
