package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonFormat;

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.rest.UpdateCreate;
import grafiosch.rest.UpdateCreateJpaRepository;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ClosedMarginUnits;
import grafioschtrader.dto.ExDateFromTaxDataResult;
import grafioschtrader.dto.ProposedMarginFinanceCost;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Transaction.CashTransaction;
import grafioschtrader.entities.Transaction.SecurityTransaction;
import grafioschtrader.exportcsv.CashTransferRelinkResult;
import grafioschtrader.exportcsv.CashTransferRelinkService;
import grafioschtrader.exportcsv.TransactionCsvExportService;
import grafioschtrader.instrument.SecurityMarginUnitsCheck;
import grafioschtrader.receipt.TransactionReceiptService;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;
import grafioschtrader.repository.TransactionJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@RestController
@RequestMapping(RequestGTMappings.TRANSACTION_MAP)
@Tag(name = Transaction.TABNAME, description = "Controller for transaction")
public class TransactionResource extends UpdateCreate<Transaction> {

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private TransactionReceiptService transactionReceiptService;

  @Autowired
  private TransactionCsvExportService transactionCsvExportService;

  @Autowired
  private CashTransferRelinkService cashTransferRelinkService;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  protected UpdateCreateJpaRepository<Transaction> getUpdateCreateJpaRepository() {
    return transactionJpaRepository;
  }

  @Operation(summary = "Create transaction receipt PDFs for the given security transactions of the tenant", description = """
      Generates one receipt PDF per transaction in the language of the user. A single transaction is returned as PDF,
      multiple transactions as ZIP archive. The receipts can be re-imported through the PDF transaction import using
      the Grafioschtrader import templates.""", tags = { Transaction.TABNAME })
  @PostMapping(value = "/receipts")
  public void createTransactionReceipts(@RequestBody TransactionReceiptRequest transactionReceiptRequest,
      HttpServletResponse response) throws Exception {
    transactionReceiptService.createReceipts(transactionReceiptRequest.idTransactions(), response);
  }

  /**
   * Request body of the transaction receipt creation.
   *
   * @param idTransactions the IDs of the security transactions a receipt PDF is created for
   */
  public record TransactionReceiptRequest(List<Integer> idTransactions) {
  }

  @Operation(summary = "Export the transactions of the tenant or one portfolio as re-importable CSV", description = """
      Produces one CSV file per securities account in the language of the user: a single file is returned as plain
      CSV, several files as ZIP archive. The files can be re-imported through the CSV transaction import using the
      Grafioschtrader import templates. Margin open/close rows are exported but marked as not re-importable.""", tags = {
      Transaction.TABNAME })
  @PostMapping(value = "/exportcsv")
  public void exportTransactionsCsv(@RequestBody TransactionCsvExportRequest transactionCsvExportRequest,
      HttpServletResponse response) throws Exception {
    transactionCsvExportService.exportTransactions(transactionCsvExportRequest.idPortfolio(),
        transactionCsvExportRequest.dateFrom(), transactionCsvExportRequest.dateTo(), response);
  }

  /**
   * Request body of the transaction CSV export.
   *
   * @param idPortfolio optional portfolio filter; null exports the whole tenant
   * @param dateFrom    optional inclusive lower bound on the transaction date
   * @param dateTo      optional inclusive upper bound on the transaction date
   */
  public record TransactionCsvExportRequest(Integer idPortfolio,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate dateFrom,
      @JsonFormat(pattern = BaseConstants.STANDARD_DATE_FORMAT) LocalDate dateTo) {
  }

  @Operation(summary = "Connect the unlinked withdrawal/deposit sides of cash account transfers", description = """
      Maintenance step after re-importing exported CSV files: transfers whose two sides were imported through
      separate files are matched by transaction time and amounts and connected again. Only unambiguous one-to-one
      matches are linked; ambiguous or rejected candidates are reported.""", tags = { Transaction.TABNAME })
  @PostMapping(value = "/connecttransfers", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CashTransferRelinkResult> connectCashTransfers() {
    return new ResponseEntity<>(cashTransferRelinkService.relinkCashTransfers(), HttpStatus.OK);
  }

  @Operation(summary = "Get connected transaction for a existing open margin position", description = "", tags = {
      Transaction.TABNAME })
  @GetMapping(value = "/connectedmargin/{idTransaction}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ClosedMarginUnits> getClosedMarginUnitsByIdTransaction(
      @Parameter(description = "Id of open margin position", required = true) @PathVariable final Integer idTransaction) {
    return new ResponseEntity<>(transactionJpaRepository.getClosedMarginUnitsByIdTransaction(idTransaction),
        HttpStatus.OK);
  }

  @Operation(summary = "Get all transactions of a tenant", description = "", tags = { Transaction.TABNAME })
  @GetMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Transaction>> getTransactionByTenant() {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Transaction> transaction = transactionJpaRepository
        .findByIdTenantOrderByTransactionTimeDesc(user.getIdTenant());
    return new ResponseEntity<>(transaction, HttpStatus.OK);
  }

  @Operation(summary = "Get all transactions of a portfolio", description = "", tags = { Transaction.TABNAME })
  @GetMapping(value = "/portfolio/{idPortfolio}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<List<Transaction>> getTransactionByPortfolio(
      @Parameter(description = "Id of portfolio", required = true) @PathVariable final Integer idPortfolio) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Transaction> transaction = transactionJpaRepository.getTransactionsByIdPortfolio(idPortfolio,
        user.getIdTenant());
    return new ResponseEntity<>(transaction, HttpStatus.OK);
  }

  @Operation(summary = "Get a single transaction by transactions Id", description = "", tags = { Transaction.TABNAME })
  @GetMapping(value = "/{idTransaction}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> getTransactionByIdTransaction(
      @Parameter(description = "Id of transaction", required = true) @PathVariable final Integer idTransaction) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Transaction transaction = transactionJpaRepository.getReferenceById(idTransaction);
    if (!user.getIdTenant().equals(transaction.getIdTenant())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    return new ResponseEntity<>(transaction, HttpStatus.OK);
  }

  @Operation(summary = "Get all transactions from a cash account by Id cash account", description = "", tags = {
      Transaction.TABNAME })
  @GetMapping(value = "/{idSecuritycashAccount}/cashaccount", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CashaccountTransactionPosition[]> getTransactionsWithBalanceForCashaccount(
      @Parameter(description = "Id of cash account", required = true) @PathVariable final Integer idSecuritycashAccount,
      @RequestParam(value = "transactionTypes[]", required = false) final int[] transactionTypesNum,
      @RequestParam(required = true) final int year) {
    return new ResponseEntity<>(transactionJpaRepository.getTransactionsWithBalanceForCashaccount(idSecuritycashAccount,
        year, transactionTypesNum), HttpStatus.OK);
  }

  /**
   * Creates a transaction through the generic mapping inherited from {@link UpdateCreate}, enforcing the lifetime
   * per-tenant transaction limit that the specialised endpoints below already enforce.
   *
   * <p>
   * Neither of the generic guards reaches this entity: {@code Transaction} is a {@code TenantBaseID}, so
   * {@code createEntity} skips the daily CUD check, and {@code LimitKeyConfig.KEY_TRANSACTION} is registered with
   * {@code checkedOnGenericCreate = false} so that the specialised call sites can raise the specific translated
   * message instead of a bare {@code LIMIT_SECURITY_BREACH}. Without this override the generic mapping would grow the
   * {@code transaction} table without any cap.
   * </p>
   *
   * <p>
   * This also covers a {@code PUT} carrying no id: {@code UpdateCreate.updateEntity} delegates such a request to
   * {@code create}, which is this method.
   * </p>
   */
  @Operation(summary = "New transaction", description = """
      Generic create, kept for completeness. Prefer /securitytrans, /singlecashtrans or /cashaccounttransfer, which
      validate against the transaction subtype.""", tags = { Transaction.TABNAME })
  @Override
  @PostMapping(produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> create(@Valid @RequestBody Transaction entity) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    transactionJpaRepository.throwWhenTransactionLimitReached(user.getIdTenant());
    return createEntity(entity);
  }

  ///////////////////////////////////////////////////////////
  // Create, update for security transaction
  ///////////////////////////////////////////////////////////
  @Operation(summary = "New security transaction", description = "", tags = { Transaction.TABNAME })
  @PostMapping(value = "/securitytrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> createTransaction(
      @Validated(SecurityTransaction.class) @RequestBody Transaction entity) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    transactionJpaRepository.throwWhenTransactionLimitReached(user.getIdTenant());
    return createEntity(entity);
  }

  @Operation(summary = "Update transaction with security involved", description = "", tags = { Transaction.TABNAME })
  @PutMapping(value = "/securitytrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> updateTransaction(
      @Validated(SecurityTransaction.class) @RequestBody Transaction entity) throws Exception {
    return updateEntity(entity);
  }

  @Operation(summary = "Get the calculated finance cost of a margin position ", description = "", tags = {
      Transaction.TABNAME })
  @GetMapping(value = "/financecost/{idTransaction}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ProposedMarginFinanceCost> getEstimatedMarginFinanceCost(
      @PathVariable final Integer idTransaction) throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return new ResponseEntity<>(
        SecurityMarginUnitsCheck.getEstimatedFinanceCost(transactionJpaRepository, user.getIdTenant(), idTransaction),
        HttpStatus.OK);
  }

  ///////////////////////////////////////////////////////////
  // Create, update for single cash transaction
  ///////////////////////////////////////////////////////////
  @PostMapping(value = "/singlecashtrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> createSingleCash(@Validated(CashTransaction.class) @RequestBody Transaction entity)
      throws Exception {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    transactionJpaRepository.throwWhenTransactionLimitReached(user.getIdTenant());
    return createEntity(entity);
  }

  @PutMapping(value = "/singlecashtrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> updateSingleCash(@Validated(CashTransaction.class) @RequestBody Transaction entity)
      throws Exception {
    return updateEntity(entity);
  }

  @Operation(summary = "Change only the taxable flag of a dividend or interest transaction, bypassing the closedUntil lock", description = "", tags = {
      Transaction.TABNAME })
  @PutMapping(value = "/{idTransaction}/taxableinterest/{taxableInterest}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> updateTaxableInterest(@PathVariable final Integer idTransaction,
      @PathVariable final boolean taxableInterest) {
    return ResponseEntity.ok(transactionJpaRepository.updateTaxableInterest(idTransaction, taxableInterest));
  }

  @Operation(summary = "Back-fill the ex-date of the tenant's dividend transactions of a tax year from the imported Swiss ICTax tax data, bypassing the closedUntil lock and never overwriting an existing ex-date", description = "", tags = {
      Transaction.TABNAME })
  @PutMapping(value = "/exdatefromtaxdata/{year}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<ExDateFromTaxDataResult> applyExDatesFromTaxData(@PathVariable final short year) {
    return ResponseEntity.ok(transactionJpaRepository.applyExDatesFromTaxData(year));
  }

  //////////////////////////////////////////////////////////////////////
  // Create, update for cash account transfer, concerns two Transaction
  // Attention: POST is used for create and update
  //////////////////////////////////////////////////////////////////////
  @PostMapping(value = "/cashaccounttransfer", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CashAccountTransfer> createDoubleTransaction(
      @Validated(CashTransaction.class) @RequestBody final CashAccountTransfer cashAccountTransfer) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    // Only a new transfer increases the transaction count; an update of an existing transfer does not.
    if (cashAccountTransfer.getWithdrawalTransaction().getIdTransaction() == null) {
      transactionJpaRepository.throwWhenTransactionLimitReached(user.getIdTenant());
    }
    CashAccountTransfer cashAccountTransferExisting = new CashAccountTransfer(
        checkAndSetEntityWithTenant(cashAccountTransfer.getWithdrawalTransaction(), user),
        checkAndSetEntityWithTenant(cashAccountTransfer.getDepositTransaction(), user));
    return ResponseEntity.ok().body(
        transactionJpaRepository.updateCreateCashaccountTransfer(cashAccountTransfer, cashAccountTransferExisting));
  }

  ///////////////////////////////////////////////////////////
  // Delete for all kind of transactions
  ///////////////////////////////////////////////////////////
  @DeleteMapping(value = "/{idTransaction}", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> deleteTransactionSingleDoubleTransaction(@PathVariable final Integer idTransaction) {
    log.debug("Delete by id Transaction : {}", idTransaction);
    transactionJpaRepository.deleteSingleDoubleTransaction(idTransaction);
    return ResponseEntity.noContent().build();
  }


}
