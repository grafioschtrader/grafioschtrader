import { Component, signal, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { ButtonModule } from '@openng/optimus-ui/button';
import { MessageModule } from '@openng/optimus-ui/message';
import { forkJoin, finalize } from 'rxjs';
import { TableCrudSupportMenu } from '../../lib/datashowbase/table.crud.support.menu';
import { ConfigurableTableComponent } from '../../lib/datashowbase/configurable-table.component';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { ClassDescriptorInputAndShow } from '../../lib/dynamicfield/field.descriptor.input.and.show';
import { HelpIds } from '../../lib/help/help.ids';
import { TenantAIProvider, TenantAIProviderOptions, AIKeyTestResult } from '../model/tenant-ai-provider';
import { TenantAIProviderService } from '../service/tenant-ai-provider.service';
import { TenantAIProviderEditComponent } from './tenant-ai-provider-edit.component';

@Component({
  selector: 'tenant-ai-provider-table',
  imports: [ConfigurableTableComponent, TranslateModule, ButtonModule, MessageModule, TenantAIProviderEditComponent],
  template: `
    <p>{{ 'AI_PROVIDERS_HELP' | translate }}</p>
    <div style="display: flex; gap: 0.5rem; align-items: center; margin-bottom: 0.75rem">
      <p-button
        [label]="'AI_PROVIDER_CREATE' | translate"
        [disabled]="!options() || gps.isReadOnlyUser()"
        (onClick)="handleEditEntity(null)" />
      @if (selectedEntity(); as selectedEntityValue) {
        <p-button
          [label]="'EDIT' | translate"
          [disabled]="gps.isReadOnlyUser()"
          (onClick)="handleEditEntity(selectedEntityValue)" />
        <p-button
          [label]="'DELETE' | translate"
          [disabled]="gps.isReadOnlyUser()"
          (onClick)="handleDeleteEntity(selectedEntityValue)" />
        <p-button
          [label]="'AI_TEST_KEY' | translate"
          [disabled]="testing() || gps.isReadOnlyUser()"
          (onClick)="testKey(selectedEntityValue)" />
      }
      <p-button [rounded]="true" [style]="{ 'margin-left': 'auto' }" (onClick)="helpLink()">
        <i class="pi pi-question" pButtonIcon></i>
      </p-button>
    </div>
    @if (testing()) {
      <p>{{ 'AI_TEST_KEY_WAITING' | translate }}</p>
    }
    @if (testResult(); as result) {
      <p-message
        [severity]="result.succeeded ? 'success' : 'error'"
        [text]="result.succeeded ? ('AI_TEST_KEY_SUCCEEDED' | translate) : result.reason" />
    }
    <configurable-table
      [data]="entityList()"
      [fields]="fields"
      [fieldsRevision]="fieldsRevision()"
      [dataKey]="entityKeyName"
      selectionMode="single"
      [(selection)]="selectedEntity"
      [multiSortMeta]="multiSortMeta"
      [customSortFn]="customSorter"
      [valueGetterFn]="valueGetter"
      [baseLocale]="baseLocale"
      [scrollable]="false"
      [showContextMenu]="!!contextMenuItems()"
      [contextMenuItems]="contextMenuItems()"
      [contextMenuAppendTo]="'body'"
      (componentClick)="onComponentClick($event)" />
    @if (visibleDialog()) {
      @if (options(); as providerOptions) {
        @if (formDefinition(); as definition) {
          <tenant-ai-provider-edit
            [visibleDialog]="visibleDialog()"
            [entity]="editedEntity"
            [options]="providerOptions"
            [formDefinition]="definition"
            [existingProviders]="existingProviders"
            (closeDialog)="handleCloseDialog($event)" />
        }
      }
    }
  `
})
export class TenantAIProviderTableComponent extends TableCrudSupportMenu<TenantAIProvider> {
  private service: TenantAIProviderService = inject(TenantAIProviderService);

  readonly options = signal<TenantAIProviderOptions | null>(null);
  readonly formDefinition = signal<ClassDescriptorInputAndShow | null>(null);
  readonly testResult = signal<AIKeyTestResult | null>(null);
  readonly testing = signal(false);
  /** Set together with visibleDialog, which renders the dialog */
  editedEntity: TenantAIProvider | null = null;
  existingProviders: string[] = [];

  constructor() {
    super('TenantAIProvider', inject(TenantAIProviderService));
    this.addColumnFeqH(DataType.String, 'provider', true, false, {
      fieldValueFN: (row) => this.options()?.providerNames[row.provider] || row.provider
    });
    this.addColumnFeqH(DataType.String, 'model', true, false, {
      fieldValueFN: (row) => row.model || this.options()?.defaultModels[row.provider] || ''
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
    forkJoin({
      options: this.service.getProviders(),
      definition: this.gps.getEntityFormDefinition('TenantAIProvider')
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(({ options, definition }) => {
        this.options.set(options);
        this.formDefinition.set(definition);
        this.readData();
      });
  }
  protected override prepareCallParam(entity: TenantAIProvider | null): void {
    this.editedEntity = entity;
    this.existingProviders = this.entityList()
      .map((e) => e.provider)
      .filter((provider): provider is string => provider != null);
  }
  protected override readData(): void {
    this.service
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((rows) => {
        this.createTranslatedValueStoreAndFilterField(rows);
        this.entityList.set(rows);
        this.testResult.set(null);
        const selectedId = this.selectedEntity()?.idTenantAIProvider;
        this.resetMenu(rows.find((row) => row.idTenantAIProvider === selectedId) ?? null);
      });
  }
  protected override hasRightsForDeleteEntity(_: TenantAIProvider): boolean {
    return true;
  }
  protected override hasRightsForCreateEntity(_: TenantAIProvider | null): boolean {
    return !!this.options();
  }
  protected override addCustomMenusToSelectedEntity(entity: TenantAIProvider, items: MenuItem[]): void {
    if (!this.gps.isReadOnlyUser()) {
      items.push({ label: 'AI_TEST_KEY', disabled: this.testing(), command: () => this.testKey(entity) });
    }
  }
  testKey(entity: TenantAIProvider): void {
    const id = entity.idTenantAIProvider;
    if (id == null || this.testing()) {
      return;
    }
    this.testing.set(true);
    this.testResult.set(null);
    this.service
      .testKey(id)
      .pipe(
        finalize(() => this.testing.set(false)),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe({ next: (result) => this.testResult.set(result), error: () => {} });
  }
  override getHelpContextId(): string {
    return HelpIds.HELP_INTRO_SETTINGS;
  }
  /** The dialog is opened through DialogService and has no help button of its own. */
  helpLink(): void {
    this.gps.toExternalHelpWebpage(this.gps.getUserLang(), this.getHelpContextId());
  }
}
