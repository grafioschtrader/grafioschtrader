/**
 * Enum for entity kinds - types of data that can be exchanged.
 * This is the grafioschtrader-specific implementation; generic code in lib/gnet
 * should use ExchangeKindTypeInfo from the backend instead.
 */
export enum GTNetExchangeKindType {
  LAST_PRICE = 0,
  HISTORICAL_PRICES = 1,
  SECURITY_METADATA = 2
}
