package grafioschtrader.rest;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

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
import org.springframework.web.bind.annotation.RestController;

import grafioschtrader.GlobalConstants;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ClosedMarginUnits;
import grafioschtrader.dto.ProposedMarginFinanceCost;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.Transaction.CashTransaction;
import grafioschtrader.entities.Transaction.SecurityTransaction;
import grafioschtrader.entities.User;
import grafioschtrader.instrument.SecurityMarginUnitsCheck;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;
import grafioschtrader.repository.TransactionJpaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping(RequestMappings.TRANSACTION_MAP)
@Tag(name = Transaction.TABNAME, description = "Controller for transaction")
public class TransactionResource extends UpdateCreate<Transaction> {

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  protected UpdateCreateJpaRepository<Transaction> getUpdateCreateJpaRepository() {
    return transactionJpaRepository;
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
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }

    return new ResponseEntity<>(transaction, HttpStatus.OK);
  }

  @Operation(summary = "Get all transactions from a cash account by Id cash account", description = "", tags = {
      Transaction.TABNAME })
  @GetMapping(value = "/{idSecuritycashAccount}/cashaccount", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CashaccountTransactionPosition[]> getTransactionsWithSaldoForCashaccount(
      @Parameter(description = "Id of cash account", required = true) @PathVariable final Integer idSecuritycashAccount) {
    return new ResponseEntity<>(transactionJpaRepository.getTransactionsWithSaldoForCashaccount(idSecuritycashAccount),
        HttpStatus.OK);
  }

  ///////////////////////////////////////////////////////////
  // Create, update for security transaction
  ///////////////////////////////////////////////////////////
  @Operation(summary = "New security transaction", description = "", tags = { Transaction.TABNAME })
  @PostMapping(value = "/securitytrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> createTransaction(
      @Validated(SecurityTransaction.class) @RequestBody Transaction entity) throws Exception {
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
        SecurityMarginUnitsCheck.getEstimatedFinanceCost(transactionJpaRepository, user, idTransaction), HttpStatus.OK);
  }

  ///////////////////////////////////////////////////////////
  // Create, update for single cash transaction
  ///////////////////////////////////////////////////////////
  @PostMapping(value = "/singlecashtrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> createSingleCash(@Validated(CashTransaction.class) @RequestBody Transaction entity)
      throws Exception {
    return createEntity(entity);
  }

  @PutMapping(value = "/singlecashtrans", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<Transaction> updateSingleCash(@Validated(CashTransaction.class) @RequestBody Transaction entity)
      throws Exception {
    return updateEntity(entity);
  }

  //////////////////////////////////////////////////////////////////////
  // Create, update for cash account transfer, concerns two Transaction
  // Attention: POST is used for create and update
  //////////////////////////////////////////////////////////////////////
  @PostMapping(value = "/cashaccounttransfer", produces = APPLICATION_JSON_VALUE)
  public ResponseEntity<CashAccountTransfer> createDoubleTransaction(
      @Validated(CashTransaction.class) @RequestBody final CashAccountTransfer cashAccountTransfer) {

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
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
