import {Component, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, FilterService} from 'primeng/api';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {StandingOrderService} from '../service/standing.order.service';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {StandingOrderSecurityEditComponent} from './standing-order-security-edit.component';
import {StandingOrderFailureTableComponent} from './standing-order-failure-table.component';
import {StandingOrderTableBase} from './standing-order-table-base';
import {TransactionType} from '../../shared/types/transaction.type';

/**
 * Table component displaying security standing orders (dtype='S') for the current tenant.
 * Shown as a tab in TenantTabMenuComponent.
 */
@Component({
  selector: 'standing-order-security-table',
  template: `
    <div class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <configurable-table
        [data]="standingOrders"
        [fields]="fields"
        [dataKey]="'idStandingOrder'"
        [(selection)]="selectedEntity"
        (rowSelect)="onRowSelect($event)"
        (rowUnselect)="onRowUnselect($event)"
        [contextMenuItems]="contextMenuItems"
        [contextMenuAppendTo]="'body'"
        [showContextMenu]="isActivated()"
        [expandable]="true"
        [canExpandFn]="canExpandRow.bind(this)"
        [expandedRowTemplate]="failureExpansion"
        (rowExpand)="onRowExpand($event)"
        [valueGetterFn]="getValueByPath.bind(this)"
        [baseLocale]="baseLocale">
      </configurable-table>
    </div>

    <ng-template #failureExpansion let-so>
      <standing-order-failure-table [failures]="getFailuresForOrder(so)">
      </standing-order-failure-table>
    </ng-template>

    @if (visibleEditDialog) {
      <standing-order-security-edit
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        (closeDialog)="onDialogClose($event)">
      </standing-order-security-edit>
    }
  `,
  standalone: true,
  imports: [CommonModule, TranslateModule, ConfigurableTableComponent,
    StandingOrderSecurityEditComponent, StandingOrderFailureTableComponent]
})
export class StandingOrderSecurityTableComponent extends StandingOrderTableBase implements OnInit {

  constructor(
    activePanelService: ActivePanelService,
    standingOrderService: StandingOrderService,
    messageToastService: MessageToastService,
    confirmationService: ConfirmationService,
    filterService: FilterService,
    usersettingsService: UserSettingsService,
    translateService: TranslateService,
    gps: GlobalparameterService
  ) {
    super(activePanelService, standingOrderService, messageToastService, confirmationService,
      filterService, usersettingsService, translateService, gps);
  }

  ngOnInit(): void {
    this.loadData();
  }

  protected override addSubtypeColumns(): void {
    this.addColumn(DataType.String, 'security.name', 'SECURITY', true, false);
    this.addColumn(DataType.String, 'cashaccount.name', 'CASHACCOUNT', true, false);
    this.addColumnFeqH(DataType.Numeric, 'units', true, false);
    this.addColumnFeqH(DataType.Numeric, 'investAmount', true, false);
  }

  protected override getDtype(): string {
    return 'S';
  }

  protected override getAllowedTransactionTypes(): TransactionType[] {
    return [TransactionType.ACCUMULATE, TransactionType.REDUCE];
  }
}
