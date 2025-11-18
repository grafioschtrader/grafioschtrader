import {Component, OnDestroy} from '@angular/core';
import {DialogService} from 'primeng/dynamicdialog';
import {TableCrudSupportMenu} from '../../datashowbase/table.crud.support.menu';
import {ConnectorApiKey, SubscriptionTypeReadableName} from '../types/connector.api.key';
import {ConfirmationService, FilterService} from 'primeng/api';
import {MessageToastService} from '../../message/message.toast.service';
import {ActivePanelService} from '../../mainmenubar/service/active.panel.service';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../services/globalparameter.service';
import {UserSettingsService} from '../../services/user.settings.service';
import {HelpIds} from '../../help/help.ids';
import {ConnectorApiKeyService} from '../service/connector.api.key.service';
import {DataType} from '../../dynamic-form/models/data.type';
import {ColumnConfig, TranslateValue} from '../../datashowbase/column.config';
import {BaseSettings} from '../../base.settings';
import {SelectOptionsHelper} from '../../helper/select.options.helper';
import {ValueKeyHtmlSelectOptions} from '../../dynamic-form/models/value.key.html.select.options';

@Component({
  template: `
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      [selectionMode]="'single'"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [scrollable]="false"
      [stripedRows]="true"
      [showGridlines]="true"
      [containerClass]="{'data-container': true, 'active-border': isActivated(), 'passiv-border': !isActivated()}"
      [showContextMenu]="!!contextMenuItems"
      [contextMenuItems]="contextMenuItems"
      [ownerHighlightFn]="isNotSingleModeAndOwner.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      (componentClick)="onComponentClick($event)">

      <h4 caption>{{ entityNameUpper | translate }}</h4>

    </configurable-table>

    @if (visibleDialog) {
      <connector-api-key-edit [visibleDialog]="visibleDialog"
                              [connectorApiKey]="connectorApiKey"
                              [strn]="strn"
                              [existingProviders]="existingProviders"
                              [subscriptionTypeOptionsMap]="subscriptionTypeOptionsMap"
                              (closeDialog)="handleCloseDialog($event)">
      </connector-api-key-edit>
    }
  `,
  providers: [DialogService],
  standalone: false
})
export class ConnectorApiKeyTableComponent extends TableCrudSupportMenu<ConnectorApiKey> implements OnDestroy {
  connectorApiKey: ConnectorApiKey;

  strn: { [id: string]: SubscriptionTypeReadableName };
  existingProviders: string[];
  subscriptionTypeOptionsMap: { [providerId: string]: ValueKeyHtmlSelectOptions[] };

  constructor(private connectorApiKeyService: ConnectorApiKeyService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService) {
    super(BaseSettings.CONNECTOR_API_KEY, connectorApiKeyService, confirmationService, messageToastService,
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
    this.connectorApiKey = entity;

    // Prepare subscription type options for each provider
    this.subscriptionTypeOptionsMap = {};
    Object.keys(this.strn).forEach(providerId => {
      const subscriptionTypes = this.strn[providerId].subscriptionTypes;
      // Convert SubscriptionTypeValue to strings
      const subscriptionTypeStrings = subscriptionTypes.map(st =>
        typeof st === 'object' && st !== null && 'id' in st ? String(st.id) : String(st)
      );
      // Use translateArrayKeyEqualValue and remove the empty first option
      this.subscriptionTypeOptionsMap[providerId] = SelectOptionsHelper.translateArrayKeyEqualValue(
        this.translateService, subscriptionTypeStrings
      ).slice(1);
    });
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
