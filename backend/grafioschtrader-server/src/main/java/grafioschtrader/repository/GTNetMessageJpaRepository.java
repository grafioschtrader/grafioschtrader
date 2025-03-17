package grafioschtrader.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNetMessage;

public interface GTNetMessageJpaRepository extends JpaRepository<GTNetMessage, Integer>,
    GTNetMessageJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetMessage> {

  Stream<GTNetMessage> findAllByOrderByIdGtNetAscTimestampAsc();
}
