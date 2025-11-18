import {Component, OnDestroy} from '@angular/core';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {StockexchangeService} from '../service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {combineLatest} from 'rxjs';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {HelpIds} from '../../lib/help/help.ids';
import {plainToInstance} from 'class-transformer';
import {StockexchangeCallParam} from './stockexchange.call.param';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ColumnConfig} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {TableCrudSupportMenuSecurity} from '../../lib/datashowbase/table.crud.support.menu.security';
import {StockexchangeBaseData, StockexchangeMic} from '../model/stockexchange.base.data';
import {StockexchangeHasSecurity} from '../model/stockexchange.has.security';
import {StockexchangeHelper} from './stockexchange.helper';
import {AppHelper} from '../../lib/helper/app.helper';

/**
 * Shows stock exchanges in a table
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow.bind(this)"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{'data-container-full': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{entityNameUpper | translate}}</h4>

    </configurable-table>

    <!-- Expanded row content template -->
    <ng-template #expandedContent let-stockexchange>
      <trading-calendar-stockexchange [stockexchange]="stockexchange"
                                      [sourceCopyStockexchanges]="getCopySourceStockexchanges(stockexchange.idStockexchange)">
      </trading-calendar-stockexchange>
    </ng-template>

    @if (visibleDialog) {
      <stockexchange-edit [visibleDialog]="visibleDialog"
                          [callParam]="callParam"
                          (closeDialog)="handleCloseDialog($event)">
      </stockexchange-edit>
    }
  `,
  providers: [DialogService],
  standalone: false
})
export class StockexchangeTableComponent extends TableCrudSupportMenuSecurity<Stockexchange> implements OnDestroy {

  callParam: StockexchangeCallParam = new StockexchangeCallParam();

  private countriesAsKeyValue: { [cc: string]: string } = {};
  private stockexchangeMics: StockexchangeMic[];

  constructor(private stockexchangeService: StockexchangeService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(AppSettings.STOCKEXCHANGE, stockexchangeService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService);

    this.addColumnFeqH(DataType.String, 'mic', true, false, {
      width: 40,
      templateName: AppSettings.OWNER_TEMPLATE
    });
    this.addColumnFeqH(DataType.String, 'name', true, false, {
      width: 180,
    });
    this.addColumnFeqH(DataType.String, 'countryCode', true, false,
      {fieldValueFN: this.getDisplayNameForCounty.bind(this)});
    this.addColumnFeqH(DataType.Boolean, 'secondaryMarket', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'noMarketValue', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.TimeString, 'timeOpen', true, false);
    this.addColumnFeqH(DataType.TimeString, 'timeClose', true, false);
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {width: 120});
    this.addColumn(DataType.String, 'nameIndexUpdCalendar', 'ID_INDEX_UPD_CALENDAR', true, false, {width: 180});
    this.addColumnFeqH(DataType.DateString, 'maxCalendarUpdDate', true, false);
    this.addColumnFeqH(DataType.TimeString, 'localTime', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'lastDirectPriceUpdate', true, false,
      {width: 100});
    this.multiSortMeta.push({field: 'name', order: 1});
    this.prepareTableAndTranslate();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_STOCKEXCHANGE;
  }

  getDisplayNameForCounty(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.countriesAsKeyValue[dataobject['countryCode']];
  }

  override onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedEntity);
    }
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  canExpandRow(stockexchange: Stockexchange): boolean {
    return !stockexchange.noMarketValue;
  }

  getCopySourceStockexchanges(targetIdStockexchange: number): ValueKeyHtmlSelectOptions[] {
    return this.entityList.filter(stockexhange => !stockexhange.noMarketValue
      && targetIdStockexchange !== stockexhange.idStockexchange)
      .map(stockexchange => new ValueKeyHtmlSelectOptions(stockexchange.idStockexchange, stockexchange.name));
  }

  protected override readData(): void {
    if (this.callParam.countriesAsHtmlOptions) {
      combineLatest([this.stockexchangeService.getAllStockexchanges(true),
        this.stockexchangeService.stockexchangesHasSecurity()]).subscribe((data: [Stockexchange[], StockexchangeHasSecurity[]]) => {
        this.prepareStockexchanges(data[0], data[1]);
      });
    } else {
      this.stockexchangeService.getAllStockexchangesBaseData().subscribe((sbd: StockexchangeBaseData) => {
        this.stockexchangeMics = sbd.stockexchangeMics;
        this.prepareCountiesSelect(sbd.countries);
        this.prepareStockexchanges(sbd.stockexchanges, sbd.hasSecurity);
      });
    }
  }

  private prepareStockexchanges(stockexchanges: Stockexchange[], shs: StockexchangeHasSecurity[]): void {
    this.entityList = plainToInstance(Stockexchange, stockexchanges);
    shs.forEach(keyvalue => this.hasSecurityObject[keyvalue.id] = keyvalue.s);
    this.refreshSelectedEntity();
  }

  private prepareCountiesSelect(vkhso: ValueKeyHtmlSelectOptions[]): void {
    this.callParam.countriesAsHtmlOptions = vkhso;
    this.countriesAsKeyValue = StockexchangeHelper.transform(vkhso);
  }

  public override getEditMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = super.getEditMenuItems(this.selectedEntity);
    menuItems.push({separator: true});
    menuItems.push({
      label: 'WEBSITE', command: (e) => AppHelper.toExternalWebpage(this.selectedEntity.website, 'stockexchange'),
      disabled: !this.selectedEntity || !this.selectedEntity.website
    });
    return menuItems;
  }

  protected override prepareCallParam(entity: Stockexchange) {
    this.callParam.hasSecurity = entity && this.hasSecurityObject[this.getId(entity)] !== 0;
    this.callParam.stockexchange = entity;
    this.callParam.stockexchangeMics = this.stockexchangeMics;
    this.callParam.existingMic = new Set(this.entityList.map(se => se.mic));
  }
}
