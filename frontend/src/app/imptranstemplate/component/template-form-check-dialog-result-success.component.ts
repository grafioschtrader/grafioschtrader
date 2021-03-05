import {ChangeDetectorRef, Component, Input, OnChanges, OnInit} from '@angular/core';
import {FormTemplateCheck} from './form.template.check';
import {DataType} from '../../dynamic-form/models/data.type';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {SingleRecordConfigBase} from '../../shared/datashowbase/single.record.config.base';

@Component({
  selector: 'template-form-check-dialog-result-success',
  template: `
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
              globalparameterService: GlobalparameterService) {
    super(translateService, globalparameterService);
  }


  ngOnInit(): void {

    this.addFieldProperty(DataType.String, this.TEMPLATE_PURPOSE, 'TEMPLATE_PURPOSE');
    this.addFieldProperty(DataType.DateString, this.VALID_SINCE, 'VALID_SINCE');

    this.addFieldProperty(DataType.String, 'transactionType', 'TRANSACTION_TYPE', {translateValues: true});
    this.addFieldProperty(DataType.DateString, 'transactionTime', 'DATE');
    this.addFieldProperty(DataType.DateString, 'exDate', 'exDate');
    this.addFieldProperty(DataType.String, 'currencyAccount', 'ACCOUNT_CURRENCY');
    this.addFieldProperty(DataType.String, 'isin', 'ISIN');
    this.addFieldProperty(DataType.String, 'currencySecurity', 'SECURITY_CURRENCY');
    this.addFieldProperty(DataType.NumericRaw, 'currencyExRate', 'EXCHANGE_RATE', {maxFractionDigits: 5});
    this.addFieldProperty(DataType.NumericRaw, 'units', 'QUANTITY');
    this.addFieldProperty(DataType.NumericRaw, 'quotation', 'QUOTATION_DIV', {maxFractionDigits: 5});
    this.addFieldProperty(DataType.Numeric, 'accruedInterest', 'ACCRUED_INTEREST');
    this.addFieldProperty(DataType.Numeric, 'taxCost', 'TAX_COST');
    this.addFieldProperty(DataType.Numeric, 'transactionCost', 'TRANSACTION_COST');
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
