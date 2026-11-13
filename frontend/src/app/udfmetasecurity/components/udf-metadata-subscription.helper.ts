import { TranslateService } from '@ngx-translate/core';
import { UDFFillSource, UDFMetadataSecurity } from '../model/udf.metadata.security';

/** Read-only configuration shown before adopting or reordering an automatic field. */
export function securitySubscriptionRows(
  field: UDFMetadataSecurity,
  translate: TranslateService
): { label: string; value: string | null }[] {
  return field.fillSource === UDFFillSource.MANUAL
    ? []
    : [
        { label: 'FILL_SOURCE', value: translate.instant(field.fillSource) },
        { label: 'FILL_RULE', value: field.fillRule },
        { label: 'FILL_PERIOD', value: field.fillPeriod ? translate.instant(field.fillPeriod) : '' }
      ];
}

/**
 * The notice shown before adopting or reordering a field a language model fills: whether the requests for the own
 * securities are made with the language model of the own tenant and at the own cost.
 *
 * @param field - The field to adopt or the adopted field
 * @returns The translation key of the notice, or null for every other fill source
 */
export function securitySubscriptionNotice(field: UDFMetadataSecurity): string | null {
  if (field.fillSource !== UDFFillSource.LLM) {
    return null;
  }
  return field.ownModelUsed ? 'UDF_SUBSCRIBE_LLM_OWN_MODEL' : 'UDF_SUBSCRIBE_LLM_NO_MODEL';
}
