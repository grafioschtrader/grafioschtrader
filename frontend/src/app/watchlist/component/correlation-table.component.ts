import {ChangeDetectorRef, Component, Input, OnDestroy} from '@angular/core';
import {TableConfigBase} from '../../shared/datashowbase/table.config.base';
import {FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {CorrelationInstrument, CorrelationResult, CorrelationSet} from '../../entities/correlation.set';
import {DataType} from '../../dynamic-form/models/data.type';
import {AppSettings} from '../../shared/app.settings';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {Securitycurrency} from '../../entities/securitycurrency';
import {Security} from '../../entities/security';
import {InfoLevelType} from '../../shared/message/info.leve.type';
import {ProcessedActionData} from '../../shared/types/processed.action.data';
import {CorrelationSetService} from '../service/correlation.set.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {DataChangedService} from '../../shared/maintree/service/data.changed.service';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {ProcessedAction} from '../../shared/types/processed.action';
import {Subscription} from 'rxjs';

@Component({
  selector: 'correlation-table',
  template: `
    <p-table [columns]="fields" [value]="securitycurrencyList" selectionMode="single"
             [(selection)]="selectedEntity" dataKey="idSecuritycurrency"
             (sortFunction)="customSort($event)" [customSort]="true"
             styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
      <ng-template pTemplate="header" let-fields>
        <tr>
          <th style="width:24px"></th>
          <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
              [style.width.px]="field.width">
            {{field.headerTranslated}}
            <p-sortIcon [field]="field.field"></p-sortIcon>
          </th>
        </tr>
      </ng-template>
      <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
        <tr [pSelectableRow]="el">
          <td>
            <a href="#" [pRowToggler]="el">
              <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
            </a>
          </td>
          <td *ngFor="let field of fields" [style.background-color]="getBackgroundColor(el, field)"
              [ngClass]="field.dataType===DataType.NumericShowZero ? 'text-right': ''">
            <ng-container [ngSwitch]="field.templateName">
              <ng-container *ngSwitchCase="'check'">
                <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
              </ng-container>
              <ng-container *ngSwitchDefault>
                {{getValueByPath(el, field)}}
              </ng-container>
            </ng-container>
          </td>
        </tr>
      </ng-template>
      <ng-template pTemplate="rowexpansion" let-tdc let-columns="fields">
        <tr>
          <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
            <h4>Some Data</h4>
          </td>
        </tr>
      </ng-template>
    </p-table>
    <correlation-add-instrument *ngIf="visibleAddInstrumentDialog" [idCorrelationSet]="correlationSet.idCorrelationSet"
                                [tenantLimits]="tenantLimits"
                                [visibleAddInstrumentDialog]="visibleAddInstrumentDialog"
                                (closeDialog)="handleCloseAddInstrumentDialog($event)">
    </correlation-add-instrument>
  `
})
export class CorrelationTableComponent extends TableConfigBase implements OnDestroy {
  @Input() childToParent: ChildToParent;

  tenantLimits: TenantLimit[];
  securitycurrencyList: Securitycurrency[];
  selectedEntity: Securitycurrency;
  visibleAddInstrumentDialog: boolean;

  private correlationResult: CorrelationResult;
  private correlationSet: CorrelationSet;
  private subscriptionInstrumentAdded: Subscription;

  constructor(private correlationSetService: CorrelationSetService,
              private messageToastService: MessageToastService,
              private dataChangedService: DataChangedService,
              changeDetectionStrategy: ChangeDetectorRef,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(changeDetectionStrategy, filterService, usersettingsService, translateService, gps);
    this.createDynamicTableDefinition(null, null);
    this.addInstrumentsToCorrelationSet();
  }

  parentSelectionChanged(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.createDynamicTableDefinition(correlationSet, correlationResult);
    this.readListLimitOnce(correlationSet);
  }

  private readListLimitOnce(correlationSet: CorrelationSet): void {
    if (!this.tenantLimits && correlationSet) {
      this.correlationSetService.getCorrelationSetInstrumentLimit(correlationSet.idCorrelationSet).subscribe(limit => {
        this.tenantLimits = [limit];
      });
    }
  }

  private createDynamicTableDefinition(correlationSet: CorrelationSet, correlationResult: CorrelationResult): void {
    this.removeAllColumns();
    this.correlationResult = correlationResult;
    this.addColumnFeqH(DataType.String, 'name', true, false,
      {width: 200, templateName: AppSettings.OWNER_TEMPLATE});
    if (correlationSet) {
      this.addCorrelationDefinition(correlationSet);
    } else {
      this.securitycurrencyList = [];
    }

    this.translateHeadersAndColumns();
  }

  private addCorrelationDefinition(correlationSet: CorrelationSet): void {
    this.addColumnFeqH(DataType.String, 'tickerSymbol', true, false);
    let i = 0;
    correlationSet.securitycurrencyList.forEach(sc => {
      let label = (sc.hasOwnProperty('tickerSymbol') ? (<Security>sc).tickerSymbol : sc.name);
      if (!label) {
        label = '(' + i + ')';
        (<Security>sc).tickerSymbol = label;
      }
      this.addColumnFeqH(DataType.NumericShowZero, label,
        true, false, {fieldValueFN: this.getCorrelation.bind(this), userValue: i++});
    });
    this.securitycurrencyList = correlationSet.securitycurrencyList;
    this.correlationSet = correlationSet;
  }

  public prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({separator: true});
    menuItems.push(
      {
        label: 'ADD_EXISTING_SECURITY' + AppSettings.DIALOG_MENU_SUFFIX, command: (e) => this.addExistingSecurity(e),
        disabled: !this.tenantLimits || !this.correlationSet
          || this.correlationSet.securitycurrencyList.length >= this.tenantLimits[0].limit
      }
    );

    menuItems.push({
      label: 'REMOVE_INSTRUMENT', command: (event) => this.removeInstrumentFromCorrelationSet(this.selectedEntity
        .idSecuritycurrency)
    });
    return menuItems;
  }

  addExistingSecurity(event) {
    this.visibleAddInstrumentDialog = true;
  }

  handleCloseAddInstrumentDialog(processedActionData: ProcessedActionData) {
    this.visibleAddInstrumentDialog = false;
    this.childToParent.refreshData(null);
  }

  private addInstrumentsToCorrelationSet(): void {
    this.subscriptionInstrumentAdded = this.dataChangedService.dateChanged$.subscribe(processedActionData => {
      if (processedActionData.data.hasOwnProperty('idCorrelationSet')
        && processedActionData.action === ProcessedAction.UPDATED) {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'ADDED_SECURITY_TO_WATCHLIST');
        this.childToParent.refreshData(processedActionData.data);
      }
    });
  }

  removeInstrumentFromCorrelationSet(idSecuritycurrency: number) {
    this.correlationSetService.removeInstrumentFromCorrelationSet(this.correlationSet.idCorrelationSet,
      idSecuritycurrency).subscribe(correlationSet => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'REMOVED_INSTRUMENT_FROM_CORRELATIONSET');
      // this.dataChangedService.dataHasChanged(new ProcessedActionData(ProcessedAction.DELETED, new CorrelationSet()));
      this.childToParent.refreshData(correlationSet);
    });
  }

  getCorrelation(dataobject: Securitycurrency, field: ColumnConfig, valueField: any): string | number {
    const ci: CorrelationInstrument = this.correlationResult.correlationInstruments.find(correlationResult =>
      correlationResult.idSecuritycurrency === dataobject.idSecuritycurrency);
    return ci ? ci.correlations[field.userValue] : null;
  }

  getBackgroundColor(dataobject: Securitycurrency, field: ColumnConfig): string {
    if (field.userValue != null) {
      const value: number = this.getCorrelation(dataobject, field, null) as number;
      return 'hsl(' + ((value + 1) * 58) + ',100%, 50%)';
    }
    return null;
  }

  ngOnDestroy(): void {
    this.subscriptionInstrumentAdded && this.subscriptionInstrumentAdded.unsubscribe();
  }

}

export interface ChildToParent {
  refreshData(correlationSet: CorrelationSet);
}



