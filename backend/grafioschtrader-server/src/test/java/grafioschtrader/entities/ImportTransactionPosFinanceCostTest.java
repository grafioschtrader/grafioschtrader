package grafioschtrader.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import grafioschtrader.types.SpecialInvestmentInstruments;
import grafioschtrader.types.TransactionType;

/**
 * Pure unit tests for the FINANCE_COST branch of {@link ImportTransactionPos#calcCashaccountAmount()}. The calculated
 * amount must mirror Transaction.validateSecurityMarginCashaccountAmount: for margin instruments the finance cost
 * (units = number of days, quotation = daily cost) is the REDUCE formula negated, i.e. a cash outflow.
 */
class ImportTransactionPosFinanceCostTest {

  private Security securityWith(SpecialInvestmentInstruments specialInvestmentInstrument) {
    Assetclass assetclass = new Assetclass();
    assetclass.setSpecialInvestmentInstrument(specialInvestmentInstrument);
    Security security = new Security();
    security.setAssetClass(assetclass);
    return security;
  }

  private ImportTransactionPos financeCostPos(SpecialInvestmentInstruments specialInvestmentInstrument, double units,
      double quotation, double cashaccountAmount) {
    ImportTransactionPos pos = new ImportTransactionPos();
    pos.setTransactionType(TransactionType.FINANCE_COST.getValue());
    pos.setSecurity(securityWith(specialInvestmentInstrument));
    pos.setUnits(units);
    pos.setQuotation(quotation);
    pos.setCashaccountAmount(cashaccountAmount);
    return pos;
  }

  @Test
  @DisplayName("Margin finance cost: days times daily cost gives a negative cash amount")
  void marginFinanceCostIsNegative() {
    ImportTransactionPos pos = financeCostPos(SpecialInvestmentInstruments.CFD, 31.0, 1.5, 46.5);
    pos.calcCashaccountAmount();
    assertThat(pos.getCalcCashaccountAmount()).isEqualTo(-46.5);
    assertThat(pos.getCashaccountAmount()).isEqualTo(-46.5);
  }

  @Test
  @DisplayName("Margin finance cost accepts the document amount regardless of its sign")
  void marginFinanceCostNormalizesDocumentSign() {
    ImportTransactionPos pos = financeCostPos(SpecialInvestmentInstruments.CFD, 10.0, 2.0, -20.0);
    pos.calcCashaccountAmount();
    assertThat(pos.getCalcCashaccountAmount()).isEqualTo(-20.0);
    assertThat(pos.getCashaccountAmount()).isEqualTo(-20.0);
  }

  @Test
  @DisplayName("Non-margin finance cost keeps the REDUCE sign as in the transaction validation")
  void nonMarginFinanceCostKeepsReduceSign() {
    ImportTransactionPos pos = financeCostPos(SpecialInvestmentInstruments.DIRECT_INVESTMENT, 31.0, 1.5, 46.5);
    pos.calcCashaccountAmount();
    assertThat(pos.getCalcCashaccountAmount()).isEqualTo(46.5);
    assertThat(pos.getCashaccountAmount()).isEqualTo(46.5);
  }
}
