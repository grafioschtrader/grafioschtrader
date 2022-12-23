package grafioschtrader.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafioschtrader.entities.UserEntityChangeLimit;
import grafioschtrader.rest.UpdateCreateJpaRepository;

public interface UserEntityChangeLimitJpaRepository extends JpaRepository<UserEntityChangeLimit, Integer>,
    UserEntityChangeLimitJpaRepositoryCustom, UpdateCreateJpaRepository<UserEntityChangeLimit> {

  Stream<UserEntityChangeLimit> findByIdUser(Integer idUser);

}
