package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.ConnectorApiKey;

@Repository
public interface ConnectorApiKeyJpaRepository extends JpaRepository<ConnectorApiKey, String>, ConnectorApiKeyJpaRepositoryCustom {
  

}
