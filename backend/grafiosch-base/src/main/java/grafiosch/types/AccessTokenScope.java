package grafiosch.types;

import java.util.Arrays;

/**
 * What a personal access token may do on the paths that accept it. The token never reaches a path outside the
 * application's machine endpoint, so the scope only distinguishes reading from proposing changes there.
 */
public enum AccessTokenScope {
  /** Reading tools only. */
  READ((byte) 1),
  /** Reading and proposing changes; the proposals still have to be approved in the user interface. */
  WRITE((byte) 2);

  private final Byte value;

  private AccessTokenScope(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AccessTokenScope getAccessTokenScopeByValue(byte value) {
    return Arrays.stream(AccessTokenScope.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
