import {Component, Input, OnChanges, OnInit} from '@angular/core';
import {FormTemplateCheck} from './form.template.check';
import {DataType} from '../../dynamic-form/models/data.type';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

/**
 * Shows the summary of a success full check template against a platform document as text
 */
@Component({
  selector: 'template-form-check-dialog-result-success',
  template: `
    <h4>{{'IMPORT_POS_CHECK_SUCCESS' | translate}}</h4>
    <div *ngFor="let field of fields" class="row">
      <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 showlabel" align="right">
        {{field.headerTranslated}}:
      </div>
      <div class="col-lg-6 col-md-6 col-sm-6 col-xs-6 nopadding wrap">
        {{getValueByPath(formTemplateCheck.importTransactionPos, field)}}
      </div>
    </div>
  `
})
export class TemplateFormCheckDialogResultSuccessComponent extends SingleRecordConfigBase implements OnInit, OnChanges {
  @Input() formTemplateCheck: FormTemplateCheck;

  readonly TEMPLATE_PURPOSE = 'templatePurpose';
  readonly VALID_SINCE = 'validSince';

  constructor(translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps);
  }

  ngOnInit(): void {
    this.addFieldPropertyFeqH(DataType.String, this.TEMPLATE_PURPOSE);
    this.addFieldPropertyFeqH(DataType.DateString, this.VALID_SINCE);
    this.addFieldPropertyFeqH(DataType.String, 'transactionType',
      {translateValues: TranslateValue.NORMAL});
    this.addFieldProperty(DataType.DateString, 'transactionTime', 'DATE');
    this.addFieldPropertyFeqH(DataType.DateString, 'exDate');
    this.addFieldProperty(DataType.String, 'currencyAccount', 'ACCOUNT_CURRENCY');
    this.addFieldPropertyFeqH(DataType.String, 'isin');
    this.addFieldProperty(DataType.String, 'currencySecurity', 'SECURITY_CURRENCY');
    this.addFieldProperty(DataType.NumericRaw, 'currencyExRate', 'EXCHANGE_RATE', {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldProperty(DataType.NumericRaw, 'units', 'QUANTITY');
    this.addFieldProperty(DataType.NumericRaw, 'quotation', 'QUOTATION_DIV',
      {maxFractionDigits: AppSettings.FID_MAX_FRACTION_DIGITS});
    this.addFieldPropertyFeqH(DataType.Numeric, 'accruedInterest');
    this.addFieldPropertyFeqH(DataType.Numeric, 'taxCost');
    this.addFieldPropertyFeqH(DataType.Numeric, 'transactionCost');
    this.addFieldProperty(DataType.String, 'field1StringImp', 'IMPORT_FREE_STR');
    this.addFieldProperty(DataType.Numeric, 'cashaccountAmount', 'TOTAL_AMOUNT');
    this.addFieldProperty(DataType.Numeric, 'calcCashaccountAmount', 'CALC_TOTAL_VALUE');
    this.translateHeadersAndColumns();
  }

  ngOnChanges(): void {
    this.formTemplateCheck.importTransactionPos[this.TEMPLATE_PURPOSE] = this.formTemplateCheck.successParsedTemplateState.templatePurpose;
    this.formTemplateCheck.importTransactionPos[this.VALID_SINCE] = this.formTemplateCheck.successParsedTemplateState.validSince;
    this.createTranslatedValueStore([this.formTemplateCheck.importTransactionPos]);
  }

}
