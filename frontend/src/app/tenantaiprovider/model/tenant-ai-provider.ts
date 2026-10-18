import { TenantBaseId } from '../../lib/entities/tenant.base.id';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

/**
 * Only apiKeyHint comes back from the server; apiKey is submitted once and never retained in the table. Every form
 * field is initialised, because the dynamic form copies a value only into a property that already exists.
 */
export class TenantAIProvider extends TenantBaseId {
  idTenantAIProvider: number = null;
  provider: string = null;
  model: string = null;
  priority = 1;
  active = true;
  /** Requests per UTC day the tenant allows over this key; null for no limit of the tenant */
  dailyRequestLimit: number = null;
  apiKey?: string = null;
  apiKeyHint?: string;
  /** Requests over this key in the current UTC day, set by the server when the keys are listed */
  requestsToday?: number;
  getId(): number {
    return this.idTenantAIProvider;
  }
}

export interface TenantAIProviderOptions {
  providers: ValueKeyHtmlSelectOptions[];
  keyProviders: string[];
  defaultModels: Record<string, string>;
  providerNames: Record<string, string>;
}
export interface AIKeyTestResult {
  succeeded: boolean;
  reason: string;
}
