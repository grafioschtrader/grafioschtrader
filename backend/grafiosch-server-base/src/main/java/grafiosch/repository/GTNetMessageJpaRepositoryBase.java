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
   * Saves a GTNetMessage entity. Implementations may cascade to related entities like message params.
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

  /**
   * Resolves the peer-scoped identity of a delivery. The triple matches the unique key
   * {@code uk_gt_net_message_source}: {@code id_source_gt_net_message} is only unique within the sending instance, so
   * the peer and the direction are part of the identity. A hit on the RECEIVED direction means this delivery has been
   * processed before and must repeat neither its side effects nor its budget charge.
   *
   * @param idGtNet              the peer the message came from
   * @param sendRecv             the direction, {@code SendReceivedType.RECEIVED} for an inbound delivery
   * @param idSourceGtNetMessage the message id the sender assigned
   * @return the row of the first delivery, empty when this is the first
   */
  Optional<GTNetMessage> findByIdGtNetAndSendRecvAndIdSourceGtNetMessage(Integer idGtNet, byte sendRecv,
      Integer idSourceGtNetMessage);
}
