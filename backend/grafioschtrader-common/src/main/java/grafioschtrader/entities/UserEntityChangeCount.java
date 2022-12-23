package grafioschtrader.entities;

import java.io.Serializable;
import java.util.Date;
import java.util.Objects;

import grafioschtrader.common.DateHelper;
import grafioschtrader.types.OperationType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = UserEntityChangeCount.TABNAME)
public class UserEntityChangeCount {

  public static final String TABNAME = "user_entity_change_count";

  @EmbeddedId
  UserEntityChangeCountId idUserEntityChangeCount;

  @Column(name = "count_insert")
  private int countInsert;

  @Column(name = "count_update")
  private int countUpdate;

  @Column(name = "count_delete")
  private int countDelete;

  public UserEntityChangeCount() {

  }

  public UserEntityChangeCount(UserEntityChangeCountId idUserEntityChangeCounter) {
    this.idUserEntityChangeCount = idUserEntityChangeCounter;
  }

  public int getCountInsert() {
    return countInsert;
  }

  public void setCountInsert(int countInsert) {
    this.countInsert = countInsert;
  }

  public UserEntityChangeCountId getIdUserEntityChangeCount() {
    return idUserEntityChangeCount;
  }

  public int getCountUpdate() {
    return countUpdate;
  }

  public int getCountDelete() {
    return countDelete;
  }

  public void incrementCounter(OperationType operationType) {
    switch (operationType) {
    case ADD:
      countInsert++;
      break;
    case UPDATE:
      countUpdate++;
      break;
    default:
      countDelete++;
    }
  }

  @Embeddable
  public static class UserEntityChangeCountId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "id_user")
    private Integer idUser;

    @Column(name = "date")
    private Date date;

    @Column(name = "entity_name")
    private String entityName;

    public UserEntityChangeCountId() {

    }

    public UserEntityChangeCountId(Integer idUser, Date date, String tablename) {
      this.idUser = idUser;
      this.date = DateHelper.setTimeToZeroAndAddDay(date, 0);
      this.entityName = tablename;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o)
        return true;
      if (o == null || getClass() != o.getClass())
        return false;
      UserEntityChangeCountId that = (UserEntityChangeCountId) o;
      return Objects.equals(idUser, that.idUser) && Objects.equals(date, that.date)
          && Objects.equals(entityName, that.entityName);
    }

    @Override
    public int hashCode() {
      return Objects.hash(idUser, date, entityName);
    }
  }

}
