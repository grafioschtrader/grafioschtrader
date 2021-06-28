package grafioschtrader.entities;

import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

import grafioschtrader.types.MessageCodesGTNetwork;
import io.swagger.v3.oas.annotations.media.Schema;

@Entity
@Table(name = GTDataExchange.TABNAME)
@Schema(description = "Contains definition which data will be exchanged. It works only for existing data")
public class GTDataExchange {

  public static final String TABNAME = "gt_network_exchange";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_gt_network_exchange")
  private Integer idGtNetworkExchange;

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

}
