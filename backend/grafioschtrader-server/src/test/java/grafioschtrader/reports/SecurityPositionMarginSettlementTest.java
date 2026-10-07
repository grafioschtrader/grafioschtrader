package grafioschtrader.reports;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalDateTime;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.entities.Assetclass;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.reportviews.TransactionsMarginOpenUnits;
import grafioschtrader.reportviews.securityaccount.SecurityPositionSummary;
import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * The unrealized gain/loss of the open positions of a margin instrument is split by the currency of the cash account
 * each position settles into, which is what the holdings treemap needs to place it in the right cash tile.
 */
class SecurityPositionMarginSettlementTest {

  @Test
  @DisplayName("Open positions settling into CHF and USD split the gain/loss per settlement currency")
  void gainLossPerSettlementCurrency() {
    Security forex = new Security();
    forex.setIdSecuritycurrency(1);
    forex.setName("EUR/USD");
    forex.setCurrency("USD");
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(SpecialInvestmentInstruments.FOREX);
    forex.setAssetClass(assetclass);

    var position = new SecurityPositionSummary("CHF", forex, Map.of("CHF", 2, "USD", 2));
    position.adjustedCostBase = 1_000;
    // Long 1000 at 1.0, closed at 1.1: +100, settling into CHF
    addOpenPosition(position, 1, forex, cashaccount("CHF"), TransactionType.ACCUMULATE, 1_000, 1.0);
    // Short 500 at 1.2, closed at 1.1: +50, settling into USD
    addOpenPosition(position, 2, forex, cashaccount("USD"), TransactionType.REDUCE, 500, 1.2);

    position.calcGainLossByPrice(1.1);
    position.calcMainCurrency(0.9);

    assertEquals(100.0, position.marginGainLossBySettlementCurrency.get("CHF"), 0.0001);
    assertEquals(50.0, position.marginGainLossBySettlementCurrency.get("USD"), 0.0001);
    double sum = position.marginGainLossBySettlementCurrency.values().stream().mapToDouble(Double::doubleValue).sum();
    assertEquals(position.accountValueSecurity, sum, 0.0001);
    double sumMC = position.marginGainLossBySettlementCurrencyMC.values().stream().mapToDouble(Double::doubleValue)
        .sum();
    assertEquals(position.accountValueSecurityMC, sumMC, 0.0001);
    assertEquals(90.0, position.marginGainLossBySettlementCurrencyMC.get("CHF"), 0.0001);
  }

  private void addOpenPosition(SecurityPositionSummary position, int idTransaction, Security security,
      Cashaccount cashaccount, TransactionType transactionType, double units, double quotation) {
    Transaction transaction = new Transaction(1, cashaccount, security, units, quotation, transactionType, null, null,
        null, LocalDateTime.of(2026, 1, idTransaction, 10, 0), null);
    transaction.setAssetInvestmentValue2(1.0);
    position.getTransactionsMarginOpenUnitsMap().put(idTransaction,
        new TransactionsMarginOpenUnits(transaction, units, 0, 1.0));
  }

  private Cashaccount cashaccount(String currency) {
    Cashaccount cashaccount = new Cashaccount();
    cashaccount.setCurrency(currency);
    return cashaccount;
  }
}
