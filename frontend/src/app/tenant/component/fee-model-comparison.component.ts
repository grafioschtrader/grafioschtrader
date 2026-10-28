import {
  afterNextRender,
  computed,
  DestroyRef,
  inject,
  Injector,
  signal,
  Component,
  OnDestroy,
  OnInit,
  viewChild
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AppSettings } from '../../shared/app.settings';

import { TranslateModule } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { FeeModelComparisonResponse } from '../../entities/fee.model.comparison';
import { Portfolio } from '../../entities/portfolio';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FieldFormGroup } from '../../lib/dynamic-form/models/form.group.definition';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { HelpIds } from '../../lib/help/help.ids';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { SecurityaccountService } from '../../securityaccount/service/securityaccount.service';
import { FeeModelComparisonTableComponent } from './fee-model-comparison-table.component';
import { FxObservationGroup } from '../../entities/fx.observation';
import { FxObservationTableComponent } from './fx-observation-table.component';
import { TenantService } from '../service/tenant.service';

/**
 * Parent component for the fee model comparison view. Uses dynamic-form for the security
 * account selection and exclude-zero-cost checkbox, displays summary statistics in fieldsets,
 * and delegates the detail table to FeeModelComparisonTableComponent.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />
      @if (comparisonResponse() && comparisonResponse().totalTransactions > 0) {
        <div class="fcontainer">
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">
              {{ 'OVERVIEW' | translate }}
            </legend>
            @for (field of summaryInfoFields; track field.field) {
              <div class="row">
                <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                <div class="col-md-7 nopadding wrap">
                  {{ getValueByPath(comparisonResponse(), field) }}
                </div>
              </div>
            }
          </fieldset>
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">
              {{ 'ACCURACY' | translate }}
            </legend>
            @for (field of summaryStatFields; track field.field) {
              <div class="row">
                <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                <div class="col-md-7 nopadding wrap">
                  {{ getValueByPath(comparisonResponse(), field) }}
                </div>
              </div>
            }
          </fieldset>
        </div>
      }
      <fee-model-comparison-table [details]="details()" />
      <h3>{{ 'FX_OBSERVATION_ACCOUNT' | translate }}</h3>
      <p>{{ 'FX_OBSERVATION_LIMITATION' | translate }}</p>
      <fx-observation-table [groups]="accountFxGroups()" />
      <h3>{{ 'FX_OBSERVATION_TRANSFERS' | translate }}</h3>
      <p>{{ 'FX_OBSERVATION_TRANSFER_LIMITATION' | translate }}</p>
      <fx-observation-table [groups]="transferFxGroups()" />
    </div>
  `,
  imports: [TranslateModule, ...DYNAMIC_FORM_IMPORTS, FeeModelComparisonTableComponent, FxObservationTableComponent]
})
export class FeeModelComparisonComponent extends ShowRecordConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {
  private portfolioService: PortfolioService = inject(PortfolioService);
  private securityaccountService: SecurityaccountService = inject(SecurityaccountService);
  private tenantService: TenantService = inject(TenantService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);

  readonly form = viewChild(DynamicFormComponent);
  readonly comparisonTable = viewChild(FeeModelComparisonTableComponent);

  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configObject: { [name: string]: FieldConfig };

  readonly comparisonResponse = signal<FeeModelComparisonResponse>(null);
  readonly details = computed(() => this.comparisonResponse()?.details ?? []);
  readonly accountFxGroups = signal<FxObservationGroup[]>([]);
  readonly transferFxGroups = signal<FxObservationGroup[]>([]);
  private readonly destroyRef = inject(DestroyRef);
  private readonly renderInjector = inject(Injector);
  private comparisonRequest: Subscription;
  private observationRequest: Subscription;

  summaryInfoFields: ColumnConfig[] = [];
  summaryStatFields: ColumnConfig[] = [];

  private subscriptions: Subscription[] = [];

  constructor() {
    super();
    this.formConfig = { labelColumns: 2, nonModal: true };
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idSecuritycashAccount', 'SECURITYACCOUNT', false, {
        usedLayoutColumns: 6
      }),
      DynamicFieldHelper.createFieldCheckboxHeqF('excludeZeroCost', {
        usedLayoutColumns: 6
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    this.initSummaryFields();
    this.subscriptions.push(
      this.tenantService
        .getFxObservations()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((response) => {
          this.transferFxGroups.set(response.groups);
        })
    );
    afterNextRender(
      () => {
        this.configObject.excludeZeroCost.formControl.setValue(true);
        this.subscriptions.push(
          this.configObject.idSecuritycashAccount.formControl.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
              this.loadComparison();
              this.loadObservations();
            }),
          this.configObject.excludeZeroCost.formControl.valueChanges
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => this.loadComparison())
        );
        this.loadSecurityaccounts();
      },
      { injector: this.renderInjector }
    );
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  onComponentClick(event: any): void {
    this.activePanelService.activatePanel(this, {
      showMenu: this.comparisonTable()?.getMenuShowOptions()
    });
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONCOSTS;
  }

  ngOnDestroy(): void {
    this.comparisonRequest?.unsubscribe();
    this.observationRequest?.unsubscribe();
    this.subscriptions.forEach((s) => s.unsubscribe());
    this.activePanelService.destroyPanel(this);
  }

  private initSummaryFields(): void {
    this.summaryInfoFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'planName', 'PLAN'),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'totalTransactions', 'TOTAL', true, false, {
        maxFractionDigits: 0
      }),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'comparedCount', 'COMPARED', true, false, {
        maxFractionDigits: 0
      }),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'skippedCount', 'SKIPPED', true, false, {
        maxFractionDigits: 0
      }),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'errorCount', 'ERRORS', true, false, {
        maxFractionDigits: 0
      })
    ];
    this.summaryStatFields = [
      ShowRecordConfigBase.createColumnConfig(
        DataType.Numeric,
        'meanAbsoluteError',
        'MEAN_ABSOLUTE_ERROR',
        true,
        false,
        { maxFractionDigits: 2 }
      ),
      ShowRecordConfigBase.createColumnConfig(
        DataType.Numeric,
        'meanRelativeError',
        'MEAN_RELATIVE_ERROR',
        true,
        false,
        { maxFractionDigits: AppSettings.FID_PERCENTAGE_FRACTION }
      ),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'rmse', 'RMSE', true, false, { maxFractionDigits: 2 })
    ];
    const allFields = [...this.summaryInfoFields, ...this.summaryStatFields];
    const headerKeys = allFields.map((f) => f.headerKey);
    this.translateService
      .get(headerKeys)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((translations) => {
        allFields.forEach((f) => (f.headerTranslated = translations[f.headerKey]));
        this.notifyViewChanged();
      });
  }

  private loadSecurityaccounts(): void {
    this.portfolioService
      .getPortfoliosForTenantOrderByName()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((portfolios: Portfolio[]) => {
        const options: ValueKeyHtmlSelectOptions[] = [new ValueKeyHtmlSelectOptions('', '')];
        for (const portfolio of portfolios) {
          if (portfolio.securityaccountList) {
            for (const sa of portfolio.securityaccountList) {
              options.push(new ValueKeyHtmlSelectOptions(sa.idSecuritycashAccount, portfolio.name + ' / ' + sa.name));
            }
          }
        }
        this.configObject.idSecuritycashAccount.valueKeyHtmlOptions = options;
      });
  }

  private loadComparison(): void {
    this.comparisonRequest?.unsubscribe();
    this.comparisonResponse.set(null);
    const idSecuritycashAccount = this.configObject.idSecuritycashAccount.formControl.value;
    const excludeZeroCost = this.configObject.excludeZeroCost.formControl.value ?? true;
    if (idSecuritycashAccount) {
      this.comparisonRequest = this.securityaccountService
        .getFeeModelComparison(+idSecuritycashAccount, excludeZeroCost)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((response: FeeModelComparisonResponse) => {
          this.comparisonResponse.set(response);
        });
    }
  }

  /** Independent request: absent or failing commission models cannot suppress observed FX conversions. */
  private loadObservations(): void {
    this.observationRequest?.unsubscribe();
    this.accountFxGroups.set([]);
    const idAccount = this.configObject.idSecuritycashAccount.formControl.value;
    if (idAccount) {
      this.observationRequest = this.securityaccountService
        .getFxObservations(+idAccount)
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((response) => {
          this.accountFxGroups.set(response.groups);
        });
    }
  }
}
