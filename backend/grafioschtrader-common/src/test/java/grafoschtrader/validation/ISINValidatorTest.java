package grafoschtrader.validation;



import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import grafioschtrader.validation.ISINValidator;


class ISINValidatorTest {

  @Test
  void isValidTest() {
    ISINValidator iSINValidator = new ISINValidator();
    assert(iSINValidator.isValid("IE0032895942", null));
    assertFalse(iSINValidator.isValid("IE0032895943", null));
  }
}
