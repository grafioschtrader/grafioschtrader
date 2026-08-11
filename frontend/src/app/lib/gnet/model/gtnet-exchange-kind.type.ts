/**
 * Enum for entity kinds - types of data that can be exchanged over GTNet.
 *
 * The values are the numeric codes the backend persists and transports; generic code that only needs to describe an
 * exchange kind should prefer the backend supplied {@link ExchangeKindTypeInfo} over hard coding these constants.
 */
export enum GTNetExchangeKindType {
  LAST_PRICE = 0,
  HISTORICAL_PRICES = 1,
  SECURITY_METADATA = 2
}
