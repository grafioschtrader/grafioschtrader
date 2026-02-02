import {Component, Injector, QueryList, ViewChildren} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {GTNet, GTNetWithMessages} from '../model/gtnet';
import {getValidResponseCodes, GTNetMessage, GTNetMessageCodeType, MessageVisibility, MsgCallParam, SendReceivedType} from '../model/gtnet.message';
import {GTNetService} from '../service/gtnet.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../lib/help/help.ids';
import {GTNetMessageTreeTableComponent} from './gtnet-message-treetable.component';
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../lib/base.settings';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';

/**
 * Component for displaying admin-only messages in a dedicated tab.
 * Shows GTNet domains in read-only mode with expandable admin messages.
 * Messages are filtered to only show visibility=ADMIN_ONLY.
 */
@Component({
  selector: 'gtnet-admin-messages',
  standalone: true,
  imports: [
    CommonModule,
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
      [data]="getFilteredGtNetList()"
      [fields]="fields"
      [dataKey]="'idGtNet'"
      [selectionMode]="'multiple'"
      [(selection)]="selectedEntities"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="true"
      [containerClass]="{'data-container-full': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [expandable]="true"
      [expandedRowTemplate]="expandedRow"
      [canExpandFn]="canExpand.bind(this)"
      [ownerHighlightFn]="isMyEntry.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      [contextMenuAppendTo]="'body'"
      (componentClick)="onComponentClick($event)"
      (rowExpand)="onRowExpand($event)">

      <h4 caption>{{ 'GT_NET_ADMIN_MESSAGES' | translate }}</h4>

    </configurable-table>

    <ng-template #expandedRow let-row>
      @if (isLoadingMessages(row.idGtNet)) {
        <div style="padding: 1rem; text-align: center;">
          <i class="fa fa-spinner fa-spin"></i> {{ 'LOADING' | translate }}...
        </div>
      } @else if (gtNetMessageMap[row.idGtNet]?.length) {
        <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[row.idGtNet]"
                                 [incomingPendingIds]="getIncomingPendingIds(row.idGtNet)"
                                 [outgoingPendingIds]="getOutgoingPendingIds(row.idGtNet)"
                                 [formDefinitions]="formDefinitions"
                                 [showFilter]="false"
                                 (dataChanged)="onTreeTableDataChanged($event)">
        </gtnet-message-treetable>
      } @else {
        <div style="padding: 1rem; text-align: center; color: #888;">
          {{ 'NO_DATA_FOUND' | translate }}
        </div>
      }
    </ng-template>

    @if (visibleDialogMsg) {
      <gtnet-message-edit [visibleDialog]="visibleDialogMsg"
                          [msgCallParam]="msgCallParam"
                          (closeDialog)="handleCloseDialogMsg($event)">
      </gtnet-message-edit>
    }
  `,
  providers: [DialogService]
})
export class GTNetAdminMessagesComponent extends TableCrudSupportMenu<GTNet> {
  @ViewChildren(GTNetMessageTreeTableComponent) messageTreeTables: QueryList<GTNetMessageTreeTableComponent>;

  private readonly domainRemoteName = 'domainRemoteName';
  gtNetList: GTNet[] = [];
  gtNetMyEntryId: number;
  /** Selected entities for multi-select checkbox mode */
  selectedEntities: GTNet[] = [];
  /** Message count per idGtNet from admin message counts */
  gtNetMessageCountMap: { [key: number]: number } = {};
  /** Cache for loaded admin messages (lazy loaded when row is expanded) */
  gtNetMessageMap: { [key: number]: GTNetMessage[] } = {};
  /** Set of idGtNet values for which messages have been loaded */
  loadedMessageIds = new Set<number>();
  /** Set of idGtNet values currently being loaded */
  loadingMessageIds = new Set<number>();
  outgoingPendingReplies: { [key: number]: number[] } = {};
  incomingPendingReplies: { [key: number]: number[] } = {};
  formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  visibleDialogMsg = false;
  msgCallParam: MsgCallParam;
  /** All admin messages loaded from backend */
  private allAdminMessages: GTNetMessage[] = [];
  /** Whether current user has admin role (only admins can send admin messages) */
  private isUserAdmin: boolean;

  constructor(private gtNetService: GTNetService,
    private gtNetMessageService: GTNetMessageService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {

    // No CRUD options - this is a read-only view for GTNet entities
    super(AppSettings.GT_NET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector, []);

    this.isUserAdmin = gps.hasRole(BaseSettings.ROLE_ADMIN);

    this.addColumnFeqH(DataType.String, this.domainRemoteName, true, false,
      {width: 200, templateName: 'owner'});
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {width: 120});
    this.addColumn(DataType.NumericInteger, 'adminMessageCount', 'GT_NET_ADMIN_MESSAGE_COUNT', true, false,
      {fieldValueFN: this.getAdminMessageCount.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'toBeAnswered', true, false,
      {fieldValueFN: this.getToBeAnsweredCount.bind(this)});
    this.addColumnFeqH(DataType.Numeric, 'answerExpected', true, false,
      {fieldValueFN: this.getAnswerExpectedCount.bind(this)});
    this.multiSortMeta.push({field: this.domainRemoteName, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    // No edit dialog for this read-only view
  }

  protected override readData(): void {
    const observable = [
      this.gtNetService.getAllGTNetsWithMessages(),
      this.gtNetMessageService.getAdminMessages(),
      this.gtNetMessageService.getAdminMessageCounts(),
      ...(!this.formDefinitions ? [this.gtNetMessageService.getAllFormDefinitionsWithClass()] : [])
    ];

    combineLatest(observable).subscribe((data) => {
      const gtNetWithMessages = <GTNetWithMessages>data[0];
      this.allAdminMessages = <GTNetMessage[]>data[1];
      this.gtNetMessageCountMap = <{ [key: number]: number }>data[2];
      this.formDefinitions ??= <{ [type: string]: ClassDescriptorInputAndShow }>data[3];

      // Show all GTNet domains so admins can send messages to any of them
      this.gtNetList = gtNetWithMessages.gtNetList;

      this.gtNetMyEntryId = gtNetWithMessages.gtNetMyEntryId;

      // Calculate pending replies from admin messages only
      this.calculatePendingReplies();

      this.createTranslatedValueStoreAndFilterField(this.gtNetList);

      // Save previously expanded row IDs before clearing
      const previouslyLoadedIds = new Set(this.loadedMessageIds);

      // Clear message cache on data refresh
      this.gtNetMessageMap = {};
      this.loadedMessageIds.clear();
      this.loadingMessageIds.clear();

      // Re-populate messages for previously expanded rows
      previouslyLoadedIds.forEach(idGtNet => {
        const messages = this.allAdminMessages.filter(msg => msg.idGtNet === idGtNet);
        if (messages.length > 0) {
          this.gtNetMessageMap[idGtNet] = messages;
          this.loadedMessageIds.add(idGtNet);
        }
      });

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
    this.allAdminMessages.forEach(msg => {
      if (msg.replyTo != null) {
        repliedToIds.add(msg.replyTo);
      }
    });

    this.allAdminMessages.forEach(msg => {
      // Check if this message type supports replies
      const validResponses = getValidResponseCodes(msg.messageCode);
      if (validResponses.length === 0) {
        return;
      }
      // Skip if already replied to
      if (repliedToIds.has(msg.idGtNetMessage)) {
        return;
      }
      const idGtNet = msg.idGtNet;
      const isReceived = msg.sendRecv === 'RECEIVED' || msg.sendRecv === 'RECEIVE' || msg.sendRecv === SendReceivedType.RECEIVE;
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
      this.resetMenu(this.selectedEntities?.length > 0 ? this.selectedEntities[0] : null);
    }
  }

  public override getEditMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    // Only admins can send admin messages
    if (this.isUserAdmin) {
      menuItems.push({
        label: 'GT_NET_ADMIN_MESSAGE_SEND', command: (e) => this.sendAdminMsg(),
        disabled: this.selectedEntities.length === 0
      });
    }
    return menuItems;
  }

  /**
   * Returns the list of GTNet entries filtered for multi-select:
   * - Excludes the local server's own entry
   * - Only includes entries with completed handshake (gtNetConfig exists)
   */
  getFilteredGtNetList(): GTNet[] {
    return this.gtNetList.filter(gtNet =>
      gtNet.idGtNet !== this.gtNetMyEntryId && gtNet.gtNetConfig != null
    );
  }

  private sendAdminMsg(): void {
    const targetIds = this.selectedEntities.map(e => e.idGtNet);
    this.msgCallParam = new MsgCallParam(this.formDefinitions, null, null, null, false, null, null);
    this.msgCallParam.targetIds = targetIds;
    this.msgCallParam.preselectedMessageCode = GTNetMessageCodeType.GT_NET_ADMIN_MESSAGE_SEL_C;
    this.visibleDialogMsg = true;
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_ADMIN_MGS;
  }

  handleCloseDialogMsg(processedActionData: ProcessedActionData): void {
    this.visibleDialogMsg = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  canExpand(row: GTNet): boolean {
    return (this.gtNetMessageCountMap[row.idGtNet] ?? 0) > 0;
  }

  /**
   * Handles row expansion event - filters admin messages for this GTNet from cached data.
   */
  onRowExpand(event: { data: GTNet }): void {
    const idGtNet = event.data.idGtNet;
    const hasMessages = (this.gtNetMessageCountMap[idGtNet] ?? 0) > 0;

    // Only process if there are messages and not already loaded
    if (hasMessages && !this.loadedMessageIds.has(idGtNet) && !this.loadingMessageIds.has(idGtNet)) {
      this.loadingMessageIds.add(idGtNet);

      // Filter admin messages for this GTNet from already loaded data
      setTimeout(() => {
        this.gtNetMessageMap[idGtNet] = this.allAdminMessages.filter(msg => msg.idGtNet === idGtNet);
        this.loadedMessageIds.add(idGtNet);
        this.loadingMessageIds.delete(idGtNet);
        this.clearTreeTableSelection();
      });
    }
  }

  /**
   * Clears the selection in all message tree table components.
   */
  private clearTreeTableSelection(): void {
    setTimeout(() => {
      this.messageTreeTables?.forEach(treeTable => treeTable.clearSelection());
    });
  }

  /**
   * Checks if messages are currently being loaded for a GTNet.
   */
  isLoadingMessages(idGtNet: number): boolean {
    return this.loadingMessageIds.has(idGtNet);
  }

  isMyEntry(row: GTNet, field: ColumnConfig): boolean {
    return row.idGtNet === this.gtNetMyEntryId;
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
