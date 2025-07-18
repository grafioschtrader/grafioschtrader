package grafioschtrader.validation;

import grafiosch.BaseConstants;
import grafiosch.entities.MultilanguageString;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class ValidMultilanguageValidator implements ConstraintValidator<ValidMultilanguage, MultilanguageString> {

  @Override
  public boolean isValid(MultilanguageString value, ConstraintValidatorContext context) {
    return value.getMap().size() == 2
        && value.getMap().entrySet().stream().filter(x -> (BaseConstants.G_LANGUAGE_CODES.contains(x.getKey()))
            && x.getValue() != null && x.getValue().trim().length() > 0).count() == 2;
  }

}
