import type { PanelClickEvent } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, Subscription, switchMap } from 'rxjs';
import { TreeTableConfigBase } from '../../lib/datashowbase/tree.table.config.base';
import { TranslatePipe } from '@ngx-translate/core';
import { SecurityService } from '../service/security.service';
import {
  HistoryquoteQualityGroup,
  HistoryquoteQualityGrouped,
  HistoryquoteQualityHead,
  HistoryquoteQualityIds
} from '../model/historyquote.quality.group';
import { MenuItem, SelectItem, TreeNode } from '@openng/optimus-ui/api';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ColumnGroupConfig } from '../../lib/datashowbase/column.config';
import { IGlobalMenuAttach } from '../../lib/mainmenubar/component/iglobal.menu.attach';
import { HelpIds } from '../../lib/help/help.ids';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { AppHelper } from '../../lib/helper/app.helper';
import { plainToInstance } from 'class-transformer';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { TimeSeriesQuotesService } from '../../historyquote/service/time.series.quotes.service';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import {
  SecurityHistoryquoteQualityTableComponent,
  SecurityIdWithCurrency
} from './security-historyquote-quality-table.component';

import { Panel } from '@openng/optimus-ui/panel';
import { Select } from '@openng/optimus-ui/select';
import { FormsModule } from '@angular/forms';
import { ConfigurableTreeTableComponent } from '../../lib/datashowbase/configurable-tree-table.component';
import { SharedModule } from '@openng/optimus-ui/api';

/**
 * Shows the quality of historical price data per stock exchange or data provider.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <p-panel>
        <p-header>
          <h4>{{ 'SECURITY_HISTORY_QUALITY' | translate }} ({{ 'LAST_UPDATE' | translate }}: {{ lastUpdate() }})</h4>
        </p-header>
        <label class="small-padding control-label" for="groupSelect">{{ 'MAIN_QUALITY_GROUP' | translate }}</label>
        <p-select id="groupSelect" [options]="groups" [(ngModel)]="selectedGroup" (onChange)="groupChanged($event)" />
      </p-panel>
      <configurable-tree-table
        [data]="qualityNode()"
        [fields]="fields"
        [fieldsRevision]="fieldsRevision()"
        dataKey="uniqueKey"
        sortField="name"
        [selection]="selectedNode()"
        (selectionChange)="selectedNode.set($event)"
        (nodeSelect)="nodeSelect($event)"
        [contextMenuItems]="contextMenuItems()"
        [showContextMenu]="!!contextMenuItems()"
        [valueGetterFn]="valueGetter"
        [footerTemplate]="qualityFooter" />
      <ng-template #qualityFooter>
        <tr>
          @for (field of fields; track field) {
            @if (field.visible) {
              <td
                class="row-total"
                [style.width.px]="field.width"
                [class.text-end]="
                  field.dataType === DataType.NumericInteger ||
                  field.dataType === DataType.Numeric ||
                  field.dataType === DataType.DateTimeNumeric
                ">
                {{ getValueColumnTotal(field, 0, historyquoteQualityHead(), null) }}
              </td>
            }
          }
        </tr>
      </ng-template>
      <security-historyquote-quality-table
        [historyquoteQualityIds]="historyquoteQualityIds() ?? undefined"
        [groupTitle]="groupTitle() ?? undefined"
        (changedIdSecurity)="handleChangedIdSecurity($event)" />
    </div>
  `,
  imports: [
    TranslatePipe,
    Panel,
    SharedModule,
    Select,
    FormsModule,
    ConfigurableTreeTableComponent,
    SecurityHistoryquoteQualityTableComponent
  ]
})
export class SecurityHistoryquoteQualityTreetableComponent
  extends TreeTableConfigBase
  implements OnInit, IGlobalMenuAttach
{
  private timeSeriesQuotesService: TimeSeriesQuotesService = inject(TimeSeriesQuotesService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private securityService: SecurityService = inject(SecurityService);

  readonly historyquoteQualityIds = signal<HistoryquoteQualityIds | null>(null);
  readonly groupTitle = signal<string | null>(null);
  readonly contextMenuItems = signal<MenuItem[] | null>(null);
  securityIdWithCurrency: SecurityIdWithCurrency | null;

  readonly qualityNode = signal<TreeNode[]>([]);
  readonly historyquoteQualityHead = signal<HistoryquoteQualityHead | null>(null);
  readonly selectedNode = signal<TreeNode | null>(null);
  selectedGroup: string;
  readonly lastUpdate = signal<string | null>(null);

  groups: SelectItem[] = [];

  private readonly destroyRef = inject(DestroyRef);
  /** A newer group selection supersedes the request of the previous one. */
  private readonly groupRequests = new Subject<string>();
  private menuSubscription: Subscription;

  constructor() {
    super();
    this.addColumnFeqH(DataType.String, 'name', true, false, {
      width: 250,
      columnGroupConfigs: [new ColumnGroupConfig(null, 'GRAND_TOTAL')]
    });

    this.addColumnFeqH(DataType.NumericInteger, 'numberOfSecurities', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'activeNowSecurities', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'connectorCreated', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'manualImported', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'filledLinear', true, false);
    this.addColumnFeqH(DataType.Numeric, 'qualityPercentage', true, false, {
      headerSuffix: '%'
    });
    this.addColumnFeqH(DataType.Numeric, 'ohlPercentage', true, false, {
      headerSuffix: '%'
    });
    this.setSameFieldNameForGroupField(1);
    this.translateHeadersAndColumns();

    SelectOptionsHelper.createSelectItemForEnum(this.translateService, HistoryquoteQualityGrouped, this.groups);
    this.groupRequests
      .pipe(
        switchMap((grouped) => this.securityService.getHistoryquoteQualityHead(grouped)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((hqg: HistoryquoteQualityHead) => this.showData(hqg));
    this.destroyRef.onDestroy(() => this.menuSubscription?.unsubscribe());
  }

  ngOnInit(): void {
    this.selectedGroup = HistoryquoteQualityGrouped[HistoryquoteQualityGrouped.STOCKEXCHANGE_GROUPED];
    this.readData(this.selectedGroup);
  }

  readData(historyquoteQualityGrouped: string): void {
    this.groupRequests.next(historyquoteQualityGrouped);
  }

  groupChanged(event: { value: string }): void {
    this.readData(event.value);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    this.resetMenu();
  }

  handleChangedIdSecurity(securityIdWithCurrency: SecurityIdWithCurrency | null): void {
    this.securityIdWithCurrency = securityIdWithCurrency;
    this.resetMenu();
  }

  hideContextMenu(): void {}

  callMeDeactivate(): void {}

  getHelpContextId(): string {
    return HelpIds.HELP_HISTORYQUOTE_QUALITY;
  }

  nodeSelect(event: {
    node: { data: HistoryquoteQualityGroup; parent: { parent: { data: { name: string } }; data: { name: string } } };
  }): void {
    const hqg: HistoryquoteQualityGroup = event.node.data;
    if (hqg.categoryType !== null) {
      this.groupTitle.set(event.node.parent.parent.data.name + ' / ' + event.node.parent.data.name + ' / ' + hqg.name);
      this.historyquoteQualityIds.set(
        plainToInstance(HistoryquoteQualityIds, event.node.data, {
          excludeExtraneousValues: true
        }) as HistoryquoteQualityIds
      );
    }
  }

  /** Publishes the translated show menu of the selected security as a new array, or no menu without a selection. */
  protected resetMenu(): void {
    this.menuSubscription?.unsubscribe();
    if (this.securityIdWithCurrency) {
      this.menuSubscription = TranslateHelper.translateMenuItems$(
        this.timeSeriesQuotesService.getMenuItems(
          this.securityIdWithCurrency.idSecurity,
          this.securityIdWithCurrency.currency,
          false
        ),
        this.translateService
      ).subscribe((menuItems) => this.publishMenu(menuItems));
    } else {
      this.publishMenu(null);
    }
  }

  private publishMenu(menuItems: MenuItem[] | null): void {
    this.contextMenuItems.set(menuItems);
    this.activePanelService.activatePanel(this, { showMenu: menuItems, editMenu: null });
  }

  private showData(hqg: HistoryquoteQualityHead): void {
    const tn: TreeNode[] = [];
    this.addTreeNode(tn, hqg);
    this.historyquoteQualityHead.set(hqg);
    this.lastUpdate.set(AppHelper.getDateByFormat(this.gps, hqg.lastUpdate));
    this.selectedNode.set(null);
    this.qualityNode.set(tn[0].children ?? []);
  }

  private addTreeNode(tn: TreeNode[], hqg: HistoryquoteQualityGroup, parentNode?: TreeNode): void {
    const children: TreeNode[] = [];
    const treeNode: TreeNode = {
      data: hqg,
      children,
      expanded: false,
      leaf: hqg.childrendHqg.length === 0,
      parent: parentNode
    };
    tn.push(treeNode);
    hqg.childrendHqg.forEach((historyquoteQualityGroup: HistoryquoteQualityGroup) =>
      this.addTreeNode(children, historyquoteQualityGroup, treeNode)
    );
  }
}
