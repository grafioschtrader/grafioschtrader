package grafioschtrader.rest;

import java.util.List;

import org.springframework.web.bind.annotation.*;

import grafiosch.rest.RequestMappings;
import grafioschtrader.service.YamlConfigurationValidation;
import grafioschtrader.service.YamlConfigurationValidation.Diagnostic;
import grafioschtrader.service.YamlConfigurationValidation.Format;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Authenticated, side-effect-free validation of explicitly supported configuration formats. */
@RestController
@RequestMapping(RequestMappings.API + "yaml")
public class YamlValidationResource {
  public record ValidationRequest(@NotNull Format format, @Size(max = 1000000) String yaml, Boolean activatable) {
  }

  @PostMapping("/validate")
  public List<Diagnostic> validate(@Valid @RequestBody ValidationRequest request) {
    return YamlConfigurationValidation.validate(request.format(), request.yaml(),
        !Boolean.FALSE.equals(request.activatable()));
  }
}
