package grafiosch.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import grafiosch.entities.GTNetMessageAnswer;

/**
 * Base repository interface for GTNetMessageAnswer entities used by the library handler infrastructure.
 */
@NoRepositoryBean
public interface GTNetMessageAnswerJpaRepositoryBase extends JpaRepository<GTNetMessageAnswer, Integer> {

  /**
   * Finds auto-response rules for a given request message code, ordered by priority.
   *
   * @param requestMsgCode the request message code value
   * @return list of matching rules ordered by priority
   */
  List<GTNetMessageAnswer> findByRequestMsgCodeOrderByPriority(byte requestMsgCode);
}
