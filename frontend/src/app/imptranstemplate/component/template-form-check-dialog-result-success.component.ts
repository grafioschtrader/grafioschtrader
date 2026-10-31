import { Component, computed, effect, input, untracked } from '@angular/core';

import type { FormTemplateCheckResult } from './form.template.check';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { TranslateModule } from '@ngx-translate/core';
import { SingleRecordConfigBase } from '../../lib/datashowbase/single.record.config.base';
import { TranslateValue } from '../../lib/datashowbase/column.config';

/**
 * Shows the summary of a success full check template against a platform document as text
 */
@Component({
  selector: 'template-form-check-dialog-result-success',
  template: `
    <h4>{{ 'IMPORT_POS_CHECK_SUCCESS' | translate }}</h4>
    @for (field of fields; track field) {
      <div class="row">
        <div class="col-md-6 showlabel text-end">{{ field.headerTranslated }}:</div>
        <div class="col-md-6 nopadding wrap">
          {{ getValueByPath(transactionPos(), field) }}
        </div>
      </div>
    }
  `,
  imports: [TranslateModule]
})
export class TemplateFormCheckDialogResultSuccessComponent extends SingleRecordConfigBase {
  readonly formTemplateCheck = input<FormTemplateCheckResult>();
  readonly transactionPos = computed(() => {
    const result = this.formTemplateCheck();
    return result
      ? {
          ...result.importTransactionPos,
          templatePurpose: result.successParsedTemplateState?.templatePurpose,
          validSince: result.successParsedTemplateState?.validSince
        }
      : null;
  });

  readonly TEMPLATE_PURPOSE = 'templatePurpose';
  readonly VALID_SINCE = 'validSince';

  constructor() {
    super();
    this.addFieldPropertyFeqH(DataType.String, this.TEMPLATE_PURPOSE);
    this.addFieldPropertyFeqH(DataType.DateString, this.VALID_SINCE);
    this.addFieldPropertyFeqH(DataType.String, 'transactionType', {
      translateValues: TranslateValue.NORMAL
    });
    this.addFieldProperty(DataType.DateString, 'transactionTime', 'DATE');
    this.addFieldPropertyFeqH(DataType.DateString, 'exDate');
    this.addFieldProperty(DataType.String, 'currencyAccount', 'ACCOUNT_CURRENCY');
    this.addFieldPropertyFeqH(DataType.String, 'isin');
    this.addFieldProperty(DataType.String, 'currencySecurity', 'SECURITY_CURRENCY');
    this.addFieldProperty(DataType.NumericRaw, 'currencyExRate', 'EXCHANGE_RATE', {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldProperty(DataType.NumericRaw, 'units', 'QUANTITY');
    this.addFieldProperty(DataType.NumericRaw, 'quotation', 'QUOTATION_DIV', {
      maxFractionDigits: this.gps.getMaxFractionDigits()
    });
    this.addFieldPropertyFeqH(DataType.Numeric, 'accruedInterest');
    this.addFieldPropertyFeqH(DataType.Numeric, 'taxCost');
    this.addFieldPropertyFeqH(DataType.Numeric, 'transactionCost');
    this.addFieldProperty(DataType.String, 'field1StringImp', 'IMPORT_FREE_STR');
    this.addFieldProperty(DataType.Numeric, 'cashaccountAmount', 'TOTAL_AMOUNT');
    this.addFieldProperty(DataType.Numeric, 'calcCashaccountAmount', 'CALC_TOTAL_VALUE');
    this.translateHeadersAndColumns();
    effect(() => {
      const row = this.transactionPos();
      untracked(() => this.createTranslatedValueStore(row ? [row] : []));
    });
  }
}
