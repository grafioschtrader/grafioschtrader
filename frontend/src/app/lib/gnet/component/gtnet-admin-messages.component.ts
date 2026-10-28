import { afterNextRender, Component, computed, signal, viewChildren, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { FormsModule } from '@angular/forms';
import { TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { GTNet, GTNetWithMessages } from '../model/gtnet';
import { GTNetMessage, GTNetMessageCodeType, MsgCallParam, SendReceivedType } from '../model/gtnet.message';
import { GTNetService } from '../service/gtnet.service';
import { MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateModule } from '@ngx-translate/core';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig } from '../../datashowbase/column.config';
import { HelpIds } from '../../help/help.ids';
import { GTNetMessageTreeTableComponent } from './gtnet-message-treetable.component';
import { combineLatest } from 'rxjs';
import { GTNetMessageService } from '../service/gtnet.message.service';
import { GTNetProtocolService } from '../service/gtnet.protocol.service';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { BaseSettings } from '../../base.settings';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { GTNetMessageEditComponent } from './gtnet-message-edit.component';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { ProcessedAction } from '../../types/processed.action';
import { ProcessedActionData } from '../../types/processed.action.data';

/**
 * Component for displaying admin-only messages in a dedicated tab.
 * Shows GTNet domains in read-only mode with expandable admin messages.
 * Messages are filtered to only show visibility=ADMIN_ONLY.
 */
@Component({
  selector: 'gtnet-admin-messages',
  imports: [
    FormsModule,
    TranslateModule,
    ConfigurableTableComponent,
    ContextMenuModule,
    TooltipModule,
    GTNetMessageEditComponent,
    GTNetMessageTreeTableComponent
  ],
  template: `
    <configurable-table
      [data]="filteredGtNetList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idGtNet'"
      [selectionMode]="'multiple'"
      [(selection)]="selectedEntities"
      [contextMenuItems]="contextMenuItems()"
      [showContextMenu]="isUserAdmin"
      [containerClass]="{
        'data-container-full': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [expandable]="true"
      [expandedRowTemplate]="expandedRow"
      [canExpandFn]="canExpand"
      [ownerHighlightFn]="isMyEntry"
      [valueGetterFn]="valueGetter"
      [contextMenuAppendTo]="'body'"
      (componentClick)="onComponentClick($event)"
      (rowExpand)="onRowExpand($event)">
      <h4 caption>{{ 'GT_NET_ADMIN_MESSAGES' | translate }}</h4>
    </configurable-table>

    <ng-template #expandedRow let-row>
      @if (gtNetMessageMap()[row.idGtNet]?.length) {
        <gtnet-message-treetable
          [gtNetMessages]="gtNetMessageMap()[row.idGtNet]"
          [incomingPendingIds]="getIncomingPendingIds(row.idGtNet)"
          [outgoingPendingIds]="getOutgoingPendingIds(row.idGtNet)"
          [formDefinitions]="formDefinitions"
          [showFilter]="false"
          (dataChanged)="onTreeTableDataChanged($event)" />
      } @else {
        <div style="padding: 1rem; text-align: center; color: #888;">
          {{ 'NO_DATA_FOUND' | translate }}
        </div>
      }
    </ng-template>

    @if (visibleDialogMsg()) {
      <gtnet-message-edit
        [visibleDialog]="visibleDialogMsg()"
        [msgCallParam]="msgCallParam"
        (closeDialog)="handleCloseDialogMsg($event)" />
    }
  `,
  providers: [DialogService]
})
export class GTNetAdminMessagesComponent extends TableCrudSupportMenu<GTNet> {
  private gtNetService: GTNetService = inject(GTNetService);
  private gtNetMessageService: GTNetMessageService = inject(GTNetMessageService);
  private gtNetProtocolService: GTNetProtocolService = inject(GTNetProtocolService);

  readonly messageTreeTables = viewChildren(GTNetMessageTreeTableComponent);

  private readonly domainRemoteName = 'domainRemoteName';
  readonly gtNetMyEntryId = signal<number>(null);
  /** Selected entities for multi-select checkbox mode */
  readonly selectedEntities = signal<GTNet[]>([]);
  /**
   * The peers an admin message can go to: every entry except the own one with a completed handshake (both tokens
   * present, not merely a configuration row).
   */
  readonly filteredGtNetList = computed(() =>
    this.entityList().filter(
      (gtNet) => gtNet.idGtNet !== this.gtNetMyEntryId() && !!gtNet.gtNetConfig?.authorizedRemoteEntry
    )
  );
  /** Message count per idGtNet from admin message counts */
  gtNetMessageCountMap: { [key: number]: number } = {};
  /** Admin messages per idGtNet of the expanded rows; every change publishes a new map */
  readonly gtNetMessageMap = signal<{ [key: number]: GTNetMessage[] }>({});
  outgoingPendingReplies: { [key: number]: number[] } = {};
  incomingPendingReplies: { [key: number]: number[] } = {};
  formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  readonly visibleDialogMsg = signal(false);
  msgCallParam: MsgCallParam;

  /** Only rows with admin messages have something to expand */
  readonly canExpand = (row: GTNet): boolean => (this.gtNetMessageCountMap[row.idGtNet] ?? 0) > 0;

  /** Highlights the own entry */
  readonly isMyEntry = (row: GTNet, field: ColumnConfig): boolean => row.idGtNet === this.gtNetMyEntryId();
  /** All admin messages loaded from backend */
  private allAdminMessages: GTNetMessage[] = [];
  /** Whether current user has admin role (only admins can send admin messages) */
  protected isUserAdmin: boolean;

  constructor() {
    // No CRUD options - this is a read-only view for GTNet entities
    super(BaseSettings.GT_NET, inject(GTNetService), []);

    this.isUserAdmin = this.gps.hasRole(BaseSettings.ROLE_ADMIN);

    this.addColumnFeqH(DataType.String, this.domainRemoteName, true, false, {
      width: 200,
      templateName: 'owner'
    });
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {
      width: 120
    });
    this.addColumn(DataType.NumericInteger, 'adminMessageCount', 'GT_NET_ADMIN_MESSAGE_COUNT', true, false, {
      fieldValueFN: this.getAdminMessageCount.bind(this)
    });
    this.addColumnFeqH(DataType.NumericInteger, 'toBeAnswered', true, false, {
      fieldValueFN: this.getToBeAnsweredCount.bind(this)
    });
    this.addColumnFeqH(DataType.Numeric, 'answerExpected', true, false, {
      fieldValueFN: this.getAnswerExpectedCount.bind(this)
    });
    this.multiSortMeta.push({ field: this.domainRemoteName, order: 1 });
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    // No edit dialog for this read-only view
  }

  protected override readData(): void {
    // The protocol comes first because calculatePendingReplies() below asks it which codes expect an answer.
    const observable = [
      this.gtNetProtocolService.load(),
      this.gtNetService.getAllGTNetsWithMessages(),
      this.gtNetMessageService.getAdminMessages(),
      this.gtNetMessageService.getAdminMessageCounts(),
      ...(!this.formDefinitions ? [this.gtNetMessageService.getAllFormDefinitionsWithClass()] : [])
    ];

    combineLatest(observable)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        const gtNetWithMessages = <GTNetWithMessages>data[1];
        this.allAdminMessages = <GTNetMessage[]>data[2];
        this.gtNetMessageCountMap = <{ [key: number]: number }>data[3];
        this.formDefinitions ??= <{ [type: string]: ClassDescriptorInputAndShow }>data[4];

        // Calculate pending replies from admin messages only
        this.calculatePendingReplies();

        this.createTranslatedValueStoreAndFilterField(gtNetWithMessages.gtNetList);

        // Re-populate messages for previously expanded rows
        const gtNetMessageMap: { [key: number]: GTNetMessage[] } = {};
        Object.keys(this.gtNetMessageMap()).forEach((key) => {
          const idGtNet = Number(key);
          const messages = this.allAdminMessages.filter((msg) => msg.idGtNet === idGtNet);
          if (messages.length > 0) {
            gtNetMessageMap[idGtNet] = messages;
          }
        });
        this.gtNetMessageMap.set(gtNetMessageMap);

        this.gtNetMyEntryId.set(gtNetWithMessages.gtNetMyEntryId);
        // Show all GTNet domains so admins can send messages to any of them
        this.entityList.set(gtNetWithMessages.gtNetList);
        this.prepareTableAndTranslate();
      });
  }

  /**
   * Calculates pending replies from admin messages grouped by idGtNet.
   * For admin messages, any incoming message (root or reply) that hasn't been replied to
   * is considered pending, allowing ongoing back-and-forth conversations.
   */
  private calculatePendingReplies(): void {
    this.incomingPendingReplies = {};
    this.outgoingPendingReplies = {};

    // Build a set of message IDs that have been replied to
    const repliedToIds = new Set<number>();
    this.allAdminMessages.forEach((msg) => {
      if (msg.replyTo != null) {
        repliedToIds.add(msg.replyTo);
      }
    });

    this.allAdminMessages.forEach((msg) => {
      // Check if this message type supports replies
      const validResponses = this.gtNetProtocolService.getValidResponseCodes(msg.messageCode);
      if (validResponses.length === 0) {
        return;
      }
      // Skip if already replied to
      if (repliedToIds.has(msg.idGtNetMessage)) {
        return;
      }
      const idGtNet = msg.idGtNet;
      const isReceived =
        msg.sendRecv === 'RECEIVED' || msg.sendRecv === 'RECEIVE' || msg.sendRecv === SendReceivedType.RECEIVE;
      if (isReceived) {
        // Incoming message I need to answer (can be root or reply)
        this.incomingPendingReplies[idGtNet] ??= [];
        this.incomingPendingReplies[idGtNet].push(msg.idGtNetMessage);
      } else {
        // Outgoing message awaiting response (can be root or reply)
        this.outgoingPendingReplies[idGtNet] ??= [];
        this.outgoingPendingReplies[idGtNet].push(msg.idGtNetMessage);
      }
    });
  }

  override onComponentClick(event): void {
    if (!event[GTNetMessageTreeTableComponent.consumedGT]) {
      this.resetMenu(this.selectedEntities()[0] ?? null);
    }
  }

  protected override getEditMenuItems(entity: GTNet): MenuItem[] {
    const menuItems: MenuItem[] = [];
    // Only admins can send admin messages
    if (this.isUserAdmin) {
      menuItems.push({
        label: 'GT_NET_ADMIN_MESSAGE_SEND',
        command: (e) => this.sendAdminMsg(),
        disabled: this.selectedEntities().length === 0
      });
    }
    return menuItems;
  }

  private sendAdminMsg(): void {
    if (!this.isUserAdmin) {
      return;
    }
    const targetIds = this.selectedEntities().map((e) => e.idGtNet);
    this.msgCallParam = new MsgCallParam(this.formDefinitions, null, null, null, false, null, null);
    this.msgCallParam.targetIds = targetIds;
    this.msgCallParam.preselectedMessageCode = GTNetMessageCodeType.GT_NET_ADMIN_MESSAGE_SEL_C;
    this.visibleDialogMsg.set(true);
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_ADMIN_MGS;
  }

  handleCloseDialogMsg(processedActionData: ProcessedActionData): void {
    this.visibleDialogMsg.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  /**
   * Handles row expansion event - takes the admin messages of this GTNet from the data loaded with the table.
   */
  onRowExpand(event: { data: GTNet }): void {
    const idGtNet = event.data.idGtNet;
    const hasMessages = (this.gtNetMessageCountMap[idGtNet] ?? 0) > 0;
    if (hasMessages && !this.gtNetMessageMap()[idGtNet]) {
      this.gtNetMessageMap.update((map) => ({
        ...map,
        [idGtNet]: this.allAdminMessages.filter((msg) => msg.idGtNet === idGtNet)
      }));
      this.clearTreeTableSelection();
    }
  }

  /**
   * Clears the selection in all message tree table components once the expanded row is rendered.
   */
  private clearTreeTableSelection(): void {
    afterNextRender(() => this.messageTreeTables()?.forEach((treeTable) => treeTable.clearSelection()), {
      injector: this.injector
    });
  }

  protected override hasRightsForUpdateEntity(row: GTNet): boolean {
    return false; // Read-only view
  }

  /** Returns admin message count for this GTNet */
  getAdminMessageCount(dataobject: GTNet, field: ColumnConfig, valueField: any): number {
    return this.gtNetMessageCountMap[dataobject.idGtNet] ?? 0;
  }

  /** Returns count of incoming pending replies (requests I need to answer) */
  getToBeAnsweredCount(dataobject: GTNet, field: ColumnConfig, valueField: any): number {
    return this.incomingPendingReplies?.[dataobject.idGtNet]?.length ?? 0;
  }

  /** Returns count of outgoing pending replies (requests awaiting response) */
  getAnswerExpectedCount(dataobject: GTNet, field: ColumnConfig, valueField: any): number {
    return this.outgoingPendingReplies?.[dataobject.idGtNet]?.length ?? 0;
  }

  /** Returns set of incoming pending message IDs (requests I need to answer) */
  getIncomingPendingIds(idGtNet: number): Set<number> {
    return new Set(this.incomingPendingReplies?.[idGtNet] ?? []);
  }

  /** Returns set of outgoing pending message IDs (requests awaiting response) */
  getOutgoingPendingIds(idGtNet: number): Set<number> {
    return new Set(this.outgoingPendingReplies?.[idGtNet] ?? []);
  }

  /** Handle data changes from the tree table (e.g., reply sent, batch deletion) */
  onTreeTableDataChanged(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }
}
