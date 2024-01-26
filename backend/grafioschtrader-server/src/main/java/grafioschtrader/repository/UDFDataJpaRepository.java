package grafioschtrader.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.UDFData;
import grafioschtrader.entities.UDFData.UDFDataKey;

public interface UDFDataJpaRepository extends JpaRepository<UDFData, UDFDataKey>  {


}
