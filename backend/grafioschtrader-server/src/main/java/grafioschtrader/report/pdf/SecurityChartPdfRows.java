package grafioschtrader.report.pdf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import grafioschtrader.entities.Transaction;
import grafioschtrader.report.pdf.SecurityChartPdfComposer.Row;
import grafioschtrader.reportviews.transaction.SecurityTransactionPosition;
import grafioschtrader.types.TransactionType;

/**
 * Chooses and maps the rows of the table of a chart PDF from the transaction positions the report calculated: the
 * transactions the chart marks, in the order of their booking, and the hypothetical sale of the open position at the
 * end. The figures are the ones the transaction table of the application shows, taken as they are.
 */
public final class SecurityChartPdfRows {

  /** Relative tolerance below which an adjusted price counts as equal to the booked price. */
  private static final double SAME_PRICE = 1e-9;

  private SecurityChartPdfRows() {
  }

  /**
   * Builds the rows. Which transactions exist is decided by the positions; the numbers only label them.
   *
   * @param positions the positions of the report in the scope of the chart
   * @param numbers   the number at the marker of each transaction the chart shows, by transaction id; a transaction
   *                  without number is not shown
   * @return the numbered transactions by booking time and id, then the hypothetical sale without a number. The sale of
   *         the open position is part of the table whatever part of the history the chart shows.
   */
  public static List<Row> of(List<SecurityTransactionPosition> positions, Map<Integer, Integer> numbers) {
    List<Row> rows = new ArrayList<>(positions.stream()
        .filter(position -> !isHypothetical(position) && numbers.containsKey(position.transaction.getIdTransaction()))
        .sorted(Comparator.comparing((SecurityTransactionPosition p) -> p.transaction.getTransactionTime())
            .thenComparing(p -> p.transaction.getIdTransaction()))
        .map(position -> toRow(position, numbers.get(position.transaction.getIdTransaction()))).toList());
    positions.stream().filter(SecurityChartPdfRows::isHypothetical).map(position -> toRow(position, null))
        .forEach(rows::add);
    return rows;
  }

  /** The sale or purchase that values an open position at the latest price; it is calculated, not booked. */
  static boolean isHypothetical(SecurityTransactionPosition position) {
    TransactionType type = position.transaction.getTransactionType();
    return type == TransactionType.HYPOTHETICAL_SELL || type == TransactionType.HYPOTHETICAL_BUY;
  }

  /**
   * Maps a position to a row. The hypothetical sale carries the flags of its disposal cost estimate; a booked row
   * carries whether its marker sits at a split-adjusted price.
   *
   * @param position the transaction with its gain
   * @param number   the number at its marker, null for the hypothetical sale
   */
  static Row toRow(SecurityTransactionPosition position, Integer number) {
    Transaction t = position.transaction;
    boolean hypothetical = isHypothetical(position);
    Double adjusted = position.quotationSplitCorrection;
    boolean splitAdjusted = !hypothetical && adjusted != null && t.getQuotation() != null
        && Math.abs(adjusted - t.getQuotation()) > SAME_PRICE * Math.max(1, Math.abs(t.getQuotation()));
    boolean estimateIncomplete = Boolean.TRUE.equals(position.disposalTaxCostIncomplete)
        || Boolean.TRUE.equals(position.disposalTransactionCostIncomplete);
    String currency = t.getCashaccount() == null ? null : t.getCashaccount().getCurrency();
    return new Row(number, t.getTransactionDateAsLocalDate(), t.getTransactionType().name(), t.getUnits(),
        t.getQuotation(), t.getCashaccountAmount(), currency, position.transactionGainLoss,
        position.transactionGainLossPercentage, splitAdjusted, Boolean.TRUE.equals(position.costBasisIncomplete),
        hypothetical, estimateIncomplete);
  }
}
