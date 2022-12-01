import {AfterViewInit, Component, OnDestroy, ViewChild} from '@angular/core';
import {SingleRecordMasterViewBase} from '../../shared/masterdetail/component/single.record.master.view.base';
import {CorrelationLimit, CorrelationResult, CorrelationSet, SamplingPeriodType} from '../../entities/correlation.set';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {ActivatedRoute} from '@angular/router';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {HelpIds} from '../../shared/help/help.ids';
import {TranslateHelper} from '../../shared/helper/translate.helper';
import {CorrelationSetService} from '../service/correlation.set.service';
import {SelectOptionsHelper} from '../../shared/helper/select.options.helper';
import {ChildToParent, CorrelationTableComponent} from './correlation-table.component';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {Securitycurrency} from '../../entities/securitycurrency';
import {AppHelper} from '../../shared/helper/app.helper';
import {CorrelationEditingSupport} from './correlation.editing.support';

/**
 * Main component of correlation set. It supports the creation and deletion of a correlation set.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <p-panel>
        <p-header>
          <h4 class="ui-widget-header singleRowTableHeader">{{'CORRELATION_MATRIX' | translate}}</h4>
        </p-header>
      </p-panel>
      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm" (submitBt)="saveAndCalculate($event)">
      </dynamic-form>

      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
      <br/>
      <p
        *ngIf="timePeriod">{{'TIME_PERIOD' | translate}} {{timePeriod}} {{'INSTRUMENT_OVERLAPPING_PRICE_DATA' | translate}}</p>
      <p *ngIf="nonOverlappingDates" style="color:tomato;">{{'NON_OVERLAPPING_DATES' | translate}}</p>
      <correlation-table [childToParent]="this">
      </correlation-table>
    </div>
    <correlation-set-edit *ngIf="visibleEditDialog"
                          [visibleDialog]="visibleEditDialog"
                          [callParam]="callParam"
                          [correlationLimit]="correlationLimit"
                          (closeDialog)="handleCloseEditDialog($event)">
    </correlation-set-edit>
  `
})
export class CorrelationComponent extends SingleRecordMasterViewBase<CorrelationSet, Securitycurrency>
  implements AfterViewInit, OnDestroy, ChildToParent {

  private static readonly MAIN_FIELD = 'idCorrelationSet';
  @ViewChild(CorrelationTableComponent, {static: true}) correlationTableComponent: CorrelationTableComponent;
  timePeriod: string = null;
  nonOverlappingDates = false;

  private readonly tickerSymbol = 'tickerSymbol';
  private correlationLimit: CorrelationLimit;
  private correlationResult: CorrelationResult;
  private correlationEditingSupport: CorrelationEditingSupport = new CorrelationEditingSupport();

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
    this.formConfig = this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      2, null, true);
    this.config = this.correlationEditingSupport.getCorrelationFieldDefinition(CorrelationComponent.MAIN_FIELD, 6, 'SAVE_AND_CALC');
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.configObject.samplingPeriod.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, SamplingPeriodType, null);
  }

  ngAfterViewInit(): void {
    this.correlationSetService.getCorrelationSetLimit().subscribe((correlationLimit: CorrelationLimit) => {
      this.correlationLimit = correlationLimit;
      this.readData();
      this.valueChangedMainField();
      this.correlationEditingSupport.setUpValueChange(this.configObject, this.correlationLimit);
    });
  }

  refreshData(correlationSet: CorrelationSet): void {
    if (correlationSet) {
      this.selectedEntity.securitycurrencyList = correlationSet.securitycurrencyList;
      this.setChildDataAndCalculateWhenPossible(correlationSet, this.nonOverlappingDates);
    } else {
      this.readData();
    }
  }

  getPeriodAndRollingWithParamPrefix(): string[] {
    return this.correlationEditingSupport.getPeriodAndRollingWithParamPrefix(this.configObject);
  }

  override readData(): void {
    this.correlationSetService.getCorrelationSetByTenant().subscribe((correlationSets: CorrelationSet[]) => {
      this.entityList = correlationSets;
      this.correlationLimit.tenantLimit.actual = this.entityList.length;
      this.configObject[CorrelationComponent.MAIN_FIELD].valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(CorrelationComponent.MAIN_FIELD, 'name',
          correlationSets, false);
      this.selectedEntity && (this.selectedEntity = this.entityList.find(entity => entity[CorrelationComponent.MAIN_FIELD]
        === this.selectedEntity[CorrelationComponent.MAIN_FIELD]));
      setTimeout(() => this.setFieldValues());
    });
  }

  saveAndCalculate(event): void {
    this.form.cleanMaskAndTransferValuesToBusinessObject(this.selectedEntity);
    this.correlationSetService.update(this.selectedEntity).subscribe(cs => {
      Object.assign(this.selectedEntity, cs);
      this.setChildData(this.selectedEntity);
      this.configObject.submit.disabled = false;
    }, errorBackend => (this.configObject.submit.disabled = false));
  }

  ngOnDestroy(): void {
    super.destroy();
    this.correlationEditingSupport.destroy();
  }

  /**
   * Selection of parent may be changed or update
   */
  override setChildData(selectedEntity: CorrelationSet): void {
    this.setChildDataAndCalculateWhenPossible(selectedEntity, true);
  }

  protected override canCreate(): boolean {
    return this.correlationLimit.tenantLimit.actual < this.correlationLimit.tenantLimit.limit;
  }

  setChildDataAndCalculateWhenPossible(selectedEntity: CorrelationSet, calculate: boolean): void {
    this.timePeriod = null;
    this.nonOverlappingDates = false;
    if (selectedEntity) {

      this.childEntityList = selectedEntity.securitycurrencyList;
      if (selectedEntity.securitycurrencyList.length >= 2 && calculate) {
        this.correlationSetService.getCalculationByCorrelationSet(selectedEntity.idCorrelationSet).subscribe(
          (correlationResult: CorrelationResult) => {
            this.correlationResult = correlationResult;
            this.correlationTableComponent.parentSelectionChanged(selectedEntity, correlationResult);
            if (correlationResult.firstAvailableDate) {
              this.timePeriod = AppHelper.getDateByFormat(this.gps, correlationResult.firstAvailableDate) +
                ' - ' + AppHelper.getDateByFormat(this.gps, correlationResult.lastAvailableDate);
              this.correlationTableComponent.refreshChartWhenCorrelationSetChanges();
            } else {
              this.nonOverlappingDates = true;
            }
          });
      } else {
        this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
      }
    } else {
      this.correlationTableComponent.parentSelectionChanged(selectedEntity, this.correlationResult);
    }
  }

  override refreshMenus(): void {
    const editMenu = this.prepareEditMenu();
    const showMenu = this.prepareShowMenu();
    this.contextMenuItems = [...editMenu, ...showMenu];
    this.activePanelService.activatePanel(this, {editMenu, showMenu});
  }

  override prepareEditMenu(): MenuItem[] {
    const menuItems = this.getBaseEditMenu('CORRELATION_SET');
    menuItems.push(...this.correlationTableComponent.prepareEditMenu());
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected override prepareShowMenu(): MenuItem[] {
    const menuItems = this.correlationTableComponent.prepareShowMenu();
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected prepareCallParam(entity: CorrelationSet): void {
    this.callParam = new CallParam(null, entity);
  }
}
