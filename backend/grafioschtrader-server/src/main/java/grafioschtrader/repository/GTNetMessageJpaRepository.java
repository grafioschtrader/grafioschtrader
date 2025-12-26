package grafioschtrader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.entities.GTNetMessage;

public interface GTNetMessageJpaRepository extends JpaRepository<GTNetMessage, Integer>,
    GTNetMessageJpaRepositoryCustom, UpdateCreateJpaRepository<GTNetMessage> {

  List<GTNetMessage> findAllByOrderByIdGtNetAscTimestampAsc();

  /**
   * Finds unanswered request messages based on direction and message codes.
   *
   * Named query: GTNetMessage.findUnansweredRequests
   *
   * Returns Object[] with: [0] = id_gt_net (Integer), [1] = id_gt_net_message (Integer)
   *
   * @param sendRecv     the direction of request messages (0=SEND for outgoing, 1=RECEIVED for incoming)
   * @param messageCodes list of message codes that require a response (_RR_ codes: 1, 10, 50)
   * @return list of Object[] containing [id_gt_net, id_gt_net_message] for unanswered requests
   */
  @Query(name = "GTNetMessage.findUnansweredRequests", nativeQuery = true)
  List<Object[]> findUnansweredRequests(byte sendRecv, List<Byte> messageCodes);
}
