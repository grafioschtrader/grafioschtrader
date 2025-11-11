import {Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {TableCrudSupportMenu} from '../../lib/datashowbase/table.crud.support.menu';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../../entities/connector.api.key';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {UserSettingsService} from '../../lib/services/user.settings.service';
import {AppSettings} from '../../shared/app.settings';
import {HelpIds} from '../../lib/help/help.ids';
import {ConnectorApiKeyService} from '../service/connector.api.key.service';
import {DataType} from '../../lib/dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../lib/datashowbase/column.config';
import {CallParam} from '../../shared/maintree/types/dialog.visible';

@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <p-table [columns]="fields" [value]="entityList" selectionMode="single" [(selection)]="selectedEntity"
               [dataKey]="entityKeyName" stripedRows showGridlines>
        <ng-template #caption>
          <h4>{{ entityNameUpper | translate }}</h4>
        </ng-template>
        <ng-template #header let-fields>
          <tr>
            @for (field of fields; track field) {
              <th [pSortableColumn]="field.field">
                {{ field.headerTranslated }}
                <p-sortIcon [field]="field.field"></p-sortIcon>
              </th>
            }
          </tr>
        </ng-template>
        <ng-template #body let-el let-columns="fields">
          <tr [pSelectableRow]="el">
            @for (field of fields; track field) {
              <td>
                @switch (field.templateName) {
                  @case ('owner') {
                    <span [style]='isNotSingleModeAndOwner(field, el)? "font-weight:500": null'>
                   {{ getValueByPath(el, field) }}</span>
                  }
                  @default {
                    {{ getValueByPath(el, field) }}
                  }
                }
              </td>
            }
          </tr>
        </ng-template>
      </p-table>
      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }
    </div>
    @if (visibleDialog) {
      <connector-api-key-edit [visibleDialog]="visibleDialog"
                              [callParam]="callParam"
                              [strn]="strn"
                              [existingProviders]="existingProviders"
                              (closeDialog)="handleCloseDialog($event)">
      </connector-api-key-edit>
    }
  `,
  providers: [DialogService],
  standalone: false
})
export class ConnectorApiKeyTableComponent extends TableCrudSupportMenu<ConnectorApiKey> implements OnDestroy {
  callParam: CallParam;

  strn: { [id: string]: SubscriptionTypeReadableName };
  existingProviders: string[];

  constructor(private connectorApiKeyService: ConnectorApiKeyService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(AppSettings.CONNECTOR_API_KEY, connectorApiKeyService, confirmationService, messageToastService,
      activePanelService, dialogService, filterService, translateService, gps, usersettingsService);

    this.addColumnFeqH(DataType.String, 'idProvider', true, false,
      {fieldValueFN: this.getFeedConnectorReadableName.bind(this)});
    this.addColumnFeqH(DataType.String, 'apiKey', true, false);
    this.addColumnFeqH(DataType.String, 'subscriptionType', true, false, {translateValues: TranslateValue.NORMAL});
    this.prepareTableAndTranslate();
  }

  protected override initialize(): void {
    this.connectorApiKeyService.getFeedSubscriptionType().subscribe(strn => {
      this.strn = strn;
      super.initialize();
    });
  }

  override prepareCallParam(entity: ConnectorApiKey) {
    this.existingProviders = this.entityList.map(el => el.idProvider);
    this.callParam = new CallParam(null, entity);
  }

  protected override readData(): void {
    this.connectorApiKeyService.getAllConnectorApiKeys().subscribe(result => {
      this.createTranslatedValueStoreAndFilterField(result);
      this.entityList = result;
      this.refreshSelectedEntity();
    });
  }

  protected override hasRightsForDeleteEntity(entity: ConnectorApiKey): boolean {
    return true;
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.strn[valueField].readableName;
  }

  ngOnDestroy(): void {
    this.activePanelService.destroyPanel(this);
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_CONNECTOR_API_KEY;
  }
}
