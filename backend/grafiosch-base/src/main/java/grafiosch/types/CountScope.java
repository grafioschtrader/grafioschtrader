package grafiosch.types;

import java.util.Arrays;

/**
 * For a nested-collection limit, whether the elements are counted per parent or across all parents of the owner.
 *
 * <p>
 * Both variants exist for the same pair today: instruments per single watchlist ({@link #SINGLE}) and instruments over
 * all watchlists of a tenant ({@link #ALL}). The scope is {@code null} for a flat cap that has no parent relation.
 * </p>
 */
public enum CountScope {

  /** Count the elements of every parent belonging to the owner. */
  ALL((byte) 0),

  /** Count only the elements of the one parent the new element goes into. */
  SINGLE((byte) 1);

  private final Byte value;

  private CountScope(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static CountScope getCountScopeByValue(byte value) {
    return Arrays.stream(CountScope.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
