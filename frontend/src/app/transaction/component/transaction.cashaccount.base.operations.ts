import {TransactionBaseOperations} from './transaction.base.operations';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {Directive, EventEmitter, Input, Output, ViewChild} from '@angular/core';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {TransactionCallParam} from './transaction.call.parm';
import {TranslateService} from '@ngx-translate/core';
import {Portfolio} from '../../entities/portfolio';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {FieldFormGroup} from '../../lib/dynamic-form/models/form.group.definition';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {Subscription} from 'rxjs';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {AppSettings} from '../../shared/app.settings';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HistoryquoteService} from '../../historyquote/service/historyquote.service';
import {CurrencypairService} from '../../securitycurrency/service/currencypair.service';

/**
 * Abstract base class for cash account transaction operations that are shared between different input forms.
 * This directive provides common functionality for handling cash account transactions including form initialization,
 * portfolio management, and field configuration. It serves as a foundation for single and double cash account
 * transaction components, handling the core operations like form setup, validation, and data preparation.
 */
@Directive()
export abstract class TransactionCashaccountBaseOperations extends TransactionBaseOperations {
  /** Input parameters containing transaction call configuration and context data */
  @Input() transactionCallParam: TransactionCallParam;

  /** Event emitter for closing dialog and returning processed action data to parent component */
  @Output() closeDialog = new EventEmitter<ProcessedActionData>();

  /** Reference to the dynamic form component for accessing form controls and validation */
  @ViewChild(DynamicFormComponent) form: DynamicFormComponent;

  /** Array of field form groups defining the structure and configuration of the transaction form */
  config: FieldFormGroup[] = [];

  /** List of portfolios available for the current tenant used for populating cash account options */
  portfolios: Portfolio[];

  /** Subscription for monitoring value changes in calculation fields to trigger updates */
  protected valueChangedOnValueCalcFieldsSub: Subscription;

  /**
   * Creates an instance of TransactionCashaccountBaseOperations.
   * @param messageToastService Service for displaying toast messages to users
   * @param currencypairService Service for handling currency pair operations and exchange rates
   * @param historyquoteService Service for retrieving historical quote data
   * @param translateService Angular service for internationalization and text translation
   * @param gps Global parameter service for accessing application-wide settings and configurations
   */
  protected constructor(messageToastService: MessageToastService,
              currencypairService: CurrencypairService,
              historyquoteService: HistoryquoteService,
              translateService: TranslateService,
              gps: GlobalparameterService,) {
    super(messageToastService, currencypairService, historyquoteService, translateService, gps);
  }

  /**
   * Performs pre-initialization setup by setting default form values and triggering initialization.
   * Sets the transaction time to current date and enables form submission controls.
   */
  preInitialize(): void {
    this.resetTransactionLocked();
    this.form.setDefaultValuesAndEnableSubmit();
    this.configObject.transactionTime.formControl.setValue(new Date());
    this.initialize();
  }

  /**
   * Creates HTML select options for cash accounts from available portfolios.
   * Formats each cash account option with account name, currency, and portfolio name for user-friendly display.
   * @param portfolios Array of portfolio objects containing cash account lists
   * @returns Array of value-key HTML select options for cash account dropdown
   */
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

  /**
   * Event handler for dialog show event that triggers pre-initialization with a timeout.
   * @param event The dialog show event object
   */
  public onShow(event) {
    setTimeout(() => this.preInitialize());
  }

  /**
   * Closes the dialog by unsubscribing from active subscriptions and emitting close event.
   * Cleans up value change subscriptions and notifies parent component of dialog closure.
   */
  close(): void {
    this.valueChangedOnValueCalcFieldsSub && this.valueChangedOnValueCalcFieldsSub.unsubscribe();
    this.closeDialog.emit(new ProcessedActionData(ProcessedAction.NO_CHANGE));
  }

  /**
   * Abstract method that must be implemented by subclasses to perform specific initialization logic.
   * This method should handle the setup of form fields, data loading, and component-specific configuration.
   */
  protected abstract initialize(): void;

  /**
   * Creates and configures the transaction cost field definition for the form.
   * Sets up a currency number field with appropriate limits and number formatting mask.
   * @returns FieldConfig object configured for transaction cost input
   */
  protected getTransactionCostFieldDefinition(gps: GlobalparameterService): FieldConfig {
      return DynamicFieldHelper.createFieldInputNumberHeqF('transactionCost', false,
      AppSettings.FID_SMALL_INTEGER_LIMIT, gps.getStandardFractionDigits(), false);

  }

  /**
   * Creates and configures the debit amount field definition for the form.
   * Sets up a read-only currency number field for displaying calculated debit amounts.
   * @returns FieldConfig object configured for debit amount display
   */
  protected getDebitAmountFieldDefinition(gps: GlobalparameterService): FieldConfig {
    return DynamicFieldHelper.createFieldInputNumberHeqF('debitAmount', false,
      AppSettings.FID_MAX_INT_REAL_DOUBLE, gps.getStandardFractionDigits(),
      false,  {readonly: true});
  }

}
