package grafiosch.entities;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

import grafiosch.types.OperationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Schema(description = """
    Counts the CUD (create, update, delete) operations of the individual users on the individual information classes.
    Is used to evaluate whether the limits of the CUD operations have been exceeded.""")
@Entity
@Table(name = UserEntityChangeCount.TABNAME)
public class UserEntityChangeCount {

  public static final String TABNAME = "user_entity_change_count";

  @EmbeddedId
  UserEntityChangeCountId idUserEntityChangeCount;

  @Schema(description = "Number of insert operations")
  @Column(name = "count_insert")
  private int countInsert;

  @Schema(description = "Number of update operations")
  @Column(name = "count_update")
  private int countUpdate;

  @Schema(description = "Number of delete operations")
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
    incrementCounter(operationType, 1);
  }

  /**
   * Adds a number of operations to the counter of one operation type. A bulk write that consumes a daily budget in one
   * step is booked with the number of units it cost, so that the counter stays comparable with the limit the check read
   * before the write.
   *
   * @param operationType the operation type whose counter is raised
   * @param count         how much to add; a value below one is treated as one
   */
  public void incrementCounter(OperationType operationType, int count) {
    int increment = Math.max(count, 1);
    switch (operationType) {
    case ADD:
      countInsert += increment;
      break;
    case UPDATE:
      countUpdate += increment;
      break;
    default:
      countDelete += increment;
    }
  }

  @Embeddable
  public static class UserEntityChangeCountId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "ID of the user who is counted")
    @Column(name = "id_user")
    private Integer idUser;

    @Schema(description = "Date of the count")
    @Column(name = "date")
    private LocalDate date;

    @Schema(description = "Name of the entity which is counted")
    @Column(name = "entity_name")
    private String entityName;

    public UserEntityChangeCountId() {
    }

    public UserEntityChangeCountId(Integer idUser, LocalDate date, String tablename) {
      this.idUser = idUser;
      this.date = date;
      this.entityName = tablename;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
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
