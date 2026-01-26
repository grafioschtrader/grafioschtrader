package grafiosch.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.entities.GTNetMessage;

/**
 * Base repository interface for GTNetMessage entities used by the library handler infrastructure.
 */
@NoRepositoryBean
public interface GTNetMessageJpaRepositoryBase extends JpaRepository<GTNetMessage, Integer> {

  /**
   * Saves a GTNetMessage entity.
   * Implementations may cascade to related entities like message params.
   *
   * @param message the message to save
   * @return the saved message
   */
  GTNetMessage saveMsg(GTNetMessage message);

  /**
   * Finds a message by its ID.
   *
   * @param id the message ID
   * @return the message if found
   */
  @Override
  Optional<GTNetMessage> findById(Integer id);
}
