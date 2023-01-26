import {Component, OnDestroy} from '@angular/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {TradingPlatformPlanService} from '../service/trading.platform.plan.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {HelpIds} from '../../shared/help/help.ids';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               responsiveLayout="scroll"
               [dataKey]="entityKeyName" styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{entityNameUpper | translate}}</h4>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td *ngFor="let field of fields">
              <ng-container [ngSwitch]="field.templateName">
                <ng-container *ngSwitchCase="'owner'">
                  <span [style]='isNotSingleModeAndOwner(field, el)? "font-weight:500": null'>
                   {{getValueByPath(el, field)}}</span>
                </ng-container>
                <ng-container *ngSwitchDefault>
                  {{getValueByPath(el, field)}}
                </ng-container>
              </ng-container>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>
    <trading-platform-plan-edit *ngIf="visibleDialog"
                                [visibleDialog]="visibleDialog"
                                [callParam]="callParam"
                                (closeDialog)="handleCloseDialog($event)">
    </trading-platform-plan-edit>
  `,
  providers: [DialogService]
})
export class TradingPlatformPlanTableComponent extends TableCrudSupportMenu<TradingPlatformPlan> implements OnDestroy {

  callParam: TradingPlatformPlan;
  private platformTransactionImportKV: { [id: string]: string };

  constructor(private importTransactionPlatformService: ImportTransactionPlatformService,
              private tradingPlatformPlanService: TradingPlatformPlanService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              dialogService: DialogService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(AppSettings.TRADING_PLATFORM_PLAN, tradingPlatformPlanService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService);

    this.addColumn(DataType.String, 'platformPlanNameNLS.map.en', 'PLATFORM_PLAN_NAME', true, false,
      {headerSuffix: 'EN', templateName: AppSettings.OWNER_TEMPLATE});
    this.addColumn(DataType.String, 'platformPlanNameNLS.map.de', 'PLATFORM_PLAN_NAME', true, false,
      {headerSuffix: 'DE'});
    this.addColumnFeqH(DataType.String, 'transactionFeePlan', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'importTransactionPlatform.name', 'IMPORTTRANSACTIONGROUP', true, false);

    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: TradingPlatformPlan) {
    this.callParam = entity;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getHelpContextId(): HelpIds {
    return HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN;
  }

  protected override readData(): void {
    this.tradingPlatformPlanService.getAllTradingPlatform().subscribe(result => {
      this.createTranslatedValueStoreAndFilterField(result);
      this.entityList = result;
      this.refreshSelectedEntity();
    });
  }

  protected beforeDelete(entity: TradingPlatformPlan): TradingPlatformPlan {
    const tradingPlatformPlan = new TradingPlatformPlan();
    return Object.assign(tradingPlatformPlan, entity);
  }

  protected override hasRightsForDeleteEntity(entity: TradingPlatformPlan): boolean {
    return true;
  }

}
