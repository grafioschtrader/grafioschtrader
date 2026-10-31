import { requireInitialized } from '../../helper/required-state';
import { Component, OnInit, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HelpIds } from '../../help/help.ids';
import { TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { GTNetMessageAnswer, GTNetMessageAnswerCallParam } from '../model/gtnet.message.answer';
import { GTNetMessageAnswerService } from '../service/gtnet.message.answer.service';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TranslateModule } from '@ngx-translate/core';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { BaseSettings } from '../../base.settings';
import { DataType } from '../../dynamic-form/models/data.type';
import { TranslateValue } from '../../datashowbase/column.config';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { GTNetMessageAnswerEditComponent } from './gtnet-message-answer-edit.component';
import { GTNetMessageCodeType } from '../model/gtnet.message';

/**
 * Table component for displaying and managing GTNetMessageAnswer entities.
 * Allows admins to configure automatic response rules for incoming GTNet messages.
 */
@Component({
  selector: 'gtnet-message-answer-table',
  imports: [ConfigurableTableComponent, TranslateModule, GTNetMessageAnswerEditComponent],
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="'idGtNetMessageAnswer'"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{
        'data-container': true,
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }"
      [showContextMenu]="(contextMenuItems()?.length ?? 0) > 0"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ 'GT_NET_MESSAGE_ANSWER' | translate }}</h4>
    </configurable-table>

    @if (visibleDialog()) {
      <gtnet-message-answer-edit
        [visibleDialog]="visibleDialog()"
        [callParam]="callParam"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `,
  providers: [DialogService]
})
export class GTNetMessageAnswerTableComponent extends TableCrudSupportMenu<GTNetMessageAnswer> implements OnInit {
  private gtNetMessageAnswerService: GTNetMessageAnswerService = inject(GTNetMessageAnswerService);

  private initializedCallParam?: GTNetMessageAnswerCallParam;

  get callParam(): GTNetMessageAnswerCallParam {
    return requireInitialized(this.initializedCallParam, 'GTNetMessageAnswerTableComponent.callParam');
  }

  set callParam(value: GTNetMessageAnswerCallParam) {
    this.initializedCallParam = value;
  }

  constructor() {
    super(
      BaseSettings.GT_NET_MESSAGE_ANSWER,
      inject(GTNetMessageAnswerService),
      inject(GlobalparameterService).hasRole(BaseSettings.ROLE_ADMIN)
        ? TableCrudSupportMenu.ALLOW_ALL_CRUD_OPERATIONS
        : []
    );
  }

  override ngOnInit(): void {
    this.addColumnFeqH(DataType.String, 'requestMsgCode', true, true, {
      translateValues: TranslateValue.NORMAL,
      width: 250
    });
    this.addColumnFeqH(DataType.String, 'responseMsgCode', true, true, {
      translateValues: TranslateValue.NORMAL,
      width: 250
    });
    this.addColumnFeqH(DataType.NumericInteger, 'priority', true, true, {
      width: 80
    });
    this.addColumnFeqH(DataType.String, 'responseMsgConditional', true, true, {
      width: 300
    });
    this.addColumnFeqH(DataType.String, 'responseMsgMessage', true, true, {
      width: 300
    });
    this.addColumnFeqH(DataType.NumericInteger, 'waitDaysApply', true, true, {
      width: 100
    });
    super.ngOnInit();
  }

  public override getHelpContextId(): string {
    return HelpIds.HELP_GT_NET_AUTOANSWER;
  }

  override prepareCallParam(entity: GTNetMessageAnswer | null): void {
    this.callParam = new GTNetMessageAnswerCallParam(entity);
  }

  protected override readData(): void {
    this.gtNetMessageAnswerService
      .getAllGTNetMessageAnswers()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((data) => {
        // Convert enum values to string names for display
        data.forEach((item) => {
          if (typeof item.requestMsgCode === 'number') {
            item.requestMsgCode = GTNetMessageCodeType[item.requestMsgCode] as any;
          }
          if (typeof item.responseMsgCode === 'number') {
            item.responseMsgCode = GTNetMessageCodeType[item.responseMsgCode] as any;
          }
        });
        this.entityList.set(data);
        this.prepareTableAndTranslate();
        this.createTranslatedValueStoreAndFilterField(data);
        this.refreshSelectedEntity();
      });
  }
}
