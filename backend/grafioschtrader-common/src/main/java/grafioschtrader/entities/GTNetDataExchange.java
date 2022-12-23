package grafioschtrader.entities;

import java.time.LocalDateTime;

import grafioschtrader.gtnet.MessageCodesGTNetwork;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = GTNetDataExchange.TABNAME)
@Schema(description = "Contains definition which data will be exchanged. It works only for existing data")
public class GTNetDataExchange extends BaseID {

  public static final String TABNAME = "gt_net_exchange";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_net_exchange")
  private Integer idGtNetExchange;

  @Schema(description = "Give or receive Data")
  @Column(name = "id_out")
  private Byte inOut;

  @Schema(description = "Enitity which is exchanged, mostly histroical prices of instruments")
  @Column(name = "entity")
  private String entity;

  @Schema(description = "The id of the entity which is exchanged. It is the id on this instance")
  @Column(name = "id_entity")
  private Integer idEntity;

  @Schema(description = "The id of the entity which is exchanged. Remote means the id on the remote domain")
  @Column(name = "id_entity_remote")
  private Integer idEntityRemote;

  @Schema(description = "It will be 1 or greater, this value contains the number of indirections to a direct reference")
  @Column(name = "indirection_count")
  private short indirectionCount;
  
  @Schema(description = "The id of last message code which was send")
  @Column(name = "send_msg_code")
  private Byte sendMsgCode;

  @Schema(description = "Last timestamp send message to the remote domain")
  @Column(name = "send_msg_timestamp")
  private LocalDateTime sendMsgTimestamp;

  @Schema(description = "The id of last message code which was received")
  @Column(name = "recv_msg_code")
  private Byte recvMsgCode;

  @Schema(description = "Last timestamp when a message was received from the remote domain")
  @Column(name = "recv_msg_timestamp")
  private LocalDateTime recvMsgTimestamp;

  @Schema(description = "Last timestamp of the entity request")
  @Column(name = "request_entity_timestamp")
  private LocalDateTime requestEntityTimestamp;

  @Schema(description = "Last timestamp when the requested entity was deliverted")
  @Column(name = "give_entity_timestamp")
  private LocalDateTime giveEntityTimestamp;

  public MessageCodesGTNetwork getSendMsgCode() {
    return MessageCodesGTNetwork.getMessageCodesGTNetwork(sendMsgCode);
  }

  public void setSendMsgCode(MessageCodesGTNetwork sendMsgCode) {
    this.sendMsgCode = sendMsgCode.getValue();
  }

  public MessageCodesGTNetwork getRecvMsgCode() {
    return MessageCodesGTNetwork.getMessageCodesGTNetwork(recvMsgCode);
  }

  public void setRecvMsgCode(MessageCodesGTNetwork recvMsgCode) {
    this.recvMsgCode = recvMsgCode.getValue();
  }

  @Override
  public Integer getId() {
    return idGtNetExchange;
  }

}
