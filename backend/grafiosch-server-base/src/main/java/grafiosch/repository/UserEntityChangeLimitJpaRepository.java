package grafiosch.repository;

import java.util.stream.Stream;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.UserEntityChangeLimit;
import grafiosch.rest.UpdateCreateJpaRepository;

public interface UserEntityChangeLimitJpaRepository extends JpaRepository<UserEntityChangeLimit, Integer>,
    UserEntityChangeLimitJpaRepositoryCustom, UpdateCreateJpaRepository<UserEntityChangeLimit> {

  Stream<UserEntityChangeLimit> findByIdUser(Integer idUser);

}
