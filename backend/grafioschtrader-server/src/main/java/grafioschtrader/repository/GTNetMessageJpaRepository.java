package grafioschtrader.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetMessage;

public interface GTNetMessageJpaRepository extends JpaRepository<GTNetMessage, Integer>{
  
  Stream <GTNetMessage> findAllByOrderByIdGtNetAscTimestampAsc();
}
