package grafioschtrader.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import grafiosch.BaseConstants;
import grafiosch.entities.TaskDataChange;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.repository.TaskDataChangeJpaRepository;
import grafiosch.repository.UserJpaRepository;
import grafiosch.service.SendMailInternalExternalService;
import grafiosch.types.TaskDataExecPriority;
import grafioschtrader.common.DataBusinessHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.SecurityActionTreeData;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityAction;
import grafioschtrader.entities.SecurityActionApplication;
import grafioschtrader.entities.SecurityTransfer;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.Tenant;
import grafioschtrader.entities.Transaction;
import grafioschtrader.repository.CashaccountJpaRepository;
import grafioschtrader.repository.CurrencypairJpaRepository;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityActionApplicationJpaRepository;
import grafioschtrader.repository.SecurityActionJpaRepository;
import grafioschtrader.repository.SecurityJpaRepository;
import grafioschtrader.repository.SecurityTransferJpaRepository;
import grafioschtrader.repository.SecurityaccountJpaRepository;
import grafioschtrader.repository.TransactionJpaRepository;
import grafioschtrader.types.TaskTypeExtended;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;

/**
 * Service handling SecurityAction (admin ISIN changes) and SecurityTransfer (user account transfers). Provides methods
 * to create, apply, and reverse ISIN changes, as well as create and reverse security transfers between accounts.
 */
@Service
public class SecurityActionService {

  @Autowired
  private SecurityActionJpaRepository securityActionJpaRepository;

  @Autowired
  private SecurityActionApplicationJpaRepository securityActionApplicationJpaRepository;

  @Autowired
  private SecurityTransferJpaRepository securityTransferJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private CashaccountJpaRepository cashaccountJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private UserJpaRepository userJpaRepository;

  @Autowired
  private SendMailInternalExternalService sendMailInternalExternalService;

  @Autowired
  private TaskDataChangeJpaRepository taskDataChangeJpaRepository;

  @Autowired
  private EntityManager entityManager;

  /**
   * Returns tree data for the SecurityAction TreeTable: all system actions, the current tenant's application status,
   * and the tenant's own security transfers.
   */
  public SecurityActionTreeData getTreeData() {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getIdTenant();

    List<SecurityAction> systemActions = securityActionJpaRepository.findAllByOrderByActionDateDesc();

    List<SecurityActionApplication> applications = securityActionApplicationJpaRepository.findByIdTenant(idTenant);
    Map<Integer, SecurityActionApplication> appliedMap = applications.stream()
        .collect(Collectors.toMap(a -> a.getSecurityAction().getIdSecurityAction(), a -> a));

    List<SecurityTransfer> clientTransfers = securityTransferJpaRepository
        .findByIdTenantOrderByTransferDateDesc(idTenant);

    for (SecurityTransfer transfer : clientTransfers) {
      long count = transactionJpaRepository.countTransactionsAfterDate(
          transfer.getIdSecurityaccountTarget(),
          transfer.getSecurity().getIdSecuritycurrency(),
          transfer.getTransferDate().atStartOfDay());
      transfer.setReversible(count == 0);
    }

    return new SecurityActionTreeData(systemActions, appliedMap, clientTransfers);
  }

  /**
   * Admin creates an ISIN change event. Validates the old ISIN exists. If the new ISIN already exists, references
   * the existing security; otherwise copies the old security to create a new one with the new ISIN. Counts affected
   * tenants and sends internal mail notifications.
   */
  @Transactional
  public SecurityAction createSecurityAction(SecurityAction action) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    List<Security> oldSecurities = securityJpaRepository.findByIsin(action.getIsinOld());
    if (oldSecurities.isEmpty()) {
      throw new DataViolationException("isin.old", "gt.security.action.old.isin.not.found", null);
    }
    Security securityOld = oldSecurities.get(0);

    // Validate split ratio: both must be set and > 0, or both null
    if ((action.getFromFactor() != null) != (action.getToFactor() != null)
        || (action.getFromFactor() != null && (action.getFromFactor() <= 0 || action.getToFactor() <= 0))) {
      throw new DataViolationException("from.factor", "gt.security.action.split.ratio.invalid", null);
    }

    // Use existing security if new ISIN already exists, otherwise create a copy
    List<Security> existingNew = securityJpaRepository.findByIsin(action.getIsinNew());
    Security securityNew;
    if (!existingNew.isEmpty()) {
      securityNew = existingNew.get(0);
    } else {
      securityNew = copySecurityWithNewIsin(securityOld, action.getIsinNew(), action.getActionDate());
      securityNew = securityJpaRepository.save(securityNew);
      securityJpaRepository.reloadAsyncFullHistoryquote(securityNew);
    }

    action.setSecurityOld(securityOld);
    action.setSecurityNew(securityNew);
    action.setCreatedBy(user.getIdUser());
    action.setCreationTime(LocalDateTime.now());

    // Count affected tenants
    List<Transaction> transactions = transactionJpaRepository
        .findBySecurity_idSecuritycurrency(securityOld.getIdSecuritycurrency());
    List<Integer> affectedTenantIds = transactions.stream().map(Transaction::getIdTenant).distinct()
        .collect(Collectors.toList());
    action.setAffectedCount(affectedTenantIds.size());

    final SecurityAction savedAction = securityActionJpaRepository.save(action);

    // Send internal mail to each affected tenant's user
    for (Integer idTenant : affectedTenantIds) {
      try {
        userJpaRepository.findByIdTenant(idTenant).ifPresent(tenantUser ->
            sendMailInternalExternalService.sendInternalMail(user.getIdUser(), tenantUser.getIdUser(),
                "ISIN Change: " + savedAction.getIsinOld() + " -> " + savedAction.getIsinNew(),
                "Security ISIN " + savedAction.getIsinOld() + " changed to " + savedAction.getIsinNew() + " on "
                    + savedAction.getActionDate() + ". Apply in Security Actions."));
      } catch (Exception e) {
        // Don't fail the whole operation if one mail fails
      }
    }
    return savedAction;
  }

  /**
   * Deletes an ISIN change event if no tenant has applied it.
   */
  @Transactional
  public void deleteSecurityAction(Integer idSecurityAction) {
    if (securityActionApplicationJpaRepository.existsBySecurityAction_IdSecurityAction(idSecurityAction)) {
      throw new DataViolationException("id.security.action", "gt.security.action.has.applications", null);
    }
    securityActionJpaRepository.deleteById(idSecurityAction);
  }

  /**
   * Applies an ISIN change for the current tenant. First bulk-reassigns all post-action-date transactions to the new
   * security, then creates SELL/BUY pairs for the remaining pre-action-date position. Finally schedules a holdings
   * rebuild task.
   * <p>
   * <b>Why direct save() instead of saveOnlyAttributes():</b> The SELL/BUY pair is saved via
   * {@code transactionJpaRepository.save()} intentionally, bypassing the business rule checks in
   * {@code TransactionJpaRepositoryImpl}. After the bulk reassignment of post-action-date transactions (including
   * dividends, finance costs, etc.) to the new security, the standard plausibility checks would fail — e.g. dividend
   * transactions would reference a security the tenant no longer holds, and units integrity checks would be
   * inconsistent. Since the holdings are fully rebuilt via a scheduled task after this operation, the intermediate
   * state is acceptable. The closedUntil restriction is also intentionally bypassed because ISIN changes are
   * system-level operations that must succeed regardless of user-defined closed periods.
   */
  @Transactional
  public SecurityActionApplication applySecurityAction(Integer idSecurityAction) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getIdTenant();

    SecurityAction action = securityActionJpaRepository.findById(idSecurityAction)
        .orElseThrow(() -> new DataViolationException("id.security.action", "gt.security.action.not.found", null));

    Optional<SecurityActionApplication> existing = securityActionApplicationJpaRepository
        .findBySecurityAction_IdSecurityActionAndIdTenant(idSecurityAction, idTenant);
    if (existing.isPresent() && !existing.get().isReversed()) {
      throw new DataViolationException("id.security.action", "gt.security.action.already.applied", null);
    }

    // Check tenant has any transactions for old security before proceeding
    List<Transaction> allTenantTransactions = transactionJpaRepository
        .findByIdTenantAndIdSecurity(idTenant, action.getSecurityOld().getIdSecuritycurrency());
    if (allTenantTransactions.isEmpty()) {
      throw new DataViolationException("id.security.action", "gt.security.action.no.holdings", null);
    }

    // Get closing price on action date
    Optional<Historyquote> hqOpt = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDate(action.getSecurityOld().getIdSecuritycurrency(), action.getActionDate());
    double closePrice = hqOpt.map(Historyquote::getClose).orElseThrow(
        () -> new DataViolationException("action.date", "gt.security.action.no.price.on.date", null));

    // Save the application record first to get its ID for tagging transactions
    SecurityActionApplication application = existing.orElse(new SecurityActionApplication());
    application.setSecurityAction(action);
    application.setIdTenant(idTenant);
    application.setAppliedTime(LocalDateTime.now());
    application.setReversed(false);
    application = securityActionApplicationJpaRepository.save(application);

    // Step 1: Bulk reassign all transactions on or after action date to the new security
    transactionJpaRepository.reassignTransactionsToNewSecurity(idTenant,
        action.getSecurityOld().getIdSecuritycurrency(), action.getSecurityNew().getIdSecuritycurrency(),
        application.getIdSecurityActionApp(), action.getActionDate());
    entityManager.flush();
    entityManager.clear();

    // Step 2: Calculate net units from remaining transactions on old security (now only pre-action-date)
    List<Transaction> remainingTransactions = transactionJpaRepository
        .findByIdTenantAndIdSecurity(idTenant, action.getSecurityOld().getIdSecuritycurrency());

    Map<Integer, Double> unitsByAccount = remainingTransactions.stream()
        .filter(t -> t.getIdSecurityaccount() != null)
        .collect(Collectors.groupingBy(Transaction::getIdSecurityaccount,
            Collectors.summingDouble(t -> {
              if (t.getTransactionType() == TransactionType.ACCUMULATE) {
                return t.getUnits();
              } else if (t.getTransactionType() == TransactionType.REDUCE) {
                return -t.getUnits();
              }
              return 0.0;
            })));

    Integer firstSellId = null;
    Integer firstBuyId = null;

    // Steps 3-5: Create SELL/BUY pairs for each security account with positive units
    for (Map.Entry<Integer, Double> entry : unitsByAccount.entrySet()) {
      double units = entry.getValue();
      if (units <= 0) {
        continue;
      }
      Integer idSecurityaccount = entry.getKey();

      Securityaccount securityaccount = securityaccountJpaRepository
          .findByIdSecuritycashAccountAndIdTenant(idSecurityaccount, idTenant);
      if (securityaccount == null) {
        continue;
      }

      // Calculate buy-side units and price, applying split ratio if present
      double buyUnits = units;
      double buyPrice = closePrice;
      if (action.getFromFactor() != null && action.getToFactor() != null) {
        buyUnits = units * action.getToFactor() / (double) action.getFromFactor();
        buyPrice = closePrice * action.getFromFactor() / (double) action.getToFactor();
      }

      // Create SELL transaction (old security) — uses direct save(), see class Javadoc for rationale
      Transaction sellTx = createSecurityTransaction(idTenant, idSecurityaccount,
          securityaccount, action.getSecurityOld(), units, closePrice,
          TransactionType.REDUCE, action.getActionDate(), application.getIdSecurityActionApp());
      sellTx = transactionJpaRepository.save(sellTx);

      // Create BUY transaction (new security, with adjusted units/price if split)
      Transaction buyTx = createSecurityTransaction(idTenant, idSecurityaccount,
          securityaccount, action.getSecurityNew(), buyUnits, buyPrice,
          TransactionType.ACCUMULATE, action.getActionDate(), application.getIdSecurityActionApp());
      buyTx.setConnectedIdTransaction(sellTx.getIdTransaction());
      buyTx = transactionJpaRepository.save(buyTx);

      sellTx.setConnectedIdTransaction(buyTx.getIdTransaction());
      transactionJpaRepository.save(sellTx);

      if (firstSellId == null) {
        firstSellId = sellTx.getIdTransaction();
        firstBuyId = buyTx.getIdTransaction();
      }
    }

    application.setIdTransactionSell(firstSellId);
    application.setIdTransactionBuy(firstBuyId);
    application = securityActionApplicationJpaRepository.save(application);

    action.setAppliedCount(action.getAppliedCount() + 1);
    securityActionJpaRepository.save(action);

    // Step 6: Schedule holdings rebuild for this tenant
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(1),
        idTenant, Tenant.class.getSimpleName()));

    return application;
  }

  /**
   * Reverses an applied ISIN change for the current tenant. Reverts bulk-reassigned transactions back to the old
   * security, deletes the system-created SELL/BUY pair, marks the application as reversed, and schedules a holdings
   * rebuild task.
   */
  @Transactional
  public void reverseSecurityAction(Integer idSecurityAction) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getIdTenant();

    SecurityActionApplication application = securityActionApplicationJpaRepository
        .findBySecurityAction_IdSecurityActionAndIdTenant(idSecurityAction, idTenant)
        .orElseThrow(() -> new DataViolationException("id.security.action", "gt.security.action.not.applied", null));

    if (application.isReversed()) {
      throw new DataViolationException("id.security.action", "gt.security.action.already.reversed", null);
    }

    SecurityAction action = application.getSecurityAction();
    Integer appId = application.getIdSecurityActionApp();

    // Step 1: Revert bulk-reassigned transactions back to old security
    transactionJpaRepository.revertReassignedTransactions(
        action.getSecurityOld().getIdSecuritycurrency(), appId);

    // Step 2: Delete system-created SELL/BUY transactions
    List<Transaction> systemTransactions = transactionJpaRepository.findByIdSecurityActionApp(appId).stream()
        .filter(t -> "System-Created".equals(t.getNote()))
        .collect(Collectors.toList());
    // Clear connected references before deleting
    for (Transaction t : systemTransactions) {
      t.setConnectedIdTransaction(null);
    }
    transactionJpaRepository.saveAll(systemTransactions);
    transactionJpaRepository.flush();
    transactionJpaRepository.deleteAll(systemTransactions);

    application.setReversed(true);
    application.setIdTransactionSell(null);
    application.setIdTransactionBuy(null);
    securityActionApplicationJpaRepository.save(application);

    action.setAppliedCount(Math.max(0, action.getAppliedCount() - 1));
    securityActionJpaRepository.save(action);

    // Schedule holdings rebuild for this tenant
    taskDataChangeJpaRepository.save(new TaskDataChange(TaskTypeExtended.REBUILD_HOLDINGS_ALL_OR_SINGLE_TENANT,
        TaskDataExecPriority.PRIO_NORMAL, LocalDateTime.now().plusMinutes(1),
        idTenant, Tenant.class.getSimpleName()));
  }

  /**
   * Creates a security transfer between two securities accounts. Routes through TransactionJpaRepositoryImpl
   * (saveOnlyAttributes / updateCreateCashaccountTransfer) so that closedUntil, trading period, overdraft,
   * units integrity, and holdings adjustments are all enforced. Creates 4 transactions: SELL in source,
   * WITHDRAWAL from source cashaccount, DEPOSIT to target cashaccount, BUY in target.
   */
  @Transactional
  public SecurityTransfer createTransfer(SecurityTransfer transfer) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getIdTenant();
    transfer.setIdTenant(idTenant);

    TransferContext ctx = validateAndPrepareTransfer(transfer, idTenant);

    transfer.setQuotation(ctx.closePrice);
    transfer.setCreationTime(LocalDateTime.now());
    transfer = securityTransferJpaRepository.save(transfer);

    Transaction sellTx = createAndSaveSellTransaction(ctx, transfer.getIdSecurityTransfer());
    createAndSaveCashTransfer(ctx, transfer.getIdSecurityTransfer());
    Transaction buyTx = createAndSaveBuyTransaction(ctx, transfer.getIdSecurityTransfer());

    transfer.setIdTransactionSell(sellTx.getIdTransaction());
    transfer.setIdTransactionBuy(buyTx.getIdTransaction());
    return securityTransferJpaRepository.save(transfer);
  }

  /**
   * Reverses a security transfer by finding all transactions via idSecurityTransfer FK, clearing connected
   * references, deleting all transactions, and then deleting the SecurityTransfer record.
   */
  @Transactional
  public void reverseTransfer(Integer idSecurityTransfer) {
    User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Integer idTenant = user.getIdTenant();

    SecurityTransfer transfer = securityTransferJpaRepository.findById(idSecurityTransfer)
        .orElseThrow(() -> new DataViolationException("id.security.transfer",
            "gt.security.transfer.not.found", null));

    if (!idTenant.equals(transfer.getIdTenant())) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    long postTransferTxCount = transactionJpaRepository.countTransactionsAfterDate(
        transfer.getIdSecurityaccountTarget(),
        transfer.getSecurity().getIdSecuritycurrency(),
        transfer.getTransferDate().atStartOfDay());
    if (postTransferTxCount > 0) {
      throw new DataViolationException("id.security.transfer",
          "gt.security.transfer.reverse.has.later.transactions", null);
    }

    // Clear transfer's FK references to transactions first
    transfer.setIdTransactionSell(null);
    transfer.setIdTransactionBuy(null);
    securityTransferJpaRepository.save(transfer);
    securityTransferJpaRepository.flush();

    // Find all transactions referencing this transfer, clear connected refs, then delete
    List<Transaction> transferTransactions = transactionJpaRepository.findByIdSecurityTransfer(idSecurityTransfer);
    for (Transaction t : transferTransactions) {
      t.setConnectedIdTransaction(null);
    }
    transactionJpaRepository.saveAll(transferTransactions);
    transactionJpaRepository.flush();
    transactionJpaRepository.deleteAll(transferTransactions);

    securityTransferJpaRepository.delete(transfer);
  }

  /**
   * Validates transfer parameters and resolves all required entities. Returns a TransferContext holding the
   * validated state for use by the transaction creation helpers.
   */
  private TransferContext validateAndPrepareTransfer(SecurityTransfer transfer, Integer idTenant) {
    if (transfer.getSecurity() == null && transfer.getIdSecurity() != null) {
      Security security = securityJpaRepository.findById(transfer.getIdSecurity())
          .orElseThrow(() -> new DataViolationException("id.security", "gt.security.not.found", null));
      transfer.setSecurity(security);
    }

    if (transfer.getSecurity().isMarginInstrument()) {
      throw new DataViolationException("id.security", "gt.security.transfer.margin.not.allowed", null);
    }
    if (transfer.getIdSecurityaccountSource().equals(transfer.getIdSecurityaccountTarget())) {
      throw new DataViolationException("id.securityaccount.target", "gt.security.transfer.same.account", null);
    }

    Securityaccount sourceAccount = securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(transfer.getIdSecurityaccountSource(), idTenant);
    Securityaccount targetAccount = securityaccountJpaRepository
        .findByIdSecuritycashAccountAndIdTenant(transfer.getIdSecurityaccountTarget(), idTenant);
    if (sourceAccount == null || targetAccount == null) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }

    Optional<LocalDateTime> lastTxTime = transactionJpaRepository
        .findMaxTransactionTimeBySecurityaccountAndSecurity(
            transfer.getIdSecurityaccountSource(),
            transfer.getSecurity().getIdSecuritycurrency());
    if (lastTxTime.isPresent() && !transfer.getTransferDate().isAfter(lastTxTime.get().toLocalDate())) {
      throw new DataViolationException("transfer.date",
          "gt.security.transfer.date.before.last.transaction",
          new Object[] { lastTxTime.get().toLocalDate() });
    }

    Cashaccount sourceCashaccount = findPreferredCashaccount(sourceAccount, transfer.getSecurity().getCurrency());
    Cashaccount targetCashaccount = findPreferredCashaccount(targetAccount, transfer.getSecurity().getCurrency());

    Optional<Historyquote> hqOpt = historyquoteJpaRepository
        .findByIdSecuritycurrencyAndDate(transfer.getSecurity().getIdSecuritycurrency(), transfer.getTransferDate());
    double closePrice = hqOpt.map(Historyquote::getClose).orElseThrow(
        () -> new DataViolationException("transfer.date", "gt.security.transfer.no.price.on.date", null));

    return new TransferContext(idTenant, transfer, sourceAccount, targetAccount,
        sourceCashaccount, targetCashaccount, closePrice);
  }

  /**
   * Finds the preferred cash account in the same portfolio as the security account, preferring one whose
   * currency matches the given preferredCurrency.
   */
  private Cashaccount findPreferredCashaccount(Securityaccount securityaccount, String preferredCurrency) {
    Integer idPortfolio = securityaccount.getPortfolio().getIdPortfolio();
    List<Cashaccount> cashaccounts = cashaccountJpaRepository.findByPortfolio_IdPortfolio(idPortfolio);
    return cashaccounts.stream()
        .filter(ca -> ca.getCurrency().equals(preferredCurrency))
        .findFirst()
        .orElse(cashaccounts.stream().findFirst().orElseThrow(
            () -> new DataViolationException("id.securityaccount.source",
                "gt.security.transfer.no.cashaccount", null)));
  }

  /**
   * Creates a REDUCE (sell) transaction in the source security account and saves it via saveOnlyAttributes,
   * which enforces closedUntil, trading period, overdraft, units integrity, and adjusts holdings.
   */
  private Transaction createAndSaveSellTransaction(TransferContext ctx, Integer idSecurityTransfer) {
    double cashAmount = ctx.transfer.getUnits() * ctx.closePrice;
    Transaction sellTx = new Transaction(ctx.sourceAccount.getIdSecuritycashAccount(),
        ctx.sourceCashaccount, ctx.transfer.getSecurity(), cashAmount,
        ctx.transfer.getUnits(), ctx.closePrice, TransactionType.REDUCE,
        null, null, null, ctx.transfer.getTransferDate().atTime(12, 0), null, null, null, null);
    sellTx.setIdTenant(ctx.idTenant);
    sellTx.setIdSecurityTransfer(idSecurityTransfer);
    sellTx.setNote("System-Created");
    try {
      return transactionJpaRepository.saveOnlyAttributes(sellTx, null, null);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Creates a WITHDRAWAL + DEPOSIT cash account transfer between source and target cashaccounts.
   * Routes through updateCreateCashaccountTransfer which enforces closedUntil on both accounts,
   * validates currency pairs, and adjusts cash holdings.
   */
  private CashAccountTransfer createAndSaveCashTransfer(TransferContext ctx, Integer idSecurityTransfer) {
    double cashAmount = ctx.transfer.getUnits() * ctx.closePrice;
    LocalDateTime transferTime = ctx.transfer.getTransferDate().atTime(12, 0);

    Transaction withdrawalTx = new Transaction(ctx.sourceCashaccount, -cashAmount,
        TransactionType.WITHDRAWAL, transferTime);
    withdrawalTx.setIdTenant(ctx.idTenant);
    withdrawalTx.setIdSecurityTransfer(idSecurityTransfer);
    withdrawalTx.setNote("System-Created");

    Transaction depositTx = new Transaction(ctx.targetCashaccount, cashAmount,
        TransactionType.DEPOSIT, transferTime);
    depositTx.setIdTenant(ctx.idTenant);
    depositTx.setIdSecurityTransfer(idSecurityTransfer);
    depositTx.setNote("System-Created");

    setCurrencyExRateIfNeeded(withdrawalTx, depositTx, ctx.sourceCashaccount, ctx.targetCashaccount,
        ctx.transfer.getTransferDate());

    CashAccountTransfer cashTransfer = new CashAccountTransfer(withdrawalTx, depositTx);
    return transactionJpaRepository.updateCreateCashaccountTransfer(cashTransfer, new CashAccountTransfer());
  }

  /**
   * Creates an ACCUMULATE (buy) transaction in the target security account and saves it via saveOnlyAttributes,
   * which enforces closedUntil, trading period, overdraft, units integrity, and adjusts holdings.
   */
  private Transaction createAndSaveBuyTransaction(TransferContext ctx, Integer idSecurityTransfer) {
    double cashAmount = -(ctx.transfer.getUnits() * ctx.closePrice);
    Transaction buyTx = new Transaction(ctx.targetAccount.getIdSecuritycashAccount(),
        ctx.targetCashaccount, ctx.transfer.getSecurity(), cashAmount,
        ctx.transfer.getUnits(), ctx.closePrice, TransactionType.ACCUMULATE,
        null, null, null, ctx.transfer.getTransferDate().atTime(12, 0), null, null, null, null);
    buyTx.setIdTenant(ctx.idTenant);
    buyTx.setIdSecurityTransfer(idSecurityTransfer);
    buyTx.setNote("System-Created");
    try {
      return transactionJpaRepository.saveOnlyAttributes(buyTx, null, null);
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Sets currency exchange rate on withdrawal and deposit transactions when source and target cashaccounts
   * have different currencies. Looks up the currencypair and its close price for the transfer date.
   */
  private void setCurrencyExRateIfNeeded(Transaction withdrawalTx, Transaction depositTx,
      Cashaccount sourceCash, Cashaccount targetCash, LocalDate transferDate) {
    if (sourceCash.getCurrency().equals(targetCash.getCurrency())) {
      return;
    }
    Currencypair currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(
        sourceCash.getCurrency(), targetCash.getCurrency());
    Currencypair found = currencypairJpaRepository.findByFromCurrencyAndToCurrency(
        currencypair.getFromCurrency(), currencypair.getToCurrency());
    if (found != null) {
      Double exRate = currencypairJpaRepository.getClosePriceForDate(found, transferDate);
      if (exRate != null) {
        withdrawalTx.setCurrencyExRate(exRate);
        withdrawalTx.setIdCurrencypair(found.getIdSecuritycurrency());
        depositTx.setCurrencyExRate(exRate);
        depositTx.setIdCurrencypair(found.getIdSecuritycurrency());
      }
    }
  }

  private Security copySecurityWithNewIsin(Security source, String newIsin, LocalDate actionDate) {
    Security copy = new Security();
    copy.setIsin(newIsin);
    copy.setName(source.getName() + " (new ISIN)");
    copy.setCurrency(source.getCurrency());
    copy.setAssetClass(source.getAssetClass());
    copy.setStockexchange(source.getStockexchange());
    copy.setTickerSymbol(source.getTickerSymbol());
    copy.setDenomination(source.getDenomination());
    copy.setActiveFromDate(actionDate);
    copy.setActiveToDate(source.getActiveToDate());
    copy.setDistributionFrequency(source.getDistributionFrequency());
    copy.setLeverageFactor(source.getLeverageFactor());
    copy.setIdConnectorHistory(source.getIdConnectorHistory());
    copy.setIdConnectorIntra(source.getIdConnectorIntra());
    copy.setIdConnectorDividend(source.getIdConnectorDividend());
    copy.setUrlHistoryExtend(source.getUrlHistoryExtend());
    copy.setUrlIntraExtend(source.getUrlIntraExtend());
    copy.setStockexchangeLink(source.getStockexchangeLink());
    copy.setProductLink(source.getProductLink());
    return copy;
  }

  private Transaction createSecurityTransaction(Integer idTenant, Integer idSecurityaccount,
      Securityaccount securityaccount, Security security, double units, double quotation,
      TransactionType transactionType, LocalDate transactionDate, Integer idSecurityActionApp) {

    double cashAmount;
    if (transactionType == TransactionType.ACCUMULATE) {
      cashAmount = -(units * quotation);
    } else {
      cashAmount = units * quotation;
    }

    Cashaccount cashaccount = findPreferredCashaccount(securityaccount, security.getCurrency());

    Transaction tx = new Transaction(idSecurityaccount, cashaccount, security, cashAmount,
        units, quotation, transactionType, null, null, null,
        transactionDate.atTime(12, 0), null, null, null, null);
    tx.setIdTenant(idTenant);
    tx.setNote("System-Created");
    if (idSecurityActionApp != null) {
      tx.setIdSecurityActionApp(idSecurityActionApp);
    }
    return tx;
  }

  /**
   * Holds validated state for a security transfer operation, avoiding passing many parameters between helpers.
   */
  private static class TransferContext {
    final Integer idTenant;
    final SecurityTransfer transfer;
    final Securityaccount sourceAccount;
    final Securityaccount targetAccount;
    final Cashaccount sourceCashaccount;
    final Cashaccount targetCashaccount;
    final double closePrice;

    TransferContext(Integer idTenant, SecurityTransfer transfer, Securityaccount sourceAccount,
        Securityaccount targetAccount, Cashaccount sourceCashaccount, Cashaccount targetCashaccount,
        double closePrice) {
      this.idTenant = idTenant;
      this.transfer = transfer;
      this.sourceAccount = sourceAccount;
      this.targetAccount = targetAccount;
      this.sourceCashaccount = sourceCashaccount;
      this.targetCashaccount = targetCashaccount;
      this.closePrice = closePrice;
    }
  }
}
