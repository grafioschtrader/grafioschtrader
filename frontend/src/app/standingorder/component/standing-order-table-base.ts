import {Directive, OnDestroy} from '@angular/core';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TableConfigBase} from '../../lib/datashowbase/table.config.base';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {AppHelpIds} from '../../shared/help/help.ids';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {StandingOrder, StandingOrderFailure} from '../../entities/standing.order';
import {StandingOrderService} from '../service/standing.order.service';
import {StandingOrderCallParam} from '../model/standing.order.call.param';
import {TransactionType} from '../../shared/types/transaction.type';
import moment from 'moment';
import {BaseSettings} from '../../lib/base.settings';
import {FilterType} from '../../lib/datashowbase/filter.type';

/**
 * Abstract base class for standing order table components. Provides shared column definitions,
 * data loading with dtype filtering, translation mapping for enum fields, context menu with
 * CRUD operations, and IGlobalMenuAttach integration. Subclasses add their subtype-specific
 * columns and specify the dtype discriminator and transaction type mapping.
 */
@Directive()
export abstract class StandingOrderTableBase extends TableConfigBase implements OnDestroy, IGlobalMenuAttach {

  standingOrders: StandingOrder[] = [];
  selectedEntity: StandingOrder | null = null;
  contextMenuItems: MenuItem[] = [];
  visibleEditDialog = false;
  callParam: StandingOrderCallParam;
  failuresMap: Map<number, StandingOrderFailure[]> = new Map();
  private showInactive = false;

  protected constructor(
    protected activePanelService: ActivePanelService,
    protected standingOrderService: StandingOrderService,
    protected messageToastService: MessageToastService,
    protected confirmationService: ConfirmationService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(filterService, usersettingsService, translateService, gps);
    this.addColumn(DataType.NumericInteger, 'idStandingOrder', 'ID', true, false)
    this.addColumnFeqH(DataType.String, 'transactionType', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addSubtypeColumns();
    this.addColumnFeqH(DataType.String, 'repeatUnit', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.NumericInteger, 'repeatInterval', true, false);
    this.addColumnFeqH(DataType.DateString, 'nextExecutionDate', true, false);
    this.addColumnFeqH(DataType.DateString, 'validFrom', true, false);
    this.addColumnFeqH(DataType.DateString, 'validTo', true, false);
  }

  /** Subclasses add their specific columns (called between transactionType and repeatUnit). */
  protected abstract addSubtypeColumns(): void;

  /** Returns the dtype discriminator value to filter standing orders ('C' or 'S'). */
  protected abstract getDtype(): string;

  /** Returns the TransactionType enum values relevant to this subtype for reverse-mapping. */
  protected abstract getAllowedTransactionTypes(): TransactionType[];

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return AppHelpIds.HELP_STANDING_ORDER;
  }

  onComponentClick(event: any): void {
    this.resetMenu();
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.selectedEntity = null;
    this.resetMenu();
  }

  onDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleEditDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.loadData();
    }
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  canExpandRow(so: StandingOrder): boolean {
    return (so.failureCount ?? 0) > 0;
  }

  onRowExpand(event: { data: StandingOrder }): void {
    const so = event.data;
    if (!this.failuresMap.has(so.idStandingOrder)) {
      this.standingOrderService.getFailures(so.idStandingOrder).subscribe(failures => {
        this.failuresMap.set(so.idStandingOrder, failures);
      });
    }
  }

  getFailuresForOrder(so: StandingOrder): StandingOrderFailure[] {
    return this.failuresMap.get(so.idStandingOrder) ?? [];
  }

  protected loadData(): void {
    this.failuresMap.clear();
    this.standingOrderService.getAllForTenant().subscribe((all: StandingOrder[]) => {
      const today = moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
      this.standingOrders = all.filter(so => so.dtype === this.getDtype()
        && (this.showInactive || moment(so.validTo).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE) >= today));
      this.prepareTableAndTranslate();
      this.createTranslatedValueStore(this.standingOrders);
    });
  }

  protected handleCreate(): void {
    this.callParam = new StandingOrderCallParam(null, null);
    this.visibleEditDialog = true;
  }

  protected handleEdit(so: StandingOrder): void {
    this.callParam = new StandingOrderCallParam(so, null);
    this.visibleEditDialog = true;
  }

  protected handleDelete(so: StandingOrder): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|STANDING_ORDER', () => {
        this.standingOrderService.deleteEntity(so.idStandingOrder).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: 'STANDING_ORDER'});
          this.loadData();
        });
      });
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: this.getMenuShowOptions(),
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'CREATE|STANDING_ORDER',
      command: () => this.handleCreate()
    });
    if (this.selectedEntity) {
      menuItems.push({
        label: 'EDIT_RECORD|STANDING_ORDER',
        command: () => this.handleEdit(this.selectedEntity!)
      });
      if (!this.selectedEntity.hasTransactions) {
        menuItems.push({
          label: 'DELETE_RECORD|STANDING_ORDER',
          command: () => this.handleDelete(this.selectedEntity!)
        });
      }
    }
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  override getMenuShowOptions(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    const baseItems = super.getMenuShowOptions();
    if (baseItems) {
      menuItems.push(...baseItems);
    }
    menuItems.push({
      label: this.showInactive ? 'HIDE_INACTIVE_STANDING_ORDERS' : 'SHOW_INACTIVE_STANDING_ORDERS',
      command: () => {
        this.showInactive = !this.showInactive;
        this.loadData();
      }
    });
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

}
