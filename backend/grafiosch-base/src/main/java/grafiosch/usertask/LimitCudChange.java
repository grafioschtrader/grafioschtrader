package grafiosch.usertask;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Model class representing a request to modify CUD (Create, Update, Delete) operation limits for a specific entity
 * type. This class is used when users with limited privileges request an increase in their daily operation allowances
 * for particular entity types.
 * 
 * Users with restricted roles may have daily limits on how many CUD operations they can perform on different entity
 * types. When they need to exceed these limits, they can submit a proposal using this model class, which administrators
 * can review and approve.</br>
 * 
 * The class enforces validation constraints to ensure reasonable limit requests:</br>
 * - Entity names must be between 1-40 characters</br>
 * - Day limits must be between 1-99 operations</br>
 * - All fields are required (not null)</br>
 */
public class LimitCudChange {

  /**
   * The name of the entity type for which the limit change is requested. This identifies the specific data type or
   * table that the user needs increased CUD operation privileges for. Must be between 1-40 characters.
   */
  @NotNull
  @Size(min = 1, max = 40)
  public String entity;

  /**
   * The expiration date for the requested limit change. This date determines how long the increased limit should remain
   * in effect before reverting to the default restrictions. Administrators typically set this to provide temporary
   * increased access for specific time periods.
   */
  @NotNull
  public LocalDate untilDate;

  /**
   * The requested daily limit for CUD operations. This represents the maximum number of create, update, and delete
   * operations the user wants to perform per day for the specified entity type. Must be between 1 and 99 operations per
   * day to ensure reasonable usage.
   */
  @NotNull
  @Min(value = 1)
  @Max(value = 99)
  public Integer dayLimit;

  public LocalDate getUntilDate() {
    return untilDate;
  }

  public void setUntilDate(LocalDate untilDate) {
    this.untilDate = untilDate;
  }

  public Integer getDayLimit() {
    return dayLimit;
  }

  public void setDayLimit(Integer dayLimit) {
    this.dayLimit = dayLimit;
  }

  public String getEntity() {
    return entity;
  }

  public void setEntity(String entity) {
    this.entity = entity;
  }

}
