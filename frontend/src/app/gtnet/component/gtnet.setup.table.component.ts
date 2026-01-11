import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {
  AcceptRequestTypes, GTNet, GTNetCallParam,
  GTNetEntity, GTNetExchangeKindType, GTNetServerStateTypes, GTNetWithMessages
} from '../model/gtnet';
import {GTNetMessage, MsgCallParam} from '../model/gtnet.message';
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
import {GTNetConfigEntityTableComponent} from './gtnet-config-entity-table.component';
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../lib/base.settings';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {GTNetEditComponent} from './gtnet-edit.component';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';

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
      (componentClick)="onComponentClick($event)">

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
      <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[row.idGtNet]"
                               [incomingPendingIds]="getIncomingPendingIds(row.idGtNet)"
                               [outgoingPendingIds]="getOutgoingPendingIds(row.idGtNet)"
                               [formDefinitions]="formDefinitions"
                               (dataChanged)="onTreeTableDataChanged($event)">
      </gtnet-message-treetable>
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
  minDate: Date = new Date('2000-01-01');
  maxDate: Date = new Date('2099-12-31');
  private readonly domainRemoteName = 'domainRemoteName';
  callParam: GTNetCallParam;
  gtNetList: GTNet[];
  gtNetMyEntryId: number;
  gtNetMessageMap: { [key: number]: GTNetMessage[] };
  outgoingPendingReplies: { [key: number]: number[] };
  incomingPendingReplies: { [key: number]: number[] };
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
    usersettingsService: UserSettingsService) {

    super(AppSettings.GT_NET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
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
    this.addColumnFeqH(DataType.String, 'acceptLastpriceRequest', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'lastpriceServerState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.NumericInteger, 'lastpriceMaxLimit', true, false);
    this.addColumnFeqH(DataType.String, 'historicalPriceRequest', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'historicalPriceServerState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.NumericInteger, 'historicalMaxLimit', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'toBeAnswered', true, false,
      {fieldValueFN: this.getToBeAnsweredCount.bind(this)});
    this.addColumnFeqH(DataType.Numeric, 'answerExpected', true, false,
      {fieldValueFN: this.getAnswerExpectedCount.bind(this)});
    this.multiSortMeta.push({field: this.domainRemoteName, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    this.callParam = {gtNet: entity, isMyEntry: this.gtNetList.length === 0 || !!entity && this.isMyEntry(entity, null)};
  }

  protected override readData(): void {
    const observable = [this.gtNetService.getAllGTNetsWithMessages(),
      ...(!this.formDefinitions ? [this.gtNetMessageService.getAllFormDefinitionsWithClass()] : [])];

    combineLatest(observable).subscribe((data,) => {
      const response = <GTNetWithMessages>data[0];
      this.gtNetList = response.gtNetList;
      this.mapGTNetEntityToGTNet();
      this.gtNetMyEntryId = response.gtNetMyEntryId;
      this.createTranslatedValueStoreAndFilterField(this.gtNetList);
      this.gtNetMessageMap = response.gtNetMessageMap;
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

  private mapGTNetEntityToGTNet() {
    this.gtNetList.forEach(gtNet => gtNet.gtNetEntities.forEach(e => {
      // Convert acceptRequest to enum name string for translation
      const acceptRequestName = typeof e.acceptRequest === 'number'
        ? AcceptRequestTypes[e.acceptRequest]
        : e.acceptRequest;
      if(e.entityKind === GTNetExchangeKindType[GTNetExchangeKindType.LAST_PRICE]) {
        gtNet['acceptLastpriceRequest'] = acceptRequestName;
        gtNet['lastpriceServerState'] = e.serverState;
        gtNet['lastpriceMaxLimit'] = e.maxLimit;
      } else {
        gtNet['historicalPriceRequest'] = acceptRequestName;
        gtNet['historicalPriceServerState'] = e.serverState;
        gtNet['historicalMaxLimit'] = e.maxLimit;
      }
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
    this.msgCallParam = new MsgCallParam(this.formDefinitions, idGTNet, null, null, isAllMessage);
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
    return !!(this.gtNetMessageMap && this.gtNetMessageMap[row.idGtNet]?.length);
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

  /** Handle data changes from the tree table (e.g., reply sent) */
  onTreeTableDataChanged(processedActionData: ProcessedActionData): void {
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.readData();
    }
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
