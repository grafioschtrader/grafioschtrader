import { Subscription, finalize, forkJoin } from 'rxjs';
import { Component, DestroyRef, OnDestroy, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { metadata, SchemaPathTree } from '@angular/forms/signals';
import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import type { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { SignalDynamicFormComponent } from '../../lib/dynamic-form/signal/signal-dynamic-form.component';
import { FIELD_DISPLAY, SignalFormModel } from '../../lib/dynamic-form/signal/signal-form.model';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AppSettings } from '../../shared/app.settings';
import type { RealizedGainGrandTotal } from '../../entities/view/realizedgain/realized.gain.grand.total';
import { IdsAccounts } from '../model/ids.accounts';
import { TenantDividendSecurityAccountSelectionDialogComponent } from './tenant-dividend-security-account-selection-dialog.component';
import { TenantRealizedGainsYearTableComponent } from './tenant-realized-gains-year-table.component';
import { TenantGermanTaxHelpComponent } from './tenant-german-tax-help.component';

/** The field of the form that selects the cost basis method of the report. */
interface RealizedGainsFormModel {
  costBasisMethod: string | null;
}

/**
 * The capital gains the tenant realized by sales, grouped by year. The form selects the cost basis method of the
 * report, independent of the method the tenant or a portfolio uses in the other views, the menu selects the security
 * accounts. The table of the years is a child component.
 */
@Component({
  template: `
    <div
      (click)="onComponentClick($event)"
      [class.active-border]="isActivated()"
      [class.passiv-border]="!isActivated()"
      class="data-container">
      <signal-dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [rules]="rules"
        #form="signalDynamicForm"
        (submitBt)="submit($event)" />
      @if (grandTotal(); as total) {
        <h4 [translate]="'REALIZED_GAINS_YEAR_CAPTION'" [translateParams]="{ method: appliedMethodLabel() }"></h4>
        <div class="d-flex flex-wrap gap-4 mb-2">
          @for (summaryField of summaryFields; track summaryField.field) {
            <span>
              <strong>{{ summaryField.headerTranslated }}:</strong>
              {{ getValueByPath(total, summaryField) }}
            </span>
          }
        </div>
        <tenant-realized-gains-year-table [grandTotal]="total" />
        @if (total.tenantCountry === 'DE' && total.mainCurrency === 'EUR') {
          <tenant-german-tax-help />
        }
      }
    </div>
    @if (visibleSecurityaccountDialog()) {
      @if (idsAccounts(); as accounts) {
        @if (grandTotal(); as total) {
          <tenant-dividend-security-account-selection-dialog
            (closeDialog)="handleOnProcessedDialog($event)"
            [idsAccounts]="accounts"
            [portfolios]="total.portfolioList"
            [visibleDialog]="visibleSecurityaccountDialog()" />
        }
      }
    }
  `,
  imports: [
    TranslateModule,
    SignalDynamicFormComponent,
    TenantRealizedGainsYearTableComponent,
    TenantGermanTaxHelpComponent,
    TenantDividendSecurityAccountSelectionDialogComponent
  ]
})
export class TenantRealizedGainsComponent extends ShowRecordConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  private readonly portfolioService = inject(PortfolioService);
  private readonly gpsGT = inject(GlobalparameterGTService);
  private readonly activePanelService = inject(ActivePanelService);
  private readonly usersettingsService = inject(UserSettingsService);
  private readonly destroyRef = inject(DestroyRef);

  private readonly form = viewChild<SignalDynamicFormComponent<RealizedGainsFormModel>>('form');

  formConfig: FormConfig = AppHelper.getDefaultFormConfig(this.gps, 3, null, true);
  config: FieldFormGroup[] = [];

  readonly grandTotal = signal<RealizedGainGrandTotal | null>(null);
  readonly appliedMethodLabel = signal('');
  readonly visibleSecurityaccountDialog = signal(false);
  readonly idsAccounts = signal<IdsAccounts | null>(null);
  private readonly methodOptions = signal<ValueKeyHtmlSelectOptions[]>([]);

  /** The sums over all years, shown above the table. */
  readonly summaryFields: ColumnConfig[] = [
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandGainMC', 'REALIZED_YEAR_GAIN'),
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandLossMC', 'REALIZED_YEAR_LOSS'),
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandNetMC', 'REALIZED_YEAR_NET'),
    ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'grandSystemNetMC', 'REALIZED_SYSTEM_NET')
  ];

  /** The select shows the options as they arrive, the dependency of a field on a signal is a rule. */
  readonly rules = (path: SchemaPathTree<RealizedGainsFormModel>): void => {
    metadata(path.costBasisMethod, FIELD_DISPLAY, () => ({ valueKeyHtmlOptions: this.methodOptions() }));
  };

  private readRequest?: Subscription;
  private method: string | null = null;

  ngOnInit(): void {
    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('costBasisMethod', false),
      DynamicFieldHelper.createSubmitButton('REALIZED_GAINS_CALCULATE')
    ];
    TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config, false);
    this.loadMethodOptions();
    this.idsAccounts.set(new IdsAccounts(this.getAccountSettings(), [-1]));
    this.readData();
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_REALIZED_GAINS;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  /** Publishes the menu with the selection of the security accounts to the menu bar. */
  onComponentClick(_event: PanelClickEvent | null): void {
    const showMenu: MenuItem[] = [
      { label: 'DIV_INCLUDE_SECURITYACCOUNT', command: () => this.visibleSecurityaccountDialog.set(true) }
    ];
    TranslateHelper.translateMenuItems$(showMenu, this.translateService)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((translated) => this.activePanelService.activatePanel(this, { showMenu: translated }));
  }

  /** Reads the report with the method of the form. */
  submit(value: SignalFormModel): void {
    this.method = (value['costBasisMethod'] as string | null) || null;
    this.readData();
  }

  handleOnProcessedDialog(processedActionData: ProcessedActionData): void {
    this.visibleSecurityaccountDialog.set(false);
    if (processedActionData.action === ProcessedAction.CREATED) {
      const accounts: IdsAccounts | undefined = processedActionData.data;
      if (!accounts) {
        return;
      }
      this.idsAccounts.set(accounts);
      this.usersettingsService.saveArray(this.getSettingsKey(), accounts.idsSecurityaccount);
      this.readData();
    }
  }

  private getSettingsKey(): string {
    return this.gps.getIdTenant() + '_' + AppSettings.REALIZED_GAINS_SECURITYACCOUNTS;
  }

  private getAccountSettings(): number[] {
    return this.usersettingsService.readArray(this.getSettingsKey());
  }

  /** The empty option stands for the methods of the tenant and its portfolios. */
  private loadMethodOptions(): void {
    forkJoin([this.gpsGT.getCostBasisMethods(), this.translateService.get('REALIZED_GAINS_METHOD_PER_ACCOUNT')])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([methods, perAccount]) =>
        this.methodOptions.set([
          new ValueKeyHtmlSelectOptions('', perAccount),
          ...methods.map(
            (option) => new ValueKeyHtmlSelectOptions(option.key, this.translateService.instant(option.value))
          )
        ])
      );
  }

  private readData(): void {
    const accounts = this.idsAccounts();
    if (!accounts) {
      return;
    }
    this.readRequest?.unsubscribe();
    this.readRequest = this.portfolioService
      .getRealizedGainsByTenant(accounts.idsSecurityaccount, this.method)
      .pipe(
        takeUntilDestroyed(this.destroyRef),
        finalize(() => this.form()?.enableSubmit())
      )
      .subscribe((total) => this.showReport(total));
  }

  private showReport(total: RealizedGainGrandTotal): void {
    this.appliedMethodLabel.set(
      this.translateService.instant(total.appliedMethod === 'MIXED' ? 'COST_BASIS_METHOD_MIXED' : total.appliedMethod)
    );
    this.summaryFields.forEach((field) => {
      field.headerSuffix = total.mainCurrency;
      field.fixedCurrency = total.mainCurrency;
    });
    const headerKeys = this.summaryFields.map((field) => field.headerKey).filter((key): key is string => !!key);
    this.translateService.get(headerKeys).subscribe((translations: Record<string, string>) => {
      this.summaryFields.forEach((field) => {
        field.headerTranslated = field.headerKey ? translations[field.headerKey] : '';
      });
      this.grandTotal.set(total);
    });
  }
}
