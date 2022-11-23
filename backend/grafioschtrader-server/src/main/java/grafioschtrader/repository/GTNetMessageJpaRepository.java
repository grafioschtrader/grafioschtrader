package grafioschtrader.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.GTNetMessage;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface GTNetMessageJpaRepository extends JpaRepository<GTNetMessage, Integer>,
    GTNetMessageJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetMessage> {

  Stream<GTNetMessage> findAllByOrderByIdGtNetAscTimestampAsc();
}
