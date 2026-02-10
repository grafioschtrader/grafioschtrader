import {Component, Injector, OnInit} from '@angular/core';
import {HelpIds} from '../../help/help.ids';
import {TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {GTNetMessageAnswer, GTNetMessageAnswerCallParam} from '../model/gtnet.message.answer';
import {GTNetMessageAnswerService} from '../service/gtnet.message.answer.service';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {DialogService} from 'primeng/dynamicdialog';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {BaseSettings} from '../../base.settings';
import {DataType} from '../../dynamic-form/models/data.type';
import {TranslateValue} from '../../datashowbase/column.config';
import {ConfigurableTableComponent} from '../../datashowbase/configurable-table.component';
import {GTNetMessageAnswerEditComponent} from './gtnet-message-answer-edit.component';
import {GTNetMessageCodeType} from '../model/gtnet.message';

/**
 * Table component for displaying and managing GTNetMessageAnswer entities.
 * Allows admins to configure automatic response rules for incoming GTNet messages.
 */
@Component({
  selector: 'gtnet-message-answer-table',
  standalone: true,
  imports: [
    ConfigurableTableComponent,
    TranslateModule,
    GTNetMessageAnswerEditComponent
  ],
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="'idGtNetMessageAnswer'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems && contextMenuItems.length > 0"
      [contextMenuItems]="contextMenuItems"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ 'GT_NET_MESSAGE_ANSWER' | translate }}</h4>

    </configurable-table>

    @if (visibleDialog) {
      <gtnet-message-answer-edit
        [visibleDialog]="visibleDialog"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)">
      </gtnet-message-answer-edit>
    }
  `,
  providers: [DialogService]
})
export class GTNetMessageAnswerTableComponent extends TableCrudSupportMenu<GTNetMessageAnswer> implements OnInit {

  callParam: GTNetMessageAnswerCallParam;

  constructor(private gtNetMessageAnswerService: GTNetMessageAnswerService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector) {

    super(BaseSettings.GT_NET_MESSAGE_ANSWER, gtNetMessageAnswerService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService, injector,
      gps.hasRole(BaseSettings.ROLE_ADMIN) ? TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS : []);
  }

  override ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'requestMsgCode', true, true,
      {translateValues: TranslateValue.NORMAL, width: 250});
    this.addColumnFeqH(DataType.String, 'responseMsgCode', true, true,
      {translateValues: TranslateValue.NORMAL, width: 250});
    this.addColumnFeqH(DataType.NumericInteger, 'priority', true, true,
      {width: 80});
    this.addColumnFeqH(DataType.String, 'responseMsgConditional', true, true,
      {width: 300});
    this.addColumnFeqH(DataType.String, 'responseMsgMessage', true, true,
      {width: 300});
    this.addColumnFeqH(DataType.NumericInteger, 'waitDaysApply', true, true,
      {width: 100});
    super.ngOnInit();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_AUTOANSWER;
  }

  override prepareCallParam(entity: GTNetMessageAnswer): void {
    this.callParam = new GTNetMessageAnswerCallParam(entity);
  }

  protected override readData(): void {
    this.gtNetMessageAnswerService.getAllGTNetMessageAnswers().subscribe(data => {
      // Convert enum values to string names for display
      data.forEach(item => {
        if (typeof item.requestMsgCode === 'number') {
          item.requestMsgCode = GTNetMessageCodeType[item.requestMsgCode] as any;
        }
        if (typeof item.responseMsgCode === 'number') {
          item.responseMsgCode = GTNetMessageCodeType[item.responseMsgCode] as any;
        }
      });
      this.entityList = data;
      this.prepareTableAndTranslate();
      this.createTranslatedValueStoreAndFilterField(data);
      this.refreshSelectedEntity();
    });
  }
}
