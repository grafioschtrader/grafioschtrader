package grafiosch.entities;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Personal dashboard document. The user primary key bounds storage to one document per user; nested data is bounded and
 * validated by the dashboard repository. Included in personal export and account deletion.
 */
@Entity
@Table(name = UserDashboard.TABNAME)
public class UserDashboard extends UserBaseID {
  public static final String TABNAME = "user_dashboard";
  @Id
  @Column(name = "id_user")
  private Integer idUser;
  @Column(name = "layout", nullable = false, columnDefinition = "json")
  private String layout;
  @Version
  @Column(name = "revision", nullable = false)
  private Long revision;

  @Override
  public Integer getIdUser() {
    return idUser;
  }

  @Override
  public void setIdUser(Integer idUser) {
    this.idUser = idUser;
  }

  @Override
  @JsonIgnore
  public Integer getId() {
    return idUser;
  }

  public String getLayout() {
    return layout;
  }

  public void setLayout(String layout) {
    this.layout = layout;
  }

  public Long getRevision() {
    return revision;
  }
}
