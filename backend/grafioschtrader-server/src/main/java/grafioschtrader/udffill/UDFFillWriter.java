package grafioschtrader.udffill;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import grafiosch.BaseConstants;
import grafiosch.entities.UDFData;
import grafiosch.entities.UDFData.UDFDataKey;
import grafioschtrader.dto.UDFFillProposalDecision;
import grafioschtrader.dto.UDFFillProposalDecisionResult.Outcome;
import grafioschtrader.entities.Security;
import grafioschtrader.entities.UDFDataFill;
import grafioschtrader.entities.UDFDataFill.UDFDataFillKey;
import grafioschtrader.entities.UDFMetadataSecurity;
import grafioschtrader.types.UDFFillSource;
import grafioschtrader.types.UDFFillState;
import grafioschtrader.udffill.UDFFillDecision.RowAction;
import grafioschtrader.udffill.UDFFillDecision.ValueAction;
import grafioschtrader.udffill.UDFFillOutcome.Value;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;

/**
 * The only code path that writes an automatic value or a fill row. Each call runs in its own short transaction
 * ({@code PROPAGATION_REQUIRES_NEW}) and locks the {@code udf_data} row of the owner of the field and the fill row with
 * {@code SELECT ... FOR UPDATE} before it decides, so that a user save that committed earlier is seen and a user save
 * that commits later overwrites the document with the user's values: manual values never lose.
 *
 * A write changes only the key {@code f<id>} of the field and leaves every sibling key as it was read.
 */
@Service
public class UDFFillWriter {

  @PersistenceContext
  private EntityManager em;

  private final TransactionTemplate transactionTemplate;

  public UDFFillWriter(PlatformTransactionManager transactionManager) {
    this.transactionTemplate = new TransactionTemplate(transactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  /**
   * Applies the outcome of a fill source to one cell according to the write rule.
   *
   * @param field      the field as it was when the outcome was computed; its fill rule version must still be current
   * @param idSecurity the security
   * @param outcome    the outcome of the source
   * @return what was done with the cell
   */
  public UDFFillWriteResult write(UDFMetadataSecurity field, int idSecurity, UDFFillOutcome outcome) {
    return inTransactionWithRetry(() -> writeLocked(field, idSecurity, outcome));
  }

  private UDFFillWriteResult writeLocked(UDFMetadataSecurity field, int idSecurity, UDFFillOutcome outcome) {
    UDFMetadataSecurity current = em.find(UDFMetadataSecurity.class, field.getIdUDFMetadata());
    if (current == null || current.getFillRuleVersion() != field.getFillRuleVersion()
        || current.getFillSource() == UDFFillSource.MANUAL) {
      return UDFFillWriteResult.STALE;
    }
    String key = BaseConstants.UDF_FIELD_PREFIX + current.getIdUDFMetadata();
    UDFData udfData = lockUdfData(current, idSecurity);
    UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(current.getIdUDFMetadata(), idSecurity),
        LockModeType.PESSIMISTIC_WRITE);
    String cur = canonicalValue(udfData, key);
    UDFFillDecision decision = UDFFillDecision.decide(fill == null ? null : fill.getFillState(),
        fill == null ? null : fill.getFilledValue(), outcome, cur, current.getFillSource() == UDFFillSource.TEMPLATE);

    if (decision.valueAction() == ValueAction.WRITE) {
      String value = ((Value) outcome).value();
      String invalid = UDFFillTemplate.validate(value, current);
      if (invalid != null) {
        throw new IllegalArgumentException(invalid);
      }
      changeValue(udfData, current, idSecurity, key, value);
    } else if (decision.valueAction() == ValueAction.REMOVE) {
      changeValue(udfData, current, idSecurity, key, null);
    }
    applyRowAction(decision.rowAction(), fill, current, idSecurity, outcome);
    return decision.result();
  }

  /**
   * Upserts a LOCKED row: the user took the cell over and nothing automatic touches it, whether it holds a value or
   * not. The value stays as it is.
   *
   * @param field      the field, which must have a fill source other than manual
   * @param idSecurity the security
   */
  public void lock(UDFMetadataSecurity field, int idSecurity) {
    inTransactionWithRetry(() -> {
      UDFData udfData = lockUdfData(field, idSecurity);
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(field.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      upsertFill(fill, field, idSecurity, UDFFillState.LOCKED,
          canonicalValue(udfData, BaseConstants.UDF_FIELD_PREFIX + field.getIdUDFMetadata()), null);
      return null;
    });
  }

  /**
   * Deletes the fill row of a cell, so that the next evaluation treats it like a cell that was never filled.
   *
   * @param field      the field
   * @param idSecurity the security
   */
  public void deleteFillRow(UDFMetadataSecurity field, int idSecurity) {
    inTransactionWithRetry(() -> {
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(field.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      if (fill != null) {
        em.remove(fill);
      }
      return null;
    });
  }

  /**
   * Removes the fill row of a security that left the tenant scope. An automatic value that the user did not edit is
   * removed with it, because nothing would maintain it any more.
   *
   * @param field      the field
   * @param idSecurity the security that is no longer in the tenant scope
   */
  public void removeOutOfScope(UDFMetadataSecurity field, int idSecurity) {
    inTransactionWithRetry(() -> {
      String key = BaseConstants.UDF_FIELD_PREFIX + field.getIdUDFMetadata();
      UDFData udfData = lockUdfData(field, idSecurity);
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(field.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      if (fill != null) {
        if (fill.getFillState() == UDFFillState.APPLIED && fill.getFilledValue() != null
            && fill.getFilledValue().equals(canonicalValue(udfData, key))) {
          changeValue(udfData, field, idSecurity, key, null);
        }
        em.remove(fill);
      }
      return null;
    });
  }

  /**
   * Records the verified outcome of a language model for an empty cell: PROPOSED with the value and its evidence,
   * DISCARDED or NOT_FOUND with the reason. A proposal is never written into {@code udf_data}. Nothing is recorded when
   * the cell is locked, got a value meanwhile, or the field changed since the outcome was computed.
   *
   * @param field      the field as it was when the outcome was computed
   * @param idSecurity the security
   * @param proposal   the state to record and its details
   * @return true when the row was recorded
   */
  public boolean recordProposal(UDFMetadataSecurity field, int idSecurity, ProposalRecord proposal) {
    return inTransactionWithRetry(() -> {
      UDFMetadataSecurity current = em.find(UDFMetadataSecurity.class, field.getIdUDFMetadata());
      if (current == null || current.getFillRuleVersion() != field.getFillRuleVersion()
          || current.getFillSource() != UDFFillSource.LLM) {
        return false;
      }
      UDFData udfData = lockUdfData(current, idSecurity);
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(current.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      String cur = canonicalValue(udfData, BaseConstants.UDF_FIELD_PREFIX + current.getIdUDFMetadata());
      if ((fill != null && fill.getFillState() == UDFFillState.LOCKED) || !UDFFillDecision.isEmpty(cur)) {
        return false;
      }
      // The new row is complete before it is persisted, because the insert takes the state at persist time
      UDFDataFill row = fill == null ? new UDFDataFill(new UDFDataFillKey(current.getIdUDFMetadata(), idSecurity))
          : fill;
      row.setFillState(proposal.state());
      row.setFillSource(current.getFillSource());
      row.setRuleVersion(current.getFillRuleVersion());
      row.setFilledValue(proposal.value());
      row.setSourceUrl(proposal.sourceUrl());
      row.setEvidence(proposal.evidence());
      row.setAsOfDate(proposal.asOfDate());
      row.setVerifyNote(proposal.verifyNote());
      row.setCheckedTime(nowUtc());
      if (fill == null) {
        em.persist(row);
      }
      return true;
    });
  }

  /**
   * Records the periodic refresh of a value the user once accepted. A verified proposal replaces the value without a
   * new acceptance and is written with its source and excerpt under the current rule version. Any other outcome keeps
   * the value and only sets the check time and the reason, so the cell is asked again only after the next period.
   * Nothing is written when the cell no longer holds the automatic value, for instance because the user changed it or
   * took it over, or when the field changed since the outcome was computed.
   *
   * @param field      the field as it was when the outcome was computed
   * @param idSecurity the security
   * @param proposal   the outcome; only PROPOSED carries a value
   * @return true when the row was updated
   */
  public boolean refreshApplied(UDFMetadataSecurity field, int idSecurity, ProposalRecord proposal) {
    return inTransactionWithRetry(() -> {
      UDFMetadataSecurity current = em.find(UDFMetadataSecurity.class, field.getIdUDFMetadata());
      if (current == null || current.getFillRuleVersion() != field.getFillRuleVersion()
          || current.getFillSource() != UDFFillSource.LLM) {
        return false;
      }
      String key = BaseConstants.UDF_FIELD_PREFIX + current.getIdUDFMetadata();
      UDFData udfData = lockUdfData(current, idSecurity);
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(current.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      String cur = canonicalValue(udfData, key);
      if (fill == null || fill.getFillState() != UDFFillState.APPLIED || UDFFillDecision.isEmpty(cur)
          || !cur.equals(fill.getFilledValue())) {
        return false;
      }
      if (proposal.state() == UDFFillState.PROPOSED) {
        String invalid = UDFFillTemplate.validate(proposal.value(), current);
        if (invalid != null) {
          throw new IllegalArgumentException(invalid);
        }
        changeValue(udfData, current, idSecurity, key, proposal.value());
        fill.setFilledValue(proposal.value());
        fill.setSourceUrl(proposal.sourceUrl());
        fill.setEvidence(proposal.evidence());
        fill.setAsOfDate(proposal.asOfDate());
        fill.setVerifyNote(null);
        fill.setRuleVersion(current.getFillRuleVersion());
      } else {
        fill.setVerifyNote(proposal.verifyNote());
      }
      fill.setCheckedTime(nowUtc());
      return true;
    });
  }

  /**
   * Accepts a proposal the user saw. The value is written only when the row is still PROPOSED with the version and
   * value the user saw and the field is unchanged; a manual value that arrived meanwhile wins and the proposal is
   * dropped. Accepting an already accepted proposal again answers APPLIED.
   *
   * @param field      the field of the caller
   * @param idSecurity the security
   * @param decision   version and value the user saw
   * @return APPLIED, CONFLICT or STALE
   */
  public Outcome acceptProposal(UDFMetadataSecurity field, int idSecurity, UDFFillProposalDecision decision) {
    return inTransactionWithRetry(() -> {
      UDFMetadataSecurity current = em.find(UDFMetadataSecurity.class, field.getIdUDFMetadata());
      if (current == null || current.getFillRuleVersion() != decision.ruleVersion()
          || current.getFillSource() != UDFFillSource.LLM) {
        return Outcome.STALE;
      }
      String key = BaseConstants.UDF_FIELD_PREFIX + current.getIdUDFMetadata();
      UDFData udfData = lockUdfData(current, idSecurity);
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(current.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      String cur = canonicalValue(udfData, key);
      if (fill == null || fill.getRuleVersion() != decision.ruleVersion()
          || !decision.value().equals(fill.getFilledValue())) {
        return Outcome.STALE;
      }
      if (fill.getFillState() == UDFFillState.APPLIED && decision.value().equals(cur)) {
        return Outcome.APPLIED;
      }
      if (fill.getFillState() != UDFFillState.PROPOSED) {
        return Outcome.STALE;
      }
      if (!UDFFillDecision.isEmpty(cur)) {
        em.remove(fill);
        return Outcome.CONFLICT;
      }
      String invalid = UDFFillTemplate.validate(decision.value(), current);
      if (invalid != null) {
        throw new IllegalArgumentException(invalid);
      }
      changeValue(udfData, current, idSecurity, key, decision.value());
      fill.setFillState(UDFFillState.APPLIED);
      fill.setCheckedTime(nowUtc());
      return Outcome.APPLIED;
    });
  }

  /**
   * Rejects a proposal the user saw, under the same conditions as an accept. Rejecting again answers REJECTED.
   *
   * @param field      the field of the caller
   * @param idSecurity the security
   * @param decision   version and value the user saw
   * @return REJECTED or STALE
   */
  public Outcome rejectProposal(UDFMetadataSecurity field, int idSecurity, UDFFillProposalDecision decision) {
    return inTransactionWithRetry(() -> {
      UDFMetadataSecurity current = em.find(UDFMetadataSecurity.class, field.getIdUDFMetadata());
      UDFDataFill fill = em.find(UDFDataFill.class, new UDFDataFillKey(field.getIdUDFMetadata(), idSecurity),
          LockModeType.PESSIMISTIC_WRITE);
      if (current == null || current.getFillRuleVersion() != decision.ruleVersion() || fill == null
          || fill.getRuleVersion() != decision.ruleVersion() || !decision.value().equals(fill.getFilledValue())) {
        return Outcome.STALE;
      }
      if (fill.getFillState() == UDFFillState.REJECTED) {
        return Outcome.REJECTED;
      }
      if (fill.getFillState() != UDFFillState.PROPOSED) {
        return Outcome.STALE;
      }
      fill.setFillState(UDFFillState.REJECTED);
      fill.setCheckedTime(nowUtc());
      return Outcome.REJECTED;
    });
  }

  /**
   * What a value proposal run records for one cell.
   *
   * @param state      PROPOSED, DISCARDED or NOT_FOUND
   * @param value      the canonical proposed value, null unless PROPOSED
   * @param sourceUrl  the page that names the value
   * @param evidence   the excerpt of the page
   * @param asOfDate   the date the page gives for the value, or null
   * @param verifyNote the reason of a DISCARDED or NOT_FOUND row
   */
  public record ProposalRecord(UDFFillState state, String value, String sourceUrl, String evidence, LocalDate asOfDate,
      String verifyNote) {
  }

  private UDFData lockUdfData(UDFMetadataSecurity field, int idSecurity) {
    return em.find(UDFData.class, new UDFDataKey(field.getIdUser(), Security.class.getSimpleName(), idSecurity),
        LockModeType.PESSIMISTIC_WRITE);
  }

  /**
   * Returns the canonical current value of a key: the string form of the stored value, null when it is absent.
   */
  static String canonicalValue(UDFData udfData, String key) {
    if (udfData == null || udfData.getJsonValues() == null) {
      return null;
    }
    Object value = udfData.getJsonValues().get(key);
    return value == null ? null : value.toString();
  }

  /**
   * Writes or removes one key. A new map is set, so that the JSON column is recognised as changed; every sibling key
   * stays as read under the lock. A missing row is inserted under the owner of the field.
   */
  private void changeValue(UDFData udfData, UDFMetadataSecurity field, int idSecurity, String key, String value) {
    if (udfData == null) {
      if (value != null) {
        Map<String, Object> values = new HashMap<>();
        values.put(key, value);
        em.persist(new UDFData(new UDFDataKey(field.getIdUser(), Security.class.getSimpleName(), idSecurity), values));
      }
      return;
    }
    Map<String, Object> values = udfData.getJsonValues() == null ? new HashMap<>()
        : new HashMap<>(udfData.getJsonValues());
    if (value == null) {
      values.remove(key);
    } else {
      values.put(key, value);
    }
    udfData.setJsonValues(values);
  }

  private void applyRowAction(RowAction rowAction, UDFDataFill fill, UDFMetadataSecurity field, int idSecurity,
      UDFFillOutcome outcome) {
    switch (rowAction) {
    case DELETE -> {
      if (fill != null) {
        em.remove(fill);
      }
    }
    case UPSERT_APPLIED -> {
      Value value = (Value) outcome;
      upsertFill(fill, field, idSecurity, UDFFillState.APPLIED, value.value(), value.sourceUrl());
    }
    case UPSERT_NOT_FOUND -> upsertFill(fill, field, idSecurity, UDFFillState.NOT_FOUND, null, null);
    case NONE -> {
    }
    }
  }

  /**
   * Updates the locked fill row, or inserts a new one. The state of a new row is complete before it is persisted,
   * because the insert takes the state at the time of {@code persist}.
   */
  private void upsertFill(UDFDataFill existing, UDFMetadataSecurity field, int idSecurity, UDFFillState state,
      String filledValue, String sourceUrl) {
    UDFDataFill row = existing == null ? new UDFDataFill(new UDFDataFillKey(field.getIdUDFMetadata(), idSecurity))
        : existing;
    row.setFillState(state);
    row.setFillSource(field.getFillSource());
    row.setRuleVersion(field.getFillRuleVersion());
    row.setFilledValue(filledValue);
    row.setSourceUrl(sourceUrl);
    row.setCheckedTime(nowUtc());
    if (existing == null) {
      em.persist(row);
    }
  }

  private static LocalDateTime nowUtc() {
    return LocalDateTime.now(ZoneOffset.UTC).withNano(0);
  }

  /**
   * Runs the work in a new transaction. A duplicate-key failure of a concurrent first insert of the {@code udf_data} or
   * fill row is retried once, and then sees the row of the other transaction.
   */
  private <T> T inTransactionWithRetry(Supplier<T> work) {
    try {
      return transactionTemplate.execute(_ -> work.get());
    } catch (DataIntegrityViolationException | PersistenceException e) {
      return transactionTemplate.execute(_ -> work.get());
    }
  }
}
