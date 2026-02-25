import {CommonModule} from '@angular/common';
import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {Subscription} from 'rxjs';
import {FeeModelComparisonDetail, FeeModelComparisonResponse} from '../../entities/fee.model.comparison';
import {Portfolio} from '../../entities/portfolio';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {ShowRecordConfigBase} from '../../lib/datashowbase/show.record.config.base';
import {DynamicFormComponent} from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {FieldConfig} from '../../lib/dynamic-form/models/field.config';
import {FieldFormGroup} from '../../lib/dynamic-form/models/form.group.definition';
import {FormConfig} from '../../lib/dynamic-form/models/form.config';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {PortfolioService} from '../../portfolio/service/portfolio.service';
import {SecurityaccountService} from '../../securityaccount/service/securityaccount.service';
import {FeeModelComparisonTableComponent} from './fee-model-comparison-table.component';

/**
 * Parent component for the fee model comparison view. Uses dynamic-form for the security
 * account selection and exclude-zero-cost checkbox, displays summary statistics in fieldsets,
 * and delegates the detail table to FeeModelComparisonTableComponent.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>
      @if (comparisonResponse && comparisonResponse.totalTransactions > 0) {
        <div class="fcontainer">
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">{{ 'OVERVIEW' | translate }}</legend>
            @for (field of summaryInfoFields; track field.field) {
              <div class="row">
                <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                <div class="col-md-7 nopadding wrap">{{ getValueByPath(comparisonResponse, field) }}</div>
              </div>
            }
          </fieldset>
          <fieldset class="out-border fbox">
            <legend class="out-border-legend">{{ 'ACCURACY' | translate }}</legend>
            @for (field of summaryStatFields; track field.field) {
              <div class="row">
                <div class="col-md-5 showlabel text-end">{{ field.headerTranslated }}:</div>
                <div class="col-md-7 nopadding wrap">{{ getValueByPath(comparisonResponse, field) }}</div>
              </div>
            }
          </fieldset>
        </div>
      }
      <fee-model-comparison-table [details]="details"></fee-model-comparison-table>
    </div>
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, DynamicFormModule, FeeModelComparisonTableComponent]
})
export class FeeModelComparisonComponent extends ShowRecordConfigBase implements IGlobalMenuAttach, OnInit, OnDestroy {

  @ViewChild(DynamicFormComponent, {static: true}) form: DynamicFormComponent;
  @ViewChild(FeeModelComparisonTableComponent) comparisonTable: FeeModelComparisonTableComponent;

  formConfig: FormConfig;
  config: FieldFormGroup[] = [];
  configObject: { [name: string]: FieldConfig };

  comparisonResponse: FeeModelComparisonResponse;
  details: FeeModelComparisonDetail[] = [];

  summaryInfoFields: ColumnConfig[] = [];
  summaryStatFields: ColumnConfig[] = [];

  private subscriptions: Subscription[] = [];

  constructor(
    private portfolioService: PortfolioService,
    private securityaccountService: SecurityaccountService,
    private activePanelService: ActivePanelService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(translateService, gps);
    this.formConfig = {labelColumns: 2, nonModal: true};
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idSecuritycashAccount', 'SECURITYACCOUNT', false,
        {usedLayoutColumns: 6}),
      DynamicFieldHelper.createFieldCheckboxHeqF('excludeZeroCost', {usedLayoutColumns: 6})
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    this.initSummaryFields();
    setTimeout(() => {
      this.configObject.excludeZeroCost.formControl.setValue(true);
      this.subscriptions.push(
        this.configObject.idSecuritycashAccount.formControl.valueChanges.subscribe(() => this.loadComparison()),
        this.configObject.excludeZeroCost.formControl.valueChanges.subscribe(() => this.loadComparison())
      );
      this.loadSecurityaccounts();
    });
    this.onComponentClick(null);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  onComponentClick(event: any): void {
    this.activePanelService.activatePanel(this, {showMenu: this.comparisonTable?.getMenuShowOptions()});
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_PORTFOLIOS_TRANSACTIONCOSTS;
  }

  ngOnDestroy(): void {
    this.subscriptions.forEach(s => s.unsubscribe());
    this.activePanelService.destroyPanel(this);
  }

  private initSummaryFields(): void {
    this.summaryInfoFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.String, 'planName', 'PLAN'),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'totalTransactions', 'TOTAL',
        true, false, {maxFractionDigits: 0}),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'comparedCount', 'COMPARED',
        true, false, {maxFractionDigits: 0}),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'skippedCount', 'SKIPPED',
        true, false, {maxFractionDigits: 0}),
      ShowRecordConfigBase.createColumnConfig(DataType.NumericShowZero, 'errorCount', 'ERRORS',
        true, false, {maxFractionDigits: 0})
    ];
    this.summaryStatFields = [
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'meanAbsoluteError', 'MEAN_ABSOLUTE_ERROR',
        true, false, {maxFractionDigits: 2}),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'meanRelativeError', 'MEAN_RELATIVE_ERROR',
        true, false, {maxFractionDigits: 2}),
      ShowRecordConfigBase.createColumnConfig(DataType.Numeric, 'rmse', 'RMSE',
        true, false, {maxFractionDigits: 2})
    ];
    const allFields = [...this.summaryInfoFields, ...this.summaryStatFields];
    const headerKeys = allFields.map(f => f.headerKey);
    this.translateService.get(headerKeys).subscribe(translations => {
      allFields.forEach(f => f.headerTranslated = translations[f.headerKey]);
    });
  }

  private loadSecurityaccounts(): void {
    this.portfolioService.getPortfoliosForTenantOrderByName().subscribe((portfolios: Portfolio[]) => {
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
    const idSecuritycashAccount = this.configObject.idSecuritycashAccount.formControl.value;
    const excludeZeroCost = this.configObject.excludeZeroCost.formControl.value ?? true;
    if (idSecuritycashAccount) {
      this.securityaccountService.getFeeModelComparison(+idSecuritycashAccount, excludeZeroCost)
        .subscribe((response: FeeModelComparisonResponse) => {
          this.comparisonResponse = response;
          this.details = response.details || [];
        });
    }
  }
}
