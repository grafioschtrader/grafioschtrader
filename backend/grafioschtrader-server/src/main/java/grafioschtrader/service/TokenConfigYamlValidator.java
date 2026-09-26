package grafioschtrader.service;

import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;

import grafioschtrader.common.StrictYaml;
import grafioschtrader.dto.TokenConfig;

/** Checks token acquisition configuration without contacting the remote authentication service. */
public final class TokenConfigYamlValidator {
  public List<String> validate(String yaml) {
    if (yaml == null || yaml.isBlank())
      return List.of();
    StrictYaml.validate(yaml);
    List<String> errors = YamlSchemaValidation.validate("token-config-schema.json", yaml);
    if (!errors.isEmpty())
      return errors;
    try {
      TokenConfig config = new YAMLMapper().readValue(yaml, TokenConfig.class);
      if (Pattern.compile(config.getSeed().getRegex()).matcher("").groupCount() < 1)
        return List.of("seed.regex: a capture group is required");
      return List.of();
    } catch (Exception e) {
      // Never echo the token body, URLs or source snippets into diagnostics.
      return List.of("Invalid token configuration or seed.regex");
    }
  }
}
