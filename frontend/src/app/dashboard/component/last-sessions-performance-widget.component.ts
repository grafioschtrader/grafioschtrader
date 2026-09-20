import { AfterViewInit, Component, Input, OnChanges, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Subscription } from 'rxjs';
import { DashboardConfig, DashboardResult } from '../../lib/dashboard/dashboard.types';
import { DashboardService } from '../../lib/dashboard/dashboard.service';
import { DynamicFormModule } from '../../lib/dynamic-form/dynamic-form.module';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { Helper } from '../../lib/helper/helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { FormConfig } from '../../lib/dynamic-form/models/form.config';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ShowRecordConfigBase } from '../../lib/datashowbase/show.record.config.base';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { PortfolioService } from '../../portfolio/service/portfolio.service';
import { LastSessionsPerformance } from '../model/last.sessions.performance';
import { LastSessionsPerformanceTableComponent } from './last-sessions-performance-table.component';

/**
 * Dashboard card reporting what the client, or one of its portfolios, gained or lost over the last completed sessions.
 *
 * <p>
 * How many sessions the card covers is a layout decision and lives in the settings dialog. Which portfolio it shows is
 * not: a reader comparing portfolios changes it repeatedly and none of those changes is worth saving, so it travels as
 * a setting of one read and an ordinary Refresh returns the card to the whole client.
 * </p>
 */
@Component({
  selector: 'last-sessions-performance-widget',
  standalone: true,
  imports: [TranslateModule, DynamicFormModule, LastSessionsPerformanceTableComponent],
  template: `
    <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService" #form="dynamicForm">
    </dynamic-form>
    @if (performance) {
      @if (performance.reasonKey) {
        <small class="d-block">{{ performance.reasonKey | translate }}</small>
      } @else {
        <div class="mb-2">
          @for (totalField of totalFields; track totalField.field) {
            <span class="me-3">
              <strong>{{ totalField.headerTranslated }}:</strong> {{ getValueByPath(performance, totalField) }}
            </span>
          }
        </div>
        <last-sessions-performance-table [sessions]="rows" [currency]="performance.currency" />
        <small class="d-block">{{ 'DASHBOARD_LAST_SESSIONS_NOTE' | translate }}</small>
      }
    }
  `
})
export class LastSessionsPerformanceWidgetComponent
  extends ShowRecordConfigBase
  implements OnInit, OnChanges, AfterViewInit, OnDestroy
{
  @Input() result: DashboardResult;

  @ViewChild('form') form: DynamicFormComponent;

  performance: LastSessionsPerformance;
  /** Newest session first: a card is read from the top and the last session is what the reader came for. */
  rows: LastSessionsPerformance['sessions'] = [];
  totalFields: ColumnConfig[] = [];
  config: FieldConfig[];
  configObject: { [name: string]: FieldConfig };
  formConfig: FormConfig = { labelColumns: 3, nonModal: true };

  private idPortfolioSubscription: Subscription;

  constructor(
    public override translateService: TranslateService,
    gps: GlobalparameterService,
    private dashboardService: DashboardService,
    private portfolioService: PortfolioService
  ) {
    super(translateService, gps);
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idPortfolio', 'DASHBOARD_LAST_SESSIONS_PORTFOLIO', false, {
        usedLayoutColumns: 8
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.initTotalFields();
  }

  /**
   * The portfolios are read here rather than after the view exists, because they are data of the card and not of its
   * form; the choice is then complete however the figures themselves turn out.
   */
  ngOnInit(): void {
    // The whole client is the default, so the empty entry is a real choice rather than a missing value.
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe((portfolios) => {
      this.configObject.idPortfolio.valueKeyHtmlOptions = SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idPortfolio',
        'name',
        portfolios,
        true
      );
      this.showChosenPortfolio();
    });
  }

  ngOnChanges(): void {
    this.performance = this.result?.payload?.custom as LastSessionsPerformance;
    this.rows = [...(this.performance?.sessions ?? [])].reverse();
    this.totalFields.forEach((field) => (field.fixedCurrency = this.performance?.currency));
    this.showChosenPortfolio();
  }

  ngAfterViewInit(): void {
    this.idPortfolioSubscription = this.configObject.idPortfolio.formControl.valueChanges.subscribe(() =>
      this.reload()
    );
  }

  ngOnDestroy(): void {
    this.idPortfolioSubscription?.unsubscribe();
  }

  /**
   * Reloads this card alone for the chosen portfolio. A failure leaves the figures on screen untouched rather than
   * blanking a card because one portfolio could not be read; the dashboard reports the failure through its own refresh
   * path.
   */
  private reload(): void {
    if (!this.result?.instanceId) {
      return;
    }
    // A select of the browser answers with text, while the card is read for a portfolio id or for none at all. That
    // conversion belongs to the field, so it is left to the form helper, which also turns the empty entry into null.
    const configOverride: DashboardConfig = {};
    Helper.copyFormSingleFormConfigToBusinessObject(
      this.formConfig,
      this.configObject.idPortfolio,
      configOverride,
      true
    );
    this.dashboardService.refresh(this.result.instanceId, configOverride).subscribe((result) => {
      const performance = result?.payload?.custom as LastSessionsPerformance;
      if (performance) {
        this.performance = performance;
        this.rows = [...performance.sessions].reverse();
        this.totalFields.forEach((field) => (field.fixedCurrency = performance.currency));
      }
    });
  }

  /**
   * Lets the select agree with the figures on screen without asking for them again. It matters after a refresh of the
   * whole dashboard, which returns the card to the client and has to return the select with it.
   */
  private showChosenPortfolio(): void {
    this.configObject.idPortfolio.formControl?.setValue(this.performance?.idPortfolio ?? '', { emitEvent: false });
  }

  /**
   * The totals over the listed sessions, shown above the table so the reader gets the answer before the detail. They
   * use the same data types as the table, because a figure formatted two ways in one card reads as two figures.
   */
  private initTotalFields(): void {
    this.totalFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'totalGainMC', 'TOTAL_GAIN'),
      ShowRecordConfigBase.createColumnConfig(
        DataType.Numeric,
        'totalBalanceChangeMC',
        'DASHBOARD_LAST_SESSIONS_BALANCE_CHANGE'
      ),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'externalCashTransferMC', 'EXTERNAL_CASH_TRANSFER')
    ];
    this.translateService
      .get(this.totalFields.map((field) => field.headerKey))
      .subscribe((translations) =>
        this.totalFields.forEach((field) => (field.headerTranslated = translations[field.headerKey]))
      );
  }
}
