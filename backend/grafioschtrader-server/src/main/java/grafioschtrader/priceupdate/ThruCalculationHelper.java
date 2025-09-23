package grafioschtrader.priceupdate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.Expression;
import com.ezylang.evalex.parser.ParseException;

import grafiosch.exceptions.DataViolationException;
import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.projection.IFormulaInSecurity;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.types.HistoryquoteCreateType;

/**
 * Helper class for calculating historical price data for derived financial instruments.
 * 
 * <p>
 * A derived product's price is calculated from one or more underlying instruments, optionally using a mathematical
 * formula. Only through a derived instrument does a currency pair become tradable; in this case the instrument has no
 * formula.
 * </p>
 * 
 * <p>
 * <strong>Formula System:</strong>
 * </p>
 * <ul>
 * <li>Primary linked security uses variable 'o' (defined in {@link SecurityDerivedLink#FIRST_VAR_NAME_LETTER})</li>
 * <li>Additional securities use variables 'p', 'q', 'r', 's'</li>
 * <li>Formulas evaluated using EvalEx expression library</li>
 * <li>Example: "(o + p) / 2" averages two instruments</li>
 * </ul>
 * 
 * <p>
 * All linked instruments must have quotes for the same dates. Missing data from any linked instrument prevents
 * calculation for that date.
 * </p>
 */
public abstract class ThruCalculationHelper {

  private static final Logger log = LoggerFactory.getLogger(ThruCalculationHelper.class);

  /**
   * Loads historical data for linked instruments and creates calculated history quotes.
   * 
   * <p>
   * Retrieves historical quotes for all linked instruments and generates calculated quotes based on the security's
   * formula (if present) or by copying prices directly.
   * </p>
   * 
   * @param securityDerivedLinkJpaRepository repository for retrieving additional instrument links
   * @param historyquoteJpaRepository        repository for loading historical quotes
   * @param security                         the derived security for which to calculate quotes
   * @param correctedFromDate                start date for historical data retrieval
   * @param toDateCalc                       end date for historical data retrieval
   * @return list of calculated {@link Historyquote} entities with {@link HistoryquoteCreateType#CALCULATED}
   */
  public static List<Historyquote> loadDataAndCreateHistoryquotes(
      final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository, IFormulaInSecurity security, Date correctedFromDate,
      Date toDateCalc) {
    List<Historyquote> historyquotes = null;
    List<SecurityDerivedLink> securityDerivedLinks = Collections.emptyList();
    if (security.isCalculatedPrice()) {
      // A calculated price may have additional links to other instruments
      securityDerivedLinks = securityDerivedLinkJpaRepository
          .findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(security.getIdSecuritycurrency());
      if (!securityDerivedLinks.isEmpty()) {
        historyquotes = historyquoteJpaRepository.getHistoryquoteFromDerivedLinksByIdSecurityAndDate(
            security.getIdSecuritycurrency(), correctedFromDate, toDateCalc, securityDerivedLinks.size());
      } else {
        historyquotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
            security.getIdLinkSecuritycurrency(), correctedFromDate, toDateCalc);
      }
    } else {
      historyquotes = historyquoteJpaRepository.findByIdSecuritycurrencyAndDateBetweenOrderByDate(
          security.getIdLinkSecuritycurrency(), correctedFromDate, toDateCalc);
    }
    return createHistoryquotes(security, historyquotes, securityDerivedLinks);
  }

  /**
   * Fills gaps in historical price data for a derived security by calculating missing end-of-day quotes.
   * 
   * <p>
   * Identifies dates where all dependency securities have data but the derived security doesn't, then calculates and
   * creates new historyquote entries using the security's formula and dependency data.
   * </p>
   * 
   * @param securityDerivedLinkJpaRepository repository to fetch additional security dependencies
   * @param historyquoteJpaRepository        repository to query missing historical quotes
   * @param security                         the derived security that needs gap filling
   * @param maxDate                          the maximum date to check for gaps (inclusive)
   * @return list of newly created historyquote entries, ordered by date descending
   */
  public static List<Historyquote> fillGaps(final SecurityDerivedLinkJpaRepository securityDerivedLinkJpaRepository,
      HistoryquoteJpaRepository historyquoteJpaRepository, IFormulaInSecurity security, Date maxDate) {
    List<Historyquote> historyquotes = historyquoteJpaRepository
        .getMissingDerivedSecurityEOD(security.getIdSecuritycurrency(), maxDate);
    List<SecurityDerivedLink> securityDerivedLinks = securityDerivedLinkJpaRepository
        .findByIdEmIdSecuritycurrencyOrderByIdEmIdSecuritycurrency(security.getIdSecuritycurrency());
    return createHistoryquotes(security, historyquotes, securityDerivedLinks);
  }

  /**
   * Creates calculated history quotes from loaded data and formula expression.
   * 
   * @param security             the derived security with optional formula
   * @param historyquotes        source historical quotes from linked instruments
   * @param securityDerivedLinks additional instrument links (empty for single-link securities)
   * @return list of calculated history quotes, or null if evaluation fails
   */
  private static List<Historyquote> createHistoryquotes(IFormulaInSecurity security, List<Historyquote> historyquotes,
      List<SecurityDerivedLink> securityDerivedLinks) {
    Expression expression = null;
    List<Historyquote> createdHistoryquotes = null;

    if (security.isCalculatedPrice()) {
      expression = new Expression(security.getFormulaPrices());
    }
    try {
      if (securityDerivedLinks.isEmpty()) {
        createdHistoryquotes = createHistoryquoteWithoutAddionalLinks(security, historyquotes, expression);
      } else {
        createdHistoryquotes = createHistoryquoteWitAddionalLinks(security, historyquotes, expression,
            securityDerivedLinks);
      }
    } catch (EvaluationException | ParseException e) {
      log.error("Failed to evalute expression for security with ID {}", security.getIdSecuritycurrency());
    }
    return createdHistoryquotes;
  }

  /**
   * Creates history quotes for derived security with single linked instrument.
   * 
   * <p>
   * Applies formula evaluation if present, otherwise copies close price directly.
   * </p>
   * 
   * @param security      the derived security
   * @param historyquotes source quotes from the single linked instrument
   * @param expression    optional formula expression for price calculation
   * @return list of calculated history quotes
   * @throws EvaluationException if formula evaluation fails
   * @throws ParseException      if formula parsing fails
   */
  private static List<Historyquote> createHistoryquoteWithoutAddionalLinks(IFormulaInSecurity security,
      List<Historyquote> historyquotes, Expression expression) throws EvaluationException, ParseException {
    List<Historyquote> createdHistoryquotes = new ArrayList<>();
    for (Historyquote historyquote : historyquotes) {
      Historyquote historyquoteDerived = new Historyquote(security.getIdSecuritycurrency(),
          HistoryquoteCreateType.CALCULATED, historyquote.getDate());
      if (expression != null) {
        expression.with(SecurityDerivedLink.FIRST_VAR_NAME_LETTER, BigDecimal.valueOf(historyquote.getClose()));
        historyquoteDerived.setClose(expression.evaluate().getNumberValue().doubleValue());
      } else {
        historyquoteDerived.setClose(historyquote.getClose());
      }
      createdHistoryquotes.add(historyquoteDerived);
    }
    return createdHistoryquotes;
  }

  /**
   * Creates history quotes for derived security with multiple linked instruments.
   * 
   * <p>
   * Groups quotes by date, evaluates formula with all variables populated, and creates calculated quotes only for dates
   * where all linked instruments have data.
   * </p>
   * 
   * @param security             the derived security with formula
   * @param historyquotes        combined quotes from all linked instruments
   * @param expression           formula expression requiring all variables
   * @param securityDerivedLinks additional instrument links providing variable mappings
   * @return list of calculated history quotes
   * @throws EvaluationException if formula evaluation fails
   * @throws ParseException      if formula parsing fails
   */
  private static List<Historyquote> createHistoryquoteWitAddionalLinks(IFormulaInSecurity security,
      List<Historyquote> historyquotes, Expression expression, List<SecurityDerivedLink> securityDerivedLinks)
      throws EvaluationException, ParseException {
    List<Historyquote> createdHistoryquotes = new ArrayList<>();
    Date groupDate = null;
    Map<Integer, String> idSecurityToVarNameMap = ThruCalculationHelper.createIdSecurityToVarNameMap(security,
        securityDerivedLinks);
    for (Historyquote historyquote : historyquotes) {
      if (groupDate == null || !groupDate.equals(historyquote.getDate())) {
        addCreatedHistoryquote(createdHistoryquotes, groupDate, security, expression);
        groupDate = historyquote.getDate();
      }
      expression.with(idSecurityToVarNameMap.get(historyquote.getIdSecuritycurrency()),
          BigDecimal.valueOf(historyquote.getClose()));
    }
    addCreatedHistoryquote(createdHistoryquotes, groupDate, security, expression);
    return createdHistoryquotes;
  }

  /**
   * Adds calculated history quote for a specific date if valid.
   * 
   * @param createdHistoryquotes accumulator list for created quotes
   * @param groupDate            date for the calculated quote (null if incomplete data)
   * @param security             the derived security
   * @param expression           formula with all variables populated
   * @throws EvaluationException if formula evaluation fails
   * @throws ParseException      if formula parsing fails
   */
  private static void addCreatedHistoryquote(List<Historyquote> createdHistoryquotes, Date groupDate,
      IFormulaInSecurity security, Expression expression) throws EvaluationException, ParseException {
    if (groupDate != null) {
      Historyquote historyquoteDerived = new Historyquote(security.getIdSecuritycurrency(),
          HistoryquoteCreateType.CALCULATED, groupDate);
      createdHistoryquotes.add(historyquoteDerived);
      // Calculate save before history quote
      historyquoteDerived.setClose(expression.evaluate().getNumberValue().doubleValue());
    }
  }

  /**
   * Creates mapping from linked security IDs to formula variable names.
   * 
   * <p>
   * Maps the primary linked security to variable 'o' and additional links to their configured variable names ('p', 'q',
   * 'r', 's').
   * </p>
   * 
   * @param security             derived security with primary link
   * @param securityDerivedLinks additional linked securities with variable assignments
   * @return map of security ID to variable name for formula evaluation
   */
  public static Map<Integer, String> createIdSecurityToVarNameMap(IFormulaInSecurity security,
      List<SecurityDerivedLink> securityDerivedLinks) {
    Map<Integer, String> idSecurityToVarNameMap = new HashMap<>();
    idSecurityToVarNameMap.put(security.getIdLinkSecuritycurrency(), SecurityDerivedLink.FIRST_VAR_NAME_LETTER);
    securityDerivedLinks.stream()
        .forEach(sdl -> idSecurityToVarNameMap.put(sdl.getIdLinkSecuritycurrency(), sdl.getVarName()));
    return idSecurityToVarNameMap;
  }

  /**
   * Validates that security's formula correctly references all linked instruments.
   * 
   * <p>
   * Ensures formula contains required variables and all variables correspond to valid links:
   * </p>
   * <ul>
   * <li>Formula must contain primary variable 'o'</li>
   * <li>Formula must contain variable for each additional link</li>
   * <li>All link variable names must be in allowed set ('p', 'q', 'r', 's')</li>
   * <li>Formula must evaluate successfully with test values</li>
   * </ul>
   * 
   * @param security  the security with formula and derived links
   * @param localeStr locale for error message translation
   * @throws ParseException         if formula syntax is invalid
   * @throws EvaluationException    if formula cannot be evaluated
   * @throws DataViolationException if formula missing required variables or using invalid variable names
   */
  public static void checkFormulaAgainstInstrumetLinks(Security security, String localeStr)
      throws ParseException, EvaluationException {
    if (security.isCalculatedPrice()) {
      Expression expression = new Expression(security.getFormulaPrices());

      if (!expression.getUsedVariables().contains(SecurityDerivedLink.FIRST_VAR_NAME_LETTER)) {
        throw new DataViolationException("formula.prices", "gt.formula.must.contain.variable",
            new Object[] { SecurityDerivedLink.FIRST_VAR_NAME_LETTER }, localeStr);
      }
      expression.with(SecurityDerivedLink.FIRST_VAR_NAME_LETTER, new BigDecimal(1));
      // Check formula contains every linked Instrument and every variable in formula
      // has a value
      for (int i = 0; i < security.getSecurityDerivedLinks().length; i++) {
        SecurityDerivedLink securityDerivedLink = security.getSecurityDerivedLinks()[i];
        if (!expression.getUsedVariables().contains(securityDerivedLink.getVarName())) {
          throw new DataViolationException("formula.prices", "gt.formula.must.contain.variable",
              new Object[] { securityDerivedLink.getVarName() }, localeStr);
        }
        if (SecurityDerivedLink.ALLOWED_VAR_NAMES_LINKS.contains(securityDerivedLink.getVarName())) {
          expression.with(securityDerivedLink.getVarName(), new BigDecimal(1));
        } else {
          throw new DataViolationException("additional.instrument.name", "gt.formula.varname",
              securityDerivedLink.getVarName(), localeStr);
        }
      }
      expression.evaluate();

    }
  }

}
