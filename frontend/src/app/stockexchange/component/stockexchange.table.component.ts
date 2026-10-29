import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { StockexchangeService } from '../service/stockexchange.service';
import { Stockexchange } from '../../entities/stockexchange';
import { forkJoin, Subscription } from 'rxjs';
import { HelpIds } from '../../lib/help/help.ids';
import { plainToInstance } from 'class-transformer';
import { StockexchangeCallParam } from './stockexchange.call.param';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { ColumnConfig } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';
import { TableCrudSupportMenuSecurity } from '../../lib/datashowbase/table.crud.support.menu.security';
import { StockexchangeBaseData, StockexchangeMic } from '../model/stockexchange.base.data';
import { StockexchangeHasSecurity } from '../model/stockexchange.has.security';
import { StockexchangeHelper } from './stockexchange.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { StockexchangeEditComponent } from './stockexchange-edit.component';
import { TradingCalendarStockexchangeComponent } from './trading-calendar-stockexchange.component';

/**
 * Shows stock exchanges in a table
 */
@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollHeight]="'flex'"
      [scrollable]="true"
      [stripedRows]="true"
      [showGridlines]="true"
      [expandable]="true"
      [canExpandFn]="canExpandRow"
      [expandedRowTemplate]="expandedContent"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems()"
      [contextMenuAppendTo]="'body'"
      [ownerHighlightFn]="ownerHighlight"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>

      <!-- Country flag cell (templateName: 'icon'); tooltip shows the technical id_stockexchange -->
      <ng-template #iconCell let-row>
        <img
          src="assets/icons/flag_placeholder.png"
          [class]="'fi fi-' + (row.countryCode ? row.countryCode.toLowerCase() : 'xx')"
          style="width: 20px"
          [pTooltip]="row.idStockexchange"
          tooltipPosition="top" />
      </ng-template>
    </configurable-table>

    <!-- Expanded row content template -->
    <ng-template #expandedContent let-stockexchange>
      <trading-calendar-stockexchange
        [stockexchange]="stockexchange"
        [sourceCopyStockexchanges]="getCopySourceStockexchanges(stockexchange.idStockexchange)" />
    </ng-template>

    @if (visibleDialog()) {
      <stockexchange-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `,
  providers: [DialogService],
  imports: [
    ConfigurableTableComponent,
    StockexchangeEditComponent,
    TradingCalendarStockexchangeComponent,
    TooltipModule,
    TranslateModule
  ]
})
export class StockexchangeTableComponent extends TableCrudSupportMenuSecurity<Stockexchange> {
  private stockexchangeService: StockexchangeService = inject(StockexchangeService);

  callParam: StockexchangeCallParam = new StockexchangeCallParam();
  readonly ownerHighlight = (entity: Stockexchange, field: ColumnConfig): boolean =>
    this.isNotSingleModeAndOwner(entity, field);
  readonly canExpandRow = (stockexchange: Stockexchange): boolean => !stockexchange.noMarketValue;

  private countriesAsKeyValue: { [cc: string]: string } = {};
  private stockexchangeMics: StockexchangeMic[];
  private readDataSub?: Subscription;

  constructor() {
    super(AppSettings.STOCKEXCHANGE, inject(StockexchangeService));

    this.addColumnFeqH(DataType.String, 'mic', true, false, {
      width: 40,
      templateName: BaseSettings.OWNER_TEMPLATE
    });
    this.addColumnFeqH(DataType.String, 'name', true, false, {
      width: 180
    });
    this.addColumn(DataType.String, 'countryFlag', 'COUNTRY_FLAG', true, false, {
      width: 40,
      templateName: 'icon',
      fieldValueFN: this.getDisplayNameForCounty.bind(this)
    });
    this.addColumnFeqH(DataType.String, 'countryCode', true, false, {
      fieldValueFN: this.getDisplayNameForCounty.bind(this)
    });
    this.addColumnFeqH(DataType.Boolean, 'secondaryMarket', true, false, {
      templateName: 'check'
    });
    this.addColumnFeqH(DataType.Boolean, 'noMarketValue', true, false, {
      templateName: 'check'
    });
    this.addColumnFeqH(DataType.TimeString, 'timeOpen', true, false);
    this.addColumnFeqH(DataType.TimeString, 'timeClose', true, false);
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {
      width: 120
    });
    this.addColumn(DataType.String, 'nameIndexUpdCalendar', 'ID_INDEX_UPD_CALENDAR', true, false, { width: 180 });
    this.addColumn(DataType.String, 'nameTradingCalendarRuleSet', 'ID_TRADING_CALENDAR_RULE_SET', true, false, {
      width: 180
    });
    this.addColumnFeqH(DataType.DateString, 'maxCalendarUpdDate', true, false);
    this.addColumnFeqH(DataType.TimeString, 'localTime', true, false);
    this.addColumnFeqH(DataType.DateTimeString, 'lastDirectPriceUpdate', true, false, { width: 100 });
    this.multiSortMeta.push({ field: 'name', order: 1 });
    this.prepareTableAndTranslate();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_STOCKEXCHANGE;
  }

  getDisplayNameForCounty(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.countriesAsKeyValue[dataobject['countryCode']];
  }

  override onComponentClick(event: PanelClickEvent): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedEntity());
    }
  }

  getCopySourceStockexchanges(targetIdStockexchange: number): ValueKeyHtmlSelectOptions[] {
    return this.entityList()
      .filter(
        (stockexchange): stockexchange is Stockexchange & { idStockexchange: number; name: string } =>
          !stockexchange.noMarketValue &&
          targetIdStockexchange !== stockexchange.idStockexchange &&
          stockexchange.idStockexchange != null &&
          stockexchange.name != null
      )
      .map((stockexchange) => new ValueKeyHtmlSelectOptions(stockexchange.idStockexchange, stockexchange.name));
  }

  protected override readData(): void {
    this.readDataSub?.unsubscribe();
    if (this.callParam.countriesAsHtmlOptions) {
      this.readDataSub = forkJoin([
        this.stockexchangeService.getAllStockexchanges(true),
        this.stockexchangeService.stockexchangesHasSecurity()
      ])
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((data) => {
          this.prepareStockexchanges(data[0], data[1]);
        });
    } else {
      this.readDataSub = this.stockexchangeService
        .getAllStockexchangesBaseData()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe((sbd: StockexchangeBaseData) => {
          this.stockexchangeMics = sbd.stockexchangeMics;
          this.prepareCountiesSelect(sbd.countries);
          this.prepareStockexchanges(sbd.stockexchanges, sbd.hasSecurity);
        });
    }
  }

  private prepareStockexchanges(stockexchanges: Stockexchange[], shs: StockexchangeHasSecurity[]): void {
    this.hasSecurityObject = Object.fromEntries(shs.map((row) => [row.id, row.s]));
    this.entityList.set(plainToInstance(Stockexchange, stockexchanges));
    this.refreshSelectedEntity();
  }

  private prepareCountiesSelect(vkhso: ValueKeyHtmlSelectOptions[]): void {
    this.callParam.countriesAsHtmlOptions = vkhso;
    this.countriesAsKeyValue = StockexchangeHelper.transform(vkhso);
  }

  protected override getEditMenuItems(entity: Stockexchange): MenuItem[] {
    const menuItems = super.getEditMenuItems(entity);
    menuItems.push({ separator: true });
    menuItems.push({
      label: 'WEBSITE',
      command: () => {
        if (entity?.website) AppHelper.toExternalWebpage(entity.website, 'stockexchange');
      },
      disabled: !entity?.website
    });
    return menuItems;
  }

  protected override prepareCallParam(entity: Stockexchange) {
    this.callParam.hasSecurity = entity && this.hasSecurityObject[this.getId(entity)] !== 0;
    this.callParam.stockexchange = entity;
    this.callParam.stockexchangeMics = this.stockexchangeMics;
    this.callParam.existingMic = new Set(
      this.entityList()
        .map((se) => se.mic)
        .filter((mic) => mic != null)
    );
  }
}
