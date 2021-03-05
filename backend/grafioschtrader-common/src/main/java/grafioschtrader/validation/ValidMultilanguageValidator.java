package grafioschtrader.validation;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import grafioschtrader.GlobalConstants;
import grafioschtrader.entities.MultilanguageString;

public class ValidMultilanguageValidator implements ConstraintValidator<ValidMultilanguage, MultilanguageString> {

  @Override
  public boolean isValid(MultilanguageString value, ConstraintValidatorContext context) {
    return value.getMap().size() == 2
        && value.getMap().entrySet().stream().filter(x -> (GlobalConstants.GT_LANGUAGE_CODES.contains(x.getKey()))
            && x.getValue() != null && x.getValue().trim().length() > 0).count() == 2;
  }

}
