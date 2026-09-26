package grafioschtrader.service;

import java.util.List;

import grafioschtrader.common.StrictYaml;

/** Structural validation only; simulation start also checks ownership, account choices and tariff dates. */
public final class CustodyOpeningYamlValidator {
  private CustodyOpeningYamlValidator() {
  }

  public static List<String> validate(String yaml) {
    if (yaml == null || yaml.isBlank())
      return List.of();
    StrictYaml.validate(yaml);
    return YamlSchemaValidation.validate("custody-opening-schema.json", yaml);
  }

  public static void requireValid(String yaml) {
    List<String> errors = validate(yaml);
    if (!errors.isEmpty())
      throw new IllegalArgumentException(String.join("; ", errors));
  }
}
