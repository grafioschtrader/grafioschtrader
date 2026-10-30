import { afterNextRender, AfterRenderRef, Component, inject, Injector, OnInit, signal } from '@angular/core';

import { TranslateModule } from '@ngx-translate/core';
import { MenuItem } from '@openng/optimus-ui/api';
import { forkJoin, Subscription } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { SingleRecordMasterViewBase } from '../../lib/masterdetail/component/single.record.master.view.base';
import { GenericConnectorDef } from '../../entities/generic.connector.def';
import { GenericConnectorEndpoint } from '../../entities/generic.connector.endpoint';
import { GenericConnectorFieldMapping } from '../../entities/generic.connector.field.mapping';
import { GenericConnectorDefService } from '../service/generic.connector.def.service';
import { AppHelpIds } from '../../shared/help/help.ids';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { BaseSettings } from '../../lib/base.settings';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { AppHelper } from '../../lib/helper/app.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { AccordionModule } from '@openng/optimus-ui/accordion';
import { GenericConnectorDefDetailComponent } from './generic-connector-def-detail.component';
import { GenericConnectorEndpointPanelComponent } from './generic-connector-endpoint-panel.component';
import { GenericConnectorDefEditComponent } from './generic-connector-def-edit.component';
import { GenericConnectorEndpointEditComponent } from './generic-connector-endpoint-edit.component';
import { GenericConnectorHttpHeaderTableComponent } from './generic-connector-http-header-table.component';
import { GenericConnectorHttpHeader } from '../../entities/generic.connector.http.header';
import { GenericConnectorTestDialogComponent } from './generic-connector-test-dialog.component';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { DynamicDialogs } from '../../lib/dynamicdialog/component/dynamic.dialogs';
import { MailSendParam } from '../../lib/dynamicdialog/component/mail-send-dynamic-dialog.component';
import { AppSettings } from '../../shared/app.settings';

/**
 * Top-level component for managing generic connector definitions.
 * Displays a dropdown selector for connectors, connector detail fields, and an accordion
 * with endpoint panels containing field mapping tables.
 */
@Component({
  template: `
    <div
      class="data-container"
      (click)="onComponentClick($event)"
      #cmDiv
      [class]="{
        'active-border': isActivated(),
        'passiv-border': !isActivated()
      }">
      <h4>{{ 'GENERIC_CONNECTOR_DEF' | translate }}</h4>

      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />

      @if (isActivated() && contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" />
      }

      @if (selectedEntity) {
        <generic-connector-def-detail [connectorDef]="selectedEntity" />

        <generic-connector-http-header-table
          [httpHeaders]="selectedEntity.httpHeaders"
          [editable]="canEditConnectorDef()"
          (httpHeadersChange)="onHttpHeadersChange($event)" />

        <p-accordion [multiple]="true" [(value)]="expandedPanelValues">
          @for (endpoint of selectedEntity.endpoints; track endpoint.idEndpoint; let i = $index) {
            <div
              (click)="onEndpointPanelClick(endpoint, $event)"
              [class]="{
                'active-border': endpoint === selectedEndpoint(),
                'passiv-border': endpoint !== selectedEndpoint()
              }">
              <p-accordion-panel [value]="'' + i">
                <p-accordion-header>
                  <h5>
                    {{ endpoint.feedSupport | translate }} &mdash;
                    {{ endpoint.instrumentType | translate }}
                  </h5>
                </p-accordion-header>
                <p-accordion-content>
                  <generic-connector-endpoint-panel
                    [endpoint]="endpoint"
                    [editable]="canEditEndpoint(endpoint)"
                    (editEndpoint)="handleEditEndpoint(endpoint)"
                    (fieldMappingsChange)="onFieldMappingsChange($event, endpoint)" />
                </p-accordion-content>
              </p-accordion-panel>
            </div>
          }
        </p-accordion>
      }
    </div>

    @if (visibleEditDialog) {
      <generic-connector-def-edit
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        (closeDialog)="handleCloseEditDialog($event)" />
    }
    @if (visibleEndpointDialog()) {
      @if (selectedEntity; as definition) {
        <generic-connector-endpoint-edit
          [visibleDialog]="visibleEndpointDialog()"
          [endpoint]="endpointToEdit"
          [connectorDef]="definition"
          (closeDialog)="handleCloseEndpointDialog($event)" />
      }
    }
    @if (visibleTestDialog()) {
      @if (selectedEntity; as definition) {
        @if (selectedEndpoint(); as endpoint) {
          <generic-connector-test-dialog
            [visibleDialog]="visibleTestDialog()"
            [endpoint]="endpoint"
            [connectorDef]="definition"
            (closeDialog)="handleCloseTestDialog()" />
        }
      }
    }
  `,
  imports: [
    TranslateModule,
    ...DYNAMIC_FORM_IMPORTS,
    ContextMenuModule,
    AccordionModule,
    GenericConnectorDefDetailComponent,
    GenericConnectorEndpointPanelComponent,
    GenericConnectorDefEditComponent,
    GenericConnectorEndpointEditComponent,
    GenericConnectorHttpHeaderTableComponent,
    GenericConnectorTestDialogComponent
  ]
})
export class GenericConnectorComponent
  extends SingleRecordMasterViewBase<GenericConnectorDef, GenericConnectorEndpoint>
  implements OnInit
{
  private genericConnectorDefService: GenericConnectorDefService = inject(GenericConnectorDefService);
  private dialogService: DialogService = inject(DialogService);

  readonly visibleEndpointDialog = signal(false);
  readonly visibleTestDialog = signal(false);
  readonly selectedEndpoint = signal<GenericConnectorEndpoint | null>(null);
  endpointToEdit: GenericConnectorEndpoint;
  private readonly injector = inject(Injector);
  private readSubscription?: Subscription;
  private formRender?: AfterRenderRef;
  private lastSelectedConnectorId: number | null = null;
  private endpointParent: GenericConnectorDef | null = null;

  constructor() {
    super(
      AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR,
      'idGenericConnector',
      AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF),
      inject(GenericConnectorDefService)
    );

    this.formConfig = { labelColumns: 2, nonModal: true };
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idGenericConnector', 'GENERIC_CONNECTOR_DEF', false, {
        usedLayoutColumns: 6
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  readonly expandedPanelValues = signal<string[]>([]);

  private initExpandedPanels(): void {
    if (!this.selectedEntity?.endpoints) {
      this.expandedPanelValues.set([]);
    } else {
      this.expandedPanelValues.set(this.selectedEntity.endpoints.map((_, i) => '' + i));
    }
  }

  ngOnInit(): void {
    this.readData();
  }

  readData(): void {
    this.readSubscription?.unsubscribe();
    this.formRender?.destroy();
    this.readSubscription = this.genericConnectorDefService
      .getAllGenericConnectors()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((connectors) => {
        const selectedId = this.selectedEntity?.idGenericConnector;
        this.entityList = connectors;
        this.selectedEntity = connectors.find((connector) => connector.idGenericConnector === selectedId) ?? null;
        this.configObject.idGenericConnector.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'idGenericConnector',
            'readableName',
            connectors,
            true
          );
        this.formRender = afterNextRender(
          () => {
            this.valueChangedMainField();
            this.setFieldValues();
          },
          { injector: this.injector }
        );
      });
  }

  setChildData(selectedEntity: GenericConnectorDef | null): void {
    const newId = selectedEntity?.idGenericConnector ?? null;
    if (newId !== this.lastSelectedConnectorId) {
      this.visibleEndpointDialog.set(false);
      this.visibleTestDialog.set(false);
      this.endpointParent = null;
      this.selectedEndpoint.set(null);
      this.initExpandedPanels();
      this.lastSelectedConnectorId = newId;
    } else {
      this.selectedEndpoint.set(this.findSelectedEndpoint(selectedEntity));
    }
    this.childEntityList = selectedEntity?.endpoints || [];
    this.refreshMenus();
  }

  private findSelectedEndpoint(connector: GenericConnectorDef | null): GenericConnectorEndpoint | null {
    const selected = this.selectedEndpoint();
    return (
      (selected &&
        connector?.endpoints.find((endpoint) =>
          selected.idEndpoint != null
            ? endpoint.idEndpoint === selected.idEndpoint
            : endpoint.feedSupport === selected.feedSupport && endpoint.instrumentType === selected.instrumentType
        )) ||
      null
    );
  }

  /**
   * Returns true if the current user can edit the connector definition (def-level fields + headers).
   * Admin can always edit. Creator can edit only if no endpoint has been used successfully.
   */
  canEditConnectorDef(): boolean {
    if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      return true;
    }
    if (!this.selectedEntity || !this.gps.isEntityCreatedByUser(this.selectedEntity)) {
      return false;
    }
    return !this.selectedEntity.endpoints?.some((e) => e.everUsedSuccessfully);
  }

  /**
   * Returns true if the current user can edit the given endpoint.
   * Admin can always edit. Creator can edit only if the endpoint has not been used successfully.
   */
  canEditEndpoint(endpoint: GenericConnectorEndpoint): boolean {
    if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      return true;
    }
    if (!this.selectedEntity || !this.gps.isEntityCreatedByUser(this.selectedEntity)) {
      return false;
    }
    return !endpoint.everUsedSuccessfully;
  }

  prepareEditMenu(): MenuItem[] {
    const selected = this.selectedEntity;
    const endpoint = this.selectedEndpoint();
    const menuItems: MenuItem[] = this.getBaseEditMenu('GENERIC_CONNECTOR_DEF');

    // Override edit/delete disabled state for def-level based on usage permissions
    if (menuItems.length > 0) {
      const editItem = menuItems.find((m) => m.label?.startsWith('EDIT_RECORD'));
      if (editItem) {
        editItem.disabled = !this.selectedEntity || !this.canEditConnectorDef();
      }
      const deleteItem = menuItems.find((m) => m.label?.startsWith('DELETE_RECORD'));
      if (deleteItem) {
        deleteItem.disabled =
          !this.selectedEntity || !this.canEditConnectorDef() || this.selectedEntity?.instrumentCount > 0;
      }
    }

    if (this.selectedEntity && !this.selectedEntity.activated && this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'ACTIVATE',
        command: () => {
          if (selected) this.handleActivate(selected);
        }
      });
    }
    if (this.selectedEntity && this.selectedEntity.activated && this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'DEACTIVATE' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => {
          if (selected) this.handleDeactivate(selected);
        }
      });
    }
    if (this.selectedEntity) {
      const isAdmin = this.gps.hasRole(BaseSettings.ROLE_ADMIN);
      const isCreator = this.gps.isEntityCreatedByUser(this.selectedEntity);
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'CREATE|ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleCreateEndpoint(),
        disabled: this.selectedEntity.endpoints?.length >= 4 || (!isAdmin && !isCreator)
      });
      menuItems.push({
        label: 'EDIT_RECORD|ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleEditEndpoint(endpoint),
        disabled: !endpoint || !this.canEditEndpoint(endpoint)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ENDPOINT',
        command: () => this.handleDeleteEndpoint(endpoint),
        disabled: !endpoint || !this.canEditEndpoint(endpoint)
      });
      menuItems.push({
        label: 'TEST_ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleTestEndpoint(endpoint),
        disabled: !this.selectedEndpoint()
      });
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'MAIL_TO_ADMIN' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.mailToAdmin()
      });
    }

    return menuItems;
  }

  protected prepareCallParam(entity: GenericConnectorDef | null): void {
    this.callParam = entity;
  }

  // --- HTTP Header handling ---

  onHttpHeadersChange(headers: GenericConnectorHttpHeader[]): void {
    if (!this.selectedEntity) return;
    this.selectedEntity = Object.assign(new GenericConnectorDef(), this.selectedEntity, { httpHeaders: headers });
    this.saveConnector();
  }

  // --- Endpoint handling ---

  onEndpointPanelClick(endpoint: GenericConnectorEndpoint, event: Event): void {
    this.selectedEndpoint.set(this.selectedEndpoint() === endpoint ? null : endpoint);
    this.refreshMenus();
  }

  handleCreateEndpoint(): void {
    if (!this.selectedEntity) return;
    this.endpointParent = this.selectedEntity;
    this.endpointToEdit = new GenericConnectorEndpoint();
    this.endpointToEdit.fieldMappings = [];
    this.visibleEndpointDialog.set(true);
  }

  handleEditEndpoint(endpoint: GenericConnectorEndpoint | null): void {
    if (!endpoint || !this.selectedEntity) return;
    this.endpointParent = this.selectedEntity;
    this.endpointToEdit = { ...endpoint, fieldMappings: endpoint.fieldMappings.map((mapping) => ({ ...mapping })) };
    this.visibleEndpointDialog.set(true);
  }

  handleDeleteEndpoint(endpoint: GenericConnectorEndpoint | null): void {
    const connector = this.selectedEntity;
    if (!endpoint || !connector) return;
    AppHelper.confirmationDialog(
      this.translateService,
      this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ENDPOINT',
      () => {
        const current = this.selectedEntity;
        if (!current || current.idGenericConnector !== connector.idGenericConnector) return;
        const idx = current.endpoints.indexOf(endpoint);
        if (idx >= 0) {
          this.selectedEntity = Object.assign(new GenericConnectorDef(), this.selectedEntity, {
            endpoints: current.endpoints.filter((_, index) => index !== idx)
          });
          this.selectedEndpoint.set(null);
          this.saveConnector();
        }
      }
    );
  }

  private mailToAdmin(): void {
    if (!this.selectedEntity) return;
    const subject = this.selectedEntity.shortId + ' - ' + this.selectedEntity.readableName;
    DynamicDialogs.getOpenedMailSendComponent(
      this.translateService,
      this.dialogService,
      new MailSendParam(0, null, subject, BaseSettings.ROLE_ADMIN)
    )
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe();
  }

  handleCloseEndpointDialog(processedActionData: ProcessedActionData): void {
    this.visibleEndpointDialog.set(false);
    const parent = this.endpointParent;
    this.endpointParent = null;
    const selected = this.selectedEntity;
    if (
      processedActionData.action !== ProcessedAction.NO_CHANGE &&
      parent &&
      selected &&
      selected.idGenericConnector === parent.idGenericConnector
    ) {
      const draft = this.endpointToEdit;
      const endpoints = selected.endpoints;
      const exists = endpoints.some((endpoint) => endpoint.idEndpoint === draft.idEndpoint);
      this.selectedEntity = Object.assign(new GenericConnectorDef(), this.selectedEntity, {
        endpoints: exists
          ? endpoints.map((endpoint) => (endpoint.idEndpoint === draft.idEndpoint ? draft : endpoint))
          : [...endpoints, draft]
      });
      this.selectedEndpoint.set(draft);
      this.saveConnector();
    }
  }

  handleTestEndpoint(endpoint: GenericConnectorEndpoint | null): void {
    if (!endpoint || !this.selectedEntity) return;
    this.selectedEndpoint.set(endpoint);
    this.visibleTestDialog.set(true);
  }

  handleCloseTestDialog(): void {
    this.visibleTestDialog.set(false);
  }

  onFieldMappingsChange(mappings: GenericConnectorFieldMapping[], endpoint: GenericConnectorEndpoint): void {
    const selected = this.selectedEntity;
    if (!selected) return;
    this.selectedEntity = Object.assign(new GenericConnectorDef(), this.selectedEntity, {
      endpoints: selected.endpoints.map((current) =>
        current.idEndpoint === endpoint.idEndpoint ? { ...current, fieldMappings: mappings } : current
      )
    });
    this.selectedEndpoint.set(this.findSelectedEndpoint(this.selectedEntity));
    this.saveConnector();
  }

  // --- Admin actions ---

  private handleActivate(entity: GenericConnectorDef): void {
    const id = entity.idGenericConnector;
    if (id == null) return;
    this.genericConnectorDefService
      .activateConnector(id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
          i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)
        });
        this.readData();
      });
  }

  private handleDeactivate(entity: GenericConnectorDef): void {
    const id = entity.idGenericConnector;
    if (id == null) return;
    forkJoin([
      this.translateService.get('MSG_CONFIRM_DEACTIVATE_CONNECTOR', {
        count: entity.instrumentCount
      }),
      this.translateService.get('MSG_GENERAL_HEADER')
    ])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(([msg, header]) => {
        this.confirmationService.confirm({
          message: msg,
          header: header,
          accept: () => {
            this.genericConnectorDefService
              .deactivateConnector(id)
              .pipe(takeUntilDestroyed(this.destroyRef))
              .subscribe(() => {
                this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
                  i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)
                });
                this.readData();
              });
          }
        });
      });
  }

  private saveConnector(): void {
    const entity = this.selectedEntity;
    if (!entity) return;
    this.genericConnectorDefService
      .update(entity)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((updated) => {
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED', {
          i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)
        });
        if (this.selectedEntity?.idGenericConnector === entity.idGenericConnector) {
          this.selectedEntity = updated;
          this.selectedEndpoint.set(this.findSelectedEndpoint(updated));
        }
        this.readData();
        // The reload endpoint is admin-only; calling it as a non-admin creator raises a
        // SecurityException that counts towards the user's security breach limit and locks the
        // account after a few saves. Non-activated connectors are not registered anyway.
        if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
          this.genericConnectorDefService.reloadConnectors().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
        }
      });
  }
}
