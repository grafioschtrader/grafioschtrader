package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.MailSettingForward;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import jakarta.transaction.Transactional;

public interface MailSettingForwardJpaRepository extends JpaRepository<MailSettingForward, Integer>,
    MailSettingForwardJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<MailSettingForward> {

  List<MailSettingForward> findByIdUser(Integer idUser);

  Optional<MailSettingForward> findByIdUserAndMessageComType(Integer idUser, byte messageComType);

  @Transactional
  int deleteByIdUserAndIdMailSettingForward(Integer idUser, Integer idMailSettingForward);

}
