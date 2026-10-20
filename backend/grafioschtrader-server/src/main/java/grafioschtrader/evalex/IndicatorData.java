package grafioschtrader.evalex;

/**
 * Inputs an expression requires; alerts deliberately provide only SERIES, so they load no fundamentals and stay
 * independent of external fundamental sources.
 */
public enum IndicatorData {
  SERIES, BENCHMARK, DIVIDENDS, FUNDAMENTALS
}
