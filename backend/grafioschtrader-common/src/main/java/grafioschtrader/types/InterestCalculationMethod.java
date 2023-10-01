package grafioschtrader.types;

public enum InterestCalculationMethod {
  GERMAN_30_360, // Euro interest method, French interest method
  ENGLISH_ACT_365, // English interest method
  ACT_ACT, // Exact or effective interest method
  GERMAN, // German (commercial) interest method
  US // US interest method

}
