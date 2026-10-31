import { requireInitialized } from '../../lib/helper/required-state';
import { DestroyRef, Directive, inject, linkedSignal, OnDestroy, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ConfirmationService, MenuItem } from '@openng/optimus-ui/api';
import moment from 'moment';
import { forkJoin, Subscription } from 'rxjs';
import { StandingOrder, StandingOrderFailure } from '../../entities/standing.order';
import { BaseSettings } from '../../lib/base.settings';
import { TranslateValue } from '../../lib/datashowbase/column.config';
import { TableConfigBase } from '../../lib/datashowbase/table.config.base';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AppHelpIds } from '../../shared/help/help.ids';
import { TransactionType } from '../../shared/types/transaction.type';
import { StandingOrderCallParam } from '../model/standing.order.call.param';
import { StandingOrderService } from '../service/standing.order.service';

/**
 * Abstract base class for standing order table components. Provides shared column definitions,
 * data loading with dtype filtering, translation mapping for enum fields, context menu with
 * CRUD operations, and IGlobalMenuAttach integration. Subclasses add their subtype-specific
 * columns and specify the dtype discriminator and transaction type mapping.
 */
@Directive()
export abstract class StandingOrderTableBase extends TableConfigBase implements OnDestroy, IGlobalMenuAttach {
  protected activePanelService: ActivePanelService = inject(ActivePanelService);
  protected standingOrderService: StandingOrderService = inject(StandingOrderService);
  protected messageToastService: MessageToastService = inject(MessageToastService);
  protected confirmationService: ConfirmationService = inject(ConfirmationService);

  protected readonly destroyRef = inject(DestroyRef);
  readonly standingOrders = signal<StandingOrder[]>([]);
  readonly selectedEntity = linkedSignal<StandingOrder[], StandingOrder | null>({
    source: this.standingOrders,
    computation: (rows, previous) =>
      rows.find((row) => row.idStandingOrder === previous?.value?.idStandingOrder) ?? null
  });
  readonly contextMenuItems = signal<MenuItem[]>([]);
  readonly visibleEditDialog = signal(false);
  private initializedCallParam?: StandingOrderCallParam;

  get callParam(): StandingOrderCallParam {
    return requireInitialized(this.initializedCallParam, 'StandingOrderTableBase.callParam');
  }

  set callParam(value: StandingOrderCallParam) {
    this.initializedCallParam = value;
  }
  private readonly failuresMap = signal(new Map<number, StandingOrderFailure[]>());
  private dataSubscriptions = new Subscription();
  private menuSubscription?: Subscription;
  protected simulationTenant = false;
  private showInactive = false;

  constructor() {
    super();
    this.addColumn(DataType.NumericInteger, 'idStandingOrder', 'ID', true, false);
    this.addColumnFeqH(DataType.String, 'transactionType', true, false, { translateValues: TranslateValue.NORMAL });
    this.addSubtypeColumns();
    this.addColumnFeqH(DataType.String, 'repeatUnit', true, false, { translateValues: TranslateValue.NORMAL });
    this.addColumnFeqH(DataType.NumericInteger, 'repeatInterval', true, false);
    this.addColumnFeqH(DataType.DateString, 'nextExecutionDate', true, false);
    this.addColumnFeqH(DataType.DateString, 'validFrom', true, false);
    this.addColumnFeqH(DataType.DateString, 'validTo', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'transactionCount', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'failureCount', true, false);
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

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return AppHelpIds.HELP_STANDING_ORDER;
  }

  onComponentClick(event: any): void {
    if (!event[this.consumedGT]) {
      this.resetMenu();
    }
  }

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.selectedEntity.set(null);
    this.resetMenu();
  }

  onDialogClose(processedActionData: ProcessedActionData): void {
    this.visibleEditDialog.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.loadData();
    }
  }

  ngOnDestroy(): void {
    this.dataSubscriptions.unsubscribe();
    this.menuSubscription?.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }

  readonly canExpandRow = (so: StandingOrder): boolean => {
    return (so.transactionCount ?? 0) > 0 || (so.failureCount ?? 0) > 0;
  };

  onRowExpand(event: { data: StandingOrder }): void {
    const so = event.data;
    const id = so.idStandingOrder;
    if (id != null && (so.failureCount ?? 0) > 0 && !this.failuresMap().has(id)) {
      this.loadFailures(so);
    }
  }

  onTransactionChanged(processedActionData: ProcessedActionData): void {
    this.loadData();
  }

  getFailuresForOrder(so: StandingOrder): StandingOrderFailure[] {
    const id = so.idStandingOrder;
    return id == null ? [] : (this.failuresMap().get(id) ?? []);
  }

  protected loadData(): void {
    const previousFailureIds = new Set(this.failuresMap().keys());
    this.dataSubscriptions.unsubscribe();
    this.dataSubscriptions = new Subscription();
    this.failuresMap.set(new Map());
    this.dataSubscriptions.add(
      this.standingOrderService.getAllForTenant().subscribe((all: StandingOrder[]) => {
        const today = moment().format(BaseSettings.FORMAT_DATE_SHORT_NATIVE);
        const orders = all.filter(
          (so) =>
            so.dtype === this.getDtype() &&
            (this.simulationTenant ||
              this.showInactive ||
              moment(so.validTo).format(BaseSettings.FORMAT_DATE_SHORT_NATIVE) >= today)
        );
        this.prepareTableAndTranslate();
        this.createTranslatedValueStore(orders);
        this.standingOrders.set(orders);
        if (this.isActivated()) this.resetMenu();
        orders
          .filter(
            (so) =>
              so.idStandingOrder != null && previousFailureIds.has(so.idStandingOrder) && (so.failureCount ?? 0) > 0
          )
          .forEach((so) => {
            this.loadFailures(so);
          });
      })
    );
  }

  private loadFailures(so: StandingOrder): void {
    const id = so.idStandingOrder;
    if (id == null) return;
    this.dataSubscriptions.add(
      this.standingOrderService.getFailures(id).subscribe((failures) => {
        this.failuresMap.update((current) => new Map(current).set(id, failures));
      })
    );
  }

  protected initializeData(): void {
    this.standingOrderService
      .getCapabilities()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((capabilities) => {
        this.simulationTenant = capabilities.simulationTenant;
        this.loadData();
      });
  }

  protected handleCreate(): void {
    this.callParam = new StandingOrderCallParam(null, null, this.simulationTenant);
    this.visibleEditDialog.set(true);
  }

  protected handleEdit(so: StandingOrder): void {
    this.callParam = new StandingOrderCallParam(so, null, this.simulationTenant);
    this.visibleEditDialog.set(true);
  }

  protected handleDelete(so: StandingOrder): void {
    const id = so.idStandingOrder;
    if (id == null) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|STANDING_ORDER',
      () => {
        this.standingOrderService
          .deleteEntity(id)
          .pipe(takeUntilDestroyed(this.destroyRef))
          .subscribe(() => {
            this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_DELETE_RECORD', {
              i18nRecord: 'STANDING_ORDER'
            });
            this.loadData();
          });
      }
    );
  }

  private resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = forkJoin({
      showMenu: TranslateHelper.translateMenuItems$(this.getMenuShowOptions(), this.translateService),
      editMenu: TranslateHelper.translateMenuItems$(this.getEditMenu(), this.translateService)
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((menus) => {
        this.contextMenuItems.set(menus.editMenu);
        this.activePanelService.activatePanel(this, menus);
      });
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'CREATE|STANDING_ORDER',
      command: () => this.handleCreate()
    });
    const selected = this.selectedEntity();
    if (selected) {
      menuItems.push({
        label: 'EDIT_RECORD|STANDING_ORDER',
        command: () => this.handleEdit(selected)
      });
      if (this.simulationTenant || !selected.hasTransactions) {
        menuItems.push({
          label: 'DELETE_RECORD|STANDING_ORDER',
          command: () => this.handleDelete(selected)
        });
      }
    }
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
        this.resetMenu();
        this.loadData();
      }
    });
    return menuItems;
  }
}
