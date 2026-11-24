package grafioschtrader.types;

import java.util.Arrays;

import grafiosch.types.StableEnum;

/**
 * Scope of an {@link grafioschtrader.entities.InstrumentLogo}: either a company logo (typically matched by ISIN or
 * explicit assignment) or an issuer wordmark (automatically matched for ETFs, mutual funds and issuer-risk products).
 */
public enum LogoScope implements StableEnum {

  /** Company logo for an individual enterprise (share, bond, etc.). */
  COMPANY((byte) 0),

  /** Issuer or fund-provider wordmark (ETF, mutual fund, issuer-risk product). */
  ISSUER((byte) 1);

  private final Byte value;

  private LogoScope(final Byte value) {
    this.value = value;
  }

  @Override
  public Byte getValue() {
    return this.value;
  }

  public static LogoScope getLogoScopeByValue(byte value) {
    return Arrays.stream(LogoScope.values()).filter(e -> e.getValue().equals(value)).findFirst().orElse(null);
  }
}
