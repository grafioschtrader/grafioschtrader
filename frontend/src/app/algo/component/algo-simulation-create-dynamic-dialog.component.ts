import { DestroyRef, inject, afterNextRender, Injector, signal, Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FormsModule } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DynamicDialogConfig, DynamicDialogRef } from '@openng/optimus-ui/dynamicdialog';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { TenantService } from '../../tenant/service/tenant.service';
import { AlgoTop } from '../model/algo.top';
import { Tenant } from '../../entities/tenant';
import { Cashaccount } from '../../entities/cashaccount';
import moment from 'moment';
import {
  SimulationTenantCreateDTO,
  SimulationInitializationMode,
  SimulationPreviewDto
} from '../model/simulation.tenant';
import { AppHelper } from '../../lib/helper/app.helper';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';

/**
 * Dialog for creating a simulation tenant from an AlgoTop strategy.
 * Collects the immutable opening date and mode, with manual balances or historical liquidation preview.
 * Cash accounts are pre-loaded and passed via callParam.parentObject.cashAccounts.
 */
@Component({
  template: `
    @if (creationAvailableFrom()) {
      <p role="alert" class="alert alert-warning alert-dialog-wrap">
        {{ 'SIMULATION_PORTFOLIO_WAIT' | translate: { date: creationAvailableFrom() } }}
      </p>
    }
    @if (dataNotes().length) {
      <div class="alert alert-warning alert-dialog-wrap">
        @for (note of dataNotes(); track note) {
          <div>{{ note }}</div>
        }
      </div>
    }
    @if (manualCash()) {
      <p class="alert-dialog-wrap">{{ 'SIMULATION_MANUAL_CASH_HINT' | translate }}</p>
    }
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
    @if (preview()) {
      <p>{{ 'SIMULATION_PREVIEW_HELP' | translate }}</p>
      @for (error of preview().errors; track error) {
        <p role="alert">{{ error }}</p>
      }
      <table class="table">
        <thead>
          <tr>
            <th>{{ 'CASHACCOUNT' | translate }}</th>
            <th>{{ 'CURRENCY' | translate }}</th>
            <th>{{ 'BALANCE' | translate }}</th>
          </tr>
        </thead>
        <tbody>
          @for (account of preview().accounts; track account.idCashaccount) {
            <tr>
              <td>{{ account.name }}</td>
              <td>{{ account.currency }}</td>
              <td>{{ getBalance(account) }}</td>
            </tr>
          }
        </tbody>
      </table>
      @for (position of preview().unresolvedPositions; track position.positionKey) {
        <label [attr.for]="'destination_' + position.positionKey">
          {{ position.securityName }} / {{ position.securityaccountName }} ({{ position.currency }})
        </label>
        <select [id]="'destination_' + position.positionKey" [(ngModel)]="assignments[position.positionKey]">
          <option [ngValue]="undefined">{{ 'SIMULATION_CHOOSE_DESTINATION' | translate }}</option>
          @for (account of cashAccounts; track account.idSecuritycashAccount) {
            <option [ngValue]="account.idSecuritycashAccount">{{ account.name }} ({{ account.currency }})</option>
          }
        </select>
      }
    }
  `,
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule, FormsModule]
})
export class AlgoSimulationCreateDynamicDialogComponent extends SimpleDynamicEditBase<Tenant> implements OnInit {
  private readonly pendingReads = new Map<string, Subscription>();
  private readonly cleanupReads = inject(DestroyRef).onDestroy(() =>
    this.pendingReads.forEach((request) => request.unsubscribe())
  );
  private replaceAlgoRequest(key: string): Subscription {
    this.pendingReads.get(key)?.unsubscribe();
    const request = new Subscription();
    this.pendingReads.set(key, request);
    return request;
  }

  /** Read by MainTreeDynamicDialogs; the translated initialization-mode labels need more than the default 400px. */
  static readonly DIALOG_WIDTH = 600;
  private readonly injector = inject(Injector);

  callParam: CallParam;
  private algoTop: AlgoTop;
  cashAccounts: Cashaccount[] = [];
  readonly preview = signal<SimulationPreviewDto>(undefined);
  assignments: { [key: string]: number } = {};
  /** What the price data of the instruments allows, shown above the form. It never prevents a save. */
  readonly dataNotes = signal<string[]>([]);
  /** Localized first creation day when the fixed opening date is not yet a completed day. */
  readonly creationAvailableFrom = signal<string>(undefined);
  /** True while manual cash is chosen; only then are the balance fields and their explanation shown. */
  readonly manualCash = signal(false);
  private previewSignature: string;
  /** Currency-aware formatting of the previewed opening balances, as required for every displayed value. */
  private readonly balanceField = ShowRecordConfigBase.createColumnConfig(
    DataType.Numeric,
    'balance',
    'BALANCE',
    true,
    true,
    { currencyPrecisionField: 'currency' }
  );

  constructor(
    private tenantService: TenantService,
    dynamicDialogConfig: DynamicDialogConfig,
    dynamicDialogRef: DynamicDialogRef,
    translateService: TranslateService,
    gps: GlobalparameterService,
    messageToastService: MessageToastService
  ) {
    super(
      dynamicDialogConfig,
      dynamicDialogRef,
      HelpIds.HELP_ALGO_TREE,
      translateService,
      gps,
      messageToastService,
      tenantService
    );
  }

  ngOnInit(): void {
    this.callParam = this.dynamicDialogConfig.data.callParam;
    this.algoTop = this.callParam.thisObject as AlgoTop;
    this.cashAccounts = (this.callParam.parentObject as any)?.cashAccounts || [];

    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      (this.callParam.parentObject as any).formDefinition,
      '',
      false
    ) as FieldConfig[];

    for (const ca of this.cashAccounts) {
      this.config.push(
        DynamicFieldHelper.createFieldInputNumber(
          `cashBalance_${ca.idSecuritycashAccount}`,
          `*${ca.name} (${ca.currency})`,
          false,
          AppSettings.FID_STANDARD_INTEGER_DIGITS,
          BaseSettings.FID_STANDARD_FRACTION_DIGITS,
          false
        )
      );
    }

    this.config.push(DynamicFieldHelper.createSubmitButton());
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    this.configObject.simulationStartDate.calendarConfig = {
      ...this.configObject.simulationStartDate.calendarConfig,
      maxDate: yesterday
    };
    const fixedStartDate = this.algoTop.referenceDate
      ? moment(this.algoTop.referenceDate).add(1, 'day').startOf('day')
      : undefined;
    this.configObject.simulationStartDate.defaultValue = fixedStartDate?.toDate() ?? null;
    this.configObject.simulationStartDate.disabled = !!fixedStartDate;
    if (fixedStartDate && !fixedStartDate.isBefore(moment(), 'day')) {
      this.creationAvailableFrom.set(
        AppHelper.getDateByFormat(
          this.gps,
          fixedStartDate.clone().add(1, 'day').format(BaseSettings.FORMAT_DATE_SHORT_NATIVE)
        )
      );
      this.configObject.submit.disabled = true;
    }
    this.configObject.initializationMode.valueKeyHtmlOptions = Object.values(SimulationInitializationMode)
      .filter((mode) => !fixedStartDate || mode !== SimulationInitializationMode.MANUAL_CASH)
      .map((mode) => ({
        key: mode,
        disabled: false,
        value: this.translateService.instant(mode)
      }));
    afterNextRender(() => this.initializeForm(), { injector: this.injector });
  }

  private initializeForm(): void {
    this.form.setValue('tenantName', this.algoTop.name);

    this.form.setValue('initializationMode', SimulationInitializationMode.COPY_PORTFOLIO);
    this.form.form
      ?.get('initializationMode')
      ?.valueChanges.pipe(this.untilDialogClosed())
      .subscribe((mode) => {
        this.replaceAlgoRequest('preview');
        this.preview.set(undefined);
        this.assignments = {};
        this.previewSignature = undefined;
        this.toggleCashBalanceFields(mode === SimulationInitializationMode.MANUAL_CASH);
      });
    this.form.form
      ?.get('simulationStartDate')
      ?.valueChanges.pipe(this.untilDialogClosed())
      .subscribe((date) => {
        this.replaceAlgoRequest('preview');
        this.preview.set(undefined);
        this.assignments = {};
        this.previewSignature = undefined;
        this.loadDataNotes(date);
      });
    this.toggleCashBalanceFields(false);
    this.loadDataNotes(this.configObject.simulationStartDate.defaultValue);
  }

  /**
   * Reports what the price data allows, so the user learns it before choosing rather than from an empty replay. The
   * opening date is deliberately not bounded by any of this: opening with cash alone, before any instrument has
   * price data, is a legitimate starting point.
   *
   * @param date the opening date currently entered, if any
   */
  private loadDataNotes(date: any): void {
    const openingDate = date instanceof Date ? moment(date).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE) : undefined;
    this.replaceAlgoRequest('bounds').add(
      this.tenantService
        .getSimulationDateBounds(this.algoTop.idAlgoAssetclassSecurity, openingDate)
        .pipe(this.untilDialogClosed())
        .subscribe({
          next: (bounds) => {
            const notes: string[] = [];
            if (bounds.universeFromDate) {
              notes.push(
                this.translateService.instant('SIMULATION_UNIVERSE_FROM', {
                  date: AppHelper.getDateByFormat(this.gps, bounds.universeFromDate)
                })
              );
            }
            if (bounds.instrumentsWithoutHistory.length) {
              notes.push(
                this.translateService.instant('SIMULATION_UNIVERSE_NO_HISTORY', {
                  names: bounds.instrumentsWithoutHistory.join(', ')
                })
              );
            }
            if (bounds.unpricedAtOpeningDate.length) {
              notes.push(
                this.translateService.instant('SIMULATION_OPENING_NOT_PRICED', {
                  names: bounds.unpricedAtOpeningDate.join(', ')
                })
              );
            }
            this.dataNotes.set(notes);
          },
          error: () => this.dataNotes.set([])
        })
    );
  }

  override submit(value: { [name: string]: any }): void {
    if (this.creationAvailableFrom()) {
      return;
    }
    const dto: SimulationTenantCreateDTO = {
      idAlgoTop: this.algoTop.idAlgoAssetclassSecurity,
      tenantName: '',
      simulationStartDate: null,
      initializationMode: null
    };
    // Reads individual controls, including the disabled portfolio-derived opening date.
    this.form.cleanMaskAndTransferValuesToBusinessObject(dto);
    dto.idAlgoTop = this.algoTop.idAlgoAssetclassSecurity;
    dto.tenantName = value.tenantName;
    dto.initializationMode = value.initializationMode;
    if (dto.initializationMode === SimulationInitializationMode.MANUAL_CASH) {
      // A blank field leaves the account out of the environment, while 0 takes it over without money.
      dto.cashBalances = {};
      for (const ca of this.cashAccounts) {
        const balance = value[`cashBalance_${ca.idSecuritycashAccount}`];
        if (balance != null && balance !== '') {
          dto.cashBalances[ca.idSecuritycashAccount] = balance;
        }
      }
    }
    if (dto.initializationMode === SimulationInitializationMode.LIQUIDATE_TO_CASH) {
      dto.liquidationAssignments = this.assignments;
      const signature = JSON.stringify(dto);
      if (
        this.previewSignature !== signature ||
        this.preview()?.errors.length ||
        this.preview()?.unresolvedPositions.length
      ) {
        this.replaceAlgoRequest('preview').add(
          this.tenantService
            .previewSimulation(dto)
            .pipe(this.untilDialogClosed())
            .subscribe({
              next: (result) => {
                this.preview.set(result);
                this.previewSignature = signature;
                this.configObject.submit.disabled = false;
              },
              error: () => (this.configObject.submit.disabled = false)
            })
        );
        return;
      }
    }

    this.tenantService
      .createSimulationTenant(dto)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (returnEntity) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
            i18nRecord: this.dynamicDialogConfig.header
          });
          this.dynamicDialogRef.close(new ProcessedActionData(ProcessedAction.CREATED, returnEntity));
        },
        error: () => (this.configObject.submit.disabled = false)
      });
  }

  /**
   * Formats a previewed opening balance with the precision of its own account currency.
   *
   * @param account one entry of the preview response
   * @returns the balance formatted for the user locale
   */
  getBalance(account: { balance: number; currency: string }): string {
    return AppHelper.getValueByPathWithField(
      this.gps,
      this.translateService,
      account,
      this.balanceField,
      this.balanceField.field
    );
  }

  protected getNewOrExistingInstanceBeforeSave(value: { [p: string]: any }): Tenant {
    return undefined;
  }

  private toggleCashBalanceFields(enabled: boolean): void {
    this.manualCash.set(enabled);
    for (const ca of this.cashAccounts) {
      const fieldName = `cashBalance_${ca.idSecuritycashAccount}`;
      if (this.configObject[fieldName]) {
        this.configObject[fieldName].disabled = !enabled;
        this.configObject[fieldName].invisible = !enabled;
      }
    }
  }
}
