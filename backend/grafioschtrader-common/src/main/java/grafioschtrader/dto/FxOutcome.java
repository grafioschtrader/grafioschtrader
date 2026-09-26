package grafioschtrader.dto;

/** Distinguishes a known zero tariff from missing coverage and invalid configuration. */
public enum FxOutcome {
  MATCHED, NO_SECTION, NO_PERIOD, NO_RULE, NO_TIER_RATE, INVALID
}
