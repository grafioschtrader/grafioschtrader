import { Component, Injector, OnDestroy, ChangeDetectionStrategy } from '@angular/core';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ConfirmationService, FilterService, MenuItem } from '@openng/optimus-ui/api';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
import { forkJoin, Subscription, finalize } from 'rxjs';
import { TableCrudSupportMenu } from '../../lib/datashowbase/table.crud.support.menu';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { ActivePanelService } from '../../lib/mainmenubar/service/active.panel.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { UserSettingsService } from '../../lib/services/user.settings.service';
import { HelpIds } from '../../lib/help/help.ids';
import { TenantAIProvider, TenantAIProviderOptions, AIKeyTestResult } from '../model/tenant-ai-provider';
import { TenantAIProviderService } from '../service/tenant-ai-provider.service';
import { TenantAIProviderEditComponent } from './tenant-ai-provider-edit.component';

@Component({
  selector: 'tenant-ai-provider-table',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ConfigurableTableComponent, TranslateModule, ButtonModule, MessageModule, TenantAIProviderEditComponent],
  template: `
    <p>{{ 'AI_PROVIDERS_HELP' | translate }}</p>
    <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem">
      <p-button
        [label]="'AI_PROVIDER_CREATE' | translate"
        [disabled]="!options || gps.isReadOnlyUser()"
        (onClick)="handleEditEntity(null)" />
      @if (selectedEntity) {
        <p-button
          [label]="'EDIT' | translate"
          [disabled]="gps.isReadOnlyUser()"
          (onClick)="handleEditEntity(selectedEntity)" />
        <p-button
          [label]="'DELETE' | translate"
          [disabled]="gps.isReadOnlyUser()"
          (onClick)="handleDeleteEntity(selectedEntity)" />
        <p-button
          [label]="'AI_TEST_KEY' | translate"
          [disabled]="testing || gps.isReadOnlyUser()"
          (onClick)="testKey(selectedEntity)" />
      }
      <p-button [rounded]="true" [style]="{ 'margin-left': 'auto' }" (onClick)="helpLink()">
        <i class="pi pi-question" pButtonIcon></i>
      </p-button>
    </div>
    @if (testing) {
      <p>{{ 'AI_TEST_KEY_WAITING' | translate }}</p>
    }
    @if (testResult) {
      <p-message
        [severity]="testResult.succeeded ? 'success' : 'error'"
        [text]="testResult.succeeded ? ('AI_TEST_KEY_SUCCEEDED' | translate) : testResult.reason" />
    }
    <configurable-table
      [data]="entityList"
      [fields]="fields"
      [dataKey]="entityKeyName"
      selectionMode="single"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSort.bind(this)"
      [valueGetterFn]="getValueByPath.bind(this)"
      [baseLocale]="baseLocale"
      [scrollable]="false"
      [showContextMenu]="!!contextMenuItems"
      [contextMenuItems]="contextMenuItems"
      [contextMenuAppendTo]="'body'"
      (componentClick)="onComponentClick($event)" />
    @if (visibleDialog) {
      <tenant-ai-provider-edit
        [visibleDialog]="visibleDialog"
        [entity]="editedEntity"
        [options]="options"
        [formDefinition]="formDefinition"
        [existingProviders]="existingProviders"
        (closeDialog)="handleCloseDialog($event)" />
    }
  `
})
export class TenantAIProviderTableComponent extends TableCrudSupportMenu<TenantAIProvider> implements OnDestroy {
  options: TenantAIProviderOptions;
  formDefinition: ClassDescriptorInputAndShow;
  editedEntity: TenantAIProvider;
  existingProviders: string[] = [];
  testResult: AIKeyTestResult;
  testing = false;
  private subscriptions = new Subscription();

  constructor(
    private service: TenantAIProviderService,
    confirmation: ConfirmationService,
    messages: MessageToastService,
    panels: ActivePanelService,
    dialogs: DialogService,
    filters: FilterService,
    translate: TranslateService,
    gps: GlobalparameterService,
    settings: UserSettingsService,
    injector: Injector
  ) {
    super(
      'TenantAIProvider',
      service,
      confirmation,
      messages,
      panels,
      dialogs,
      filters,
      translate,
      gps,
      settings,
      injector
    );
    this.addColumnFeqH(DataType.String, 'provider', true, false, {
      fieldValueFN: (row) => this.options?.providerNames[row.provider] || row.provider
    });
    this.addColumnFeqH(DataType.String, 'model', true, false, {
      fieldValueFN: (row) => row.model || this.options?.defaultModels[row.provider] || ''
    });
    this.addColumnFeqH(DataType.NumericInteger, 'priority', true, false);
    this.addColumnFeqH(DataType.Boolean, 'active', true, false, { templateName: 'check' });
    this.addColumnFeqH(DataType.NumericInteger, 'dailyRequestLimit', true, false);
    this.addColumnFeqH(DataType.NumericInteger, 'requestsToday', true, false);
    this.addColumnFeqH(DataType.String, 'apiKeyHint', true, false);
    this.multiSortMeta.push({ field: 'priority', order: 1 }, { field: 'idTenantAIProvider', order: 1 });
    this.prepareTableAndTranslate();
  }
  protected override initialize(): void {
    this.subscriptions.add(
      forkJoin({
        options: this.service.getProviders(),
        definition: this.gps.getEntityFormDefinition('TenantAIProvider')
      }).subscribe(({ options, definition }) => {
        this.options = options;
        this.formDefinition = definition;
        this.readData();
      })
    );
  }
  protected override prepareCallParam(entity: TenantAIProvider): void {
    this.editedEntity = entity;
    this.existingProviders = this.entityList.map((e) => e.provider);
  }
  protected override readData(): void {
    this.subscriptions.add(
      this.service.getAll().subscribe((rows) => {
        this.entityList = rows;
        this.testResult = null;
        this.createTranslatedValueStoreAndFilterField(rows);
        this.resetMenu(rows.find((row) => row.idTenantAIProvider === this.selectedEntity?.idTenantAIProvider) ?? null);
      })
    );
  }
  protected override hasRightsForDeleteEntity(_: TenantAIProvider): boolean {
    return true;
  }
  protected override hasRightsForCreateEntity(_: TenantAIProvider): boolean {
    return !!this.options;
  }
  protected override addCustomMenusToSelectedEntity(entity: TenantAIProvider, items: MenuItem[]): void {
    if (!this.gps.isReadOnlyUser()) {
      items.push({ label: 'AI_TEST_KEY', disabled: this.testing, command: () => this.testKey(entity) });
    }
  }
  testKey(entity: TenantAIProvider): void {
    if (this.testing) {
      return;
    }
    this.testing = true;
    this.testResult = null;
    this.subscriptions.add(
      this.service
        .testKey(entity.idTenantAIProvider)
        .pipe(finalize(() => (this.testing = false)))
        .subscribe({ next: (result) => (this.testResult = result), error: () => {} })
    );
  }
  override getHelpContextId(): string {
    return HelpIds.HELP_INTRO_SETTINGS;
  }
  /** The dialog is opened through DialogService and has no help button of its own. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.getHelpContextId());
  }
  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.activePanelService.destroyPanel(this);
  }
}
