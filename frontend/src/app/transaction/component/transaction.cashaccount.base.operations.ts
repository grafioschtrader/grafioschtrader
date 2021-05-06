import {TransactionBaseOperations} from './transaction.base.operations';
import {FieldConfig} from '../../dynamic-form/models/field.config';
import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../../dynamic-form/containers/dynamic-form/dynamic-form.component';
import {TransactionCallParam} from './transaction.call.parm';
import {TranslateService} from '@ngx-translate/core';
import {Portfolio} from '../../entities/portfolio';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {FieldFormGroup} from '../../dynamic-form/models/form.group.definition';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {ProcessedAction} from '../../shared/types/processed.action';
import {AppSettings} from '../../shared/app.settings';

/**
 * Cash account operations that are shared between different input forms.
 */
@Directive()
export abstract class TransactionCashaccountBaseOperations extends TransactionBaseOperations {

  // InputMask from parent view
  @Input() transactionCallParam: TransactionCallParam;

  // Output for parent view
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();


  // Access child components
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  configObject: { [name: string]: FieldConfig };
  config: FieldFormGroup[] = [];
  portfolios: Portfolio[];

  protected valueChangedOnValueCalcFieldsSub: Subscription;

  constructor(translateService: TranslateService, gps: GlobalparameterService) {
    super(translateService, gps);
  }

  preInitialize(): void {
    this.form.setDefaultValuesAndEnableSubmit();
    this.configObject.transactionTime.formControl.setValue(new Date());
    this.initialize();
  }

  prepareCashaccountOptions(portfolios: Portfolio[]): ValueKeyHtmlSelectOptions[] {
    const cashaccountsHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
    for (const portfolio of portfolios) {
      portfolio.cashaccountList.forEach(cashaccount => cashaccountsHtmlOptions
        .push(new ValueKeyHtmlSelectOptions(cashaccount.idSecuritycashAccount,
          `${cashaccount.name} / ${cashaccount.currency} / ${portfolio.name}`))
      );
    }
    return cashaccountsHtmlOptions;
  }

  public onShow(event) {
    setTimeout(() => this.preInitialize());
  }

  close(): void {
    this.valueChangedOnValueCalcFieldsSub && this.valueChangedOnValueCalcFieldsSub.unsubscribe();
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  protected abstract initialize(): void;

  protected getTransactionCostFieldDefinition(): FieldConfig {
    return DynamicFieldHelper.createFieldCurrencyNumberHeqF('transactionCost', false,
      AppSettings.FID_SMALL_INTEGER_LIMIT, AppSettings.FID_STANDARD_FRACTION_DIGITS, false,
      this.gps.getNumberCurrencyMask());
  }

  protected getDebitAmountFieldDefinition(): FieldConfig {
    return DynamicFieldHelper.createFieldCurrencyNumberHeqF('debitAmount', false, AppSettings.FID_MAX_INTEGER_DIGITS,
      AppSettings.FID_STANDARD_FRACTION_DIGITS, false, this.gps.getNumberCurrencyMask(),
      {readonly: true});
  }


}
