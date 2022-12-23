package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.ConnectorApiKey;

public interface ConnectorApiKeyJpaRepository extends JpaRepository<ConnectorApiKey, String>, ConnectorApiKeyJpaRepositoryCustom {
  

}
