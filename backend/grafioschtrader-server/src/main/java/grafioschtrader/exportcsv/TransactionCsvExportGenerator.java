package grafioschtrader.exportcsv;

import java.text.DecimalFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import grafioschtrader.entities.Transaction;
import grafioschtrader.exportcsv.GtCsvExportDefs.Labels;
import grafioschtrader.types.TransactionType;

/**
 * Generates the re-importable transaction CSV of one export file (one securities account). The produced text follows
 * the wire format defined in {@link GtCsvExportDefs} and is parsed back by the CSV transaction import using the
 * paired templates <code>csv_base-csv-20000101-de|en.tmpl</code>; the round-trip test
 * <code>TransactionCsvExportRoundTripTest</code> guards both sides.
 *
 * <p>
 * Rules applied per row:
 * </p>
 * <ul>
 * <li><b>Order numbers:</b> the two sides of a cash transfer whose counterpart lands in the same file share a
 * synthetic order number that is unique within their calendar day, so the import's order grouping re-pairs them
 * (accountTransferSamePortfolio). All other rows carry {@link GtCsvExportDefs#ORDER_NONE}.</li>
 * <li><b>Exchange rate suppression:</b> WITHDRAWAL/DEPOSIT rows without a same-file counterpart are written without
 * exchange rate — the import cannot resolve a currency pair for an unconnected transfer side; the post-import relink
 * step recreates rate and currency pair. Other cash rows never carry an exchange rate.</li>
 * <li><b>Margin marker:</b> margin-instrument open/close rows (ACCUMULATE/REDUCE) get {@link GtCsvExportDefs#MARGIN_MARKER}
 * in the Marker column and are skipped by the import (securityRisk and the close-to-open link cannot be
 * reconstructed). FINANCE_COST rows are exported without the marker; the import links them to the open margin
 * position or fails the row visibly.</li>
 * <li><b>Accrued interest</b> is only written for non-margin instruments; on margin transactions
 * assetInvestmentValue1 holds the security risk instead.</li>
 * </ul>
 */
public class TransactionCsvExportGenerator {

  /**
   * Generates the CSV text for the given rows of one export file.
   *
   * @param transactionsOfFile the transactions belonging to this file, any order
   * @param idsInThisFile      the transaction IDs contained in this file; decides transfer pairing and exchange rate
   *                           suppression for transfer sides whose counterpart lives in another file
   * @param locale             language of the column headers and type words (German or English wire format)
   * @return the complete CSV content including the header line, lines separated by '\n'
   */
  public String generate(List<Transaction> transactionsOfFile, Set<Integer> idsInThisFile, Locale locale) {
    Labels labels = GtCsvExportDefs.forLocale(locale);
    DecimalFormat amountFormat = GtCsvExportDefs.createAmountFormat();
    DecimalFormat unitsFormat = GtCsvExportDefs.createUnitsFormat();
    List<Transaction> sorted = new ArrayList<>(transactionsOfFile);
    sorted.sort(Comparator.comparing(Transaction::getTransactionTime)
        .thenComparing(Transaction::getIdTransaction, Comparator.nullsLast(Comparator.naturalOrder())));
    Map<Integer, String> transferOrders = assignTransferOrders(sorted, idsInThisFile);

    StringBuilder sb = new StringBuilder();
    sb.append(String.join(GtCsvExportDefs.DELIMITER, labels.headersInOrder())).append('\n');
    for (Transaction transaction : sorted) {
      appendRow(sb, transaction, transferOrders, labels, amountFormat, unitsFormat);
    }
    return sb.toString();
  }

  /**
   * Assigns the shared synthetic order number to every cash transfer pair whose both sides are in this file. The
   * counter restarts per calendar day, matching the import's day-batched order grouping.
   */
  private Map<Integer, String> assignTransferOrders(List<Transaction> sorted, Set<Integer> idsInThisFile) {
    Map<Integer, String> transferOrders = new HashMap<>();
    Map<LocalDate, Integer> counterPerDay = new HashMap<>();
    for (Transaction transaction : sorted) {
      if (isTransferSide(transaction) && !transferOrders.containsKey(transaction.getIdTransaction())
          && idsInThisFile.contains(transaction.getConnectedIdTransaction())) {
        String orderNr = String
            .valueOf(counterPerDay.merge(transaction.getTransactionTime().toLocalDate(), 1, Integer::sum));
        transferOrders.put(transaction.getIdTransaction(), orderNr);
        transferOrders.put(transaction.getConnectedIdTransaction(), orderNr);
      }
    }
    return transferOrders;
  }

  private boolean isTransferSide(Transaction transaction) {
    return transaction.getConnectedIdTransaction() != null
        && (transaction.getTransactionType() == TransactionType.WITHDRAWAL
            || transaction.getTransactionType() == TransactionType.DEPOSIT);
  }

  private void appendRow(StringBuilder sb, Transaction transaction, Map<Integer, String> transferOrders, Labels labels,
      DecimalFormat amountFormat, DecimalFormat unitsFormat) {
    boolean securityRow = transaction.getSecurity() != null;
    boolean marginInstrument = securityRow && transaction.getSecurity().isMarginInstrument();
    String[] cells = new String[16];
    cells[0] = GtCsvExportDefs.DATETIME_FORMAT.format(transaction.getTransactionTime());
    cells[1] = transferOrders.getOrDefault(transaction.getIdTransaction(), GtCsvExportDefs.ORDER_NONE);
    cells[2] = GtCsvExportDefs.getTransactionTypeWord(transaction.getTransactionType(), labels);
    cells[3] = securityRow ? sanitize(transaction.getSecurity().getTickerSymbol()) : "";
    cells[4] = securityRow ? sanitize(transaction.getSecurity().getName()) : "";
    cells[5] = securityRow ? sanitize(transaction.getSecurity().getIsin()) : "";
    cells[6] = securityRow ? format(unitsFormat, transaction.getUnits()) : "";
    cells[7] = securityRow ? format(amountFormat, transaction.getQuotation()) : "";
    cells[8] = securityRow && !marginInstrument ? format(amountFormat, transaction.getAssetInvestmentValue1()) : "";
    cells[9] = format(amountFormat, transaction.getTransactionCost());
    cells[10] = format(amountFormat, transaction.getTaxCost());
    cells[11] = securityRow ? transaction.getSecurity().getCurrency() : "";
    cells[12] = format(amountFormat, transaction.getCashaccountAmount());
    cells[13] = transaction.getCashaccount().getCurrency();
    cells[14] = writeExchangeRate(transaction, transferOrders) ? format(amountFormat, transaction.getCurrencyExRate())
        : "";
    cells[15] = marginInstrument && (transaction.getTransactionType() == TransactionType.ACCUMULATE
        || transaction.getTransactionType() == TransactionType.REDUCE) ? GtCsvExportDefs.MARGIN_MARKER : "";
    sb.append(String.join(GtCsvExportDefs.DELIMITER, cells)).append('\n');
  }

  /**
   * Security rows keep their stored exchange rate. Cash rows only carry one when they are a transfer pair within this
   * file: an unconnected WITHDRAWAL/DEPOSIT with an exchange rate cannot be processed by the import (no counterpart
   * to derive the currency pair from), and FEE/INTEREST_CASHACCOUNT rows have no currency conversion.
   */
  private boolean writeExchangeRate(Transaction transaction, Map<Integer, String> transferOrders) {
    if (transaction.getSecurity() != null) {
      return true;
    }
    return transferOrders.containsKey(transaction.getIdTransaction());
  }

  private String format(DecimalFormat decimalFormat, Double value) {
    return value == null ? "" : decimalFormat.format(value);
  }

  /** Frees free-text cells from characters that would break the CSV structure (delimiter, line breaks). */
  private String sanitize(String value) {
    return value == null ? "" : value.replace(GtCsvExportDefs.DELIMITER, ",").replaceAll("[\\r\\n]+", " ").strip();
  }
}
