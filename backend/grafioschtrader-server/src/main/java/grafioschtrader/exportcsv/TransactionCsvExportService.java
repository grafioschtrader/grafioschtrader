package grafioschtrader.exportcsv;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Exports the tenant's transactions as re-importable CSV files and writes them to the HTTP response. Because a CSV
 * import run is always bound to exactly one securities account, the export produces <b>one CSV file per securities
 * account</b>: a single file is returned as plain CSV, several files are bundled into a ZIP archive.
 *
 * <p>
 * Grouping rules: security transactions go to the file of their securities account. Cash-only transactions
 * (DEPOSIT/WITHDRAWAL/FEE/INTEREST_CASHACCOUNT carry no securities account) are assigned to the file of the
 * designated securities account of their cash account's portfolio — the one with the lowest ID. On re-import the
 * head's securities account is irrelevant for cash rows since the cash account is resolved per row by currency
 * within the portfolio. Transactions of a portfolio without any securities account land in a portfolio-named
 * fallback file that documents the rows but cannot be imported without creating a securities account first.
 * </p>
 *
 * <p>
 * The optional portfolio filter restricts the export to the cash accounts of one portfolio; the optional date range
 * filters on the transaction date. The CSV wire format, transfer pairing and margin handling are described in
 * {@link GtCsvExportDefs} and {@link TransactionCsvExportGenerator}.
 * </p>
 */
@Service
public class TransactionCsvExportService {

  private static final String FILE_PREFIX = "gt_transactions_";
  private static final String ZIP_FILE_NAME = "gt_transaction_export.zip";
  /** Characters not allowed in the generated file names (file system and HTTP header safety). */
  private static final String ILLEGAL_FILE_NAME_CHARS = "[\\\\/:*?\"<>|;]";

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  private final TransactionCsvExportGenerator generator = new TransactionCsvExportGenerator();

  /**
   * Exports the transactions of the authenticated tenant and streams a single CSV or a ZIP archive to the response.
   *
   * @param idPortfolio optional portfolio filter; null exports the whole tenant
   * @param dateFrom    optional inclusive lower bound on the transaction date
   * @param dateTo      optional inclusive upper bound on the transaction date
   * @param response    the HTTP response the document is streamed to
   * @throws IOException if writing to the response fails
   */
  @Transactional(readOnly = true)
  public void exportTransactions(Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo,
      HttpServletResponse response) throws IOException {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Locale locale = user.createAndGetJavaLocale();

    List<Transaction> transactions = transactionJpaRepository.findForCsvExportByIdTenant(user.getIdTenant()).stream()
        .filter(t -> matchesScope(t, idPortfolio, dateFrom, dateTo)).toList();
    if (transactions.isEmpty()) {
      throw new DataViolationException("transaction", "gt.export.transaction.missing", null);
    }

    Map<Integer, Securityaccount> accountsById = new HashMap<>();
    Map<Integer, Securityaccount> designatedPerPortfolio = new HashMap<>();
    for (Securityaccount account : securityaccountJpaRepository.findByIdTenant(user.getIdTenant())) {
      accountsById.put(account.getIdSecuritycashAccount(), account);
      designatedPerPortfolio.merge(account.getPortfolio().getIdPortfolio(), account,
          (a, b) -> a.getIdSecuritycashAccount() <= b.getIdSecuritycashAccount() ? a : b);
    }

    Map<FileKey, List<Transaction>> fileGroups = new LinkedHashMap<>();
    for (Transaction transaction : transactions) {
      fileGroups.computeIfAbsent(fileKeyOf(transaction, designatedPerPortfolio), _ -> new ArrayList<>())
          .add(transaction);
    }
    writeResponse(fileGroups, accountsById, locale, response);
  }

  private boolean matchesScope(Transaction transaction, Integer idPortfolio, LocalDate dateFrom, LocalDate dateTo) {
    if (idPortfolio != null && !transaction.getCashaccount().getPortfolio().getIdPortfolio().equals(idPortfolio)) {
      return false;
    }
    LocalDate transactionDate = transaction.getTransactionTime().toLocalDate();
    return (dateFrom == null || !transactionDate.isBefore(dateFrom))
        && (dateTo == null || !transactionDate.isAfter(dateTo));
  }

  private FileKey fileKeyOf(Transaction transaction, Map<Integer, Securityaccount> designatedPerPortfolio) {
    if (transaction.getIdSecurityaccount() != null) {
      return new FileKey(transaction.getIdSecurityaccount(), null, null);
    }
    Portfolio portfolio = transaction.getCashaccount().getPortfolio();
    Securityaccount designated = designatedPerPortfolio.get(portfolio.getIdPortfolio());
    return designated != null ? new FileKey(designated.getIdSecuritycashAccount(), null, null)
        : new FileKey(null, portfolio.getIdPortfolio(), portfolio.getName());
  }

  private void writeResponse(Map<FileKey, List<Transaction>> fileGroups, Map<Integer, Securityaccount> accountsById,
      Locale locale, HttpServletResponse response) throws IOException {
    Map<FileKey, String> fileNames = createFileNames(fileGroups.keySet(), accountsById);
    if (fileGroups.size() == 1) {
      Map.Entry<FileKey, List<Transaction>> single = fileGroups.entrySet().iterator().next();
      response.setContentType("text/csv;charset=UTF-8");
      response.setHeader("Content-Disposition", "attachment; filename=\"" + fileNames.get(single.getKey()) + "\"");
      response.getOutputStream().write(generateFile(single.getValue(), locale).getBytes(StandardCharsets.UTF_8));
    } else {
      response.setContentType("application/zip");
      response.setHeader("Content-Disposition", "attachment; filename=\"" + ZIP_FILE_NAME + "\"");
      try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
        for (Map.Entry<FileKey, List<Transaction>> entry : fileGroups.entrySet()) {
          zos.putNextEntry(new ZipEntry(fileNames.get(entry.getKey())));
          zos.write(generateFile(entry.getValue(), locale).getBytes(StandardCharsets.UTF_8));
          zos.closeEntry();
        }
      }
    }
  }

  private String generateFile(List<Transaction> transactionsOfFile, Locale locale) {
    Set<Integer> idsInThisFile = new HashSet<>();
    transactionsOfFile.forEach(t -> idsInThisFile.add(t.getIdTransaction()));
    return generator.generate(transactionsOfFile, idsInThisFile, locale);
  }

  /**
   * Creates one file name per group: the securities account name, or the portfolio name for the fallback file of a
   * portfolio without securities accounts. Illegal characters are replaced and residual duplicates get a numeric
   * suffix.
   */
  private Map<FileKey, String> createFileNames(Set<FileKey> fileKeys, Map<Integer, Securityaccount> accountsById) {
    Set<String> usedNames = new HashSet<>();
    Map<FileKey, String> fileNames = new HashMap<>();
    for (FileKey fileKey : fileKeys) {
      String rawName;
      if (fileKey.idSecurityaccount() != null) {
        Securityaccount account = accountsById.get(fileKey.idSecurityaccount());
        rawName = account != null ? account.getName() : String.valueOf(fileKey.idSecurityaccount());
      } else {
        rawName = "portfolio_" + fileKey.portfolioName();
      }
      String baseName = FILE_PREFIX + rawName.replaceAll(ILLEGAL_FILE_NAME_CHARS, "_").strip();
      String uniqueName = baseName;
      for (int i = 2; !usedNames.add(uniqueName); i++) {
        uniqueName = baseName + "_" + i;
      }
      fileNames.put(fileKey, uniqueName + ".csv");
    }
    return fileNames;
  }

  /**
   * Grouping key of one export file: the securities account, or the portfolio (ID and name) for the fallback file of
   * a portfolio without securities accounts.
   */
  private record FileKey(Integer idSecurityaccount, Integer idPortfolio, String portfolioName) {
  }
}
