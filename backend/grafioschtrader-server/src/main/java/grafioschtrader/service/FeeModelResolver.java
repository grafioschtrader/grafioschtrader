package grafioschtrader.service;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafioschtrader.dto.FeeModelConfig;
import grafioschtrader.dto.FxFeeConfig;
import grafioschtrader.entities.Securityaccount;

/** Sole precedence rule: commissions and custody travel together; FX inherits independently. */
public final class FeeModelResolver {
  public enum Source {
    ACCOUNT, PLAN, NONE
  }

  public record ResolvedFeeModel(String commissionYaml, Source commissionSource, FxFeeConfig fx, Source fxSource) {
  }

  private static final FeeModelYamlValidator VALIDATOR = new FeeModelYamlValidator();
  private static final YAMLMapper MAPPER = new YAMLMapper();

  private FeeModelResolver() {
  }

  public static ResolvedFeeModel resolve(Securityaccount account) {
    return resolve(account, account.getFeeModelYaml());
  }

  /** A blank unsaved document removes the account override for this preview. */
  public static ResolvedFeeModel resolve(Securityaccount account, String unsavedAccountYaml) {
    String planYaml = account.getTradingPlatformPlan() == null ? null
        : account.getTradingPlatformPlan().getFeeModelYaml();
    FeeModelConfig own = parse(unsavedAccountYaml, true);
    FeeModelConfig plan = parse(planYaml, false);
    boolean commissionOwn = own.getRules() != null || own.getPeriods() != null;
    boolean commissionPlan = plan.getRules() != null || plan.getPeriods() != null;
    return new ResolvedFeeModel(commissionOwn ? unsavedAccountYaml : commissionPlan ? planYaml : null,
        commissionOwn ? Source.ACCOUNT : commissionPlan ? Source.PLAN : Source.NONE,
        own.getFx() != null ? own.getFx() : plan.getFx(),
        own.getFx() != null ? Source.ACCOUNT : plan.getFx() != null ? Source.PLAN : Source.NONE);
  }

  static FeeModelConfig parse(String yaml, boolean account) {
    if (yaml == null || yaml.isBlank())
      return new FeeModelConfig();
    var errors = VALIDATOR.validate(yaml, account);
    if (!errors.isEmpty())
      throw new IllegalArgumentException(String.join("; ", errors));
    try {
      return MAPPER.readValue(yaml, FeeModelConfig.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid fee model", e);
    }
  }
}
