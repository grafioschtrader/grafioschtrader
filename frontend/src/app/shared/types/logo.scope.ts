/**
 * Scope of an instrument logo catalog entry: company logo or issuer wordmark.
 *
 * Corresponds to backend: grafioschtrader-common/src/main/java/grafioschtrader/types/LogoScope.java
 */
export enum LogoScope {
  COMPANY = 0,
  ISSUER = 1
}

export type LogoScopeName = keyof typeof LogoScope;
