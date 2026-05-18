package grafioschtrader.connector.instrument.fred;

import java.util.List;

/**
 * Top-level shape of the FRED {@code series/observations} JSON response. Only {@code observations} is consumed;
 * remaining envelope fields (count, offset, limit, units, ...) are ignored via
 * {@code FAIL_ON_UNKNOWN_PROPERTIES=false}.
 */
class FredResponse {
  public List<FredObservation> observations;
}
