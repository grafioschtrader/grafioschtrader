package grafioschtrader.repository;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
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

import grafiosch.BaseConstants;
import grafiosch.entities.User;
import grafiosch.error.ValidationError;
import grafiosch.exceptions.DataViolationException;
import grafiosch.exceptions.GeneralNotTranslatedWithArgumentsException;
import grafiosch.rest.helper.RestHelper;
import grafioschtrader.common.DataBusinessHelper;
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
import grafioschtrader.platformimport.CombineTemplateAndImpTransPos;
import grafioschtrader.types.ImportKnownOtherFlags;
import grafioschtrader.types.TransactionType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * Implementation of custom repository operations for import transaction position management and lifecycle processing.
 * 
 * <p>This class serves as the core business logic layer for converting imported financial transaction data
 * into validated, permanent transaction records. It handles the entire import transaction lifecycle from
 * initial data validation through final transaction creation, including sophisticated correction mechanisms,
 * duplicate detection, and multi-currency support.</p>
 */
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

  /**
   * Generic utility method for setting values on multiple import transaction positions with security validation. This
   * method provides a reusable pattern for batch updates while ensuring tenant security and proper readiness status
   * recalculation after each modification.
   * 
   * @param <V>                  The type of value being set on the import positions
   * @param idTenant             The tenant ID for security validation
   * @param idTransactionPosList List of import position IDs to update
   * @param value                The value to set on each position (null allowed if requireValue is false)
   * @param requireValue         Whether the value is required (throws SecurityException if null when true)
   * @param setter               BiConsumer function that applies the value to each import position
   * @return List of updated import transaction positions with recalculated readiness status
   * @throws SecurityException if value is null when required, or if any position belongs to different tenant
   */
  private <V> List<ImportTransactionPos> setImportTransactionPosValue(Integer idTenant,
      List<Integer> idTransactionPosList, V value, boolean requireValue, BiConsumer<ImportTransactionPos, V> setter) {
    List<ImportTransactionPos> setImportTransactionPosList = new ArrayList<>();
    if (value == null && requireValue) {
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    idTransactionPosList.forEach(idTransactionPos -> {
      ImportTransactionPos importTransactionPos = importTransactionPosJpaRepository
          .findByIdTransactionPosAndIdTenant(idTransactionPos, idTenant);
      if (importTransactionPos != null) {
        setter.accept(importTransactionPos, value);
        setImportTransactionPosList.add(importTransactionPos);
      } else {
        throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
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

  /**
   * Applies data correction functions to import positions with calculation discrepancies. This utility method provides
   * a reusable pattern for correction operations that only apply to positions with non-zero cash account amount
   * differences.
   * 
   * <p>
   * The method filters positions to only process those belonging to the authenticated user and having actual
   * calculation differences, then applies the specified correction function and updates the readiness status.
   * </p>
   * 
   * @param <V>                  Generic type parameter for flexibility
   * @param idTransactionPosList List of import position IDs to process
   * @param adjuster             Consumer function that applies the specific correction logic
   * @return List of corrected import transaction positions with updated readiness status
   */
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

  /**
   * Sets potential duplicate transaction references for all import positions under a transaction header. This method
   * automatically identifies existing transactions that may match the import positions based on security, transaction
   * type, cash account, date, units, and amounts. It helps users identify potential duplicates before creating new
   * transactions.
   * 
   * <p>
   * The method queries for potential matches and updates only positions that haven't been explicitly marked as "not
   * duplicates" (idTransactionMaybe != 0). Positions with confirmed non-duplicate status retain their setting.
   * </p>
   * 
   * @param idTransactionHead        The transaction header ID to process
   * @param importTransactionPosList List of import positions to check for duplicates
   */
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

  /**
   * Saves import transaction positions and updates their readiness status with duplicate detection. This method
   * performs the critical step of determining transaction readiness while also identifying potential duplicate
   * transactions for user review.
   * 
   * <p>
   * The method:
   * </p>
   * <ul>
   * <li>Validates each position's readiness for transaction creation</li>
   * <li>Queries for potential duplicate transactions in the system</li>
   * <li>Updates maybe-transaction references for duplicate detection</li>
   * <li>Preserves user-confirmed non-duplicate settings (idTransactionMaybe = 0)</li>
   * <li>Saves all positions with updated status</li>
   * </ul>
   * 
   * @param importTransactionPosList List of import positions to save and validate
   * @return Updated list of saved import transaction positions with readiness and duplicate status
   */
  private List<ImportTransactionPos> saveAndCheckReady(List<ImportTransactionPos> importTransactionPosList) {
    // Import position with transaction can never be changed also a transaction
    // with a maybe transaction only when dTransactionMaybe == 0
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
  public void setCheckReadyForSingleTransaction(ImportTransactionPos itp) {
    if (itp.getCashaccount() != null && itp.getTransactionTime() != null && itp.getTransactionType() != null
        && itp.getCashaccountAmount() != null) {
      switch (itp.getTransactionType()) {
      case DIVIDEND:
        // Obviously there are also dividends at the weekend. GT does not support this,
        // so the payment day is postponed.
        itp.setTransactionTime(adjustIfWeekend(itp.getTransactionTime()));
      case ACCUMULATE:
      case REDUCE:
        if (itp.getSecurity() != null) {
          itp.calcDiffCashaccountAmountWhenPossible();
          if (itp.getDiffCashaccountAmount() == 0.0
              || (itp.getDiffCashaccountAmount() != 0.0 && itp.getAcceptedTotalDiff() != null
                  && itp.getDiffCashaccountAmount() == itp.getAcceptedTotalDiff().doubleValue())) {
            itp.setReadyForTransaction(true);
          }
        }
        break;
      default:
        itp.setReadyForTransaction(true);
        break;
      }
    }
  }

  /**
   * Adjusts weekend dates for dividend payments to ensure compliance with business day requirements. Since dividend
   * payments typically occur on business days, this method moves Saturday payments to Friday and Sunday payments to
   * Monday to align with standard trading calendar practices.
   * 
   * @param date Date to be checked and potentially adjusted
   * @return Adjusted date: Friday for Saturday dates, Monday for Sunday dates, unchanged for weekdays
   */
  private Date adjustIfWeekend(Date date) {
    Calendar calendar = Calendar.getInstance();
    calendar.setTime(date);
    int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
    if (dayOfWeek == Calendar.SATURDAY) {
      calendar.add(Calendar.DAY_OF_MONTH, -1);
    } else if (dayOfWeek == Calendar.SUNDAY) {
      calendar.add(Calendar.DAY_OF_MONTH, 1);
    }
    return calendar.getTime();
  }

  @Override
  public void addPossibleExchangeRateForDividend(ImportTransactionHead importTransactionHead,
      ImportTransactionPos itp) {
    if (itp.isReadyForTransaction() && itp.getTransactionType() == TransactionType.DIVIDEND
        && itp.getCurrencyExRate() == null
        && !itp.getSecurity().getCurrency().equals(itp.getCashaccount().getCurrency())) {
      itp.addKnowOtherFlags(ImportKnownOtherFlags.CAN_CASH_SECURITY_CURRENCY_MISMATCH_BUT_EXCHANGE_RATE);
    }
  }

  @Override
  public List<ImportTransactionPos> createAndSaveTransactionsByIds(List<Integer> idTransactionPosList) {
    List<ImportTransactionPos> importTransactionPosList = importTransactionPosJpaRepository
        .findAllById(idTransactionPosList);
    if (!importTransactionPosList.isEmpty()) {
      Map<Integer, ImportTransactionPos> idItpMap = checkConnectedImpTransaction(importTransactionPosList);
      createAndSaveTransactionsFromImpPos(importTransactionPosList, idItpMap);
    }
    return importTransactionPosList;
  }

  /**
   * Validates that all connected import transactions are included in the processing batch. This method ensures that
   * cash account transfer transactions, which require both withdrawal and deposit positions, have all their connected
   * components available for processing.
   * 
   * <p>
   * Connected transactions represent paired operations like cash transfers between accounts where both sides must be
   * processed together to maintain transaction integrity.
   * </p>
   * 
   * @param importTransactionPosList List of import positions to validate for connected transactions
   * @return Map of position IDs to import positions for efficient connected transaction lookup
   * @throws GeneralNotTranslatedWithArgumentsException if connected transactions are missing from the batch
   */
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
  public List<SavedImpPosAndTransaction> createAndSaveTransactionsFromImpPos(
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
      throw new SecurityException(BaseConstants.CLIENT_SECURITY_BREACH);
    }
    return savedImpPosAndTransactions;
  }

  /**
   * Adjusts bond transaction units and quotations based on existing portfolio holdings. This method handles the
   * scenario where bond transactions are imported with unit=1 but should reflect the actual holding quantity. It looks
   * up existing holdings and adjusts both units and quotation to match the portfolio position.
   * 
   * <p>
   * This correction is necessary because some trading platforms report bond transactions differently than the actual
   * holding structure, requiring adjustment based on the current portfolio state to maintain accurate transaction
   * records.
   * </p>
   * 
   * @param importTransactionHead The transaction header containing portfolio context
   * @param itp                   The import transaction position to potentially adjust
   */
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

  /**
   * Resolves security currency mismatches by selecting the correct security based on portfolio holdings. When a
   * security exists in multiple currencies (same ISIN, different currencies), this method determines the correct
   * security by examining the portfolio's actual holdings and matching against the ISIN/currency combination.
   * 
   * <p>
   * This resolution is necessary when import templates cannot distinguish between securities with the same ISIN but
   * different trading currencies. The method uses portfolio holdings as the authoritative source for determining the
   * correct security currency.
   * </p>
   * 
   * @param importTransactionHead The transaction header containing portfolio context
   * @param itp                   The import transaction position with potential currency mismatch
   */
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

  /**
   * Retrieves security holdings from the portfolio for validation and correction purposes. This method looks up
   * holdings based on ISIN, security account, and date to support various correction scenarios like currency mismatch
   * resolution and bond adjustments.
   * 
   * @param importTransactionHead The transaction header containing portfolio context
   * @param itp                   The import transaction position requiring holdings lookup
   * @param unitsMustMatch        Whether to filter holdings by exact unit match (for bond adjustments)
   * @return Optional containing matching holdings, with preference for exact unit matches when required
   */
  private Optional<HoldSecurityaccountSecurity> getHoldings(ImportTransactionHead importTransactionHead,
      ImportTransactionPos itp, boolean unitsMustMatch) {
    Date exDateOrTransactionDate = itp.getExDate() != null ? itp.getExDate() : itp.getTransactionTime();
    List<HoldSecurityaccountSecurity> hssList = holdSecurityaccountSecurityJpaRepository
        .getByISINAndSecurityAccountAndDate(itp.getIsin(),
            importTransactionHead.getSecurityaccount().getIdSecuritycashAccount(), exDateOrTransactionDate);
    return hssList.stream().filter(hss -> !unitsMustMatch || itp.getUnits().equals(hss.getHodlings()) && unitsMustMatch)
        .findFirst().map(Optional::of).orElseGet(() -> hssList.stream().findFirst());
  }

  /**
   * Automatically sets missing currency exchange rates for dividend transactions in foreign currencies. This method
   * handles ETF and fund dividends that are paid in a different currency than the security's trading currency,
   * requiring automatic exchange rate lookup and application.
   * 
   * <p>
   * The method:
   * </p>
   * <ul>
   * <li>Creates or finds the appropriate currency pair</li>
   * <li>Looks up historical exchange rates for the transaction date</li>
   * <li>Applies exchange rates to dividend amounts and tax costs</li>
   * <li>Adjusts quotations for currency conversion</li>
   * </ul>
   * 
   * @param itp The import transaction position requiring exchange rate calculation
   * @return The currency pair ID used for the exchange rate, or null if no rate was needed
   */
  private Integer setPossibleMissingCurrencyExRate(ImportTransactionPos itp) {
    if (itp.getCurrencyExRate() == null
        && itp.getKnownOtherFlags()
            .contains(ImportKnownOtherFlags.CAN_CASH_SECURITY_CURRENCY_MISMATCH_BUT_EXCHANGE_RATE)
        && itp.getCashaccount().getCurrency() != null && itp.getSecurity().getCurrency() != null
        && !itp.getCashaccount().getCurrency().equals(itp.getSecurity().getCurrency())) {
      Currencypair currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(itp.getSecurity().getCurrency(),
          itp.getCashaccount().getCurrency());
      // It is as currency
      Integer idCurrencypair = this.currencypairJpaRepository.findOrCreateCurrencypairByFromAndToCurrency(
          currencypair.getFromCurrency(), currencypair.getToCurrency(), true).getIdSecuritycurrency();
      ISecuritycurrencyIdDateClose idc = historyquoteJpaRepository
          .getCertainOrOlderDayInHistorquoteByIdSecuritycurrency(idCurrencypair, itp.getTransactionTime(), false);
      itp.setCurrencyExRate(idc.getClose());
      if (itp.getTaxCost() != null) {
        itp.setTaxCost(itp.getTaxCost() / idc.getClose(), 0.0, false);
      }
      itp.setQuotation(itp.getQuotation() / idc.getClose());
      return idCurrencypair;
    }
    return null;
  }

  /**
   * Determines and loads currency pairs for multi-currency transactions with exchange rates. This method identifies the
   * appropriate currency pair based on transaction type and manages efficient currency pair loading for batch
   * operations.
   * 
   * <p>
   * Currency pair determination logic:
   * </p>
   * <ul>
   * <li><b>WITHDRAWAL:</b> From transaction currency to connected transaction currency</li>
   * <li><b>DEPOSIT:</b> From connected transaction currency to transaction currency</li>
   * <li><b>Other types:</b> From security currency to cash account currency</li>
   * </ul>
   * 
   * @param itp                            The import transaction position with exchange rate information
   * @param idItpMap                       Map of connected import positions for transfer transactions
   * @param currencypairs                  Pre-loaded currency pairs for efficiency (may be null)
   * @param loadCurrencypairsWhenNotLoaded Whether to load all currency pairs if not provided
   * @return The currency pair ID for the transaction, or null if no currency pair needed
   */
  private Integer getPossibleCurrencypairAndLoadCurrencypairs(ImportTransactionPos itp,
      Map<Integer, ImportTransactionPos> idItpMap, List<Currencypair> currencypairs,
      boolean loadCurrencypairsWhenNotLoaded) {
    Integer idCurrencypair = null;
    Currencypair currencypair = null;
    if (itp.getCurrencyExRate() != null) {
      // Must have a currencypair
      switch (itp.getTransactionType()) {
      case WITHDRAWAL:
        currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(itp.getCurrencyAccount(),
            idItpMap.get(itp.getConnectedIdTransactionPos()).getCurrencyAccount());
        break;
      case DEPOSIT:
        currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(
            idItpMap.get(itp.getConnectedIdTransactionPos()).getCurrencyAccount(), itp.getCurrencyAccount());
        break;
      default:
        currencypair = DataBusinessHelper.getCurrencypairWithSetOfFromAndTo(itp.getCurrencySecurity(),
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

  /**
   * Creates and saves cash account transfer transactions from paired import positions. This method handles the
   * coordination of withdrawal and deposit transactions that represent money transfers between cash accounts, ensuring
   * both sides are created atomically with proper transaction linking.
   * 
   * <p>
   * The method:
   * </p>
   * <ul>
   * <li>Creates paired Transaction objects for withdrawal and deposit</li>
   * <li>Handles existing transaction updates for import corrections</li>
   * <li>Ensures proper transaction sequencing and reference linking</li>
   * <li>Updates import positions with created transaction IDs</li>
   * </ul>
   * 
   * @param importTransactionHead The transaction header containing context
   * @param itpList               Array of exactly two import positions (withdrawal and deposit)
   * @param user                  The authenticated user for tenant validation
   * @param idCurrencypair        The currency pair ID for multi-currency transfers
   * @return List of saved transaction and import position pairs
   */
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


  /**
   * Creates and saves a single financial transaction from an import position with error handling.
   * This method converts a validated import position into a permanent transaction record,
   * applying proper transactional boundaries and comprehensive error handling for data violations.
   * 
   * <p>The method executes within a transaction template to ensure:</p>
   * <ul>
   *   <li>Atomic transaction creation or update</li>
   *   <li>Proper rollback on validation errors</li>
   *   <li>Error capture and storage for user feedback</li>
   *   <li>Import position updates with transaction references</li>
   * </ul>
   * 
   * @param importTransactionHead The transaction header containing context
   * @param itp The import transaction position to convert
   * @param user The authenticated user for tenant validation
   * @param idCurrencypair The currency pair ID for multi-currency transactions
   * @return Optional containing the saved transaction and position pair, empty if creation failed
   */
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

  /**
   * Captures and stores detailed error information for failed transaction creation attempts.
   * This method processes validation exceptions and converts them into user-friendly error
   * messages stored on the import position for troubleshooting and correction guidance.
   * 
   * <p>Error information includes:</p>
   * <ul>
   *   <li>Field-specific validation failure details</li>
   *   <li>Constraint violation descriptions</li>
   *   <li>Business rule validation messages</li>
   *   <li>Formatted error text for user display</li>
   * </ul>
   * 
   * @param itp The import transaction position that failed transaction creation
   * @param dvex The data violation exception containing detailed error information
   */
  private void saveTransactionErrors(ImportTransactionPos itp, DataViolationException dvex) {
    final StringBuilder errorStringBuilder = new StringBuilder();
    ValidationError validationError = RestHelper.createValidationError(dvex, messageSource);
    validationError.getFieldErrors()
        .forEach(fe -> errorStringBuilder.append(fe.getField() + ": " + fe.getMessage() + BaseConstants.NEW_LINE));
    itp.setTransactionError(errorStringBuilder.toString());
    importTransactionPosJpaRepository.save(itp);
  }

  @Override
  public void setTrasactionIdToNullWhenExists(Integer idTransaction) {
    importTransactionPosJpaRepository.findByIdTransaction(idTransaction).ifPresent(itp -> {
      itp.setIdTransaction(null);
      itp.setConnectedIdTransactionPos(null);
      importTransactionPosJpaRepository.save(itp);
    });
  }

  @Override
  @Transactional
  public int assignSecurityToMatchingImportPositions(Security security) {
    if (security == null || security.getIsin() == null || security.getCurrency() == null) {
      return 0;
    }

    List<ImportTransactionPos> matchingPositions = importTransactionPosJpaRepository
        .findByIsinAndCurrencyWithNoSecurity(security.getIsin(), security.getCurrency());

    if (matchingPositions.isEmpty()) {
      return 0;
    }

    log.info("Auto-assigning security {} (ISIN={}, currency={}) to {} import positions",
        security.getIdSecuritycurrency(), security.getIsin(), security.getCurrency(), matchingPositions.size());

    for (ImportTransactionPos itp : matchingPositions) {
      itp.setSecurityRemoveFromFlag(security);
      setCheckReadyForSingleTransaction(itp);
    }

    importTransactionPosJpaRepository.saveAll(matchingPositions);
    return matchingPositions.size();
  }

  /**
   * Data holder class that links a successfully created transaction with its corresponding import position.
   * This class serves as a return type for transaction creation operations, providing both the
   * permanent transaction record and the updated import position that references it.
   * 
   * <p>This pairing is essential for:</p>
   * <ul>
   *   <li>Tracking the relationship between import data and created transactions</li>
   *   <li>Providing feedback on successful transaction creation</li>
   *   <li>Supporting rollback operations if needed</li>
   *   <li>Maintaining audit trails for import processing</li>
   * </ul>
   * 
   * <p>The class uses public fields for simplicity and performance, as it serves as a
   * data transfer object within the import processing workflow.</p>
   */
  public static class SavedImpPosAndTransaction {
    /** The successfully created or updated transaction record */
    public Transaction transaction;
    
    /** The import position that was converted to the transaction, now containing transaction reference */
    public ImportTransactionPos importTransactionPos;

    /**
     * Creates a new link between a saved transaction and its corresponding import position.
     * 
     * @param transaction The successfully created or updated transaction
     * @param importTransactionPos The import position that was converted, updated with transaction ID
     */
    public SavedImpPosAndTransaction(Transaction transaction, ImportTransactionPos importTransactionPos) {
      this.transaction = transaction;
      this.importTransactionPos = importTransactionPos;
    }

  }
}
