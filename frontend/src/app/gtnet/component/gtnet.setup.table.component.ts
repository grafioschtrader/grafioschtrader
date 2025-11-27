import {Component} from '@angular/core';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {GTNet, GTNetWithMessages} from '../model/gtnet';
import {GTNetMessage, MsgCallParam} from '../model/gtnet.message';
import {GTNetService} from '../service/gtnet.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {TranslateValue} from '../../lib/datashowbase/column.config';
import {HelpIds} from '../../lib/help/help.ids';
import {GTNetMessageTreeTableComponent} from './gtnet-message-treetable.component';
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../lib/dynamicfield/field.descriptor.input.and.show';
import {BaseSettings} from '../../lib/base.settings';

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table #table [columns]="fields" [value]="gtNetList" selectionMode="single"
               [(selection)]="selectedEntity" dataKey="idGtNet"
               sortMode="multiple" [multiSortMeta]="multiSortMeta"
               (sortFunction)="customSort($event)" [customSort]="true"
               stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{ 'GT_NET_NET_AND_MESSAGE' | translate }}</h4>
          @if (!gtNetMyEntryId) {
            <h5 style="color:red;">{{ 'GT_NET_COMM_REQUIREMENT' | translate }}</h5>
          }
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            <th style="width:24px"></th>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
                  [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
          @if (hasFilter) {
            <tr>
              @for (field of fields; track field) {
                <th style="overflow:visible;">
                  @switch (field.filterType) {
                    @case (FilterType.likeDataType) {
                      @switch (field.dataType) {
                        @case (field.dataType === DataType.DateString || field.dataType === DataType.DateNumeric ? field.dataType : '') {
                          <p-columnFilter [field]="field.field" display="menu" [showOperator]="true"
                                          [matchModeOptions]="customMatchModeOptions" [matchMode]="'gtNoFilter'">
                            <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                              <p-datepicker #cal [ngModel]="value" [dateFormat]="baseLocale.dateFormat"
                                            (onSelect)="filter($event)"
                                            [minDate]="minDate" [maxDate]="maxDate"
                                            (onInput)="filter(cal.value)">
                              </p-datepicker>
                            </ng-template>
                          </p-columnFilter>
                        }
                        @case (DataType.Numeric) {
                          <p-columnFilter type="numeric" [field]="field.field"
                                          [locale]="formLocale"
                                          minFractionDigits="2" display="menu"></p-columnFilter>
                        }
                      }
                    }
                  }
                </th>
              }
            </tr>
          }
        </ng-template>
        <ng-template #body let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              @if (gtNetMessageMap[el.idGtNet]) {
                <a href="#" [pRowToggler]="el">
                  <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
                </a>
              }
            </td>
            @for (field of fields; track field) {
              <td [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-end': ''" [style.max-width.px]="field.width"
                  [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
                @switch (field.templateName) {
                  @case ('myEntry') {
                    <span [style]='el.idGtNet === gtNetMyEntryId ? "font-weight:500": null'>
                     {{ getValueByPath(el, field) }}</span>
                  }
                  @case ('check') {
                    <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                  }
                  @default {
                    <span [pTooltip]="getValueByPath(el, field)"
                          tooltipPosition="top">{{ getValueByPath(el, field) }}</span>
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
        <ng-template #expandedrow let-el let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[el.idGtNet]"
                                       [formDefinitions]="formDefinitions">
              </gtnet-message-treetable>
            </td>
          </tr>
        </ng-template>
      </p-table>
      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>

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
  providers: [DialogService],
  standalone: false
})
export class GTNetSetupTableComponent extends TableCrudSupportMenu<GTNet> {
  minDate: Date = new Date('2000-01-01');
  maxDate: Date = new Date('2099-12-31');
  private readonly domainRemoteName = 'domainRemoteName';
  callParam: GTNet;
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
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);

    this.addColumnFeqH(DataType.String, this.domainRemoteName, true, false,
      {width: 200, templateName: 'myEntry'});
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {width: 120});
    this.addColumnFeqH(DataType.Boolean, 'spreadCapability', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'acceptEntityRequest', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'lastpriceServerState', true, false,
      {translateValues: TranslateValue.NORMAL});
    this.addColumnFeqH(DataType.Boolean, 'acceptLastpriceRequest', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.String, 'entityServerState', true, false,
      {translateValues: TranslateValue.NORMAL});

    this.multiSortMeta.push({field: this.domainRemoteName, order: 1});
    this.prepareTableAndTranslate();
  }

  override prepareCallParam(entity: GTNet): void {
    this.callParam = entity;
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


}
