import type { PanelClickEvent } from '../../mainmenubar/component/iglobal.menu.attach';
import { Component, DestroyRef, inject, OnInit, signal, output, viewChild, input } from '@angular/core';
import { Subscription } from 'rxjs';

import { FormsModule } from '@angular/forms';
import { TreeTableConfigBase } from '../../datashowbase/tree.table.config.base';
import { DataType } from '../../dynamic-form/models/data.type';
import {
  DeliveryStatus,
  getReverseCode,
  GTNetMessage,
  GTNetMessageCodeType,
  MessageVisibility,
  MsgCallParam,
  SendReceivedType
} from '../model/gtnet.message';
import { MsgRequest } from '../model/gtnet';
import { GTNetService } from '../service/gtnet.service';
import { GTNetProtocolService } from '../service/gtnet.protocol.service';
import { FilterService, MenuItem, TreeNode } from '@openng/optimus-ui/api';
import { TranslateModule } from '@ngx-translate/core';
import { IGlobalMenuAttach } from '../../mainmenubar/component/iglobal.menu.attach';
import { HelpIds } from '../../help/help.ids';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { ClassDescriptorInputAndShow } from '../../dynamicfield/field.descriptor.input.and.show';
import { TreeTable, TreeTableModule } from '@openng/optimus-ui/treetable';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { TooltipModule } from '@openng/optimus-ui/tooltip';
import { DatePickerModule } from '@openng/optimus-ui/datepicker';
import { MultiSelectModule } from '@openng/optimus-ui/multiselect';
import { InputTextModule } from '@openng/optimus-ui/inputtext';
import { CheckboxModule } from '@openng/optimus-ui/checkbox';
import { GTNetMessageEditComponent } from './gtnet-message-edit.component';
import { ProcessedActionData } from '../../types/processed.action.data';
import { ProcessedAction } from '../../types/processed.action';
import { GTNetMessageService } from '../service/gtnet.message.service';
import { AngularSvgIconModule, SvgIconRegistryService } from 'angular-svg-icon';
import { BaseSettings } from '../../base.settings';
import { TranslateHelper } from '../../helper/translate.helper';
import { Helper } from '../../helper/helper';
import { FilterType } from '../../datashowbase/filter.type';

/**
 * It shows the messages in a tree table.
 */
@Component({
  selector: 'gtnet-message-treetable',
  imports: [
    FormsModule,
    TreeTableModule,
    ContextMenuModule,
    TooltipModule,
    DatePickerModule,
    MultiSelectModule,
    InputTextModule,
    CheckboxModule,
    TranslateModule,
    GTNetMessageEditComponent,
    AngularSvgIconModule
  ],
  template: `
    <div
      #cmDiv
      class="data-container"
      (click)="onComponentClick($event)"
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <div class="datatable nestedtable">
        <p-treeTable
          #tt
          [value]="rootNode.children"
          [columns]="fields"
          dataKey="idGtNetMessage"
          selectionMode="single"
          [(selection)]="selectedNode"
          (selectionChange)="onSelectionChange($event)"
          showGridlines="true">
          <ng-template #header let-fields>
            <tr>
              @if (canAdministerGTNet) {
                <th style="width: 40px; text-align: center;">
                  @if (hasDeletableMessages()) {
                    <p-checkbox
                      [binary]="true"
                      [ngModel]="areAllDeletableSelected()"
                      (onChange)="onSelectAllChange($event)"
                      [pTooltip]="'SELECT_DESELECT_ALL' | translate" />
                  }
                </th>
              }
              @for (field of fields; track field) {
                <th [style.width.px]="field.width">
                  {{ field.headerTranslated }}
                </th>
              }
            </tr>
            @if (showFilter() && hasFilter) {
              <tr>
                @if (canAdministerGTNet) {
                  <th></th>
                }
                @for (field of fields; track field) {
                  <th style="overflow:visible;">
                    @switch (field.filterType) {
                      @case (FilterType.likeDataType) {
                        @if (field.dataType === DataType.DateTimeString) {
                          <p-datepicker
                            [ngModel]="null"
                            [dateFormat]="baseLocale.dateFormat"
                            (onSelect)="filterDate($event, field, tt)"
                            appendTo="body" />
                        } @else if (field.dataType === DataType.String) {
                          <input
                            pInputText
                            type="text"
                            (input)="tt.filter($any($event.target).value, field.field, 'contains')" />
                        }
                      }
                      @case (FilterType.withOptions) {
                        <!-- Several values can be selected, they are combined with OR. The clear icon reports only
                             through onClear, never through onChange, so both must reach the tree table. -->
                        <p-multiSelect
                          [options]="field.filterValues"
                          [style]="{ width: '100%' }"
                          appendTo="body"
                          [maxSelectedLabels]="1"
                          [showClear]="true"
                          [filter]="$safeNavigationMigration(field.filterValues?.length) > 10"
                          (onChange)="tt.filter($event.value?.length ? $event.value : null, field.field, 'in')"
                          (onClear)="tt.filter(null, field.field, 'in')" />
                      }

                      <!-- Columns without a supported filter render no control. -->
                      @default {}
                    }
                  </th>
                }
              </tr>
            }
          </ng-template>
          <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
            <tr [ttSelectableRow]="rowNode" [style.background-color]="getPendingBackgroundColor(rowData)">
              @if (canAdministerGTNet) {
                <td style="width: 40px; text-align: center;">
                  @if (rowData.canDelete && !rowData.replyTo) {
                    <p-checkbox
                      [binary]="true"
                      [ngModel]="selectedDeletableIds().has(rowData.idGtNetMessage)"
                      (onChange)="onDeleteCheckChange($event, rowData)" />
                  }
                </td>
              }
              @for (field of fields; track field; let i = $index) {
                @if (field.visible) {
                  <td
                    [class]="
                      field.dataType === DataType.Numeric || field.dataType === DataType.DateTimeNumeric
                        ? 'text-end'
                        : ''
                    "
                    [style.width.px]="field.width">
                    @if (i === 0) {
                      <p-treeTableToggler [rowNode]="rowNode" />
                    }
                    @switch (field.templateName) {
                      @case ('greenRed') {
                        <span
                          [pTooltip]="getValueByPath(rowData, field)"
                          [style.color]="isValueByPathMinus(rowData, field) ? 'red' : 'inherit'"
                          tooltipPosition="top">
                          {{ getValueByPath(rowData, field) }}
                        </span>
                      }
                      @case ('icon') {
                        <svg-icon
                          [name]="getValueByPath(rowData, field)"
                          [svgStyle]="{ 'width.px': 16, 'height.px': 16 }" />
                      }
                      @case ('check') {
                        <span
                          ><i
                            [class.fa]="getValueByPath(rowData, field)"
                            [class.fa-check]="getValueByPath(rowData, field)"
                            aria-hidden="true"></i
                        ></span>
                      }
                      @default {
                        <span [pTooltip]="getValueByPath(rowData, field)">{{ getValueByPath(rowData, field) }}</span>
                      }
                    }
                  </td>
                }
              }
            </tr>
          </ng-template>
        </p-treeTable>
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems()" appendTo="body" />
      </div>
    </div>
    @if (visibleDialogMsg()) {
      <gtnet-message-edit
        [visibleDialog]="visibleDialogMsg()"
        [msgCallParam]="msgCallParam"
        (closeDialog)="handleCloseDialogMsg($event)" />
    }
  `
})
export class GTNetMessageTreeTableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  protected override filterService = inject(FilterService);
  private activePanelService: ActivePanelService = inject(ActivePanelService);
  private gtNetMessageService: GTNetMessageService = inject(GTNetMessageService);
  private gtNetService: GTNetService = inject(GTNetService);
  private gtNetProtocolService: GTNetProtocolService = inject(GTNetProtocolService);
  private iconReg: SvgIconRegistryService = inject(SvgIconRegistryService);

  readonly gtNetMessages = input<GTNetMessage[]>(undefined);
  readonly incomingPendingIds = input<Set<number>>(undefined);
  readonly outgoingPendingIds = input<Set<number>>(undefined);
  readonly formDefinitions = input<{
    [type: string]: ClassDescriptorInputAndShow;
  }>(undefined);
  /** Controls whether the filter row is displayed. Default is true. */
  readonly showFilter = input<boolean>(true);
  readonly dataChanged = output<ProcessedActionData>();
  readonly contextMenu = viewChild<any>('cm');
  readonly treeTable = viewChild<TreeTable>('tt');

  public static readonly consumedGT = 'consumedGT';

  /** Maps sendRecv values to icon names */
  public static sendRecvIconMap: { [key: string]: string } = {
    [SendReceivedType.SEND]: 'send',
    ['SEND']: 'send',
    [SendReceivedType.RECEIVE]: 'envelope',
    ['RECEIVED']: 'envelope'
  };

  private static iconLoadDone = false;

  rootNode: TreeNode = { children: [] };
  selectedNode: TreeNode;
  /** Translated menu of the selection, shown as context menu and in the menu bar */
  readonly contextMenuItems = signal<MenuItem[]>([]);
  readonly visibleDialogMsg = signal(false);
  msgCallParam: MsgCallParam;
  protected readonly canAdministerGTNet: boolean;

  /** IDs of the messages selected for batch deletion; every change publishes a new set */
  readonly selectedDeletableIds = signal<ReadonlySet<number>>(new Set());

  /** Translation of the latest menu; a newer menu replaces one that is still being translated */
  private menuSubscription: Subscription;

  constructor() {
    super();
    this.canAdministerGTNet = this.gps.hasRole(BaseSettings.ROLE_ADMIN);
    GTNetMessageTreeTableComponent.registerIcons(this.iconReg);
    inject(DestroyRef).onDestroy(() => this.menuSubscription?.unsubscribe());
  }

  private static registerIcons(iconReg: SvgIconRegistryService): void {
    if (!GTNetMessageTreeTableComponent.iconLoadDone) {
      const uniqueIcons = new Set(Object.values(GTNetMessageTreeTableComponent.sendRecvIconMap));
      for (const iconName of uniqueIcons) {
        iconReg.loadSvg(BaseSettings.PATH_ASSET_ICONS + iconName + BaseSettings.SVG, iconName);
      }
      GTNetMessageTreeTableComponent.iconLoadDone = true;
    }
  }

  /**
   * Returns the icon name for a given sendRecv value.
   */
  getSendRecvIcon(entity: GTNetMessage, field: ColumnConfig, valueField: any): string {
    return GTNetMessageTreeTableComponent.sendRecvIconMap[entity.sendRecv] || 'envelope';
  }

  ngOnInit(): void {
    // The reply gate reads which codes answer which; the request is made once for the whole application.
    this.gtNetProtocolService.load().subscribe();
    this.addColumn(DataType.DateTimeString, 'timestamp', 'SEND_RECV_TIME', true, false, {
      width: 160,
      filterType: FilterType.likeDataType
    });
    this.addColumn(DataType.String, 'sendRecv', 'A', true, false, {
      fieldValueFN: this.getSendRecvIcon.bind(this),
      templateName: 'icon',
      width: 25
    });
    this.addColumnFeqH(DataType.String, 'messageCode', true, false, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions
    });
    this.addColumnFeqH(DataType.String, 'message', true, false, {
      filterType: FilterType.likeDataType
    });
    this.addColumnFeqH(DataType.String, 'visibility', true, false, {
      translateValues: TranslateValue.NORMAL,
      filterType: FilterType.withOptions,
      width: 100
    });
    this.addColumnFeqH(DataType.Boolean, 'hasBeenRead', true, false, {
      templateName: 'check',
      width: 60
    });
    this.prepareData();
    this.createTranslateValuesStoreForTranslation(this.rootNode.children);
    this.prepareTreeTableAndTranslate();
    this.prepareFilter(this.extractDataFromTree());
  }

  /**
   * Extracts all GTNetMessage data from the tree structure for filter initialization.
   */
  private extractDataFromTree(): GTNetMessage[] {
    const data: GTNetMessage[] = [];
    const traverse = (nodes: TreeNode[]) => {
      nodes?.forEach((node) => {
        if (node.data) {
          data.push(node.data);
        }
        if (node.children) {
          traverse(node.children);
        }
      });
    };
    traverse(this.rootNode.children);
    return data;
  }

  private prepareData(): void {
    const nodeMap = new Map<number, TreeNode>();

    // First pass: create all nodes and populate the nodeMap
    // This ensures all nodes exist before we establish parent-child relationships,
    // regardless of the order messages arrive from the backend (timestamp DESC).
    this.gtNetMessages().forEach((gtMessage) => {
      const node: TreeNode = { data: gtMessage, leaf: true };
      nodeMap.set(gtMessage.idGtNetMessage, node);
    });

    // Second pass: establish parent-child relationships
    this.gtNetMessages().forEach((gtMessage) => {
      const node = nodeMap.get(gtMessage.idGtNetMessage);
      if (gtMessage.replyTo) {
        const parentNode = nodeMap.get(gtMessage.replyTo);
        if (parentNode) {
          if (parentNode.leaf) {
            parentNode.leaf = false;
            parentNode.children = [];
          }
          parentNode.children.push(node);
          return;
        }
      }
      // No parent found or no replyTo - add to root
      this.rootNode.children.push(node);
    });

    // Auto-expand nodes that have unread received messages
    this.expandNodesWithUnreadMessages(this.rootNode.children, nodeMap);
  }

  /**
   * Expands parent nodes that contain unread received messages.
   */
  private expandNodesWithUnreadMessages(nodes: TreeNode[], nodeMap: Map<number, TreeNode>): void {
    nodes?.forEach((node) => {
      const msg: GTNetMessage = node.data;
      const isReceived =
        msg.sendRecv === 'RECEIVED' || msg.sendRecv === 'RECEIVE' || msg.sendRecv === SendReceivedType.RECEIVE;
      if (isReceived && !msg.hasBeenRead) {
        // Mark parent nodes as expanded
        if (msg.replyTo) {
          const parentNode = nodeMap.get(msg.replyTo);
          if (parentNode) {
            parentNode.expanded = true;
          }
        }
      }
      // Recurse into children
      if (node.children?.length) {
        this.expandNodesWithUnreadMessages(node.children, nodeMap);
      }
    });
  }

  /**
   * Called when a tree node is selected. Marks received messages as read and updates context menu.
   */
  onSelectionChange(selection: TreeNode | TreeNode[]): void {
    const node = Helper.singleSelection(selection);
    // Update context menu items based on new selection
    this.setMenuItemsToActivePanel();

    if (!node?.data) {
      return;
    }
    const msg: GTNetMessage = node.data;
    const isReceived =
      msg.sendRecv === 'RECEIVED' || msg.sendRecv === 'RECEIVE' || msg.sendRecv === SendReceivedType.RECEIVE;

    // Mark as read if it's a received message that hasn't been read. The flag is instance-wide rather than per user,
    // so setting it is an administrative act and the endpoint requires ROLE_ADMIN. This tree table is also embedded in
    // the admin-message feed, which every authenticated user may open, so a non-admin selecting a row would otherwise
    // fire a request that can only come back 403.
    if (isReceived && !msg.hasBeenRead && this.canAdministerGTNet) {
      this.gtNetMessageService.markAsRead(msg.idGtNetMessage).subscribe(() => {
        // Update local state immediately
        msg.hasBeenRead = true;
        this.notifyViewChanged();
      });
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: PanelClickEvent): void {
    event[GTNetMessageTreeTableComponent.consumedGT] = true;
    const contextMenu = this.contextMenu();
    contextMenu && contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  hideContextMenu(): void {
    const contextMenu = this.contextMenu();
    contextMenu && contextMenu.hide();
  }

  callMeDeactivate(): void {}

  /** Publishes the translated menu of the selection as context menu and in the menu bar. */
  private setMenuItemsToActivePanel(): void {
    this.menuSubscription?.unsubscribe();
    this.menuSubscription = TranslateHelper.translateMenuItems$(this.getMenuItems(), this.translateService).subscribe(
      (menuItems) => {
        this.contextMenuItems.set(menuItems);
        this.activePanelService.activatePanel(this, { editMenu: menuItems });
      }
    );
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_SETUP;
  }

  private getMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = [];
    if (!this.canAdministerGTNet) {
      return menuItems;
    }
    if (this.selectedNode?.data && this.canReplyToSelected()) {
      menuItems.push({
        label: 'REPLY',
        command: () => this.replyToSelected()
      });
    }
    if (this.selectedNode?.data && this.canReverseSelected()) {
      menuItems.push({
        label: 'REVERSE',
        command: () => this.reverseSelected()
      });
    }
    if (this.canDeleteSelected()) {
      menuItems.push({
        label: 'DELETE_SELECTED',
        command: () => this.deleteSelected()
      });
    }
    return menuItems;
  }

  /**
   * Checks if the selected message can be replied to:
   * - Must be an incoming message (sendRecv === 'RECEIVED', 'RECEIVE', or SendReceivedType.RECEIVE)
   * - Must have valid response codes defined
   * - For a threadable code: always allow reply (supports ongoing conversations)
   * - For other messages: must be pending (unanswered request)
   */
  private canReplyToSelected(): boolean {
    if (!this.canAdministerGTNet) {
      return false;
    }
    const msg: GTNetMessage = this.selectedNode?.data;
    if (!msg) {
      return false;
    }
    const isIncoming =
      msg.sendRecv === 'RECEIVED' || msg.sendRecv === 'RECEIVE' || msg.sendRecv === SendReceivedType.RECEIVE;
    const validResponses = this.gtNetProtocolService.getValidResponseCodes(msg.messageCode);
    if (!isIncoming || validResponses.length === 0) {
      return false;
    }
    // A threadable code - the admin message is the only one - continues a conversation instead of closing a request,
    // so it may always be answered.
    if (this.gtNetProtocolService.isThreadable(msg.messageCode)) {
      return true;
    }
    // For other message types, require pending status
    const isPending = this.incomingPendingIds()?.has(msg.idGtNetMessage) ?? false;
    return isPending;
  }

  private replyToSelected(): void {
    if (!this.canAdministerGTNet) {
      return;
    }
    const msg: GTNetMessage = this.selectedNode.data;
    const validResponses = this.gtNetProtocolService.getValidResponseCodes(msg.messageCode);
    // Get parent's visibility for inheritance rules
    const parentVisibility = this.getParentVisibility(msg);
    this.msgCallParam = new MsgCallParam(
      this.formDefinitions(),
      msg.idGtNet,
      msg.idGtNetMessage,
      null,
      false,
      validResponses,
      null,
      parentVisibility
    );
    this.visibleDialogMsg.set(true);
  }

  /**
   * Gets the visibility of the thread root message for inheritance rules.
   * If the message being replied to is itself a reply, we traverse up to find the root.
   * For simplicity, we use the immediate parent's visibility - backend will enforce
   * the full inheritance rule.
   */
  private getParentVisibility(msg: GTNetMessage): MessageVisibility {
    const visibilityValue =
      typeof msg.visibility === 'string'
        ? MessageVisibility[msg.visibility as keyof typeof MessageVisibility]
        : msg.visibility;
    return visibilityValue ?? MessageVisibility.ALL_USERS;
  }

  /**
   * Checks if the selected message can be reversed (cancelled):
   * - Must be a sent message (sendRecv === 'SEND' or SendReceivedType.SEND)
   * - Must be a reversible message type (MAINTENANCE or OPERATION_DISCONTINUED)
   * - The dates in the message must be in the future
   */
  private canReverseSelected(): boolean {
    if (!this.canAdministerGTNet) {
      return false;
    }
    const msg: GTNetMessage = this.selectedNode?.data;
    if (!msg) {
      return false;
    }
    const isSent = msg.sendRecv === 'SEND' || msg.sendRecv === SendReceivedType.SEND;
    const reverseCode = getReverseCode(msg.messageCode);
    if (!isSent || !reverseCode) {
      return false;
    }
    // Check if dates are in the future
    return this.areDatesInFuture(msg);
  }

  /**
   * Checks if the dates in the message parameters are in the future.
   * For MAINTENANCE: checks fromDateTime
   * For OPERATION_DISCONTINUED: checks closeStartDate
   */
  private areDatesInFuture(msg: GTNetMessage): boolean {
    const params = msg.gtNetMessageParamMap;
    if (!params) {
      return false;
    }
    const now = new Date();
    // Check for maintenance message (fromDateTime)
    const fromDateTimeParam = params['fromDateTime'];
    if (fromDateTimeParam) {
      const fromDateTime = new Date((fromDateTimeParam.paramValue || fromDateTimeParam) as string);
      return fromDateTime > now;
    }
    // Check for discontinued message (closeStartDate)
    const closeStartDateParam = params['closeStartDate'];
    if (closeStartDateParam) {
      const closeStartDate = Helper.parseDateOnlyAsLocal(
        (closeStartDateParam.paramValue || closeStartDateParam) as string
      );
      return closeStartDate > now;
    }
    return false;
  }

  /**
   * Sends a cancel message for the selected announcement.
   * Sets idOriginalMessage to link the cancellation to the original message.
   */
  private reverseSelected(): void {
    if (!this.canAdministerGTNet) {
      return;
    }
    const msg: GTNetMessage = this.selectedNode.data;
    const reverseCode = getReverseCode(msg.messageCode);
    if (!reverseCode) {
      return;
    }
    const msgRequest = new MsgRequest(msg.idGtNet, null, GTNetMessageCodeType[reverseCode], null);
    msgRequest.idOriginalMessage = msg.idGtNetMessage;
    this.gtNetService.submitMsg(msgRequest).subscribe({
      next: () => {
        this.dataChanged.emit(new ProcessedActionData(ProcessedAction.UPDATED, null));
      }
    });
  }

  handleCloseDialogMsg(processedActionData: ProcessedActionData): void {
    this.visibleDialogMsg.set(false);
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.dataChanged.emit(processedActionData);
    }
  }

  /**
   * Returns the background color for messages based on their status:
   * - red: delivery failed (all targets are terminally unavailable or the message expired)
   * - greenyellow: incoming pending requests (I need to answer)
   * - yellow: outgoing pending requests (awaiting answer from recipient)
   * - null: normal state
   */
  getPendingBackgroundColor(rowData: GTNetMessage): string | null {
    if (rowData.deliveryStatus === DeliveryStatus.FAILED || rowData.deliveryStatus === 'FAILED') {
      return 'red';
    }
    if (this.incomingPendingIds()?.has(rowData.idGtNetMessage)) {
      return 'greenyellow';
    }
    if (this.outgoingPendingIds()?.has(rowData.idGtNetMessage)) {
      return 'yellow';
    }
    return null;
  }

  /**
   * Handles checkbox change events for batch deletion selection.
   *
   * @param event the checkbox change event
   * @param rowData the message being selected/deselected
   */
  onDeleteCheckChange(event: any, rowData: GTNetMessage): void {
    if (!this.canAdministerGTNet) {
      return;
    }
    const selected = new Set(this.selectedDeletableIds());
    if (event.checked) {
      selected.add(rowData.idGtNetMessage);
    } else {
      selected.delete(rowData.idGtNetMessage);
    }
    this.selectedDeletableIds.set(selected);
    // Update menu items to reflect selection state
    this.setMenuItemsToActivePanel();
  }

  /**
   * Returns all deletable messages from the current tree, respecting the filter.
   * A message is deletable if canDelete=true and replyTo=null (request level only).
   */
  private getFilteredDeletableMessages(): GTNetMessage[] {
    const deletable: GTNetMessage[] = [];
    const filteredNodes = this.treeTable()?.filteredNodes ?? this.rootNode.children;

    const traverse = (nodes: TreeNode[]) => {
      nodes?.forEach((node) => {
        const msg: GTNetMessage = node.data;
        if (msg?.canDelete && !msg.replyTo) {
          deletable.push(msg);
        }
        if (node.children) {
          traverse(node.children);
        }
      });
    };
    traverse(filteredNodes);
    return deletable;
  }

  /**
   * Checks if there are any deletable messages in the current view.
   */
  hasDeletableMessages(): boolean {
    return this.canAdministerGTNet && this.getFilteredDeletableMessages().length > 0;
  }

  /**
   * Checks if all deletable messages are currently selected.
   */
  areAllDeletableSelected(): boolean {
    const deletable = this.getFilteredDeletableMessages();
    if (deletable.length === 0) {
      return false;
    }
    const selected = this.selectedDeletableIds();
    return deletable.every((msg) => selected.has(msg.idGtNetMessage));
  }

  /**
   * Handles the select all checkbox change event.
   * Toggles selection of all deletable messages based on current state.
   */
  onSelectAllChange(event: any): void {
    if (!this.canAdministerGTNet) {
      return;
    }
    const deletable = this.getFilteredDeletableMessages();
    const selected = new Set(this.selectedDeletableIds());
    if (event.checked) {
      // Select all deletable messages
      deletable.forEach((msg) => selected.add(msg.idGtNetMessage));
    } else {
      // Deselect all deletable messages
      deletable.forEach((msg) => selected.delete(msg.idGtNetMessage));
    }
    this.selectedDeletableIds.set(selected);
    // Update menu items to reflect selection state
    this.setMenuItemsToActivePanel();
  }

  /**
   * Checks if there are any messages selected for deletion.
   */
  canDeleteSelected(): boolean {
    return this.canAdministerGTNet && this.selectedDeletableIds().size > 0;
  }

  /**
   * Deletes all selected messages.
   * Emits ProcessedActionData with idGtNet so parent can reload only affected messages.
   */
  deleteSelected(): void {
    if (!this.canDeleteSelected()) {
      return;
    }
    const idsToDelete = Array.from(this.selectedDeletableIds());
    // Get idGtNet from the first message (all messages in this component belong to the same GTNet)
    const idGtNet = this.gtNetMessages()?.[0]?.idGtNet;
    this.gtNetService.deleteMessageBatch(idsToDelete).subscribe({
      next: () => {
        this.selectedDeletableIds.set(new Set());
        this.dataChanged.emit(new ProcessedActionData(ProcessedAction.DELETED, idGtNet));
      }
    });
  }

  /**
   * Clears the selection of deletable messages.
   * Called from parent component when messages are reloaded.
   */
  clearSelection(): void {
    this.selectedDeletableIds.set(new Set());
    this.setMenuItemsToActivePanel();
  }
}
