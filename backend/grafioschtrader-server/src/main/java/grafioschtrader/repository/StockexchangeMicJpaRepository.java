package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import grafioschtrader.entities.StockexchangeMic;

@Repository
public interface StockexchangeMicJpaRepository extends JpaRepository<StockexchangeMic, String> {
  
  Optional<StockexchangeMic> findByMicAndCountryCode(String mic, String countryCode);
}
