package grafiosch.types;

import java.util.Arrays;

/**
 * Selects which anti-flooding mechanism a row of {@code entity_limit} configures.
 *
 * <p>
 * The three families are counted by different means and are never interchangeable: a {@link #MAX} key bounds how many
 * rows may exist at all and is answered by a live {@code count(*)}, while the two daily families bound how many
 * operations a user may perform on one calendar day and are answered by {@code user_entity_change_count}.
 * </p>
 */
public enum LimitType {

  /** Lifetime cap on the number of existing rows. Requires a registered limit key with a counter. */
  MAX((byte) 0),

  /** Daily budget of create/update/delete operations of one user on one entity. */
  DAY_CUD((byte) 1),

  /** Daily budget of read operations of one user on one pseudo entity, currently only {@code HistoryquoteRead}. */
  DAY_READ((byte) 2);

  private final Byte value;

  private LimitType(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static LimitType getLimitTypeByValue(byte value) {
    return Arrays.stream(LimitType.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
