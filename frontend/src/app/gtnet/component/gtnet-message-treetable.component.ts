import {Component, EventEmitter, Input, OnInit, Output, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {TreeTableConfigBase} from '../../lib/datashowbase/tree.table.config.base';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {DeliveryStatus, getReverseCode, getValidResponseCodes, GTNetMessage, GTNetMessageCodeType, MessageVisibility, MsgCallParam, SendReceivedType} from '../model/gtnet.message';
import {MsgRequest} from '../model/gtnet';
import {GTNetService} from '../service/gtnet.service';
import {FilterService, MenuItem, TreeNode} from 'primeng/api';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {IGlobalMenuAttach} from '../../lib/mainmenubar/component/iglobal.menu.attach';
import {HelpIds} from '../../lib/help/help.ids';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {TreeTable, TreeTableModule} from 'primeng/treetable';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {DatePickerModule} from 'primeng/datepicker';
import {SelectModule} from 'primeng/select';
import {InputTextModule} from 'primeng/inputtext';
import {CheckboxModule} from 'primeng/checkbox';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {AngularSvgIconModule, SvgIconRegistryService} from 'angular-svg-icon';
import {BaseSettings} from '../../lib/base.settings';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {FilterType} from '../../lib/datashowbase/filter.type';

/**
 * It shows the messages in a tree table.
 */
@Component({
  selector: 'gtnet-message-treetable',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TreeTableModule,
    ContextMenuModule,
    TooltipModule,
    DatePickerModule,
    SelectModule,
    InputTextModule,
    CheckboxModule,
    TranslateModule,
    GTNetMessageEditComponent,
    AngularSvgIconModule
  ],
  template: `
    <div #cmDiv class="data-container" (click)="onComponentClick($event)"
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <div class="datatable nestedtable">
        <p-treeTable #tt [value]="rootNode.children" [columns]="fields" dataKey="idGtNetMessage"
                     selectionMode="single" [(selection)]="selectedNode"
                     (selectionChange)="onSelectionChange($event)"
                     showGridlines="true">
          <ng-template #header let-fields>
            <tr>
              <th style="width: 40px; text-align: center;">
                @if (hasDeletableMessages()) {
                  <p-checkbox [binary]="true"
                              [ngModel]="areAllDeletableSelected()"
                              (onChange)="onSelectAllChange($event)"
                              [pTooltip]="'SELECT_DESELECT_ALL' | translate">
                  </p-checkbox>
                }
              </th>
              @for (field of fields; track field) {
                <th [style.width.px]="field.width">
                  {{ field.headerTranslated }}
                </th>
              }
            </tr>
            @if (hasFilter) {
              <tr>
                <th></th>
                @for (field of fields; track field) {
                  <th style="overflow:visible;">
                    @switch (field.filterType) {
                      @case (FilterType.likeDataType) {
                        @if (field.dataType === DataType.DateTimeString) {
                          <p-datepicker [ngModel]="null" [dateFormat]="baseLocale.dateFormat"
                            (onSelect)="filterDate($event, field, tt)" appendTo="body">
                          </p-datepicker>
                        } @else if (field.dataType === DataType.String) {
                          <input pInputText type="text"
                            (input)="tt.filter($any($event.target).value, field.field, 'contains')">
                        }
                      }
                      @case (FilterType.withOptions) {
                        <p-select [options]="field.filterValues" [style]="{'width':'100%'}"
                          (onChange)="tt.filter($event.value, field.field, 'equals')">
                        </p-select>
                      }
                    }
                  </th>
                }
              </tr>
            }
          </ng-template>
          <ng-template #body let-rowNode let-rowData="rowData" let-columns="fields">
            <tr [ttSelectableRow]="rowNode"
                [style.background-color]="getPendingBackgroundColor(rowData)">
              <td style="width: 40px; text-align: center;">
                @if (rowData.canDelete && !rowData.replyTo) {
                  <p-checkbox [binary]="true"
                              [ngModel]="selectedDeletableIds.has(rowData.idGtNetMessage)"
                              (onChange)="onDeleteCheckChange($event, rowData)">
                  </p-checkbox>
                }
              </td>
              @for (field of fields; track field; let i = $index) {
                @if (field.visible) {
                  <td
                    [ngClass]="(field.dataType===DataType.Numeric || field.dataType===DataType.DateTimeNumeric)? 'text-end': ''"
                    [style.width.px]="field.width">
                    @if (i === 0) {
                      <p-treeTableToggler [rowNode]="rowNode"></p-treeTableToggler>
                    }
                    @switch (field.templateName) {
                      @case ('greenRed') {
                        <span [pTooltip]="getValueByPath(rowData, field)"
                              [style.color]='isValueByPathMinus(rowData, field)? "red": "inherit"'
                              tooltipPosition="top">
                        {{ getValueByPath(rowData, field) }}
                      </span>
                      }
                      @case ('icon') {
                        <svg-icon [name]="getValueByPath(rowData, field)"
                                  [svgStyle]="{ 'width.px':16, 'height.px':16 }"></svg-icon>
                      }
                      @case ('check') {
                        <span><i [ngClass]="{'fa fa-check': getValueByPath(rowData, field)}"
                                 aria-hidden="true"></i></span>
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
        <p-contextMenu #cm [target]="cmDiv" [model]="contextMenuItems" appendTo="body">
        </p-contextMenu>
      </div>
    </div>
    @if (visibleDialogMsg) {
      <gtnet-message-edit [visibleDialog]="visibleDialogMsg"
                          [msgCallParam]="msgCallParam"
                          (closeDialog)="handleCloseDialogMsg($event)">
      </gtnet-message-edit>
    }
  `
})

export class GTNetMessageTreeTableComponent extends TreeTableConfigBase implements OnInit, IGlobalMenuAttach {
  @Input() gtNetMessages: GTNetMessage[];
  @Input() incomingPendingIds: Set<number>;
  @Input() outgoingPendingIds: Set<number>;
  @Input() formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  @Output() dataChanged = new EventEmitter<ProcessedActionData>();
  @ViewChild('cm') contextMenu: any;
  @ViewChild('tt') treeTable: TreeTable;

  public static consumedGT = 'consumedGT';

  /** Maps sendRecv values to icon names */
  public static sendRecvIconMap: { [key: string]: string } = {
    [SendReceivedType.SEND]: 'send',
    ['SEND']: 'send',
    [SendReceivedType.RECEIVE]: 'envelope',
    ['RECEIVED']: 'envelope'
  };

  private static iconLoadDone = false;

  rootNode: TreeNode = {children: []};
  selectedNode: TreeNode;
  selectedGTNetMessage: GTNetMessage;
  contextMenuItems: MenuItem[] = [];
  visibleDialogMsg = false;
  msgCallParam: MsgCallParam;

  /** Set of message IDs selected for batch deletion */
  selectedDeletableIds: Set<number> = new Set();

  constructor(private activePanelService: ActivePanelService,
              private gtNetMessageService: GTNetMessageService,
              private gtNetService: GTNetService,
              private iconReg: SvgIconRegistryService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService) {
    super(translateService, gps, filterService);
    GTNetMessageTreeTableComponent.registerIcons(this.iconReg);
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
    this.addColumn(DataType.DateTimeString, 'timestamp', 'SEND_RECV_TIME', true, false,
      {width: 160, filterType: FilterType.likeDataType});
    this.addColumn(DataType.String, 'sendRecv', 'A', true, false,
      {fieldValueFN: this.getSendRecvIcon.bind(this), templateName: 'icon', width: 25});
    this.addColumnFeqH(DataType.String, 'messageCode', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions});
    this.addColumnFeqH(DataType.String, 'message', true, false,
      {filterType: FilterType.likeDataType});
    this.addColumnFeqH(DataType.String, 'visibility', true, false,
      {translateValues: TranslateValue.NORMAL, filterType: FilterType.withOptions, width: 100});
    this.addColumnFeqH(DataType.Boolean, 'hasBeenRead', true, false, {templateName: 'check', width: 60});
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
      nodes?.forEach(node => {
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
    this.gtNetMessages.forEach(gtMessage => {
      const node: TreeNode = {data: gtMessage, leaf: true};
      nodeMap.set(gtMessage.idGtNetMessage, node);
    });

    // Second pass: establish parent-child relationships
    this.gtNetMessages.forEach(gtMessage => {
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
    nodes?.forEach(node => {
      const msg: GTNetMessage = node.data;
      const isReceived = msg.sendRecv === 'RECEIVED' || msg.sendRecv === SendReceivedType.RECEIVE;
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
   * Called when a tree node is selected. Marks received messages as read.
   */
  onSelectionChange(node: TreeNode): void {
    if (!node?.data) {
      return;
    }
    const msg: GTNetMessage = node.data;
    const isReceived = msg.sendRecv === 'RECEIVED' || msg.sendRecv === SendReceivedType.RECEIVE;

    // Mark as read if it's a received message that hasn't been read
    if (isReceived && !msg.hasBeenRead) {
      this.gtNetMessageService.markAsRead(msg.idGtNetMessage).subscribe(() => {
        // Update local state immediately
        msg.hasBeenRead = true;
      });
    }
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event): void {
    event[GTNetMessageTreeTableComponent.consumedGT] = true;
    this.contextMenu && this.contextMenu.hide();
    this.setMenuItemsToActivePanel();
  }

  hideContextMenu(): void {
    this.contextMenu && this.contextMenu.hide();
  }

  callMeDeactivate(): void {
  }

  private setMenuItemsToActivePanel(): void {
    this.activePanelService.activatePanel(this,
      {editMenu: this.getMenuItems()});
    this.contextMenuItems = this.getMenuItems();
  }

  public getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  private getMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = [];
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
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Checks if the selected message can be replied to:
   * - Must be an incoming message (sendRecv === 'RECEIVED' or SendReceivedType.RECEIVE)
   * - Must be a pending (unanswered) request
   * - Must have valid response codes defined
   */
  private canReplyToSelected(): boolean {
    const msg: GTNetMessage = this.selectedNode?.data;
    if (!msg) {
      return false;
    }
    const isIncoming = msg.sendRecv === 'RECEIVED' || msg.sendRecv === SendReceivedType.RECEIVE;
    const isPending = this.incomingPendingIds?.has(msg.idGtNetMessage) ?? false;
    const validResponses = getValidResponseCodes(msg.messageCode);
    return isIncoming && isPending && validResponses.length > 0;
  }

  private replyToSelected(): void {
    const msg: GTNetMessage = this.selectedNode.data;
    const validResponses = getValidResponseCodes(msg.messageCode);
    // Get parent's visibility for inheritance rules
    const parentVisibility = this.getParentVisibility(msg);
    this.msgCallParam = new MsgCallParam(
      this.formDefinitions,
      msg.idGtNet,
      msg.idGtNetMessage,
      null,
      false,
      validResponses,
      null,
      parentVisibility
    );
    this.visibleDialogMsg = true;
  }

  /**
   * Gets the visibility of the thread root message for inheritance rules.
   * If the message being replied to is itself a reply, we traverse up to find the root.
   * For simplicity, we use the immediate parent's visibility - backend will enforce
   * the full inheritance rule.
   */
  private getParentVisibility(msg: GTNetMessage): MessageVisibility {
    const visibilityValue = typeof msg.visibility === 'string'
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
      const fromDateTime = new Date(fromDateTimeParam.paramValue || fromDateTimeParam);
      return fromDateTime > now;
    }
    // Check for discontinued message (closeStartDate)
    const closeStartDateParam = params['closeStartDate'];
    if (closeStartDateParam) {
      const closeStartDate = new Date(closeStartDateParam.paramValue || closeStartDateParam);
      return closeStartDate > now;
    }
    return false;
  }

  /**
   * Sends a cancel message for the selected announcement.
   * Sets idOriginalMessage to link the cancellation to the original message.
   */
  private reverseSelected(): void {
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
    this.visibleDialogMsg = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.dataChanged.emit(processedActionData);
    }
  }

  /**
   * Returns the background color for messages based on their status:
   * - red: delivery failed (all retry attempts exhausted)
   * - greenyellow: incoming pending requests (I need to answer)
   * - yellow: outgoing pending requests (awaiting answer from recipient)
   * - null: normal state
   */
  getPendingBackgroundColor(rowData: GTNetMessage): string | null {
    if (rowData.deliveryStatus === DeliveryStatus.FAILED || rowData.deliveryStatus === 'FAILED') {
      return 'red';
    }
    if (this.incomingPendingIds?.has(rowData.idGtNetMessage)) {
      return 'greenyellow';
    }
    if (this.outgoingPendingIds?.has(rowData.idGtNetMessage)) {
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
    if (event.checked) {
      this.selectedDeletableIds.add(rowData.idGtNetMessage);
    } else {
      this.selectedDeletableIds.delete(rowData.idGtNetMessage);
    }
    // Update menu items to reflect selection state
    this.setMenuItemsToActivePanel();
  }

  /**
   * Returns all deletable messages from the current tree, respecting the filter.
   * A message is deletable if canDelete=true and replyTo=null (request level only).
   */
  private getFilteredDeletableMessages(): GTNetMessage[] {
    const deletable: GTNetMessage[] = [];
    const filteredNodes = this.treeTable?.filteredNodes ?? this.rootNode.children;

    const traverse = (nodes: TreeNode[]) => {
      nodes?.forEach(node => {
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
    return this.getFilteredDeletableMessages().length > 0;
  }

  /**
   * Checks if all deletable messages are currently selected.
   */
  areAllDeletableSelected(): boolean {
    const deletable = this.getFilteredDeletableMessages();
    if (deletable.length === 0) {
      return false;
    }
    return deletable.every(msg => this.selectedDeletableIds.has(msg.idGtNetMessage));
  }

  /**
   * Handles the select all checkbox change event.
   * Toggles selection of all deletable messages based on current state.
   */
  onSelectAllChange(event: any): void {
    const deletable = this.getFilteredDeletableMessages();
    if (event.checked) {
      // Select all deletable messages
      deletable.forEach(msg => this.selectedDeletableIds.add(msg.idGtNetMessage));
    } else {
      // Deselect all deletable messages
      deletable.forEach(msg => this.selectedDeletableIds.delete(msg.idGtNetMessage));
    }
    // Update menu items to reflect selection state
    this.setMenuItemsToActivePanel();
  }

  /**
   * Checks if there are any messages selected for deletion.
   */
  canDeleteSelected(): boolean {
    return this.selectedDeletableIds.size > 0;
  }

  /**
   * Deletes all selected messages.
   * Emits ProcessedActionData with idGtNet so parent can reload only affected messages.
   */
  deleteSelected(): void {
    if (!this.canDeleteSelected()) {
      return;
    }
    const idsToDelete = Array.from(this.selectedDeletableIds);
    // Get idGtNet from the first message (all messages in this component belong to the same GTNet)
    const idGtNet = this.gtNetMessages?.[0]?.idGtNet;
    this.gtNetService.deleteMessageBatch(idsToDelete).subscribe({
      next: () => {
        this.selectedDeletableIds.clear();
        this.dataChanged.emit(new ProcessedActionData(ProcessedAction.DELETED, idGtNet));
      }
    });
  }

  /**
   * Clears the selection of deletable messages.
   * Called from parent component when messages are reloaded.
   */
  clearSelection(): void {
    this.selectedDeletableIds.clear();
    this.setMenuItemsToActivePanel();
  }

}
