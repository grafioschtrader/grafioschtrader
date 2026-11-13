import { HttpParams } from '@angular/common/http';
import { AppHelper } from '../../lib/helper/app.helper';
import { SecuritycurrencySearch } from '../../entities/search/securitycurrency.search';

/** Name of the property of the search that holds the criteria of the advanced search. */
const ADVANCED_CRITERIA = 'advancedCriteria';

/**
 * Turns a search into the query parameters of the search endpoints. The flat properties are written by the generic
 * builder, which would turn a list of objects into `advancedCriteria=[object Object]`; the criteria are therefore taken
 * out first and written as indexed parameters that Spring binds into a list: `advancedCriteria[0].field=U12`.
 * Every set property is written, `0` and `false` included. Without criteria no parameter is added, so a search without
 * advanced criteria sends what it always sent.
 *
 * @param search - The search criteria of the dialog
 * @param allowBooleanNullFields - Names of flat properties that are sent even when null
 * @returns The query parameters
 */
export function securitycurrencySearchParams(
  search: SecuritycurrencySearch,
  allowBooleanNullFields: string[] = []
): HttpParams {
  const { advancedCriteria, ...flat } = search;
  let params = AppHelper.getHttpParamsOfObjectAllowBooleanNullFields(flat, allowBooleanNullFields);
  advancedCriteria.forEach((criterion, index) => {
    for (const [property, value] of Object.entries(criterion)) {
      if (value !== null && value !== undefined && value !== '') {
        params = params.append(`${ADVANCED_CRITERIA}[${index}].${property}`, String(value));
      }
    }
  });
  return params;
}
