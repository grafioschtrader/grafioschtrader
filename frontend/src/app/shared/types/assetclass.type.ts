export enum AssetclassType {
  EQUITIES = 0,
  FIXED_INCOME = 1,
  MONEY_MARKET = 2,
  COMMODITIES = 3,
  REAL_ESTATE = 4,
  MULTI_ASSET = 5,
  CONVERTIBLE_BOND = 6,
  CREDIT_DERIVATIVE = 7,
  CURRENCY_PAIR = 8,

  // Exist only for Client and is not saved to repository
  CURRENCY_CASH = 11,
  CURRENCY_FOREIGN = 12
}
