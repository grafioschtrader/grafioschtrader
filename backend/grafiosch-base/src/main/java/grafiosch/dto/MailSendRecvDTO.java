package grafiosch.dto;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = """
Data transfer object interface for mail send/receive operations in the internal messaging system. This interface 
defines the contract for mail message data including sender/receiver information, message content, conversation 
threading, and read status tracking.
""")
public interface MailSendRecvDTO {
  @Schema(description = "Unique identifier of the mail message")
  public Integer getIdMailSendRecv();

  @Schema(description = """
      Indicates whether this is a sent ('S') or received ('R') message from the perspective of the current user""")
  public String getSendRecv();

  @Schema(description = "Unique identifier of the user who sent this message")
  public Integer getIdUserFrom();

  @Schema(description = """
      Indicates whether the message has been read by the current user. This is user-specific and does not affect 
      the read status for other recipients of the same message.""")
  public Boolean getHasBeenRead();

  @Schema(description = """
      Unique identifier of the specific user this message was sent to. This is null for role-based messages where 
      the message is addressed to a role rather than an individual user.""")
  public Integer getIdUserTo();

  @Schema(description = """
      Unique identifier of the role this message was sent to. This is null for direct user-to-user messages. 
      When present, indicates this is a role-based message visible to all users with that role.""")
  public Integer getIdRoleTo();

  @Schema(description = "Subject line of the mail message")
  public String getSubject();

  @Schema(description = "Main content/body text of the mail message")
  public String getMessage();

  @Schema(description = """
      Reference to the local parent message ID for conversation threading. This links replies to their original 
      messages, enabling conversation grouping and thread management within the same system instance.""")
  public Integer getIdReplyToLocal();

  @Schema(description = """
      Reference to a remote parent message ID for cross-system conversation threading. This enables message 
      threading across different system instances in a distributed setup. NOT USED YET.""")
  public Integer getIdReplyToRemote();

  @Schema(description = "Timestamp when the message was sent or received")
  @JsonFormat(pattern = BaseConstants.STANDARD_LOCAL_DATE_TIME)
  public LocalDateTime getSendRecvTime();

  @Schema(description = """
      Display name of the target role for role-based messages. This provides a human-readable role name for UI 
      display purposes when the message is addressed to a role rather than an individual user.""")
  public Byte getReplyToRolePrivate();

  public String getRoleNameTo();
}
