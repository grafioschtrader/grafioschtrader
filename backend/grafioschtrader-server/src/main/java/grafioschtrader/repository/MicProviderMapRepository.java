package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.MicProviderMap;
import grafioschtrader.entities.MicProviderMap.IdProviderMic;

public interface MicProviderMapRepository extends JpaRepository<MicProviderMap, IdProviderMic>{

  
}
