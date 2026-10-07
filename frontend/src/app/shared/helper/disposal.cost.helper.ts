import { TranslateService } from '@ngx-translate/core';
import { DisposalCostDetail } from '../../entities/view/disposal.cost.detail';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { AppHelper } from '../../lib/helper/app.helper';

/**
 * Display support for the disposal cost estimate of the hypothetical sale (gt.disposal.cost.estimate). The estimate
 * itself is computed by the backend; the frontend only highlights incomplete values and explains them in a tooltip.
 */
export class DisposalCostHelper {
  /**
   * Background of a cell whose estimated value is incomplete because a fee model, a tax model or a matching rule is
   * missing. Semi-transparent so that it stays readable in the light and the dark theme.
   */
  public static readonly INCOMPLETE_STYLE: { [key: string]: string } = {
    'background-color': 'rgba(234, 179, 8, 0.30)'
  };

  /** Fields of the position tables that carry a disposal cost estimate. */
  public static readonly DISPOSAL_FIELDS: string[] = ['disposalCostMC', 'valueAfterDisposalMC'];

  /**
   * Builds the tooltip text explaining an estimate: per account and cost component either the matched rule or the
   * translated reason why the component is unknown.
   *
   * @param details - The explanation lines delivered by the backend
   * @param translateService - Translation service; the texts are already loaded at application start
   * @param gps - Supplies the locale for formatting the amounts
   * @returns The lines separated by line breaks, null when there is nothing to explain
   */
  public static getDetailsText(
    details: DisposalCostDetail[],
    translateService: TranslateService,
    gps: GlobalparameterService
  ): string {
    if (!details?.length) {
      return null;
    }
    return details
      .map((d) => {
        const part = translateService.instant('DISPOSAL_PART_' + d.part);
        const what = d.reason ? '⚠ ' + translateService.instant(d.reason) : d.rule;
        const detail = d.detail ? ` (${d.detail})` : '';
        const amount = d.amount != null && !d.reason ? ': ' + AppHelper.numberFormat(gps, d.amount, 2, 2) : '';
        return `${d.securityaccountName ? d.securityaccountName + ' – ' : ''}${part}: ${what}${detail}${amount}`;
      })
      .join('\n');
  }
}
