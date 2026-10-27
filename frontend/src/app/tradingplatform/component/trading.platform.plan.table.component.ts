import { Component, Injector, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { TableCrudSupportMenu } from '../../lib/datashowbase/table.crud.support.menu';
import { TradingPlatformPlan } from '../../entities/tradingplatformplan';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { TradingPlatformPlanService } from '../service/trading.platform.plan.service';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { HelpIds } from '../../lib/help/help.ids';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { ColumnConfig, TranslateValue } from '../../lib/datashowbase/column.config';
import { AppSettings } from '../../shared/app.settings';
import { BaseSettings } from '../../lib/base.settings';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { TradingPlatformPlanEditComponent } from './trading-platform-plan-edit.component';
import { FeeModelEditComponent } from './fee-model-edit.component';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';

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
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="isActivated()"
      [contextMenuItems]="contextMenuItems()"
      [ownerHighlightFn]="ownerHighlight"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>
    </configurable-table>

    @if (visibleDialog()) {
      <trading-platform-plan-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)">
      </trading-platform-plan-edit>
    }

    @if (visibleFeeModelDialog()) {
      <fee-model-edit
        [visibleDialog]="visibleFeeModelDialog()"
        [callParam]="feeModelCallParam"
        (closeDialog)="handleCloseFeeModelDialog($event)">
      </fee-model-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [TranslateModule, ConfigurableTableComponent, TradingPlatformPlanEditComponent, FeeModelEditComponent]
})
export class TradingPlatformPlanTableComponent extends TableCrudSupportMenu<TradingPlatformPlan> {
  callParam: TradingPlatformPlan;
  readonly visibleFeeModelDialog = signal(false);
  feeModelCallParam: TradingPlatformPlan;
  readonly ownerHighlight = (entity: TradingPlatformPlan, field: ColumnConfig): boolean =>
    this.isNotSingleModeAndOwner(entity, field);

  private readDataSubscription?: Subscription;

  constructor(
    private tradingPlatformPlanService: TradingPlatformPlanService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      AppSettings.TRADING_PLATFORM_PLAN,
      tradingPlatformPlanService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );

    this.addColumn(DataType.String, 'platformPlanNameNLS.map.en', 'PLATFORM_PLAN_NAME', true, false, {
      headerSuffix: 'EN',
      templateName: BaseSettings.OWNER_TEMPLATE
    });
    this.addColumn(DataType.String, 'platformPlanNameNLS.map.de', 'PLATFORM_PLAN_NAME', true, false, {
      headerSuffix: 'DE'
    });
    this.addColumnFeqH(DataType.String, 'transactionFeePlan', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.addColumn(DataType.String, 'importTransactionPlatform.name', 'IMPORT_TRANSACTION_PLATFORM', true, false);

    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: TradingPlatformPlan) {
    this.callParam = entity;
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_BASEDATA_TRADING_PLATFORM_PLAN;
  }

  protected override readData(): void {
    this.readDataSubscription?.unsubscribe();
    this.readDataSubscription = this.tradingPlatformPlanService
      .getAllTradingPlatform()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.createTranslatedValueStoreAndFilterField(result);
        this.entityList.set(result);
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

  protected override addCustomMenusToSelectedEntity(entity: TradingPlatformPlan, menuItems: MenuItem[]): void {
    menuItems.push({
      label: 'EDIT_FEE_MODEL' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.handleEditFeeModel(entity)
    });
  }

  private handleEditFeeModel(entity: TradingPlatformPlan): void {
    this.feeModelCallParam = entity;
    this.visibleFeeModelDialog.set(true);
  }

  handleCloseFeeModelDialog(processedActionData: ProcessedActionData): void {
    this.visibleFeeModelDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }
}
