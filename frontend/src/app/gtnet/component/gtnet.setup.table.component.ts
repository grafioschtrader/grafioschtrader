import {Component} from '@angular/core';
import {CrudMenuOptions, TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {GTNet, GTNetWithMessages} from '../model/gtnet';
import {GTNetMessage, MsgCallParam} from '../model/gtnet.message';
import {GTNetService} from '../service/gtnet.service';
import {ConfirmationService, FilterService, MenuItem} from 'primeng/api';
import {MessageToastService} from '../../shared/message/message.toast.service';
import {ActivePanelService} from '../../shared/mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {UserSettingsService} from '../../shared/service/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateValue} from '../../shared/datashowbase/column.config';
import {HelpIds} from '../../shared/help/help.ids';
import {GTNetMessageTreeTableComponent} from './gtnet-message-treetable.component';
import {combineLatest} from 'rxjs';
import {GTNetMessageService} from '../service/gtnet.message.service';
import {ClassDescriptorInputAndShow} from '../../shared/dynamicfield/field.descriptor.input.and.show';

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table #table [columns]="fields" [value]="gtNetList" selectionMode="single"
               [(selection)]="selectedEntity" dataKey="idGtNet"
               sortMode="multiple" [multiSortMeta]="multiSortMeta"
               responsiveLayout="scroll"
               (sortFunction)="customSort($event)" [customSort]="true"
               styleClass="sticky-table p-datatable-striped p-datatable-gridlines">
        <ng-template pTemplate="caption">
          <h4>{{'GTNET_SETUP' | translate}}</h4>
          <h5 *ngIf="!gtNetMyEntryId" style="color:red;">{{'GTNET_COMM_REQUIREMENT' | translate}}</h5>
        </ng-template>
        <ng-template pTemplate="header" let-fields>
          <tr>
            <th style="width:24px"></th>
            <th *ngFor="let field of fields" [pSortableColumn]="field.field" [pTooltip]="field.headerTooltipTranslated"
                [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              {{field.headerTranslated}}
              <p-sortIcon [field]="field.field"></p-sortIcon>
            </th>
          </tr>
          <tr *ngIf="hasFilter">
            <th *ngFor="let field of fields" [ngSwitch]="field.filterType" style="overflow:visible;">
              <ng-container *ngSwitchCase="FilterType.likeDataType">
                <ng-container [ngSwitch]="field.dataType">
                  <p-columnFilter *ngSwitchCase="field.dataType === DataType.DateString || field.dataType === DataType.DateNumeric
                              ? field.dataType : ''" [field]="field.field" display="menu" [showOperator]="true"
                                  [matchModeOptions]="customMatchModeOptions" [matchMode]="'gtNoFilter'">
                    <ng-template pTemplate="filter" let-value let-filter="filterCallback">
                      <p-calendar #cal [ngModel]="value" [dateFormat]="baseLocale.dateFormat"
                                  (onSelect)="filter($event)"
                                  monthNavigator="true" yearNavigator="true" yearRange="2000:2099"
                                  (onInput)="filter(cal.value)">
                      </p-calendar>
                    </ng-template>
                  </p-columnFilter>
                  <p-columnFilter *ngSwitchCase="DataType.Numeric" type="numeric" [field]="field.field"
                                  [locale]="formLocale"
                                  minFractionDigits="2" display="menu"></p-columnFilter>
                </ng-container>
              </ng-container>
            </th>
          </tr>
        </ng-template>
        <ng-template pTemplate="body" let-expanded="expanded" let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            <td>
              <a *ngIf="gtNetMessageMap[el.idGtNet]" href="#"
                 [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            <td *ngFor="let field of fields"
                [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              <ng-container [ngSwitch]="field.templateName">
                <ng-container *ngSwitchCase="'myEntry'">
                  <span [style]='el.idGtNet === gtNetMyEntryId ? "font-weight:500": null'>
                   {{getValueByPath(el, field)}}</span>
                </ng-container>
                <ng-container *ngSwitchCase="'check'">
                  <span><i [ngClass]="{'fa fa-check': getValueByPath(el, field)}" aria-hidden="true"></i></span>
                </ng-container>
                <ng-container *ngSwitchDefault>
                  <span [pTooltip]="getValueByPath(el, field)"
                        tooltipPosition="top">{{getValueByPath(el, field)}}</span>
                </ng-container>
              </ng-container>
            </td>
          </tr>
        </ng-template>
        <ng-template pTemplate="rowexpansion" let-el let-columns="fields">
          <tr>
            <td [attr.colspan]="numberOfVisibleColumns + 1" style="overflow:visible;">
              <gtnet-message-treetable [gtNetMessages]="gtNetMessageMap[el.idGtNet]"
                                       [formDefinitions]="formDefinitions">
              </gtnet-message-treetable>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>

    <gtnet-edit *ngIf="visibleDialog"
                [visibleDialog]="visibleDialog"
                [callParam]="callParam"
                (closeDialog)="handleCloseDialog($event)">
    </gtnet-edit>
    <gtnet-message-edit *ngIf="visibleDialogMsg"
                        [visibleDialog]="visibleDialogMsg"
                        [msgCallParam]="msgCallParam"
                        (closeDialog)="handleCloseDialogMsg($event)">
    </gtnet-message-edit>
  `,
  providers: [DialogService]
})
export class GTNetSetupTableComponent extends TableCrudSupportMenu<GTNet> {
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

    super(AppSettings.GTNET, gtNetService, confirmationService, messageToastService, activePanelService,
      dialogService, filterService, translateService, gps, usersettingsService,
      gps.hasRole(AppSettings.ROLE_ADMIN) ? [CrudMenuOptions.Allow_Create,
        CrudMenuOptions.Allow_Delete] : []);

    this.addColumnFeqH(DataType.String, this.domainRemoteName, true, false,
      {width: 200, templateName: 'myEntry'});
    this.addColumnFeqH(DataType.String, 'timeZone', true, false, {width: 120});
    this.addColumnFeqH(DataType.Boolean, 'spreadCapability', true, false,
      {templateName: 'check'});
    this.addColumnFeqH(DataType.Boolean, 'acceptEntityRequest', true, false,
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
      label: 'GTNET_MESSAGE_SEND', command: (e) => this.sendMsgSelected(),
      disabled: !this.selectedEntity && !this.gtNetMyEntryId
    });
    return menuItems;
  }

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_GTNET_SETUP;
  }


  // Handle Messages
  ////////////////////////////////////////
  private sendMsgSelected(): void {
    this.msgCallParam = new MsgCallParam(this.formDefinitions, [this.selectedEntity.idGtNet], null, null);
    this.visibleDialogMsg = true;
  }

  handleCloseDialogMsg(dynamicMsg: any): void {
    this.visibleDialogMsg = false;
  }


}
