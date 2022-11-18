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

import grafioschtrader.entities.Historyquote;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.SecurityDerivedLink;
import grafioschtrader.entities.projection.IFormulaInSecurity;
import grafioschtrader.exceptions.DataViolationException;
import grafioschtrader.repository.HistoryquoteJpaRepository;
import grafioschtrader.repository.SecurityDerivedLinkJpaRepository;
import grafioschtrader.types.HistoryquoteCreateType;

/*-
 *  A derived product always has one or more underlying instruments, and may also contain a calculation formula for the price data.
 *  Only through a derived instrument does a currency pair become tradable, in this case the instrument has no formula.
 *
 */
public abstract class ThruCalculationHelper {

  private static final Logger log = LoggerFactory.getLogger(ThruCalculationHelper.class);
  
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

    return createHistoryquotes(security, historyquotes, securityDerivedLinks, correctedFromDate, toDateCalc);
  }

  private static List<Historyquote> createHistoryquotes(IFormulaInSecurity security, List<Historyquote> historyquotes,
      List<SecurityDerivedLink> securityDerivedLinks, Date correctedFromDate, Date toDateCalc) {
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

  public static Map<Integer, String> createIdSecurityToVarNameMap(IFormulaInSecurity security,
      List<SecurityDerivedLink> securityDerivedLinks) {
    Map<Integer, String> idSecurityToVarNameMap = new HashMap<>();
    idSecurityToVarNameMap.put(security.getIdLinkSecuritycurrency(), SecurityDerivedLink.FIRST_VAR_NAME_LETTER);
    securityDerivedLinks.stream()
        .forEach(sdl -> idSecurityToVarNameMap.put(sdl.getIdLinkSecuritycurrency(), sdl.getVarName()));
    return idSecurityToVarNameMap;
  }

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
