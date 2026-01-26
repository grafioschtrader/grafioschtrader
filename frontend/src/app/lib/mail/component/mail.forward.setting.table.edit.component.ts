import {Component, OnInit} from '@angular/core';
import {IGlobalMenuAttach} from '../../mainmenubar/component/iglobal.menu.attach';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {MailSettingForwardService} from '../service/mail.setting.forward.service';
import {
  MailSendForwardDefault,
  MailSettingForward,
  MailSettingForwardVar,
  MessageComType,
  MessageTargetType
} from '../model/mail.send.recv';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {TableEditConfigBase} from '../../datashowbase/table.edit.config.base';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {TranslateHelper} from '../../helper/translate.helper';
import {AppHelper} from '../../helper/app.helper';
import {InfoLevelType} from '../../message/info.leve.type';
import {MessageToastService} from '../../message/message.toast.service';
import {HelpIds} from '../../help/help.ids';
import {EditableTableComponent, RowEditSaveEvent} from '../../datashowbase/editable-table.component';
import {TranslateModule} from '@ngx-translate/core';

/**
 * Component for editing mail forwarding settings using the reusable EditableTableComponent.
 * Allows users to configure how different message types should be forwarded (internal mail,
 * external mail, or no forwarding).
 */
@Component({
  template: `
    <editable-table
      [(data)]="mailSettingForwardList"
      [fields]="fields"
      dataKey="rowKey"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale"
      [customSortFn]="customSort.bind(this)"
      [createNewEntityFn]="createNewEntity.bind(this)"
      [contextMenuItems]="contextMenuItems"
      [showContextMenu]="isActivated()"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      (rowEditSave)="onRowEditSave($event)"
      (rowEditCancel)="onRowEditCancel($event)"
      (rowAdded)="onRowAdded($event)"
      (rowSelect)="onRowSelect($event)"
      (rowUnselect)="onRowUnselect($event)"
      (componentClick)="onComponentClick($event)">
    </editable-table>
  `,
  standalone: true,
  imports: [EditableTableComponent, TranslateModule]
})
export class MailForwardSettingTableEditComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {
  contextMenuItems: MenuItem[] = [];
  mailSettingForwardList: MailSettingForward[] = [];
  selectedEntity: MailSettingForward;

  private mailSendForwardDefault: MailSendForwardDefault;
  private newRowCounter = 0;

  private readonly MAIL_SETTING_FORWARD = 'MAIL_SETTING_FORWARD';

  constructor(private activePanelService: ActivePanelService,
              private mailSettingForwardService: MailSettingForwardService,
              private messageToastService: MessageToastService,
              private confirmationService: ConfirmationService,
              filterService: FilterService,
              translateService: TranslateService,
              gps: GlobalparameterService,
              usersettingsService: UserSettingsService) {
    super(filterService, usersettingsService, translateService, gps);

    // Configure MESSAGE_COM_TYPE column
    this.addEditColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_COM_TYPE, true,
      {translateValues: TranslateValue.NORMAL, width: 450});
    const comTypeCol = this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_COM_TYPE);
    comTypeCol.cec.canEditFn = (row: MailSettingForward) => !row.idMailSettingForward;

    // Configure MESSAGE_TARGET_TYPE column with dependent dropdown
    this.addEditColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_TARGET_TYPE, true,
      {translateValues: TranslateValue.NORMAL, width: 450});
    const targetTypeCol = this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_TARGET_TYPE);
    targetTypeCol.cec.dependsOnField = MailSettingForwardVar.MESSAGE_COM_TYPE;
    targetTypeCol.cec.optionsProviderFn = (row: MailSettingForward) => this.getTargetTypeOptions(row);

    // Configure ID_USER_DIRECT column
    this.addEditColumnFeqH(DataType.NumericInteger, MailSettingForwardVar.ID_USER_DIRECT, false);
  }

  // ============================================================================
  // IGlobalMenuAttach Implementation
  // ============================================================================

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

  // ============================================================================
  // Lifecycle
  // ============================================================================

  ngOnInit(): void {
    this.mailSettingForwardService.getSendForwardDefault().subscribe((msfd: MailSendForwardDefault) => {
      this.mailSendForwardDefault = msfd;
      this.readData();
    });
  }

  // ============================================================================
  // Data Loading
  // ============================================================================

  private readData(): void {
    this.mailSettingForwardService.getMailSettingForwardByUser().subscribe((msfList: MailSettingForward[]) => {
      this.mailSettingForwardList = msfList.map(msf => {
        (msf as any).rowKey = msf.idMailSettingForward
          ? `existing_${msf.idMailSettingForward}`
          : `new_${this.newRowCounter++}`;
        return msf;
      });

      this.refreshMessageComTypeOptions();
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(this.mailSettingForwardList);
    });
  }

  // ============================================================================
  // Dropdown Options
  // ============================================================================

  /** Provides options for MESSAGE_TARGET_TYPE based on selected MESSAGE_COM_TYPE */
  private getTargetTypeOptions(row: MailSettingForward): any[] {
    if (row.messageComType != null && this.mailSendForwardDefault) {
      const msfdc = this.mailSendForwardDefault.mailSendForwardDefaultMapForUser[row.messageComType];
      if (msfdc) {
        // Set default value if not already set
        if (row.messageTargetType == null && msfdc.messageTargetDefaultType != null) {
          row.messageTargetType = msfdc.messageTargetDefaultType as MessageTargetType;
        }
        return SelectOptionsHelper.createHtmlOptionsFromEnum(
          this.translateService, MessageTargetType, msfdc.mttPossibleTypeSet, false);
      }
    }
    return [];
  }

  /** Updates available MESSAGE_COM_TYPE options (excludes already used types) */
  private refreshMessageComTypeOptions(): void {
    const usedTypes = this.mailSettingForwardList
      .filter(msf => msf.messageComType != null)
      .map(msf => MessageComType[MessageComType[msf.messageComType]]);

    const availableTypes = Object.keys(this.mailSendForwardDefault.mailSendForwardDefaultMapForUser)
      .filter(mct => usedTypes.indexOf(MessageComType[MessageComType[mct]]) === -1);

    this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_COM_TYPE).cec.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService, MessageComType, availableTypes, false);
  }

  // ============================================================================
  // Entity Factory
  // ============================================================================

  /** Creates a new MailSettingForward entity for inline editing */
  createNewEntity = (): MailSettingForward => {
    const entity = new MailSettingForward();
    (entity as any).rowKey = `new_${this.newRowCounter++}`;
    return entity;
  };

  // ============================================================================
  // Context Menu
  // ============================================================================

  onComponentClick(event: any): void {
    this.resetMenu();
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu();
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = [
      {
        label: 'DELETE_RECORD|' + this.MAIL_SETTING_FORWARD,
        command: () => this.handleDeleteEntity(this.selectedEntity),
        disabled: !this.selectedEntity || !this.selectedEntity.idMailSettingForward
      }
    ];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  // ============================================================================
  // Edit Event Handlers
  // ============================================================================

  onRowEditSave(event: RowEditSaveEvent<MailSettingForward>): void {
    const entity = event.row;

    if (entity.messageComType == null || entity.messageTargetType == null) {
      this.messageToastService.showMessageI18n(InfoLevelType.WARNING, 'REQUIRED_FIELDS_MISSING');
      return;
    }

    this.mailSettingForwardService.update(entity).subscribe({
      next: () => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
          event.isNew ? 'MSG_RECORD_CREATED' : 'MSG_RECORD_SAVED',
          {i18nRecord: this.MAIL_SETTING_FORWARD});
        this.readData();
      }
    });
  }

  onRowEditCancel(event: any): void {
    // Refresh options since a new row might have been cancelled
    this.refreshMessageComTypeOptions();
  }

  onRowAdded(event: any): void {
    // Refresh MESSAGE_COM_TYPE options when a new row is added
    this.refreshMessageComTypeOptions();
  }

  // ============================================================================
  // Selection Handlers
  // ============================================================================

  onRowSelect(event: any): void {
    this.resetMenu();
  }

  onRowUnselect(event: any): void {
    this.resetMenu();
  }

  // ============================================================================
  // Delete Handler
  // ============================================================================

  private handleDeleteEntity(entity: MailSettingForward): void {
    if (!entity?.idMailSettingForward) {
      return;
    }

    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.MAIL_SETTING_FORWARD, () => {
        this.mailSettingForwardService.deleteEntity(entity.idMailSettingForward).subscribe(() => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: this.MAIL_SETTING_FORWARD});
          this.selectedEntity = null;
          this.resetMenu();
          this.readData();
        });
      });
  }
}
