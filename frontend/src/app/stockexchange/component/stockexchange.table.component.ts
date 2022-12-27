import {Component, OnDestroy} from '@angular/core';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {StockexchangeService} from '../service/stockexchange.service';
import {Stockexchange} from '../../entities/stockexchange';
import {combineLatest} from 'rxjs';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {HelpIds} from '../../shared/help/help.ids';
import {plainToClass} from 'class-transformer';
import {StockexchangeCallParam} from './stockexchange.call.param';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {DialogService} from 'primeng/dynamicdialog';
import {ColumnConfig} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {TableCrudSupportMenuSecurity} from '../../shared/datashowbase/table.crud.support.menu.security';
import {StockexchangeBaseData, StockexchangeMic} from '../model/stockexchange.base.data';
import {StockexchangeHasSecurity} from '../model/stockexchange.has.security';
import {BusinessHelper} from '../../shared/helper/business.helper';
import {StockexchangeHelper} from './stockexchange.helper';

/**
 * Shows stock exchanges in a table
 */
@Component({
  template: `
    <div class="data-container-full" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               (sortFunction)="customSort($event)" [customSort]="true" sortMode="multiple"
               responsiveLayout="scroll" scrollHeight="flex" [scrollable]="true"
               [multiSortMeta]="multiSortMeta"
               [dataKey]="entityKeyName" styleClass="p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{entityNameUpper | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="max-width:24px"></th>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
                [style.min-width.px]="field.width">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td style="max-width:24px">
              <a *ngIf="!el.noMarketValue" href="#" [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            <td *ngFor="let field of fields" [style.min-width.px]="field.width">
              <ng-container [ngSwitch]="field.templateName">
                <ng-container *ngSwitchCase="'owner'">
                  <span [style]='isNotSingleModeAndOwner(field, el)? "font-weight:500": null'>
                   {{getValueByPath(el, field)}}</span>
                </ng-container>
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
        <ng-template pTemplate="rowexpansion" let-stockexchange let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <trading-calendar-stockexchange [stockexchange]="stockexchange"
                                              [sourceCopyStockexchanges]="getCopySourceStockexchanges(stockexchange.idStockexchange)">
              </trading-calendar-stockexchange>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems && isActivated()" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>

    <stockexchange-edit *ngIf="visibleDialog" [visibleDialog]="visibleDialog"
                        [callParam]="callParam"
                        (closeDialog)="handleCloseDialog($event)">
    </stockexchange-edit>
  `,
  providers: [DialogService]
})
export class StockexchangeTableComponent extends TableCrudSupportMenuSecurity<Stockexchange> implements OnDestroy {

  callParam: StockexchangeCallParam = new StockexchangeCallParam();

  private countriesAsHtmlOptions: ValueKeyHtmlSelectOptions[];
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
      width: 180,});
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

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_BASEDATA_STOCKEXCHANGE;
  }

  getDisplayNameForCounty(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.countriesAsKeyValue[dataobject['countryCode']];
  }

  onComponentClick(event): void {
    if (!event[this.consumedGT]) {
      this.resetMenu(this.selectedEntity);
    }
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  getCopySourceStockexchanges(targetIdStockexchange: number): ValueKeyHtmlSelectOptions[] {
    const valueKeyHtmlSelectOptions: ValueKeyHtmlSelectOptions[] = [];
    return this.entityList.filter(stockexhange => !stockexhange.noMarketValue
      && targetIdStockexchange !== stockexhange.idStockexchange)
      .map(stockexchange => new ValueKeyHtmlSelectOptions(stockexchange.idStockexchange, stockexchange.name));
  }

  protected override readData(): void {
    if (this.callParam.countriesAsHtmlOptions) {
      combineLatest([this.stockexchangeService.getAllStockexchanges(true),
        this.stockexchangeService.stockexchangesHasSecurity()]).subscribe(data => {
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
    this.entityList = plainToClass(Stockexchange, stockexchanges);
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
      label: 'WEBSITE', command: (e) => BusinessHelper.toExternalWebpage(this.selectedEntity.website, 'stockexchange'),
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
