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
import grafiosch.gtnet.GTNetMessageCodeRegistry;
import grafiosch.gtnet.GTNetProtocolDescriptor;
import grafiosch.gtnet.m2m.model.MessageEnvelope;

/**
 * Repository implementation for GTNetMessageAnswer entity. Handles validation of EvalEx expressions in
 * responseMsgConditional field.
 */
public class GTNetMessageAnswerJpaRepositoryImpl extends BaseRepositoryImpl<GTNetMessageAnswer>
    implements GTNetMessageAnswerJpaRepositoryCustom {

  private static final Logger log = LoggerFactory.getLogger(GTNetMessageAnswerJpaRepositoryImpl.class);

  @Autowired
  private GTNetMessageAnswerJpaRepository gtNetMessageAnswerJpaRepository;

  @Autowired
  private GTNetMessageCodeRegistry messageCodeRegistry;

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

    validateCodePair(newEntity);

    // Validate responseMsgConditional if present
    String conditional = newEntity.getResponseMsgConditional();
    if (conditional != null && !conditional.isBlank()) {
      validateEvalExExpression(conditional);
    }

    return RepositoryHelper.saveOnlyAttributes(gtNetMessageAnswerJpaRepository, newEntity, existingEntity,
        updatePropertyLevelClasses);
  }

  /**
   * Rejects a rule that pairs a request with an answer the protocol does not accept for it.
   *
   * <p>
   * Both columns are plain bytes and used to take any registered code, so nothing stopped a rule from answering a
   * handshake with a data-request rejection — and the resolver returned it unchecked. The protocol also marks the
   * answers a person may not choose at all: {@code GT_NET_FIRST_HANDSHAKE_REJECT_NOT_IN_LIST_S} is the refusal for a
   * domain this server does not know, and configured as a rule it would tell an already stored peer that it is not in
   * the list.
   * </p>
   *
   * @param rule the rule about to be persisted
   */
  private void validateCodePair(GTNetMessageAnswer rule) {
    GTNetProtocolDescriptor request = messageCodeRegistry.getDescriptor(rule.getRequestMsgCodeValue());
    if (request == null || !request.autoAnswerRequest()) {
      throw new DataViolationException("request.msg.code", "gt.gtnet.answer.request.not.allowed",
          new Object[] { request == null ? rule.getRequestMsgCodeValue() : request.name() });
    }
    GTNetProtocolDescriptor response = messageCodeRegistry.getDescriptor(rule.getResponseMsgCodeValue());
    if (response == null || !response.autoAnswerResponse()) {
      throw new DataViolationException("response.msg.code", "gt.gtnet.answer.response.not.allowed",
          new Object[] { response == null ? rule.getResponseMsgCodeValue() : response.name() });
    }
    if (!request.isValidResponse(response.value())) {
      throw new DataViolationException("response.msg.code", "gt.gtnet.answer.response.not.for.request",
          new Object[] { response.name(), request.name() });
    }
  }

  /**
   * Validates that the given string is a syntactically valid EvalEx expression. Does not evaluate the expression, only
   * checks syntax by triggering parsing.
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
      throw new DataViolationException("response.msg.conditional", "gt.evalex.invalid.expression",
          new Object[] { e.getMessage() }, localeStr);
    } catch (Exception e) {
      log.warn("Error parsing expression: {} - Error: {}", expressionString, e.getMessage());
      String localeStr = getLocaleString();
      throw new DataViolationException("response.msg.conditional", "gt.evalex.parse.error",
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
