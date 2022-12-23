package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import grafioschtrader.GlobalConstants;
import grafioschtrader.common.DataHelper;
import grafioschtrader.dto.CashAccountTransfer;
import grafioschtrader.dto.ISecuritycurrencyIdDateClose;
import grafioschtrader.entities.Cashaccount;
import grafioschtrader.entities.Currencypair;
import grafioschtrader.entities.HoldSecurityaccountSecurity;
import grafioschtrader.entities.ImportTransactionHead;
import grafioschtrader.entities.ImportTransactionPos;
import grafioschtrader.entities.ImportTransactionTemplate;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.Transaction;
import grafioschtrader.entities.User;
import grafioschtrader.error.ValidationError;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.rest.helper.RestHelper;
import grafioschtrader.types.ImportKnownOtherFlags;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

public class ImportTransactionPosJpaRepositoryImpl implements ImportTransactionPosJpaRepositoryCustom {

  @Autowired
  private ImportTransactionTemplateJpaRepository importTransactionTemplateJpaRepository;

  @Autowired
  private ImportTransactionHeadJpaRepository importTransactionHeadJpaRepository;

  @Autowired
  private ImportTransactionPosJpaRepository importTransactionPosJpaRepository;

  @Autowired
  private SecurityJpaRepository securityJpaRepository;

  @Autowired
  private CurrencypairJpaRepository currencypairJpaRepository;

  @Autowired
  private CashaccountJpaRepository cashaccountJpaRepository;

  @Autowired
  private TransactionJpaRepository transactionJpaRepository;

  @Autowired
  private HistoryquoteJpaRepository historyquoteJpaRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private HoldSecurityaccountSecurityJpaRepository holdSecurityaccountSecurityJpaRepository;

  @Autowired
  private MessageSource messageSource;

  @PersistenceContext
  private EntityManager entityManager;

  private final Logger log = LoggerFactory.getLogger(this.getClass());

  @Override
  public List<CombineTemplateAndImpTransPos> getCombineTemplateAndImpTransPosListByTransactionHead(
      Integer idTransactionHead) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<CombineTemplateAndImpTransPos> combineTemplateAndImpTransPosList = new ArrayList<>();

    List<ImportTransactionTemplate> importTransactionTemplateList = importTransactionTemplateJpaRepository
        .getImportTemplateByImportTransPos(idTransactionHead, user.getIdTenant());

    importTransactionTemplateList.forEach(importTransactionTemplate -> {
      entityManager.detach(importTransactionTemplate);
      importTransactionTemplate.setTemplateAsTxt(null);
    });
    Map<Integer, ImportTransactionTemplate> importTransactionTemplateMap = importTransactionTemplateList.stream()
        .collect(Collectors.toMap(ImportTransactionTemplate::getIdTransactionImportTemplate, Function.identity()));

    List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
        .findByIdTransactionHeadAndIdTenant(idTransactionHead, user.getIdTenant());
    setIdTrasactionMayBeForImportTransactionHead(idTransactionHead, importTransactionPosList);

    importTransactionPosList.forEach(importTransactionPos -> {
      importTransactionPos.calcDiffCashaccountAmountWhenPossible();
      Integer idTemplate = importTransactionPos.getIdTransactionImportTemplate();

      combineTemplateAndImpTransPosList.add(new CombineTemplateAndImpTransPos(importTransactionPos,
          idTemplate == null ? null : importTransactionTemplateMap.get(idTemplate)));
    });
    return combineTemplateAndImpTransPosList;
  }

  @Override
  @Modifying
  @Transactional
  public List<ImportTransactionPos> setSecurity(Integer idSecuritycurrency, List<Integer> idTransactionPosList) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Security security = securityJpaRepository
        .findByIdTenantPrivateIsNullOrIdTenantPrivateAndIdSecuritycurrency(idSecuritycurrency, user.getIdTenant());
    return setImportTransactionPosValue(user.getIdTenant(), idTransactionPosList, security, true,
        ImportTransactionPos::setSecurityRemoveFromFlag);
  }

  @Override
  public List<ImportTransactionPos> setCashAccount(Integer idSecuritycashAccount, List<Integer> idTransactionPosList) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    Cashaccount cashaccount = cashaccountJpaRepository.findByIdSecuritycashAccountAndIdTenant(idSecuritycashAccount,
        user.getIdTenant());
    return setImportTransactionPosValue(user.getIdTenant(), idTransactionPosList, cashaccount, true,
        ImportTransactionPos::setCashaccount);
  }

  @Override
  public List<ImportTransactionPos> setIdTransactionMayBe(Integer idTransactionMaybe,
      List<Integer> idTransactionPosList) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    return setImportTransactionPosValue(user.getIdTenant(), idTransactionPosList, idTransactionMaybe, false,
        ImportTransactionPos::setIdTransactionMaybe);
  }

  private <V> List<ImportTransactionPos> setImportTransactionPosValue(Integer idTenant,
      List<Integer> idTransactionPosList, V value, boolean requireValue, BiConsumer<ImportTransactionPos, V> setter) {
    List<ImportTransactionPos> setImportTransactionPosList = new ArrayList<>();
    if (value == null && requireValue) {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    idTransactionPosList.forEach(idTransactionPos -> {
      ImportTransactionPos importTransactionPos = importTransactionPosJpaRepository
          .findByIdTransactionPosAndIdTenant(idTransactionPos, idTenant);
      if (importTransactionPos != null) {
        setter.accept(importTransactionPos, value);
        setImportTransactionPosList.add(importTransactionPos);
      } else {
        throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
      }
    });
    return saveAndCheckReady(setImportTransactionPosList);
  }

  @Override
  public void deleteMultiple(List<Integer> idTransactionPosList) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
        .findAllById(idTransactionPosList);
    List<ImportTransactionPos> iTPDeleteList = importTransactionPosList.stream()
        .filter(importTransactionPos -> importTransactionPos.getIdTenant().equals(user.getIdTenant()))
        .collect(Collectors.toList());
    importTransactionPosJpaRepository.deleteAllInBatch(iTPDeleteList);
  }

  @Override
  public List<ImportTransactionPos> adjustCurrencyExRateOrQuotation(List<Integer> idTransactionPosList) {
    return correctDataWithFN(idTransactionPosList, ImportTransactionPos::adjustCurrencyExRateOrQuotation);
  }

  @Override
  public List<ImportTransactionPos> acceptTotalDiff(List<Integer> idTransactionPosList) {
    return correctDataWithFN(idTransactionPosList, ImportTransactionPos::adjustAcceptedTotalDiffToDiffTotal);
  }

  private <V> List<ImportTransactionPos> correctDataWithFN(List<Integer> idTransactionPosList,
      Consumer<ImportTransactionPos> adjuster) {
    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
    final List<ImportTransactionPos> changedImportTransactionPosList = new ArrayList<>();
    List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
        .findAllById(idTransactionPosList);
    importTransactionPosList.forEach(importTransactionPos -> {
      if (importTransactionPos.getIdTenant().equals(user.getIdTenant())) {
        importTransactionPos.calcDiffCashaccountAmountWhenPossible();
        if (importTransactionPos.getDiffCashaccountAmount() != 0.0) {
          changedImportTransactionPosList.add(importTransactionPos);
          adjuster.accept(importTransactionPos);
        }
      }
    });
    return saveAndCheckReady(changedImportTransactionPosList);
  }

  private void setIdTrasactionMayBeForImportTransactionHead(Integer idTransactionHead,
      List<ImportTransactionPos> importTransactionPosList) {
    List<ImportTransactionPos> itpList = new ArrayList<>();
    Map<Integer, Integer> mayHasTransactionIdPosMap = Arrays
        .stream(importTransactionPosJpaRepository
            .getIdTransactionPosWithPossibleTransactionByIdTransactionHead(idTransactionHead))
        .collect(Collectors.toMap(p -> p[0], p -> p[1]));
    importTransactionPosList.forEach(itp -> {
      var valueBefore = itp.getIdTransactionMaybe();
      if (!(itp.getIdTransactionMaybe() != null && itp.getIdTransactionMaybe().equals(0))) {
        itp.setIdTransactionMaybe(mayHasTransactionIdPosMap.get(itp.getIdTransactionPos()));
        if (!Objects.equals(valueBefore, itp.getIdTransactionMaybe())) {
          itpList.add(itp);
        }
      }
    });
    importTransactionPosJpaRepository.saveAll(itpList);
  }

  private List<ImportTransactionPos> saveAndCheckReady(List<ImportTransactionPos> importTransactionPosList) {
    // Import position with transaction can never be changed also a trans action
    // with a maybe transaction only
    // when dTransactionMaybe == 0
    List<Integer> idTransactionPosList = importTransactionPosList.stream()
        .filter(itp -> itp.getIdTransaction() == null
            || (itp.getIdTransactionMaybe() != null || itp.getIdTransactionMaybe().equals(0)))
        .map(ImportTransactionPos::getIdTransactionPos).collect(Collectors.toList());
    Map<Integer, Integer> mayHasTransactionIdPosMap = idTransactionPosList.isEmpty() ? new HashMap<>()
        : Arrays
            .stream(importTransactionPosJpaRepository
                .getIdTransactionPosWithPossibleTransactionByIdTransactionPos(idTransactionPosList))
            .collect(Collectors.toMap(p -> p[0], p -> p[1]));
    importTransactionPosList.forEach(itp -> {
      setCheckReadyForSingleTransaction(itp);
      if ((itp.getIdTransactionMaybe() == null || !itp.getIdTransactionMaybe().equals(0))) {
        itp.setIdTransactionMaybe(mayHasTransactionIdPosMap.get(itp.getIdTransactionPos()));
      }
    });
    return importTransactionPosJpaRepository.saveAll(importTransactionPosList);
  }

  @Override
  public void setCheckReadyForSingleTransaction(ImportTransactionPos importTransactionPos) {
    if (importTransactionPos.getCashaccount() != null && importTransactionPos.getTransactionTime() != null
        && importTransactionPos.getTransactionType() != null && importTransactionPos.getCashaccountAmount() != null) {
      switch (importTransactionPos.getTransactionType()) {
      case ACCUMULATE:
      case DIVIDEND:
      case REDUCE:
        if (importTransactionPos.getSecurity() != null) {
          importTransactionPos.calcDiffCashaccountAmountWhenPossible();
          if (importTransactionPos.getDiffCashaccountAmount() == 0.0
              || (importTransactionPos.getDiffCashaccountAmount() != 0.0
                  && importTransactionPos.getAcceptedTotalDiff() != null && importTransactionPos
                      .getDiffCashaccountAmount() == importTransactionPos.getAcceptedTotalDiff().doubleValue())) {
            importTransactionPos.setReadyForTransaction(true);
          }
        }
        break;
      default:
        importTransactionPos.setReadyForTransaction(true);
        break;
      }
    }
  }

  @Override
  public List<ImportTransactionPos> createAndSaveTransactionsByIds(List<Integer> idTransactionPosList) {
    List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
        .findAllById(idTransactionPosList);
    if (!importTransactionPosList.isEmpty()) {
      Map<Integer, ImportTransactionPos> idItpMap = checkConnectedImpTransaction(importTransactionPosList);
      createAndSaveTransactionsImpPos(importTransactionPosList, idItpMap);
    }
    return importTransactionPosList;
  }

  private Map<Integer, ImportTransactionPos> checkConnectedImpTransaction(
      List<ImportTransactionPos> importTransactionPosList) {
    Map<Integer, ImportTransactionPos> idItpMap = importTransactionPosList.stream()
        .collect(Collectors.toMap(ImportTransactionPos::getIdTransactionPos, Function.identity()));
    Set<Integer> idConnectedSet = importTransactionPosList.stream()
        .filter(itp -> itp.getConnectedIdTransactionPos() != null)
        .map(ImportTransactionPos::getConnectedIdTransactionPos).collect(Collectors.toSet());
    idConnectedSet.removeAll(idItpMap.keySet());
    if (!idConnectedSet.isEmpty()) {
      List<Integer> filePartList = idConnectedSet.stream()
          .map(id -> idItpMap.values().stream().filter(ipt -> ipt.getConnectedIdTransactionPos().equals(id)))
          .findFirst().get().map(ImportTransactionPos::getIdFilePart).collect(Collectors.toList());
      throw new GeneralNotTranslatedWithArgumentsException("gt.import.column.missing.connected",
          new Object[] { filePartList.stream().map(String::valueOf).collect(Collectors.joining(",")) });
    }
    return idItpMap;
  }

  @Override
  public List<SavedImpPosAndTransaction> createAndSaveTransactionsImpPos(
      List<ImportTransactionPos> importTransactionPosList, Map<Integer, ImportTransactionPos> idItpMap) {

    List<Currencypair> currencypairs = null;
    Set<Integer> doneCashaccountTranssferId = new HashSet<>();

    final User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();

    List<SavedImpPosAndTransaction> savedImpPosAndTransactions = new ArrayList<>();
    ImportTransactionHead importTransactionHead = importTransactionHeadJpaRepository
        .findByIdTransactionHeadAndIdTenant(importTransactionPosList.get(0).getIdTransactionHead(), user.getIdTenant());

    if (importTransactionHead != null) {
      // Sort by transaction time is required
      Collections.sort(importTransactionPosList);
      for (ImportTransactionPos itp : importTransactionPosList) {
        if (!doneCashaccountTranssferId.contains(itp.getIdTransactionPos())) {
          Integer idCurrencypair = null;
          // Only transaction that belongs to this tenant will be processed
          if (itp.isReadyForTransaction()
              && itp.getIdTransactionHead().equals(importTransactionHead.getIdTransactionHead())
              && itp.getCashaccount().getIdTenant().equals(user.getIdTenant())) {
            adjustBondUnitsAndQuotation(importTransactionHead, itp);
            correctSecurityCurrencyMissmatch(importTransactionHead, itp);
            idCurrencypair = setPossibleMissingCurrencyExRate(itp);

            if (idCurrencypair == null) {
              idCurrencypair = getPossibleCurrencypairAndLoadCurrencypairs(itp, idItpMap, currencypairs,
                  importTransactionPosList.size() > 1);
            }
            itp.calcCashaccountAmount();
          }
          if (idItpMap != null && itp.getConnectedIdTransactionPos() != null
              && idItpMap.containsKey(itp.getIdTransactionPos())) {
            // Connected cash account transfer
            ImportTransactionPos otherItp = idItpMap.get(itp.getConnectedIdTransactionPos());
            savedImpPosAndTransactions.addAll(saveCashAccountTransferTransaction(importTransactionHead,
                new ImportTransactionPos[] { itp, otherItp }, user, idCurrencypair));
            doneCashaccountTranssferId.add(otherItp.getIdTransactionPos());
          } else {
            // Other transaction
            saveSingleTransaction(importTransactionHead, itp, user, idCurrencypair)
                .ifPresent(savedImpPosAndTransactions::add);
          }
        }
      }
    } else {
      throw new SecurityException(GlobalConstants.CLIENT_SECURITY_BREACH);
    }
    return savedImpPosAndTransactions;
  }

  private void adjustBondUnitsAndQuotation(ImportTransactionHead importTransactionHead, ImportTransactionPos itp) {
    if (itp.getKnownOtherFlags().contains(
        ImportKnownOtherFlags.CAN_BOND_ADJUST_UNITS_AND_QUOTATION_WHEN_UNITS_EQUAL_ONE) && itp.getUnits().equals(1.0)) {
      Optional<HoldSecurityaccountSecurity> hssMatching = getHoldings(importTransactionHead, itp, false);
      if (hssMatching.isPresent()) {
        HoldSecurityaccountSecurity hss = hssMatching.get();
        itp.setUnits(hss.getHodlings());
        itp.setQuotation(itp.getQuotation() / itp.getUnits());
      }
    }
  }

  private void correctSecurityCurrencyMissmatch(ImportTransactionHead importTransactionHead, ImportTransactionPos itp) {
    if (itp.getKnownOtherFlags().contains(ImportKnownOtherFlags.SECURITY_CURRENCY_MISMATCH)) {
      Optional<HoldSecurityaccountSecurity> hssMatching = getHoldings(importTransactionHead, itp, true);
      if (hssMatching.isPresent()) {
        itp.removeKnowOtherFlags(ImportKnownOtherFlags.SECURITY_CURRENCY_MISMATCH);
        Security security = securityJpaRepository.getReferenceById(hssMatching.get().getHssk().getIdSecuritycurrency());
        itp.setSecurity(security);
      }
    }
  }

  private Optional<HoldSecurityaccountSecurity> getHoldings(ImportTransactionHead importTransactionHead,
      ImportTransactionPos itp, boolean unitsMustMatch) {
    Date exDateOrTransactionDate = itp.getExDate() != null ? itp.getExDate() : itp.getTransactionTime();
    List<HoldSecurityaccountSecurity> hssList = holdSecurityaccountSecurityJpaRepository
        .getByISINAndSecurityAccountAndDate(itp.getIsin(),
            importTransactionHead.getSecurityaccount().getIdSecuritycashAccount(), exDateOrTransactionDate);

    return hssList.stream().filter(hss -> !unitsMustMatch || itp.getUnits().equals(hss.getHodlings()) && unitsMustMatch)
        .findFirst().map(Optional::of).orElseGet(() -> hssList.stream().findFirst());
  }

  private Integer setPossibleMissingCurrencyExRate(ImportTransactionPos itp) {
    if (itp.getCurrencyExRate() == null
        && itp.getKnownOtherFlags()
            .contains(ImportKnownOtherFlags.CAN_CASH_SECURITY_CURRENCY_MISMATCH_BUT_EXCHANGE_RATE)
        && itp.getCashaccount().getCurrency() != null && itp.getSecurity().getCurrency() != null
        && !itp.getCashaccount().getCurrency().equals(itp.getSecurity().getCurrency())) {
      Currencypair currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(itp.getCurrencySecurity(),
          itp.getCashaccount().getCurrency());
      // It is as currency
      Integer idCurrencypair = this.currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(
          currencypair.getFromCurrency(), currencypair.getToCurrency(), true).getIdSecuritycurrency();
      ISecuritycurrencyIdDateClose idc = historyquoteJpaRepository
          .getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(idCurrencypair, itp.getTransactionTime(), false);
      itp.setCurrencyExRate(idc.getClose());
      // itp.setCashaccountAmount(itp.getCashaccountAmount() * idc.getClose());
      itp.setQuotation(itp.getQuotation() / idc.getClose());
      return idCurrencypair;
    }
    return null;
  }

  private Integer getPossibleCurrencypairAndLoadCurrencypairs(ImportTransactionPos itp,
      Map<Integer, ImportTransactionPos> idItpMap, List<Currencypair> currencypairs,
      boolean loadCurrencypairsWhenNotLoaded) {
    Integer idCurrencypair = null;
    Currencypair currencypair = null;
    if (itp.getCurrencyExRate() != null) {
      // Must have a currencypair
      switch (itp.getTransactionType()) {
      case WITHDRAWAL:
        currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(itp.getCurrencyAccount(),
            idItpMap.get(itp.getConnectedIdTransactionPos()).getCurrencyAccount());
        break;
      case DEPOSIT:
        currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(
            idItpMap.get(itp.getConnectedIdTransactionPos()).getCurrencyAccount(), itp.getCurrencyAccount());
        break;

      default:
        currencypair = DataHelper.getCurrencypairWithSetOfFromAndTo(itp.getCurrencySecurity(),
            itp.getCashaccount().getCurrency());
      }

      if (currencypairs == null && loadCurrencypairsWhenNotLoaded) {
        currencypairs = this.currencypairJpaRepository.findAll();
      }
      if (currencypairs != null) {
        idCurrencypair = this.currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(currencypairs,
            currencypair.getFromCurrency(), currencypair.getToCurrency()).getIdSecuritycurrency();
      } else {
        idCurrencypair = this.currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(
            currencypair.getFromCurrency(), currencypair.getToCurrency(), true).getIdSecuritycurrency();
      }
    }
    return idCurrencypair;
  }

  private List<SavedImpPosAndTransaction> saveCashAccountTransferTransaction(
      ImportTransactionHead importTransactionHead, ImportTransactionPos[] itpList, User user, Integer idCurrencypair) {
    Transaction[] existingTransactions = new Transaction[2];
    Transaction[] transactions = new Transaction[2];
    for (int i = 0; i < itpList.length; i++) {
      transactions[i] = new Transaction(itpList[i].getCashaccount(), itpList[i].getCashaccountAmount(),
          itpList[i].getTransactionType(), itpList[i].getTransactionTime());
      transactions[i].setCurrencyExRate(itpList[i].getCurrencyExRate());
      transactions[i].setIdCurrencypair(idCurrencypair);
      transactions[i].setTaxCost(itpList[i].getTaxCost());
      transactions[i].setIdTenant(user.getIdTenant());
      if (itpList[i].getIdTransaction() != null) {
        existingTransactions[i] = transactionJpaRepository.findByIdTransactionAndIdTenant(itpList[i].getIdTransaction(),
            user.getIdTenant());
        transactions[i]
            .setIdTransaction(existingTransactions[i] == null ? null : existingTransactions[i].getIdTransaction());
      }
    }

    CashAccountTransfer cashAccountTransfer = new CashAccountTransfer(transactions);
    CashAccountTransfer cashAccountTransferExisting = existingTransactions[0] != null
        ? new CashAccountTransfer(existingTransactions)
        : new CashAccountTransfer(cashAccountTransfer.getWithdrawalTransaction(),
            cashAccountTransfer.getDepositTransaction());

    cashAccountTransfer = transactionJpaRepository.updateCreateCashaccountTransfer(cashAccountTransfer,
        cashAccountTransferExisting);
    transactions = cashAccountTransfer.getTransactionsAsArray();
    int i = (itpList[0].getTransactionType() == transactions[0].getTransactionType()) ? 0 : 1;
    itpList[i].setIdTransaction(transactions[0].getIdTransaction());
    itpList[(i + 1) % 2].setIdTransaction(transactions[1].getIdTransaction());
    List<SavedImpPosAndTransaction> sip = new ArrayList<>();
    for (int k = 0; k < itpList.length; k++) {
      sip.add(
          new SavedImpPosAndTransaction(transactions[(i + k) % 2], importTransactionPosJpaRepository.save(itpList[k])));
    }
    return sip;
  }

  private Optional<SavedImpPosAndTransaction> saveSingleTransaction(ImportTransactionHead importTransactionHead,
      ImportTransactionPos itp, User user, Integer idCurrencypair) {

    return transactionTemplate.execute(status -> {
      try {
        Transaction transaction = new Transaction(importTransactionHead.getSecurityaccount().getIdSecuritycashAccount(),
            itp.getCashaccount(), itp.getSecurity(), itp.getCalcCashaccountAmount(), itp.getUnits(), itp.getQuotation(),
            itp.getTransactionType(), itp.getTaxCost(), itp.getTransactionCost(), itp.getAccruedInterest(),
            itp.getTransactionTime(), itp.getCurrencyExRate(), idCurrencypair, itp.getExDate(),
            itp.getTaxableInterest());

        Transaction existingEntity = null;
        if (itp.getIdTransaction() != null) {
          // Existing transaction, it will be checked for the right tenant
          existingEntity = transactionJpaRepository.findByIdTransactionAndIdTenant(itp.getIdTransaction(),
              user.getIdTenant());
        }

        if (itp.getIdTransaction() == null || itp.getIdTransaction() != null && existingEntity != null) {
          // New or existing transaction can be created or updated
          transaction.setIdTenant(user.getIdTenant());
          Transaction savedTransaction = transactionJpaRepository.saveOnlyAttributesFormImport(transaction,
              existingEntity);
          itp.setIdTransaction(savedTransaction.getIdTransaction());
          itp.setIdTransactionMaybe(null);
          return Optional
              .of(new SavedImpPosAndTransaction(savedTransaction, importTransactionPosJpaRepository.save(itp)));
        }
      } catch (DataViolationException dvex) {
        saveTransactionErrors(itp, dvex);
      } catch (Exception ex) {
        log.error(ex.getMessage(), ex);
        status.setRollbackOnly();
      }
      return Optional.empty();
    });
  }

  private void saveTransactionErrors(ImportTransactionPos itp, DataViolationException dvex) {
    final StringBuilder errorStringBuilder = new StringBuilder();
    ValidationError validationError = RestHelper.createValidationError(dvex, messageSource);
    validationError.getFieldErrors()
        .forEach(fe -> errorStringBuilder.append(fe.getField() + ": " + fe.getMessage() + "\n"));
    itp.setTransactionError(errorStringBuilder.toString());
    importTransactionPosJpaRepository.save(itp);
  }

  public static class SavedImpPosAndTransaction {
    public Transaction transaction;
    public ImportTransactionPos importTransactionPos;

    public SavedImpPosAndTransaction(Transaction transaction, ImportTransactionPos importTransactionPos) {
      this.transaction = transaction;
      this.importTransactionPos = importTransactionPos;
    }

  }

  @Override
  public void setTrasactionIdToNullWhenExists(Integer idTransaction) {
    importTransactionPosJpaRepository.findByIdTransaction(idTransaction).ifPresent(itp -> {
      itp.setIdTransaction(null);
      itp.setConnectedIdTransactionPos(null);
      importTransactionPosJpaRepository.save(itp);
    });

  }

}
