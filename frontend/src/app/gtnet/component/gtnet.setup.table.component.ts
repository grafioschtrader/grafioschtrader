import {Component} from "@angular/core";
import {CrudMenuOptions, TableCrudSupportMenu} from "../../shared/datashowbase/table.crud.support.menu";
import {GTNet} from "../model/gtnet";
import {GTNetMessage} from "../model/gtnet.message";
import {GTNwtService} from "../service/gtnet.service";
import {ConfirmationService, FilterService} from "primeng/api";
import {MessageToastService} from "../../shared/message/message.toast.service";
import {ActivePanelService} from "../../shared/mainmenubar/service/active.panel.service";
import {DialogService} from "primeng/dynamicdialog";
import {TranslateService} from "@ngx-translate/core";
import {GlobalparameterService} from "../../shared/service/globalparameter.service";
import {UserSettingsService} from "../../shared/service/user.settings.service";
import {AppSettings} from "../../shared/app.settings";

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
              <a *ngIf="gtNetMessageMap.has(el.idGtNet)" href="#"
                 [pRowToggler]="el">
                <i [ngClass]="expanded ? 'fa fa-fw fa-chevron-circle-down' : 'fa fa-fw fa-chevron-circle-right'"></i>
              </a>
            </td>
            <td *ngFor="let field of fields"
                [ngClass]="(field.dataType===DataType.NumericShowZero || field.dataType===DataType.DateTimeNumeric
                || field.dataType===DataType.NumericInteger)? 'text-right': ''" [style.max-width.px]="field.width"
                [ngStyle]="field.width? {'flex-basis': '0 0 ' + field.width + 'px'}: {}">
              <ng-container [ngSwitch]="field.templateName">
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
              <gtnet-message-treetable gtNetMessages="gtNetMessageMap[el.idGtNet]"></gtnet-message-treetable>
            </td>
          </tr>
        </ng-template>
      </p-table>
      <p-contextMenu *ngIf="contextMenuItems" [target]="cmDiv" [model]="contextMenuItems"
                     appendTo="body"></p-contextMenu>
    </div>
  `,
})
export class GTNetSetupTableComponent extends TableCrudSupportMenu<GTNet>  {
  callParam: GTNet;
  gtNetList: GTNet[];
  gtNetMessageMap: { [key: number]: GTNetMessage[]};

  constructor(private gtNetService: GTNwtService,
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
  }

  override prepareCallParm(entity: GTNet): void {
    this.callParam = entity;
  }

  protected override readData(): void {
    this.gtNetService.getAllGTNetsWithMessages().subscribe(gtNetWithMessages => {
      this.gtNetList = gtNetWithMessages.gtNetList;
      this.gtNetMessageMap = gtNetWithMessages.gtNetMessageMap;
    })
  }

}
