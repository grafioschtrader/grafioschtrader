package grafioschtrader.entities;

import java.util.Objects;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

import jakarta.persistence.Transient;

/**
 * Only surrogate key with Integer type is supported, since some public data
 * changes may be only a proposal. It would be complicated to save composite key
 * in the {@link ProposeChangeEntity}.
 *
 * @author Hugo Graf
 *
 */
public abstract class BaseID {

  @Transient
  @JsonIgnore
  UUID uuid;

  public abstract Integer getId();

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BaseID that = (BaseID) o;
    return Objects.equals(this.getNotNullId(), that.getNotNullId());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.getNotNullId());
  }

  private Object getNotNullId() {
    if (this.getId() != null) {
      return this.getId();
    } else {
      if (uuid == null) {
        uuid = UUID.randomUUID();
      }
      return uuid;
    }
  }

}
