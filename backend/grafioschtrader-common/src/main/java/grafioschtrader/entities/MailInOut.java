package grafioschtrader.entities;

import static javax.persistence.InheritanceType.JOINED;

import javax.persistence.Column;
import javax.persistence.DiscriminatorColumn;
import javax.persistence.DiscriminatorType;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Inheritance;
import javax.persistence.Table;
import javax.persistence.Transient;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/**
 * Base class for mailing system. Mail also supports M2M on Grafioschtraders.
 * Attention the ID's of the role must be the same on all Grafioschtrader
 * systems.
 * 
 * @author Hugo Graf
 *
 */
@Entity
@Table(name = MailInOut.TABNAME)
@Inheritance(strategy = JOINED)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.STRING)
public abstract class MailInOut extends BaseID {

  public static final String TABNAME = "mail_in_out";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id_mail_inout")
  private Integer idMailInOut;

  @NotNull
  @Column(name = "id_user_from")
  private Integer idUserFrom;

  @Column(name = "id_user_to")
  private Integer idUserTo;

  @Column(name = "id_role_to")
  private Integer idRoleTo;

  @NotNull
  @Size(min = 2, max = 96)
  @Column(name = "subject")
  private String subject;

  @Transient
  String roleNameTo;

  @NotNull
  @Size(min = 2, max = 1024)
  @Column(name = "message")
  private String message;

  public MailInOut() {
  }

  public MailInOut(Integer idUserFrom, Integer idUserTo, String roleNameTo, String subject, String message) {
    this.idUserFrom = idUserFrom;
    this.idUserTo = idUserTo;
    this.roleNameTo = roleNameTo;
    this.subject = subject;
    this.message = message;
  }

  public Integer getIdUserFrom() {
    return idUserFrom;
  }

  public void setIdUserFrom(Integer idUserFrom) {
    this.idUserFrom = idUserFrom;
  }

  public Integer getIdUserTo() {
    return idUserTo;
  }

  public void setIdUserTo(Integer idUserTo) {
    this.idUserTo = idUserTo;
  }

  public Integer getIdRoleTo() {
    return idRoleTo;
  }

  public void setIdRoleTo(Integer idRoleTo) {
    this.idRoleTo = idRoleTo;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public Integer getIdMailInOut() {
    return idMailInOut;
  }

  public String getRoleNameTo() {
    return roleNameTo;
  }

  public void setRoleNameTo(String roleNameTo) {
    this.roleNameTo = roleNameTo;
  }

  @Override
  public Integer getId() {
    return null;
  }

}
