package grafioschtrader.receipt;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HashSet;
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
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Transaction;
import grafioschtrader.receipt.GtReceiptDefs.Labels;
import grafioschtrader.receipt.TransactionReceiptPdfGenerator.ReceiptContext;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Creates transaction receipt PDFs for selected security transactions and writes them to the HTTP response. A single
 * selected transaction is returned as a plain PDF; multiple transactions are bundled into a ZIP archive with one PDF
 * per transaction.
 *
 * <p>
 * File names follow the pattern <code>yyyyMMdd_&lt;localized type word&gt;.pdf</code>. When more than one selected
 * transaction falls on the same calendar day, those file names are extended with the transaction time to
 * <code>yyyyMMdd_HHmmss_&lt;type word&gt;.pdf</code>; a residual collision (same second and type) gets a numeric
 * suffix. The receipt language and the type word follow the language of the authenticated user (German or English).
 * </p>
 */
@Service
public class TransactionReceiptService {

  private static final DateTimeFormatter FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
  private static final DateTimeFormatter FILE_TIME_FORMAT = DateTimeFormatter.ofPattern("HHmmss");

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  private final TransactionReceiptPdfGenerator pdfGenerator = new TransactionReceiptPdfGenerator();

  /**
   * Generates the receipts for the given transaction IDs of the authenticated tenant and writes a single PDF or a ZIP
   * archive to the response.
   *
   * @param idTransactions the selected transaction IDs; every ID must belong to a supported security transaction of
   *                       the user's tenant
   * @param response       the HTTP response the document is streamed to
   * @throws Exception if a transaction is missing/unsupported or PDF generation fails
   */
  @Transactional(readOnly = true)
  public void createReceipts(List<Integer> idTransactions, HttpServletResponse response) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final Locale locale = user.createAndGetJavaLocale();
    final Labels labels = GtReceiptDefs.forLocale(locale);

    List<Integer> idsDistinct = idTransactions == null ? List.of() : idTransactions.stream().distinct().toList();
    if (idsDistinct.isEmpty()) {
      throw new DataViolationException("transaction", "gt.receipt.transaction.missing", null);
    }
    List<Transaction> transactions = transactionJpaRepository
        .findForReceiptsByIdTenantAndIdTransactionIn(user.getIdTenant(), idsDistinct);
    if (transactions.size() != idsDistinct.size()) {
      throw new DataViolationException("transaction", "gt.receipt.transaction.missing", null);
    }
    for (Transaction transaction : transactions) {
      if (!GtReceiptDefs.isSupported(transaction.getTransactionType())) {
        throw new DataViolationException("transaction.type", "gt.receipt.unsupported.transactiontype",
            new Object[] { transaction.getTransactionType().name() });
      }
    }

    List<String> fileNames = createFileNames(transactions, labels);
    if (transactions.size() == 1) {
      byte[] pdf = generate(transactions.get(0), locale, user);
      response.setContentType("application/pdf");
      response.setHeader("Content-Disposition", "attachment; filename=\"" + fileNames.get(0) + "\"");
      response.getOutputStream().write(pdf);
    } else {
      response.setContentType("application/zip");
      response.setHeader("Content-Disposition", "attachment; filename=\"transaction_receipts.zip\"");
      try (ZipOutputStream zos = new ZipOutputStream(response.getOutputStream())) {
        for (int i = 0; i < transactions.size(); i++) {
          zos.putNextEntry(new ZipEntry(fileNames.get(i)));
          zos.write(generate(transactions.get(i), locale, user));
          zos.closeEntry();
        }
      }
    }
  }

  private byte[] generate(Transaction transaction, Locale locale, User user) throws Exception {
    return pdfGenerator.generate(transaction, locale,
        new ReceiptContext(user.getNickname(), getSecurityaccountName(transaction, user),
            transaction.getCashaccount().getName()));
  }

  private String getSecurityaccountName(Transaction transaction, User user) {
    if (transaction.getIdSecurityaccount() == null) {
      return null;
    }
    Securityaccount securityaccount = securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(transaction.getIdSecurityaccount(), user.getIdTenant());
    return securityaccount == null ? null : securityaccount.getName();
  }

  /**
   * Creates the file names for the given transactions in matching order. The time infix is only added for days that
   * carry more than one selected transaction; a numeric suffix resolves remaining duplicates.
   */
  private List<String> createFileNames(List<Transaction> transactions, Labels labels) {
    Map<LocalDate, Integer> countPerDay = new HashMap<>();
    for (Transaction transaction : transactions) {
      countPerDay.merge(transaction.getTransactionTime().toLocalDate(), 1, Integer::sum);
    }
    Set<String> usedNames = new HashSet<>();
    return transactions.stream().map(transaction -> {
      StringBuilder name = new StringBuilder(FILE_DATE_FORMAT.format(transaction.getTransactionTime()));
      if (countPerDay.get(transaction.getTransactionTime().toLocalDate()) > 1) {
        name.append('_').append(FILE_TIME_FORMAT.format(transaction.getTransactionTime()));
      }
      name.append('_').append(GtReceiptDefs.getTransactionTypeWord(transaction, labels));
      String baseName = name.toString();
      String uniqueName = baseName;
      for (int i = 2; !usedNames.add(uniqueName); i++) {
        uniqueName = baseName + "_" + i;
      }
      return uniqueName + ".pdf";
    }).toList();
  }
}
