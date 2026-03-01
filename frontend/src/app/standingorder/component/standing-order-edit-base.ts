import {Directive, Input} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {StandingOrder} from '../../entities/standing.order';
import {StandingOrderService} from '../service/standing.order.service';
import {StandingOrderCallParam} from '../model/standing.order.call.param';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {Portfolio} from '../../entities/portfolio';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {RepeatUnit} from '../../shared/types/repeat.unit';
import {PeriodDayPosition} from '../../shared/types/period.day.position';
import {WeekendAdjustType} from '../../shared/types/weekend.adjust.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {FormHelper} from '../../lib/dynamic-form/components/FormHelper';

/**
 * Abstract base class for standing order edit dialogs. Provides shared scheduling field definitions,
 * enum select option initialization, and cashaccount loading from portfolios.
 * Subclasses add their specific fields and implement setExistingValues() and getNewOrExistingInstanceBeforeSave().
 */
@Directive()
export abstract class StandingOrderEditBase extends SimpleEntityEditBase<StandingOrder> {

  /** Fieldset name for the transaction-related fields group. */
  static readonly FS_TRANSACTION = 'TRANSACTION_DATA';
  /** Fieldset name for the scheduling/execution-related fields group. */
  static readonly FS_SCHEDULE = 'EXECUTION_SCHEDULE';

  @Input() callParam: StandingOrderCallParam;

  /** Loaded portfolios, available for subclasses after loadCashaccountsFromPortfolios completes. */
  protected portfolios: Portfolio[] = [];

  protected constructor(
    protected portfolioService: PortfolioService,
    helpId: string,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService,
    standingOrderService: StandingOrderService
  ) {
    super(helpId, 'STANDING_ORDER', translateService, gps,
      messageToastService, standingOrderService);
  }

  /**
   * Creates the shared scheduling field configs used by both cashaccount and security edit dialogs.
   * All fields belong to the EXECUTION_SCHEDULE fieldset group.
   * Includes: repeatUnit, repeatInterval, periodDayPosition, dayOfExecution, monthOfExecution,
   * weekendAdjust, validFrom, validTo, note, and submit button.
   */
  protected static createSchedulingFields(): FieldConfig[] {
    const fs = StandingOrderEditBase.FS_SCHEDULE;
    return [
      DynamicFieldHelper.createFieldSelectStringHeqF('repeatUnit', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('repeatInterval', true, 3, 0, false, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectStringHeqF('periodDayPosition', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldInputNumberHeqF('dayOfExecution', false, 2, 0, false, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectNumberHeqF('monthOfExecution', false, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldSelectStringHeqF('weekendAdjust', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'validFrom', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'validTo', true, {fieldsetName: fs}),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', 500, false, {fieldsetName: fs}),
      DynamicFieldHelper.createSubmitButton()
    ];
  }

  /**
   * Populates the shared scheduling select options (repeatUnit, periodDayPosition, weekendAdjust)
   * and loads cashaccounts from portfolios. Subclasses must populate their own transactionType select
   * before calling this method.
   */
  protected override initialize(): void {
    this.initializeTransactionTypeOptions();
    this.configObject.repeatUnit.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, RepeatUnit);
    this.configObject.periodDayPosition.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, PeriodDayPosition);
    this.configObject.weekendAdjust.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, WeekendAdjustType);
    this.configObject.monthOfExecution.valueKeyHtmlOptions =
      SelectOptionsHelper.createMonthOptions(this.gps.getUserLang());
    this.setupRepeatUnitListener();
    this.setupPeriodDayPositionListener();
    this.loadCashaccountsFromPortfolios();
  }

  /**
   * Subclasses must populate the transactionType select with the appropriate enum subset.
   */
  protected abstract initializeTransactionTypeOptions(): void;

  /**
   * Subclasses must set form values from existing standing order or transaction.
   * Called after cashaccount options have been loaded.
   */
  protected abstract setExistingValues(): void;

  /**
   * Applies two-tier field locking that mirrors the backend annotation levels:
   * 1. transactionType has no update annotation — always disabled on edit (never updatable after creation).
   * 2. @LockedWhenUsed fields — disabled once the standing order has created transactions.
   * Must be called AFTER all other state adjustments (e.g., applyCostFormulaState) so nothing re-enables locked fields.
   */
  protected disableFieldsForEdit(so: StandingOrder): void {
    // transactionType has no update annotation in the backend — never updatable after creation
    FormHelper.disableEnableFieldConfigs(true, [this.configObject.transactionType]);
    // @LockedWhenUsed fields — locked once the standing order has created transactions
    if (so?.hasTransactions) {
      FormHelper.disableEnableFieldConfigs(true,
        this.config.filter(f => f.fieldsetName === StandingOrderEditBase.FS_TRANSACTION));
      // validFrom is in FS_SCHEDULE but annotated @LockedWhenUsed in the backend
      FormHelper.disableEnableFieldConfigs(true, [this.configObject.validFrom]);
    }
  }

  /**
   * Subscribes to repeatUnit changes. When DAYS is selected, periodDayPosition, dayOfExecution
   * and monthOfExecution are irrelevant and get disabled/cleared. For MONTHS, monthOfExecution
   * is disabled. For YEARS all three are enabled (subject to periodDayPosition logic).
   */
  private setupRepeatUnitListener(): void {
    this.configObject.repeatUnit.formControl.valueChanges.subscribe((unit: string) => {
      this.applyRepeatUnit(unit);
    });
  }

  /**
   * Subscribes to periodDayPosition changes. Enables dayOfExecution only when SPECIFIC_DAY (0) is selected;
   * disables and clears it for FIRST_DAY or LAST_DAY since the day is implicit.
   */
  private setupPeriodDayPositionListener(): void {
    this.configObject.periodDayPosition.formControl.valueChanges.subscribe((pos: string) => {
      this.applyPeriodDayPosition(pos);
    });
  }

  /**
   * Enables/disables periodDayPosition, dayOfExecution and monthOfExecution based on the selected repeat unit.
   * DAYS: all three disabled and cleared. MONTHS: monthOfExecution disabled, others follow periodDayPosition.
   * YEARS: monthOfExecution enabled, others follow periodDayPosition.
   */
  protected applyRepeatUnit(unit: string): void {
    if (unit === RepeatUnit[RepeatUnit.DAYS]) {
      this.configObject.periodDayPosition.formControl.disable();
      this.configObject.periodDayPosition.formControl.setValue(null);
      this.configObject.dayOfExecution.formControl.disable();
      this.configObject.dayOfExecution.formControl.setValue(null);
      this.configObject.monthOfExecution.formControl.disable();
      this.configObject.monthOfExecution.formControl.setValue(null);
    } else {
      this.configObject.periodDayPosition.formControl.enable();
      this.applyPeriodDayPosition(this.configObject.periodDayPosition.formControl.value
        ?? PeriodDayPosition[PeriodDayPosition.SPECIFIC_DAY]);
      if (unit === RepeatUnit[RepeatUnit.YEARS]) {
        this.configObject.monthOfExecution.formControl.enable();
      } else {
        this.configObject.monthOfExecution.formControl.disable();
        this.configObject.monthOfExecution.formControl.setValue(null);
      }
    }
  }

  /**
   * Enables dayOfExecution when SPECIFIC_DAY is selected, disables and clears it otherwise.
   */
  protected applyPeriodDayPosition(pos: string): void {
    if (pos === PeriodDayPosition[PeriodDayPosition.SPECIFIC_DAY]) {
      this.configObject.dayOfExecution.formControl.enable();
    } else {
      this.configObject.dayOfExecution.formControl.disable();
      this.configObject.dayOfExecution.formControl.setValue(null);
    }
  }

  /**
   * Converts an enum select form control value (string name like "ACCUMULATE") to its numeric value.
   * SelectOptionsHelper.createHtmlOptionsFromEnum uses enum string names as keys, but the backend expects
   * numeric byte values. Handles both string and numeric inputs for safety.
   */
  static enumFormValueToNumeric(enumType: any, formValue: any): number | null {
    if (formValue == null || formValue === '') {
      return null;
    }
    if (typeof formValue === 'number') {
      return formValue;
    }
    const numeric = enumType[formValue];
    return numeric != null ? numeric : null;
  }

  /**
   * Loads cashaccount options by fetching portfolios and flattening their cashaccount lists.
   */
  private loadCashaccountsFromPortfolios(): void {
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe((portfolios: Portfolio[]) => {
      this.portfolios = portfolios;
      const cashaccountsHtmlOptions: ValueKeyHtmlSelectOptions[] = [];
      for (const portfolio of portfolios) {
        if (portfolio.cashaccountList) {
          portfolio.cashaccountList.forEach(ca => cashaccountsHtmlOptions.push(
            new ValueKeyHtmlSelectOptions(ca.idSecuritycashAccount,
              `${ca.name} / ${ca.currency} / ${portfolio.name}`)));
        }
      }
      this.configObject.idCashaccount.valueKeyHtmlOptions = cashaccountsHtmlOptions;
      this.afterPortfoliosLoaded();
      this.setExistingValues();
      const unit = this.configObject.repeatUnit.formControl.value;
      this.applyRepeatUnit(unit ?? RepeatUnit[RepeatUnit.MONTHS]);
    });
  }

  /**
   * Hook called after portfolios are loaded. Subclasses can override to populate additional
   * options derived from portfolios (e.g., securityaccount select).
   */
  protected afterPortfoliosLoaded(): void {
  }
}
