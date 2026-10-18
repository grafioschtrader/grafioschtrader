package grafioschtrader.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

import grafiosch.dto.ValueKeyHtmlSelectOptions;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Allowed tenant providers, their default models, and which ones require an API key.")
public record TenantAIProviderOptions(List<ValueKeyHtmlSelectOptions> providers, Set<String> keyProviders,
    Map<String, String> defaultModels, Map<String, String> providerNames) {
}
