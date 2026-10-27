import { Component, Injector, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { map, of, Subscription, switchMap } from 'rxjs';

import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { TableCrudSupportMenu } from '../../datashowbase/table.crud.support.menu';
import { ConnectorApiKey, SubscriptionTypeReadableName } from '../types/connector.api.key';
import { ConfirmationService, FilterService } from '@openng/optimus-ui/api';
import { MessageToastService } from '../../message/message.toast.service';
import { ActivePanelService } from '../../mainmenubar/service/active.panel.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfigurableTableComponent } from '../../datashowbase/configurable-table.component';
import { ConnectorApiKeyEditComponent } from './connector.api.key.edit.component';
import { GlobalparameterService } from '../../services/globalparameter.service';
import { UserSettingsService } from '../../services/user.settings.service';
import { HelpIds } from '../../help/help.ids';
import { ConnectorApiKeyService } from '../service/connector.api.key.service';
import { DataType } from '../../dynamic-form/models/data.type';
import { ColumnConfig, TranslateValue } from '../../datashowbase/column.config';
import { BaseSettings } from '../../base.settings';
import { ValueKeyHtmlSelectOptions } from '../../dynamic-form/models/value.key.html.select.options';

@Component({
  template: `
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
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
      [showContextMenu]="!!contextMenuItems()"
      [contextMenuItems]="contextMenuItems()"
      [valueGetterFn]="valueGetter"
      (componentClick)="onComponentClick($event)">
      <h4 caption>{{ entityNameUpper | translate }}</h4>
    </configurable-table>

    @if (visibleDialog()) {
      <connector-api-key-edit
        [visibleDialog]="visibleDialog()"
        [connectorApiKey]="connectorApiKey"
        [strn]="strn()"
        [existingProviders]="existingProviders"
        [subscriptionTypeOptionsMap]="subscriptionTypeOptionsMap()"
        (closeDialog)="handleCloseDialog($event)">
      </connector-api-key-edit>
    }
  `,
  providers: [DialogService],
  standalone: true,
  imports: [ConfigurableTableComponent, TranslateModule, ConnectorApiKeyEditComponent]
})
export class ConnectorApiKeyTableComponent extends TableCrudSupportMenu<ConnectorApiKey> {
  connectorApiKey: ConnectorApiKey;

  readonly strn = signal<Record<string, SubscriptionTypeReadableName>>({});
  existingProviders: string[];
  readonly subscriptionTypeOptionsMap = signal<Record<string, ValueKeyHtmlSelectOptions[]>>({});
  private readDataSub?: Subscription;

  constructor(
    private connectorApiKeyService: ConnectorApiKeyService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    dialogService: DialogService,
    filterService: FilterService,
    translateService: TranslateService,
    gps: GlobalparameterService,
    usersettingsService: UserSettingsService,
    injector: Injector
  ) {
    super(
      BaseSettings.CONNECTOR_API_KEY,
      connectorApiKeyService,
      confirmationService,
      messageToastService,
      activePanelService,
      dialogService,
      filterService,
      translateService,
      gps,
      usersettingsService,
      injector
    );

    this.addColumnFeqH(DataType.String, 'idProvider', true, false, {
      fieldValueFN: this.getFeedConnectorReadableName.bind(this)
    });
    this.addColumnFeqH(DataType.String, 'apiKey', true, false);
    this.addColumnFeqH(DataType.String, 'subscriptionType', true, false, {
      translateValues: TranslateValue.NORMAL
    });
    this.prepareTableAndTranslate();
  }

  protected override initialize(): void {
    this.connectorApiKeyService
      .getFeedSubscriptionType()
      .pipe(
        switchMap((strn) => {
          const subscriptions = Object.fromEntries(
            Object.entries(strn).map(([provider, details]) => [
              provider,
              details.subscriptionTypes.map((type) => String(typeof type === 'object' ? type.id : type))
            ])
          );
          const keys = [...new Set(Object.values(subscriptions).flat())];
          return (keys.length ? this.translateService.get(keys) : of({})).pipe(
            map((translations) => ({
              strn,
              options: Object.fromEntries(
                Object.entries(subscriptions).map(([provider, types]) => [
                  provider,
                  types.map((type) => new ValueKeyHtmlSelectOptions(type, translations[type] ?? type))
                ])
              )
            }))
          );
        }),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(({ strn, options }) => {
        this.strn.set(strn);
        this.subscriptionTypeOptionsMap.set(options);
        super.initialize();
      });
  }

  override prepareCallParam(entity: ConnectorApiKey) {
    this.existingProviders = this.entityList().map((el) => el.idProvider);
    this.connectorApiKey = entity;
  }

  protected override readData(): void {
    this.readDataSub?.unsubscribe();
    this.readDataSub = this.connectorApiKeyService
      .getAllConnectorApiKeys()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((result) => {
        this.createTranslatedValueStoreAndFilterField(result);
        this.entityList.set(result);
        this.refreshSelectedEntity();
      });
  }

  protected override hasRightsForDeleteEntity(entity: ConnectorApiKey): boolean {
    return true;
  }

  getFeedConnectorReadableName(dataobject: any, field: ColumnConfig, valueField: any): string {
    return this.strn()[valueField]?.readableName ?? valueField;
  }

  override getHelpContextId(): string {
    return HelpIds.HELP_CONNECTOR_API_KEY;
  }
}
