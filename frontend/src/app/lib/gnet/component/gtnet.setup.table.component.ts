import {Component, Injector, QueryList, ViewChildren} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {AcceptRequestTypes, ExchangeKindTypeInfo, GTNet, GTNetCallParam, GTNetWithMessages} from '../model/gtnet';
import {GTNetMessage, MsgCallParam} from '../model/gtnet.message';
import {GTNetService} from '../service/gtnet.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {HelpIds} from '../../help/help.ids';
import {GTNetMessageTreeTableComponent} from './gtnet-message-treetable.component';
import {GTNetConfigEntityTableComponent} from './gtnet-config-entity-table.component';
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../base.settings';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {GTNetEditComponent} from './gtnet-edit.component';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {ProcessedAction} from '../../types/processed.action';
import {ProcessedActionData} from '../../types/processed.action.data';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    TranslateModule,
    ConfigurableTableComponent,
    ContextMenuModule,
    TooltipModule,
    GTNetEditComponent,
    GTNetMessageEditComponent,
    GTNetMessageTreeTableComponent,
    GTNetConfigEntityTableComponent
  ],
  template: `
    <configurable-table
      [data]="gtNetList"
      [fields]="fields"
      [dataKey]="'idGtNet'"
      [(selection)]="selectedEntity"
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

      <h4 caption>{{ 'GT_NET_NET_AND_MESSAGE' | translate }}
        @if (!gtNetMyEntryId) {
          <div>
            <span style="color:red; font-size: 80%">{{ 'GT_NET_COMM_REQUIREMENT' | translate }}</span>
          </div>
        } @else if (gtNetList.length === 1) {
          <div>
            <span style="color:blue; font-size: 80%">{{ 'GT_NET_COMM_REQUIREMENT_REMOTE' | translate }}</span>
          </div>
        }
      </h4>

    </configurable-table>

    <ng-template #expandedRow let-row>
      @if (hasConfigEntity(row)) {
        <gtnet-config-entity-table
          [gtNetEntities]="row.gtNetEntities"
          (dataChanged)="onConfigEntityDataChanged($event)">
        </gtnet-config-entity-table>
      }
      @if (isLoadingMessages(row.idGtNet)) {
        <div style="padding: 1rem; text-align: center;">
          <i class="fa fa-spinner fa-spin"></i> {{ 'LOADING' | translate }}...
        </div>
      } @else if (gtNetMessageMap[row.idGtNet]?.length) {
        <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[row.idGtNet]"
                                 [incomingPendingIds]="getIncomingPendingIds(row.idGtNet)"
                                 [outgoingPendingIds]="getOutgoingPendingIds(row.idGtNet)"
                                 [formDefinitions]="formDefinitions"
                                 (dataChanged)="onTreeTableDataChanged($event)">
        </gtnet-message-treetable>
      }
    </ng-template>

    @if (visibleDialog) {
      <gtnet-edit [visibleDialog]="visibleDialog"
                  [callParam]="callParam"
                  (closeDialog)="handleCloseDialog($event)">
      </gtnet-edit>
    }
    @if (visibleDialogMsg) {
      <gtnet-message-edit [visibleDialog]="visibleDialogMsg"
                          [msgCallParam]="msgCallParam"
                          (closeDialog)="handleCloseDialogMsg($event)">
      </gtnet-message-edit>
    }
  `,
  providers: [DialogService]
})
export class GTNetSetupTableComponent extends TableCrudSupportMenu<GTNet> {
  @ViewChildren(GTNetMessageTreeTableComponent) messageTreeTables: QueryList<GTNetMessageTreeTableComponent>;

  minDate: Date = new Date('2000-01-01');
  maxDate: Date = new Date('2099-12-31');
  private readonly domainRemoteName = 'domainRemoteName';
  callParam: GTNetCallParam;
  gtNetList: GTNet[];
  gtNetMyEntryId: number;
  /** Message count per idGtNet - used to determine if expander should show */
  gtNetMessageCountMap: { [key: number]: number } = {};
  /** Cache for loaded messages (lazy loaded when row is expanded) */
  gtNetMessageMap: { [key: number]: GTNetMessage[] } = {};
  /** Set of idGtNet values for which messages have been loaded */
  loadedMessageIds = new Set<number>();
  /** Set of idGtNet values currently being loaded */
  loadingMessageIds = new Set<number>();
  outgoingPendingReplies: { [key: number]: number[] };
  incomingPendingReplies: { [key: number]: number[] };
  idOpenDiscontinuedMessage: number;
  exchangeKindTypes: ExchangeKindTypeInfo[] = [];
  private dynamicKindFields: ColumnConfig[] = [];
  formDefinitions: { [type: string]: ClassDescriptorInputAndShow };
  visibleDialogMsg = false;
  msgCallParam: MsgCallParam;

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

    super(BaseSettings.GT_NET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService, injector,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create, CrudMenuOptions.Allow_Edit] : []);

    this.addColumnFeqH(DataType.String, this.domainRemoteName, true, false,
      {width: 200, templateName: 'owner'});
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {width: 120});
    this.addColumnFeqH(DataType.Boolean, 'spreadCapability', true, false,
      {templateName: 'check', width: 30});
    this.addColumnFeqH(DataType.NumericInteger, 'dailyRequestLimit', true, false);
    this.addColumnFeqH(DataType.String, 'serverOnline', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Boolean, 'serverBusy', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'allowServerCreation', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'authorized', true, false,
      {templateName: 'check', fieldValueFN: this.isAuthorizedRemote.bind(this)});
    this.addColumnFeqH(DataType.NumericInteger, 'toBeAnswered', true, false,
      {fieldValueFN: this.getToBeAnsweredCount.bind(this)});
    this.addColumnFeqH(DataType.Numeric, 'answerExpected', true, false,
      {fieldValueFN: this.getAnswerExpectedCount.bind(this)});

    this.multiSortMeta.push({field: this.domainRemoteName, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    this.callParam = {
      gtNet: entity,
      isMyEntry: this.gtNetList.length === 0 || !!entity && this.isMyEntry(entity, null),
      exchangeKindTypes: this.exchangeKindTypes
    };
  }

  protected override readData(): void {
    const observable = [this.gtNetService.getAllGTNetsWithMessages(),
      ...(!this.formDefinitions ? [this.gtNetMessageService.getAllFormDefinitionsWithClass()] : [])];

    combineLatest(observable).subscribe((data,) => {
      const response = <GTNetWithMessages>data[0];
      this.gtNetList = response.gtNetList;
      this.exchangeKindTypes = response.exchangeKindTypes || [];
      this.addSyncableKindColumns();
      this.mapGTNetEntityToGTNet();
      this.gtNetMyEntryId = response.gtNetMyEntryId;
      this.idOpenDiscontinuedMessage = response.idOpenDiscontinuedMessage;
      this.createTranslatedValueStoreAndFilterField(this.gtNetList);
      this.gtNetMessageCountMap = response.gtNetMessageCountMap || {};
      // Clear message cache on data refresh
      this.gtNetMessageMap = {};
      this.loadedMessageIds.clear();
      this.loadingMessageIds.clear();
      this.outgoingPendingReplies = response.outgoingPendingReplies;
      this.incomingPendingReplies = response.incomingPendingReplies;
      this.formDefinitions ??= <{ [type: string]: ClassDescriptorInputAndShow }>data[1];
      this.prepareTableAndTranslate();
    });
  }

  override onComponentClick(event): void {
    if (!event[GTNetMessageTreeTableComponent.consumedGT]) {
      this.resetMenu(this.selectedEntity);
    }
  }

  private addSyncableKindColumns(): void {
    this.dynamicKindFields.forEach(f => {
      const idx = this.fields.indexOf(f);
      if (idx >= 0) {
        this.fields.splice(idx, 1);
      }
    });
    this.dynamicKindFields = [];
    for (const kind of this.exchangeKindTypes.filter(k => k.syncable)) {
      this.dynamicKindFields.push(
        this.addColumn(DataType.String, `accept_${kind.name}`, 'ACCEPT_REQUEST', true, false,
          {translateValues: TranslateValue.NORMAL, headerGroupKey: kind.name}),
        this.addColumn(DataType.String, `serverState_${kind.name}`, 'SERVER_STATE', true, false,
          {translateValues: TranslateValue.NORMAL, headerGroupKey: kind.name}),
        this.addColumn(DataType.NumericInteger, `maxLimit_${kind.name}`, 'GT_NET_MAX_LIMIT', true, false,
          {headerGroupKey: kind.name})
      );
    }
  }

  private mapGTNetEntityToGTNet(): void {
    const syncableKinds = this.exchangeKindTypes.filter(k => k.syncable);
    const syncableValues = new Set(syncableKinds.map(k => k.value));
    this.gtNetList.forEach(gtNet => gtNet.gtNetEntities.forEach(e => {
      const kindValue = typeof e.entityKind === 'number' ? e.entityKind : Number(e.entityKind);
      if (!syncableValues.has(kindValue)) {
        return;
      }
      const kindName = this.exchangeKindTypes.find(k => k.value === kindValue)?.name ?? String(kindValue);
      const acceptRequestName = typeof e.acceptRequest === 'number'
        ? AcceptRequestTypes[e.acceptRequest] : e.acceptRequest;
      gtNet[`accept_${kindName}`] = acceptRequestName;
      gtNet[`serverState_${kindName}`] = e.serverState;
      gtNet[`maxLimit_${kindName}`] = e.maxLimit;
    }));
  }

  isAuthorizedRemote(dataobject: any, field: ColumnConfig, valueField: any): boolean {
    return this.isAuthorizedRemoteEntry(dataobject);
  }

  public override getEditMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = super.getEditMenuItems(this.selectedEntity);
    menuItems.push({separator: true});
    menuItems.push({
      label: 'GT_NET_MESSAGE_SEND', command: (e) => this.sendMsg(),
      disabled: !this.canSendMessage()
    });
    return menuItems;
  }

  /**
   * Checks if sending a message is possible:
   * - With selection: target must not be my own entry
   * - Without selection: at least one authorized remote entry must exist (for ALL messages)
   */
  private canSendMessage(): boolean {
    if (this.selectedEntity) {
      return this.selectedEntity.idGtNet !== this.gtNetMyEntryId;
    }
    return this.hasAuthorizedRemoteEntry();
  }

  /**
   * Checks if at least one remote GTNet entry has been authorized (handshake completed with token exchange).
   * Authorization requires both tokenThis and tokenRemote to be present in gtNetConfig.
   * Required for sending ALL broadcast messages.
   */
  private hasAuthorizedRemoteEntry(): boolean {
    return this.gtNetList?.some(gtNet => this.isAuthorizedRemoteEntry(gtNet)) ?? false;
  }

  private isAuthorizedRemoteEntry(gtNet: GTNet): boolean {
    return gtNet.gtNetConfig?.authorizedRemoteEntry ?? false;
  }

  private sendMsg(): void {
    const isAllMessage = !this.selectedEntity;
    const idGTNet = this.selectedEntity?.idGtNet ?? null;
    this.msgCallParam = new MsgCallParam(this.formDefinitions, idGTNet, null, null, isAllMessage, null,
      this.idOpenDiscontinuedMessage);
    // Exclude admin messages - they should only be sent from GTNetAdminMessagesComponent
    this.msgCallParam.excludeAdminMessages = true;
    this.visibleDialogMsg = true;
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  handleCloseDialogMsg(processedActionData: ProcessedActionData): void {
    this.visibleDialogMsg = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  canExpand(row: GTNet): boolean {
    const hasMessages = (this.gtNetMessageCountMap[row.idGtNet] ?? 0) > 0;
    return hasMessages || this.hasConfigEntity(row);
  }

  /**
   * Handles row expansion event - lazy loads messages if not already loaded.
   */
  onRowExpand(event: { data: GTNet }): void {
    const idGtNet = event.data.idGtNet;
    const hasMessages = (this.gtNetMessageCountMap[idGtNet] ?? 0) > 0;

    // Only load if there are messages and not already loaded or loading
    if (hasMessages && !this.loadedMessageIds.has(idGtNet) && !this.loadingMessageIds.has(idGtNet)) {
      this.loadingMessageIds.add(idGtNet);
      this.gtNetService.getMessagesByIdGtNet(idGtNet).subscribe({
        next: (messages) => {
          this.gtNetMessageMap[idGtNet] = messages;
          this.loadedMessageIds.add(idGtNet);
          this.loadingMessageIds.delete(idGtNet);
          // Clear selection in tree table after messages are loaded
          this.clearTreeTableSelection();
        },
        error: () => {
          this.loadingMessageIds.delete(idGtNet);
        }
      });
    }
  }

  /**
   * Clears the selection in all message tree table components.
   */
  private clearTreeTableSelection(): void {
    // Use setTimeout to ensure the tree table component is rendered after data update
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
    return this.isMyEntry(row, null);
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
      // Check if this is a deletion with idGtNet - reload only messages for that GTNet
      if (processedActionData.action === ProcessedAction.DELETED && typeof processedActionData.data === 'number') {
        const idGtNet = processedActionData.data as number;
        this.reloadMessagesForGtNet(idGtNet);
      } else {
        this.readData();
      }
    }
  }

  /**
   * Reloads messages for a specific GTNet without reloading the entire table.
   * Used after batch deletion to refresh only the affected messages.
   */
  private reloadMessagesForGtNet(idGtNet: number): void {
    // Mark as not loaded to allow reload
    this.loadedMessageIds.delete(idGtNet);
    this.loadingMessageIds.add(idGtNet);

    this.gtNetService.getMessagesByIdGtNet(idGtNet).subscribe({
      next: (messages) => {
        this.gtNetMessageMap[idGtNet] = messages;
        this.loadedMessageIds.add(idGtNet);
        this.loadingMessageIds.delete(idGtNet);
        // Clear selection in tree table after messages are loaded
        this.clearTreeTableSelection();
      },
      error: () => {
        this.loadingMessageIds.delete(idGtNet);
      }
    });
  }

  /** Handle data changes from the config entity table */
  onConfigEntityDataChanged(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
  }

  /** Check if any GTNetEntity has a GTNetConfigEntity */
  hasConfigEntity(row: GTNet): boolean {
    return row.gtNetEntities?.some(entity => entity.gtNetConfigEntity != null) ?? false;
  }

}
