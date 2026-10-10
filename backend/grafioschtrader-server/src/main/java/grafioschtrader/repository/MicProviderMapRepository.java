package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.MicProviderMap.IdProviderMic;

public interface MicProviderMapRepository extends JpaRepository<MicProviderMap, IdProviderMic> {

  /**
   * Returns every exchange mapping of one data provider, so that a provider search can translate the provider's
   * exchange codes and symbol suffixes back into MICs.
   *
   * @param idProvider id of the data provider, e.g. "yahoo"
   * @return the mappings of that provider, empty when none exist
   */
  List<MicProviderMap> findByIdProviderMic_IdProvider(String idProvider);
}
