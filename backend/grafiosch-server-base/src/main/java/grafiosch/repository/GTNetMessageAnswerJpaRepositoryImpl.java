package grafiosch.repository;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.parser.ParseException;

import grafiosch.entities.GTNet;
import grafiosch.entities.GTNetMessage;
import grafiosch.entities.GTNetMessageAnswer;
import grafiosch.entities.User;
import grafiosch.exceptions.DataViolationException;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Repository implementation for GTNetMessageAnswer entity.
 * Handles validation of EvalEx expressions in responseMsgConditional field.
 */
public class GTNetMessageAnswerJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessageAnswer>
    implements GTNetMessageAnswerJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageAnswerJpaRepositoryImpl.class);

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Override
  public GTNetMessage getMessageAnswerBy(GTNet myGTNet, GTNet remoteGTNet, MessageEnvelope meRequest) {
    GTNetMessage gtNetMessageAnw = new GTNetMessage();
    List<GTNetMessageAnswer> messageAnswers = gtNetMessageAnswerJpaRepository
        .findByRequestMsgCodeOrderByPriority(meRequest.messageCode);
    if (messageAnswers.isEmpty()) {
      // No auto-response rules configured for this message type
      // Message will require manual admin review
    }

    return gtNetMessageAnw;
  }

  @Override
  public GTNetMessageAnswer saveOnlyAttributes(GTNetMessageAnswer newEntity, final GTNetMessageAnswer existingEntity,
      Set<Class<? extends Annotation>> updatePropertyLevelClasses) throws Exception {

    // Validate responseMsgConditional if present
    String conditional = newEntity.getResponseMsgConditional();
    if (conditional != null && !conditional.isBlank()) {
      validateEvalExExpression(conditional);
    }

    return RepositoryHelper.saveOnlyAttributes(gtNetMessageAnswerJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * Validates that the given string is a syntactically valid EvalEx expression.
   * Does not evaluate the expression, only checks syntax by triggering parsing.
   *
   * @param expressionString the expression to validate
   * @throws DataViolationException if the expression is syntactically invalid
   */
  private void validateEvalExExpression(String expressionString) {
    try {
      Expression expression = new Expression(expressionString);
      // Validate syntax by getting used variables - this triggers parsing
      expression.getUsedVariables();
      log.debug("Expression validated successfully: {}", expressionString);
    } catch (ParseException e) {
      log.warn("Invalid EvalEx expression: {} - Error: {}", expressionString, e.getMessage());
      String localeStr = getLocaleString();
      throw new DataViolationException("responseMsgConditional", "gt.evalex.invalid.expression",
          new Object[] { e.getMessage() }, localeStr);
    } catch (Exception e) {
      log.warn("Error parsing expression: {} - Error: {}", expressionString, e.getMessage());
      String localeStr = getLocaleString();
      throw new DataViolationException("responseMsgConditional", "gt.evalex.parse.error",
          new Object[] { e.getMessage() }, localeStr);
    }
  }

  /**
   * Gets the locale string from the current user context.
   *
   * @return locale string or null if not available
   */
  private String getLocaleString() {
    try {
      User user = (User) SecurityContextHolder.getContext().getAuthentication().getDetails();
      return user.createAndGetJavaLocale().toString();
    } catch (Exception e) {
      return null;
    }
  }

}
