package grafiosch.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import grafiosch.entities.MailSettingForward;
import grafiosch.rest.UpdateCreateDeleteWithUserIdJpaRepository;
import jakarta.transaction.Transactional;

/**
 * JPA repository interface for managing {@link MailSettingForward} entities.
 * 
 * <p>This repository provides data access operations for user mail forwarding preferences,
 * allowing users to configure how different types of messages are delivered (internal only,
 * external email only, or both). It supports user-specific forwarding rules, message type
 * filtering, and optional message redirection to other users.
 */
public interface MailSettingForwardJpaRepository extends JpaRepository<MailSettingForward, Integer>,
    MailSettingForwardJpaRepositoryCustom, UpdateCreateDeleteWithUserIdJpaRepository<MailSettingForward> {

  
  List<MailSettingForward> findByIdUser(Integer idUser);

  Optional<MailSettingForward> findByIdUserAndMessageComType(Integer idUser, byte messageComType);

  @Transactional
  int deleteByIdUserAndIdMailSettingForward(Integer idUser, Integer idMailSettingForward);

}
