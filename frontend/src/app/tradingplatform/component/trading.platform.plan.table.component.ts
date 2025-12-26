import {Component, OnDestroy} from '@angular/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {TradingPlatformPlan} from '../../entities/tradingplatformplan';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {TradingPlatformPlanService} from '../service/trading.platform.plan.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {HelpIds} from '../../lib/help/help.ids';
import {ImportTransactionPlatformService} from '../../imptranstemplate/service/import.transaction.platform.service';
import {DialogService} from 'primeng/dynamicdialog';
import {ConfirmationService, FilterService} from 'primeng/api';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {AppSettings} from '../../shared/app.settings';
import {BaseSettings} from '../../lib/base.settings';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {TradingPlatformPlanEditComponent} from './trading-platform-plan-edit.component';

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
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{entityNameUpper | translate}}</h4>

    </configurable-table>

    @if (visibleDialog) {
      <trading-platform-plan-edit [visibleDialog]="visibleDialog"
                                  [callParam]="callParam"
                                  (closeDialog)="handleCloseDialog($event)">
      </trading-platform-plan-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, ConfigurableTableComponent, TradingPlatformPlanEditComponent]
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
      {headerSuffix: 'EN', templateName: BaseSettings.OWNER_TEMPLATE});
    this.addColumn(DataType.String, 'platformPlanNameNLS.map.de', 'PLATFORM_PLAN_NAME', true, false,
      {headerSuffix: 'DE'});
    this.addColumnFeqH(DataType.String, 'transactionFeePlan', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumn(DataType.String, 'importTransactionPlatform.name', 'IMPORT_TRANSACTION_PLATFORM', true, false);

    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: TradingPlatformPlan) {
    this.callParam = entity;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN;
  }

  protected override readData(): void {
    this.tradingPlatformPlanService.getAllTradingPlatform().subscribe(result => {
      this.createTranslatedValueStoreAndFilterField(result);
      this.entityList = result;
      this.refreshSelectedEntity();
    });
  }

  protected override beforeDelete(entity: TradingPlatformPlan): TradingPlatformPlan {
    const tradingPlatformPlan = new TradingPlatformPlan();
    return Object.assign(tradingPlatformPlan, entity);
  }

  protected override hasRightsForDeleteEntity(entity: TradingPlatformPlan): boolean {
    return true;
  }

}
