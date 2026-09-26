package grafioschtrader.algo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import grafiosch.dynamic.model.DynamicModelHelper;
import grafioschtrader.entities.Tenant;
import jakarta.validation.Validation;

class SimulationTenantCreateDTOTest {

  @Test
  void requestNameValidationMatchesPersistedTenant() {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (String name : new String[] { "A", "A".repeat(25) }) {
        assertTrue(validator.validateValue(SimulationTenantCreateDTO.class, "tenantName", name).isEmpty());
        assertTrue(validator.validateValue(Tenant.class, "tenantName", name).isEmpty());
      }
      for (String name : new String[] { null, "", "   ", "A".repeat(26), "A".repeat(40) }) {
        assertFalse(validator.validateValue(SimulationTenantCreateDTO.class, "tenantName", name).isEmpty());
        assertFalse(validator.validateValue(Tenant.class, "tenantName", name).isEmpty());
      }
    }
  }

  @Test
  void generatedFormExposesBothNameBounds() {
    var definition = DynamicModelHelper.getFormDefinitionOfEntityClass(SimulationTenantCreateDTO.class, 1);
    var name = definition.fieldDescriptorInputAndShows.stream().filter(field -> "tenantName".equals(field.fieldName))
        .findFirst().orElseThrow();
    assertTrue(name.required);
    assertEquals(1.0, name.min);
    assertEquals(25.0, name.max);
  }
}
