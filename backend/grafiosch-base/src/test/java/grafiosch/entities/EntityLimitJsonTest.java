package grafiosch.entities;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

class EntityLimitJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void deserializesBrowserCreatePayloadThroughDefaultConstructor() throws Exception {
    EntityLimit entityLimit = objectMapper.readValue("""
        {
          "keyId": "DAY_CUD|GenericConnectorDef|||",
          "idRole": 8,
          "idUser": null,
          "limitValue": 10,
          "validUntil": null
        }
        """, EntityLimit.class);

    assertThat(entityLimit.getKeyId()).isEqualTo("DAY_CUD|GenericConnectorDef|||");
    assertThat(entityLimit.getIdRole()).isEqualTo(8);
    assertThat(entityLimit.getIdUser()).isNull();
    assertThat(entityLimit.getLimitValue()).isEqualTo(10);
    assertThat(entityLimit.getValidUntil()).isNull();
    assertThat(validator.validate(entityLimit)).isEmpty();
  }
}
