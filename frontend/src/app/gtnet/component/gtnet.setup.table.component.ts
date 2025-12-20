import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {GTNet, GTNetCallParam, GTNetWithMessages} from '../model/gtnet';
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
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../lib/base.settings';
import {ContextMenuModule} from 'primeng/contextmenu';
import {TooltipModule} from 'primeng/tooltip';
import {GTNetEditComponent} from './gtnet-edit.component';
import {GTNetMessageEditComponent} from './gtnet-message-edit.component';
import {ConfigurableTableComponent} from '../../lib/datashowbase/configurable-table.component';

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
    GTNetMessageTreeTableComponent
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
      <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[row.idGtNet]"
                               [formDefinitions]="formDefinitions">
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
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'serverOnline', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Boolean, 'serverBusy', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'authorized', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'acceptLastpriceRequest', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'lastpriceServerState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'lastpriceExchange', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Boolean, 'acceptEntityRequest', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'entityServerState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.String, 'entityExchange', true, false,
      {translateValues: TranslateValue.NORMAL});

    this.multiSortMeta.push({field: this.domainRemoteName, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    this.callParam = {gtNet: entity, isMyEntry: !!entity && this.isMyEntry(entity, null)};
  }

  protected override readData(): void {
    const observable = [this.gtNetService.getAllGTNetsWithMessages(),
      ...(!this.formDefinitions ? [this.gtNetMessageService.getAllFormDefinitionsWithClass()] : [])];

    combineLatest(observable).subscribe((data,) => {
      this.gtNetList = (<GTNetWithMessages>data[0]).gtNetList;
      this.gtNetMyEntryId = (<GTNetWithMessages>data[0]).gtNetMyEntryId;
      this.createTranslatedValueStoreAndFilterField(this.gtNetList);
      this.gtNetMessageMap = (<GTNetWithMessages>data[0]).gtNetMessageMap;
      this.formDefinitions ??= <{ [type: string]: ClassDescriptorInputAndShow }>data[1];
      this.prepareTableAndTranslate();
    });
  }

  override onComponentClick(event): void {
    if (!event[GTNetMessageTreeTableComponent.consumedGT]) {
      this.resetMenu(this.selectedEntity);
    }
  }

  public override getEditMenuItems(): MenuItem[] {
    const menuItems: MenuItem[] = super.getEditMenuItems(this.selectedEntity);
    menuItems.push({separator: true});
    menuItems.push({
      label: 'GT_NET_MESSAGE_SEND', command: (e) => this.sendMsgSelected(),
      disabled: !this.selectedEntity && !this.gtNetMyEntryId
    });
    return menuItems;
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET;
  }

  private sendMsgSelected(): void {
    this.msgCallParam = new MsgCallParam(this.formDefinitions, this.selectedEntity.idGtNet, null, null);
    this.visibleDialogMsg = true;
  }

  handleCloseDialogMsg(dynamicMsg: any): void {
    this.visibleDialogMsg = false;
  }

  canExpand(row: GTNet): boolean {
    return !!(this.gtNetMessageMap && this.gtNetMessageMap[row.idGtNet]);
  }

  isMyEntry(row: GTNet, field: ColumnConfig): boolean {
    return row.idGtNet === this.gtNetMyEntryId;
  }

  protected override hasRightsForUpdateEntity(row: GTNet): boolean {
    return this.isMyEntry(row, null);
  }

}
