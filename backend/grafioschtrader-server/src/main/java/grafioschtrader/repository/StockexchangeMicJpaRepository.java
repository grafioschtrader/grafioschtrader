package grafioschtrader.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.StockexchangeMic;


public interface StockexchangeMicJpaRepository extends JpaRepository<StockexchangeMic, String> {
  
  Optional<StockexchangeMic> findByMicAndCountryCode(String mic, String countryCode);
}
