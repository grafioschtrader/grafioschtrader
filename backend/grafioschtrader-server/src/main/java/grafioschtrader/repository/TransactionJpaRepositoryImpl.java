package grafioschtrader.repository;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.common.DateHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ClosedMarginUnits;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.Portfolio;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Securityaccount;
import grafioschtrader.entities.TradingDaysPlus;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.instrument.SecurityGeneralUnitsCheck;
import grafioschtrader.instrument.SecurityMarginUnitsCheck;
import grafioschtrader.reportviews.currencypair.CurrencypairWithTransaction;
import grafioschtrader.reportviews.transaction.CashaccountTransactionPosition;
import grafioschtrader.types.OperationType;
import grafioschtrader.types.TransactionType;

public class TransactionJpaRepositoryImpl extends BaseRepositoryImpl<Transaction>
    implements TransactionJpaRepositoryCustom {

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private PortfolioJpaRepository portfolioJpaRepository;

  @Autowired
  private SecuritysplitJpaRepository securitysplitJpaRepository;

  @Autowired
  private GlobalparametersJpaRepository globalparametersJpaRepository;

  @Autowired
  private SecurityaccountJpaRepository securityaccountJpaRepository;

  @Autowired
  private TradingDaysPlusJpaRepository tradingDaysPlusJpaRepository;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityRepository;

  @Autowired
  private HoldCashaccountBalanceJpaRepository holdCashaccountBalanceJpaRepository;

  @Autowired
  private HoldCashaccountDepositJpaRepository holdCashaccountDepositJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  // Circular Dependency -> Lazy
  private CashaccountJpaRepository cashaccountJpaRepository;

  // Circular Dependency -> Lazy
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  ///////////////////////////////////////////////////////////////////////////////
  // Methods with general Transaction
  //////////////////////////////////////////////////////////////////////////////
  @Autowired
  public void setCashaccountJpaRepository(@Lazy final CashaccountJpaRepository cashaccountJpaRepository) {
    this.cashaccountJpaRepository = cashaccountJpaRepository;
  }

  @Autowired
  public void setImportTransactionPosJpaRepository(
      @Lazy final ImportTransactionPosJpaRepository importTransactionPosJpaRepository) {
    this.importTransactionPosJpaRepository = importTransactionPosJpaRepository;
  }

  @Override
  public List<Transaction> getSecurityAccountWithFeesAndIntrerestTransactionsByTenant(Integer idTenant) {
    return transactionJpaRepository.getSecurityAccountTransactionsByTenant(idTenant,
        TransactionType.DIVIDEND.getValue());
  }

  @Override
  public List<Transaction> getTransactionsByIdPortfolio(Integer idPortfolio, Integer idTenant) {
    Portfolio portfolio = portfolioJpaRepository.getReferenceById(idPortfolio);
    if (!idTenant.equals(portfolio.getIdTenant())) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return transactionJpaRepository.getTransactionsByIdPortfolio(idPortfolio);
  }

  @Override
  @Transactional
  @Modifying
  public CashAccountTransfer updateCreateCashaccountTransfer(CashAccountTransfer cashAccountTransfer,
      CashAccountTransfer cashAccountTransferExisting) {

    checkTransactionSecurityAndCashaccountBeforSave(cashAccountTransfer.getWithdrawalTransaction());
    checkTransactionSecurityAndCashaccountBeforSave(cashAccountTransfer.getDepositTransaction());
    checkCurrencypair(cashAccountTransfer.getWithdrawalTransaction(),
        cashAccountTransfer.getWithdrawalTransaction().getCashaccount().getCurrency(),
        cashAccountTransfer.getDepositTransaction().getCashaccount().getCurrency());
    cashAccountTransfer.getDepositTransaction().clearCurrencypairExRate();

    cashAccountTransfer.setToMinus();

    Integer withdrawalCurrencyFraction = globalparametersJpaRepository
        .getPrecisionForCurrency(cashAccountTransfer.getWithdrawalTransaction().getCashaccount().getCurrency());

    cashAccountTransfer.validateWithdrawalCashaccountAmount(withdrawalCurrencyFraction);
    cashAccountTransfer.makeAbsToatalAmount();
    CashAccountTransfer newCashAccountTransfer = new CashAccountTransfer(
        processAndSaveTransaction(cashAccountTransfer.getWithdrawalTransaction(),
            cashAccountTransferExisting.getWithdrawalTransaction(), null, true, true),
        processAndSaveTransaction(cashAccountTransfer.getDepositTransaction(),
            cashAccountTransferExisting.getDepositTransaction(), null, true, true));
    newCashAccountTransfer.connectTransactions();
    CashAccountTransfer cat = new CashAccountTransfer(
        this.transactionJpaRepository.saveAll(newCashAccountTransfer.getTransactionAsList()));
    holdCashaccountDepositJpaRepository.adjustCashaccountDepositOrWithdrawal(cat.getDepositTransaction(),
        cat.getWithdrawalTransaction());
    return cat;
  }

  @Override
//  @Transactional
//  @Modifying
  public Transaction saveOnlyAttributesFormImport(final Transaction transaction, Transaction existingEntity) {
    // return processAndSaveTransaction(transaction, existingEntity, null, false,
    // false);
    return saveOnly(transaction, existingEntity, null);
  }

  @Override
  @Transactional
  @Modifying
  public Transaction saveOnlyAttributes(final Transaction transaction, Transaction existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    return saveOnly(transaction, existingEntity, updatePropertyLevelClasses);
  }

  private Transaction saveOnly(final Transaction transaction, Transaction existingEntity,
      final Set<Class<? extends Annotation>> updatePropertyLevelClasses) {
    Securityaccount securityaccount = checkTransactionSecurityAndCashaccountBeforSave(transaction);
    checkCurrencypair(transaction);
    return processAndSaveTransaction(transaction, existingEntity, securityaccount, true, false);
  }

  @Override
  public ClosedMarginUnits getClosedMarginUnitsByIdTransaction(final Integer idTransaction) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<Transaction> transactions = transactionJpaRepository
        .findByIdTenantAndConnectedIdTransactionAndUnitsIsNotNull(user.getIdTenant(), idTransaction);
    Double units = transactions.stream().filter(
        t -> t.getTransactionType() == TransactionType.ACCUMULATE || t.getTransactionType() == TransactionType.REDUCE)
        .map(t -> t.getUnits() * (t.getTransactionType() == TransactionType.ACCUMULATE ? 1 : -1))
        .reduce(0.0, (a, b) -> a + b);
    return new ClosedMarginUnits(!transactions.isEmpty(), units);
  }

  /**
   * Checks a transaction uses the cash and/or security accounts which belongs to
   * the right tenant.
   *
   * @param transaction
   * @return
   */
  private Securityaccount checkTransactionSecurityAndCashaccountBeforSave(Transaction transaction) {
    Securityaccount securityaccount = null;
    if (transaction.getIdSecurityaccount() != null) {
      securityaccount = securityaccountJpaRepository
          .findByIdSecuritycashAccountAndIdTenant(transaction.getIdSecurityaccount(), transaction.getIdTenant());
      if (securityaccount == null) {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      }
    }
    final Cashaccount cashaccount = this.cashaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(
        transaction.getCashaccount().getIdSecuritycashAccount(), transaction.getIdTenant());
    if (cashaccount == null) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    } else {
      transaction.setCashaccount(cashaccount);
    }
    return securityaccount;
  }

  private void checkCurrencypair(final Transaction transaction) {
    if (transaction.getSecurity() != null) {
      this.checkCurrencypair(transaction, transaction.getSecurity().getCurrency(),
          transaction.getCashaccount().getCurrency());
    }
  }

  private void checkCurrencypair(final Transaction transaction, final String sourceCurrency,
      final String targetCurrency) {

    if (sourceCurrency.equals(targetCurrency)) {
      transaction.setIdCurrencypair(null);
    }
    transaction.clearCurrencypairExRate();

    if (transaction.getIdCurrencypair() != null) {
      Currencypair currencypairRequried = DataHelper.getCurrencypairWithSetOfFromAndTo(sourceCurrency, targetCurrency);
      Currencypair currencypairFound = currencypairJpaRepository.findByFromCurrencyAndToCurrency(
          currencypairRequried.getFromCurrency(), currencypairRequried.getToCurrency());
      if (!transaction.getIdCurrencypair().equals(currencypairFound.getIdSecuritycurrency())) {
        throw new DataViolationException("currencypair", "gt.trans.wrong.currencypair", new Object[] {});
      }
      Double expectedExchangeRate = currencypairJpaRepository.getClosePriceForDate(currencypairFound,
          transaction.getTransactionTime());
      if (expectedExchangeRate != null) {
        double diff = 100.0 / (expectedExchangeRate / Math.abs(expectedExchangeRate - transaction.getCurrencyExRate()));
        if (diff >= GlobalConstants.ACCEPTESD_PERCENTAGE_EXCHANGE_RATE_DIFF) {
          throw new DataViolationException("currencypair", "gt.exchangerate.exceeds.expected",
              new Object[] { transaction.getCurrencyExRate(), DataHelper.round(diff), expectedExchangeRate });
        }
      }
    }
  }

  /**
   * Every create/update on transaction must call this method!
   *
   *
   * @param transaction
   * @param existingEntity
   *
   * @return
   * @throws DataViolationException
   */
  private Transaction processAndSaveTransaction(final Transaction transaction, Transaction existingEntity,
      Securityaccount securityaccount, boolean adjustHoldings, boolean isCashAccountTransfer) {
    Transaction newTransaction = null;

    if (transaction.getTransactionType() == TransactionType.WITHDRAWAL && transaction.getCashaccountAmount() > 0.0) {
      // This amount must be always minus but from the client a positive total amount
      // is accepted
      transaction.setCashaccountAmount(transaction.getCashaccountAmount() * -1);
    }
    Integer currencyFraction = globalparametersJpaRepository
        .getPrecisionForCurrency(transaction.getCashaccount().getCurrency());
    switch (transaction.getTransactionType()) {
    case ACCUMULATE:
    case REDUCE:
    case DIVIDEND:
    case FINANCE_COST:
      checkTradingDayAndUnitsIntegrity(transaction);
      transaction.validateCashaccountAmount(getOpenPositionMarginPosition(transaction), currencyFraction);
      newTransaction = saveSecurityTransaction(transaction, existingEntity, securityaccount, adjustHoldings);
      break;

    case FEE:
      // Fee can be plus not only minus
      transaction.setCashaccountAmount(transaction.getCashaccountAmount() * -1.0);
    case WITHDRAWAL:
    case DEPOSIT:
    case INTEREST_CASHACCOUNT:
      transaction.clearAccountTransaction();
      transaction.validateCashaccountAmount(null, currencyFraction);
      newTransaction = saveTransactionAndCorrectCashaccountBalance(transaction, existingEntity, adjustHoldings,
          isCashAccountTransfer);
      break;
    default:
      break;
    }
    return newTransaction;
  }

  private Transaction getOpenPositionMarginPosition(Transaction transaction) {
    Transaction openPositionMarginTransaction = null;
    if (transaction.isMarginInstrument() && !transaction.isMarginOpenPosition()) {
      openPositionMarginTransaction = this.transactionJpaRepository
          .findByIdTransactionAndIdTenant(transaction.getConnectedIdTransaction(), transaction.getIdTenant());
      if (openPositionMarginTransaction == null) {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      } else if (transaction.getTransactionType() == TransactionType.FINANCE_COST) {
        openPositionMarginTransaction = null;
      }
    }
    return openPositionMarginTransaction;
  }

  private void checkTradingDayAndUnitsIntegrity(Transaction transaction) {
    final List<Transaction> transactions = filterMarginTransaction(transaction,
        transactionJpaRepository.findByIdSecurityaccountAndIdSecurity(transaction.getIdSecurityaccount(),
            transaction.getSecurity().getIdSecuritycurrency()));

    Optional<TradingDaysPlus> tradingDayPlusOpt = tradingDaysPlusJpaRepository
        .findById(DateHelper.getLocalDate(transaction.getTransactionTime()));
    if (tradingDayPlusOpt.isEmpty()) {
      throw new DataViolationException("transaction.time", "transaction.time.notrading", null);
    }
    checkUnitsIntegrity((transaction.getIdTransaction() == null) ? OperationType.ADD : OperationType.UPDATE,
        transactions, transaction, transaction.getSecurity());
  }

  private Transaction saveSecurityTransaction(Transaction transaction, Transaction existingEntity,
      Securityaccount securityaccount, boolean adjustHoldings) {
    if (transaction.getTransactionType() == TransactionType.DIVIDEND && transaction.isTaxableInterest() == null) {
      transaction.setTaxableInterest(false);
    }

    Transaction transactioinNew = saveTransactionAndCorrectCashaccountBalance(transaction, existingEntity,
        adjustHoldings, false);
    if (adjustHoldings) {
      adjustSecurityaccountHoldings(transactioinNew, securityaccount, true);
    }
    return transactioinNew;
  }

  private List<Transaction> filterMarginTransaction(final Transaction targetTransaction,
      List<Transaction> transactions) {
    List<Transaction> transactionsMargin = transactions;
    if (targetTransaction.isMarginInstrument()) {
      if (targetTransaction.isMarginOpenPosition()) {
        // Open position -> add all other positions
        if (targetTransaction.getIdTransaction() == null) {
          transactionsMargin = new ArrayList<>();
        } else {
          transactionsMargin = transactions.stream()
              .filter(t -> targetTransaction.getIdTransaction().equals(t.getConnectedIdTransaction())
                  && targetTransaction.getTransactionType() != TransactionType.FINANCE_COST)
              .collect(Collectors.toList());
        }
      } else {
        // A close position -> add all other positions
        transactionsMargin = transactions.stream()
            .filter(t -> (targetTransaction.getConnectedIdTransaction().equals(t.getConnectedIdTransaction())
                || targetTransaction.getConnectedIdTransaction().equals(t.getIdTransaction()))
                && !t.getIdTransaction().equals(targetTransaction.getIdTransaction())
                && targetTransaction.getTransactionType() != TransactionType.FINANCE_COST)
            .collect(Collectors.toList());
      }
    }
    return transactionsMargin;
  }

  private void adjustSecurityaccountHoldings(Transaction transaction, Securityaccount securityaccount, boolean isAdded) {
    if (transaction.getTransactionType() == TransactionType.ACCUMULATE
        || transaction.getTransactionType() == TransactionType.REDUCE) {
      holdSecurityaccountSecurityRepository.adjustSecurityHoldingForSecurityaccountAndSecurity(
          securityaccount == null
              ? securityaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(transaction.getIdSecurityaccount(),
                  transaction.getIdTenant())
              : securityaccount,
          transaction, isAdded);
    }
  }

  private Transaction saveTransactionAndCorrectCashaccountBalance(Transaction transaction, Transaction existingEntity,
      boolean adjustHoldings, boolean isCashAccountTransfer) {
    transaction = transactionJpaRepository.save(transaction);
    if (adjustHoldings) {
      holdCashaccountBalanceJpaRepository.adjustCashaccountBalanceByIdCashaccountAndFromDate(transaction);
      if (!isCashAccountTransfer && (transaction.getTransactionType() == TransactionType.DEPOSIT
          || transaction.getTransactionType() == TransactionType.WITHDRAWAL)) {
        holdCashaccountDepositJpaRepository.adjustCashaccountDepositOrWithdrawal(transaction, null);
      }
    }
    return transaction;
  }

  @Override
  @Transactional
  @Modifying
  public void deleteSingleDoubleTransaction(final Integer idTransaction) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    final Transaction transaction = transactionJpaRepository.findByIdTransactionAndIdTenant(idTransaction,
        user.getIdTenant());
    if (transaction != null) {
      if (transaction.getSecurity() != null) {
        deleteSecurityTransaction(transaction);
      } else {
        Transaction connectedTransaction = null;
        if (transaction.isCashaccountTransfer()) {
          connectedTransaction = transactionJpaRepository
              .findByIdTransactionAndIdTenant(transaction.getConnectedIdTransaction(), transaction.getIdTenant());
          removeTransaction(connectedTransaction);
        }
        removeTransaction(transaction);
        if (transaction.getTransactionType() == TransactionType.DEPOSIT
            || transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
          holdCashaccountDepositJpaRepository.adjustCashaccountDepositOrWithdrawal(transaction, connectedTransaction);
        }
      }
    }
  }

  private void deleteSecurityTransaction(final Transaction transaction) {
    final List<Transaction> transactions = filterMarginTransaction(transaction,
        transactionJpaRepository.findByIdSecurityaccountAndIdSecurity(transaction.getIdSecurityaccount(),
            transaction.getSecurity().getIdSecuritycurrency()));

    checkUnitsIntegrity(OperationType.DELETE, transactions, transaction, transaction.getSecurity());
    removeTransaction(transaction);
    adjustSecurityaccountHoldings(transaction, null, false);
  }

  private void checkUnitsIntegrity(final OperationType operationyType, final List<Transaction> transactions,
      final Transaction targetTransaction, final Security security) {
    if (targetTransaction.isMarginInstrument()) {
      SecurityMarginUnitsCheck.checkUnitsIntegrity(securitysplitJpaRepository, operationyType, transactions,
          targetTransaction, security);
    } else {
      SecurityGeneralUnitsCheck.checkUnitsIntegrity(securitysplitJpaRepository, operationyType, transactions,
          targetTransaction, targetTransaction.getSecurity());
    }
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Methods with Transaction which touches cash account
  //////////////////////////////////////////////////////////////////////////////

  private void removeTransaction(final Transaction transaction) {
    importTransactionPosJpaRepository.setTrasactionIdToNullWhenExists(transaction.getIdTransaction());
    transactionJpaRepository.delete(transaction);
    transactionJpaRepository.flush();
    holdCashaccountBalanceJpaRepository.adjustCashaccountBalanceByIdCashaccountAndFromDate(transaction);
  }

  ///////////////////////////////////////////////////////////////////////////////
  // Methods with Transaction which uses mainly security account
  //////////////////////////////////////////////////////////////////////////////

  @Override
  public CurrencypairWithTransaction getTransactionForCurrencyPair(final Integer idTenant, final Integer idCurrencypair,
      final boolean forChart) {

    final Currencypair currencypair = currencypairJpaRepository.getReferenceById(idCurrencypair);
    currencypairJpaRepository.updateLastPrice(currencypair);
    final CurrencypairWithTransaction cwt = new CurrencypairWithTransaction(currencypair);
    if (forChart) {
      addTransactionsForChart(idTenant, currencypair, cwt);
      final Currencypair currencypairReverse = currencypairJpaRepository
          .findByFromCurrencyAndToCurrency(currencypair.getToCurrency(), currencypair.getFromCurrency());
      if (currencypairReverse != null) {
        cwt.cwtReverse = new CurrencypairWithTransaction(currencypairReverse);
        addTransactionsForChart(idTenant, currencypairReverse, cwt.cwtReverse);
      }
    }

    return cwt;
  }

  private void addTransactionsForChart(final Integer idTenant, final Currencypair currencypair,
      final CurrencypairWithTransaction cwt) {
    List<Transaction> transactionList = this.transactionJpaRepository.findByCurrencypair(idTenant,
        currencypair.getIdSecuritycurrency());
    transactionList = transactionList.stream()
        .filter(transaction -> !(transaction.getCashaccount().getCurrency().equals(currencypair.getToCurrency())
            && transaction.isCashaccountTransfer()))
        .collect(Collectors.toList());
    cwt.transactionList = transactionList;

    for (final Transaction transaction : transactionList) {
      if (transaction.getCashaccount().getCurrency().equals(currencypair.getToCurrency())) {
        // For Security transaction with foreign currency, for example EUR/CHF -> CHF
        cwt.sumAmountTo -= transaction.getCashaccountAmount();
        cwt.sumAmountFrom -= transaction.getCashaccountAmount() / transaction.getCurrencyExRateNotNull();
      } else {
        // For cash transfer, for example EUR/CHF -> EUR
        cwt.sumAmountFrom += transaction.getCashaccountAmount();
        cwt.sumAmountTo += transaction.getCashaccountAmount() * transaction.getCurrencyExRateNotNull();
      }
    }
    if (currencypair.getSLast() != null) {
      cwt.gainTo = cwt.sumAmountFrom * currencypair.getSLast() - cwt.sumAmountTo;
      cwt.gainFrom = cwt.gainTo / currencypair.getSLast();
    }
  }

  @Override
  public CashaccountTransactionPosition[] getTransactionsWithSaldoForCashaccount(final Integer idSecuritycashAccount) {
    final Integer idTenant = ((User) SecurityContextHolder.getContext().getAuthentication().getDetails()).getIdTenant();

    final List<Transaction> transactions = transactionJpaRepository
        .findByCashaccount_idSecuritycashAccountAndIdTenantOrderByTransactionTimeDesc(idSecuritycashAccount, idTenant);

    final CashaccountTransactionPosition[] cashaccountTransactionPositions = new CashaccountTransactionPosition[transactions
        .size()];
    if (cashaccountTransactionPositions.length > 0) {
      int precision = globalparametersJpaRepository
          .getPrecisionForCurrency(transactions.get(0).getCashaccount().getCurrency());
      for (int i = cashaccountTransactionPositions.length - 1; i >= 0; i--) {
        cashaccountTransactionPositions[i] = new CashaccountTransactionPosition(transactions.get(i),
            (i == cashaccountTransactionPositions.length - 1) ? transactions.get(i).getCashaccountAmount()
                : DataHelper.round(
                    cashaccountTransactionPositions[i + 1].balance + transactions.get(i).getCashaccountAmount(),
                    precision));
      }
    }
    return cashaccountTransactionPositions;
  }

}
