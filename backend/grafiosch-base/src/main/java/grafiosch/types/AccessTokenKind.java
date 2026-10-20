package grafiosch.types;

import java.util.Arrays;

/**
 * Where an access token comes from. A personal token is created by the user in the token dialog; the two OAuth kinds
 * are issued to a client after the user's consent and belong to an OAuth authorization, the token family of that
 * consent.
 */
public enum AccessTokenKind {
  /** Created by the user, shown once, accepted on the machine endpoint. */
  PERSONAL((byte) 1),
  /** Issued at the token endpoint, valid for one hour and for one resource, accepted on the machine endpoint. */
  OAUTH_ACCESS((byte) 2),
  /** Issued at the token endpoint, accepted only there to obtain a new pair; rotated on every use. */
  OAUTH_REFRESH((byte) 3);

  private final Byte value;

  private AccessTokenKind(final Byte value) {
    this.value = value;
  }

  public Byte getValue() {
    return this.value;
  }

  public static AccessTokenKind getAccessTokenKindByValue(byte value) {
    return Arrays.stream(AccessTokenKind.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
