import {AfterViewInit, Component, ViewChild} from '@angular/core';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {CorrelationResult, CorrelationSet, SamplingPeriodType} from '../../entities/correlation.set';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {ActivatedRoute} from '@angular/router';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../shared/help/help.ids';
import {DynamicFieldHelper} from '../../shared/helper/dynamic.field.helper';
import {AppSettings} from '../../shared/app.settings';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {CorrelationSetService} from '../service/correlation.set.service';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {ChildToParent, CorrelationTableComponent} from './correlation-table.component';
import {TenantLimit} from '../../entities/backend/tenant.limit';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppHelper} from '../../shared/helper/app.helper';


@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <h4 class="ui-widget-header singleRowTableHeader">{{'CORRELATION_MATRIX' | translate}}</h4>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>

      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
      <br/>
      <p *ngIf="timePeriod">{{'SAMPLING_PERIOD' | translate}} {{timePeriod}} {{'INSTRUMENT_OVERLAPPING_PRICE_DATA' | translate}}</p>
      <correlation-table [childToParent]="this">
      </correlation-table>
    </div>
    <correlation-set-edit *ngIf="visibleEditDialog"
                          [visibleDialog]="visibleEditDialog"
                          [callParam]="callParam"
                          (closeDialog)="handleCloseEditDialog($event)">
    </correlation-set-edit>
  `
})
export class CorrelationComponent extends SingleRecordMasterViewBase<CorrelationSet, Securitycurrency>
  implements AfterViewInit, ChildToParent {

  private static readonly MAIN_FIELD = 'idCorrelationSet';
  timePeriod: string = null;
  @ViewChild(CorrelationTableComponent, {static: true}) correlationTableComponent: CorrelationTableComponent;

  private limitCorrelationSet: TenantLimit;
  private correlationResult: CorrelationResult;

  constructor(private activatedRoute: ActivatedRoute,
              private correlationSetService: CorrelationSetService,
              gps: GlobalparameterService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              translateService: TranslateService) {

    super(gps, HelpIds.HELP_WATCHLIST_CORRELATION,
      CorrelationComponent.MAIN_FIELD,
      'CORRELATION_SET', correlationSetService, confirmationService, messageToastService, activePanelService,
      translateService);
    this.formConfig = {labelcolumns: 2, nonModal: true};

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(CorrelationComponent.MAIN_FIELD, 'CORRELATION_SET_NAME', false,
        {usedLayoutColumns: 6}),
      DynamicFieldHelper.createFieldSelectNumberHeqF('samplingPeriod', true,
        {usedLayoutColumns: 6}),
      // DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'startDate', false),
      // DynamicFieldHelper.createFieldPcalendarHeqF(DataType.DateString, 'endDate', false),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', AppSettings.FID_MAX_LETTERS, false,
        {usedLayoutColumns: 6, disabled: true}),
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.samplingPeriod.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, SamplingPeriodType, null);
  }

  ngAfterViewInit(): void {
    this.correlationSetService.getCorrelationSetLimit().subscribe((teantLimit: TenantLimit) => {
      this.limitCorrelationSet = teantLimit;
      this.readData();
      this.valueChangedMainField();
    });
  }

  refreshData(correlationSet: CorrelationSet) {
    if (correlationSet) {
      this.selectedEntity.securitycurrencyList = correlationSet.securitycurrencyList;
      this.setChildDataAndCalculateWhenPossible(correlationSet, !correlationSet);
    } else {
      this.readData();
    }
  }

  readData(): void {
    this.correlationSetService.getCorrelationSetByTenant().subscribe((correlationSets: CorrelationSet[]) => {
      this.entityList = correlationSets;
      this.configObject[CorrelationComponent.MAIN_FIELD].valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptions(CorrelationComponent.MAIN_FIELD, 'name',
          correlationSets, false);
      this.selectedEntity && (this.selectedEntity = this.entityList.find(entity => entity[CorrelationComponent.MAIN_FIELD]
        === this.selectedEntity[CorrelationComponent.MAIN_FIELD]));
      setTimeout(() => this.setFieldValues());
    });
  }

  /**
   * Selection of parent may be changed or update
   */
  setChildData(selectedEntity: CorrelationSet): void {
    this.setChildDataAndCalculateWhenPossible(selectedEntity, true);
  }

  setChildDataAndCalculateWhenPossible(selectedEntity: CorrelationSet, calculate: boolean): void {
    this.timePeriod = null;
    if (selectedEntity) {

      this.childEntityList = selectedEntity.securitycurrencyList;
      if (selectedEntity.securitycurrencyList.length >= 2 && calculate) {
        this.correlationSetService.getCalculationByCorrelationSet(selectedEntity.idCorrelationSet).subscribe(
          (correlationResult: CorrelationResult) => {
            this.correlationResult = correlationResult;
            this.correlationTableComponent.parentSelectionChanged(selectedEntity, correlationResult);
            this.timePeriod = AppHelper.getDateByFormat(this.gps, correlationResult.firstAvailableDate) +
              ' - ' + AppHelper.getDateByFormat(this.gps, correlationResult.lastAvailableDate);
          });
      } else {
        this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
      }
    } else {
      this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
    }
  }

  prepareEditMenu(): MenuItem[] {
    const menuItems = this.getBaseEditMenu('CORRELATION_SET');

    // TODO Child Menu for Rolling correlations ?
    menuItems.push(...this.correlationTableComponent.prepareEditMenu());
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }


  protected prepareCallParm(entity: CorrelationSet): void {
    this.callParam = new CallParam(null, entity);
  }
}
