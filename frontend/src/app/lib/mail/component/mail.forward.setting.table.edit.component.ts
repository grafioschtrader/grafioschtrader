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
  MailSendForwardDefaultConfig,
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

/**
 * Editing this information class is very simple. Therefore
 * there is this prototype that uses an implementation of row editing from the PrimeNG table.
 * First conclusion: obviously, the Angular Reactive Form is not supported for row editing.
 * The following negative points were noticed:
 * - Multiple rows can be edited at a given time.
 * - The activation and termination of editing are controlled by the template. A programmatic control would be desirable.
 *   See https://stackoverflow.com/questions/74102652/primeng-table-programmatically-handle-row-editing-psaveeditablerow.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <p-table [columns]="fields" [value]="mailSettingForwardList" selectionMode="single"
               [(selection)]="selectedEntity" (onRowSelect)="onRowSelect($event)"
               (onRowUnselect)="onRowUnselect($event)" dataKey="idMailSettingForward" editMode="row"
               (sortFunction)="customSort($event)" [customSort]="true"
               stripedRows showGridlines>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field"
                  [pTooltip]="field.headerTooltipTranslated"
                  [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{field.headerTranslated}}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
            <th style="width:20%"></th>
          </tr>
        </ng-template>
        <ng-template #body let-elEdit let-columns="fields" let-editing="editing" let-ri="rowIndex">
          <tr [pEditableRow]="elEdit" [pSelectableRow]="elEdit">
            @for (field of fields; track field) {
              <td [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">

                <p-cellEditor>

                  <ng-template pTemplate="input">
                    @if (canEdit(field, elEdit)) {
                      @switch (field.dataType) {
                        @case (DataType.String) {
                          <select #input
                                  [ngStyle]="{'width': (field.width+1) + 'em'}"
                                  class="form-control input-sm"
                                  [(ngModel)]="elEdit[field.field]"
                                  [id]="field.field">
                            @for (s of field.cec.valueKeyHtmlOptions; track s) {
                              <option [value]="s.key"
                                      [disabled]="s.disabled">
                                {{ s.value }}
                              </option>
                            }
                          </select>
                        }
                        @case (DataType.NumericInteger) {
                          <input pInputText type="number" [(ngModel)]="elEdit[field.field]">
                        }
                      }
                    } @else {
                      {{getValueByPath(elEdit, field)}}
                    }
                  </ng-template>
                  <ng-template pTemplate="output">
                    {{getValueByPath(elEdit, field)}}
                  </ng-template>
                </p-cellEditor>
              </td>
            }
            <td>
              <div class="flex align-items-center justify-content-center gap-2">
                @if (!editing) {
                  <button pButton pRipple type="button" pInitEditableRow
                          icon="pi pi-pencil"
                          (click)="onRowEditInit(elEdit)" class="p-button-rounded p-button-text"></button>
                }
                @if (editing) {
                  <button pButton pRipple type="button" pSaveEditableRow icon="pi pi-check"
                          (click)="onRowEditSave(elEdit)"
                          class="p-button-rounded p-button-text p-button-success mr-2"></button>
                }
                @if (editing) {
                  <button pButton pRipple type="button" pCancelEditableRow
                          icon="pi pi-times"
                          (click)="onRowEditCancel(elEdit, ri)"
                          class="p-button-rounded p-button-text p-button-danger"></button>
                }
              </div>
            </td>
          </tr>
        </ng-template>
      </p-table>
      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>
  `,
    standalone: false
})
export class MailForwardSettingTableEditComponent extends TableEditConfigBase implements OnInit, IGlobalMenuAttach {
  contextMenuItems: MenuItem[] = [];
  mailSettingForwardList: MailSettingForward[];
  selectedEntity: MailSettingForward;

  clonedProducts: { [id: number]: MailSettingForward } = {};
  private msfdc: MailSendForwardDefaultConfig;
  private mailSendForwardDefault: MailSendForwardDefault;

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

    this.addEditColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_COM_TYPE, true,
      {translateValues: TranslateValue.NORMAL, width: 450});
    this.addEditColumnFeqH(DataType.String, MailSettingForwardVar.MESSAGE_TARGET_TYPE, true,
      {translateValues: TranslateValue.NORMAL, width: 450});
    this.addEditColumnFeqH(DataType.NumericInteger, MailSettingForwardVar.ID_USER_DIRECT, false);
  }

  isActivated(): boolean {
    return this.activePanelService.isActivated(this);
  }

  onComponentClick(event: any): void {
    this.resetMenu();
  }

  hideContextMenu(): void {
  }

  callMeDeactivate(): void {
  }

  getHelpContextId(): string {
    return HelpIds.HELP_MESSAGE_SYSTEM;
  }

  ngOnInit(): void {
    this.mailSettingForwardService.getSendForwardDefault().subscribe((msfd: MailSendForwardDefault) => {
      this.mailSendForwardDefault = msfd;
      this.readData();
    });
  }

  private readData(): void {
    this.mailSettingForwardService.getMailSettingForwardByUser().subscribe((msfList: MailSettingForward[]) => {
      this.mailSettingForwardList = msfList;

      this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_COM_TYPE).cec = {};
      this.refreshNotSetMessageComType();
      this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_TARGET_TYPE).cec = {};
      this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_TARGET_TYPE).cec.valueKeyHtmlOptions =
        SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
          MessageTargetType, [], true);
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(this.mailSettingForwardList);
    });
  }

  private resetMenu(): void {
    this.contextMenuItems = this.getEditMenu(this.selectedEntity);
    this.activePanelService.activatePanel(this, {
      showMenu: null,
      editMenu: this.contextMenuItems
    });
  }

  private getEditMenu(selectedEntity: MailSettingForward): MenuItem[] {
    const menuItems: MenuItem[] = [
      {
        label: 'CREATE|' + this.MAIL_SETTING_FORWARD,
        command: (e) => this.handleAddEntity(),
        disabled: this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_COM_TYPE).cec.valueKeyHtmlOptions.length === 0
      },
      {
        label: 'DELETE_RECORD|' + this.MAIL_SETTING_FORWARD,
        command: (e) => this.handleDeleteEntity(selectedEntity),
        disabled: !selectedEntity
      }];
    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  handleAddEntity(): void {
    this.mailSettingForwardList.push(new MailSettingForward());
  }

  handleDeleteEntity(entity: MailSettingForward) {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|' + this.MAIL_SETTING_FORWARD, () => {
        this.mailSettingForwardService.deleteEntity(entity.idMailSettingForward).subscribe(response => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS,
            'MSG_DELETE_RECORD', {i18nRecord: this.MAIL_SETTING_FORWARD});
          this.resetMenu();
          this.readData();
        });
      });
  }

  canEdit(columnConfig: ColumnConfig, mailSettingForward: MailSettingForward): boolean {
    return !(mailSettingForward.idMailSettingForward > 0 && columnConfig.field === MailSettingForwardVar.MESSAGE_COM_TYPE);
  }

  onRowEditInit(mailSettingForward: MailSettingForward) {
    this.clonedProducts[mailSettingForward.idMailSettingForward] = Object.assign(new MailSettingForward(), mailSettingForward);
  }

  onRowEditSave(mailSettingForward: MailSettingForward) {
    delete this.clonedProducts[mailSettingForward.idMailSettingForward];
  }

  onRowEditCancel(mailSettingForward: MailSettingForward, index: number) {
    this.mailSettingForwardList[index] = this.clonedProducts[mailSettingForward.idMailSettingForward];
    delete this.clonedProducts[mailSettingForward.idMailSettingForward];
  }

  onRowSelect(event): void {
    this.msfdc = this.mailSendForwardDefault.mailSendForwardDefaultMapForUser[event.data.messageComType];
    this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_TARGET_TYPE).cec.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        MessageTargetType, this.msfdc.mttPossibleTypeSet, false);
  }

  private refreshNotSetMessageComType(): void {
    const mctList = this.mailSettingForwardList.map(msfl =>
      MessageComType[MessageComType[msfl.messageComType]]);
    const possibleMsgComType = Object.keys(this.mailSendForwardDefault.mailSendForwardDefaultMapForUser).filter(
      mct => mctList.indexOf(MessageComType[MessageComType[mct]]) === -1);
    this.getColumnConfigByField(MailSettingForwardVar.MESSAGE_COM_TYPE).cec.valueKeyHtmlOptions =
      SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
        MessageComType, possibleMsgComType, false);
  }

  onRowUnselect(event): void {
    this.msfdc = null;
  }

}
