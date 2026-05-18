package grafioschtrader.connector.instrument.fred;

/**
 * Single observation row from the FRED series/observations endpoint. The {@code value} field is a string because FRED
 * encodes missing data as ".".
 */
class FredObservation {
  public String date;
  public String value;
}
