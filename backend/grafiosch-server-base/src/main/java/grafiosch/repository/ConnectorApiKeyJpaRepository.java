package grafiosch.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.ConnectorApiKey;

public interface ConnectorApiKeyJpaRepository
    extends JpaRepository<ConnectorApiKey, String>, ConnectorApiKeyJpaRepositoryCustom {

}
