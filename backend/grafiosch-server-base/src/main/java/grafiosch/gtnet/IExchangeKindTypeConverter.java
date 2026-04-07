package grafiosch.gtnet;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Spring converter for binding request parameters to {@link IExchangeKindType}.
 * Supports both numeric byte values (e.g., "0") and enum names (e.g., "LAST_PRICE").
 */
@Component
public class IExchangeKindTypeConverter implements Converter<String, IExchangeKindType> {

  private final ExchangeKindTypeRegistry registry;

  public IExchangeKindTypeConverter(ExchangeKindTypeRegistry registry) {
    this.registry = registry;
  }

  @Override
  public IExchangeKindType convert(String source) {
    IExchangeKindType result = registry.parse(source);
    if (result == null) {
      throw new IllegalArgumentException("Unknown exchange kind type: " + source);
    }
    return result;
  }
}
