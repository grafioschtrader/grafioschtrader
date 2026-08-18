package grafiosch.types;

import java.util.Arrays;

/**
 * Selects the column by which existing rows are counted for a {@link LimitType#MAX} key. It is {@code null} for the
 * daily limit types, which never count rows of the guarded table.
 */
public enum OwnerScope {

  /** Count rows of the acting user's tenant, by {@code id_tenant}. */
  TENANT((byte) 0),

  /** Count every row of the table, regardless of who owns it. Used for caps on data shared by all tenants. */
  GLOBAL((byte) 1),

  /**
   * Count rows whose current {@code created_by} is the acting user. The count moves with the rows when
   * {@code MoveCreatedByUserToOtherUserTask} reassigns shared data, which is intended: the cap bounds who is
   * answerable for the rows, not who originally typed them.
   */
  CREATOR((byte) 2);

  private final Byte value;

  private OwnerScope(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static OwnerScope getOwnerScopeByValue(byte value) {
    return Arrays.stream(OwnerScope.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
