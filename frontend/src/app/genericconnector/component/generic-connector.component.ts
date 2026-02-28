import {Component, OnDestroy, OnInit} from '@angular/core';
import {CommonModule} from '@angular/common';
import {TranslateModule, TranslateService} from '@ngx-translate/core';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {combineLatest} from 'rxjs';
import {ActivePanelService} from '../../lib/mainmenubar/service/active.panel.service';
import {SingleRecordMasterViewBase} from '../../lib/masterdetail/component/single.record.master.view.base';
import {GenericConnectorDef} from '../../entities/generic.connector.def';
import {GenericConnectorEndpoint} from '../../entities/generic.connector.endpoint';
import {GenericConnectorFieldMapping} from '../../entities/generic.connector.field.mapping';
import {GenericConnectorDefService} from '../service/generic.connector.def.service';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {AppHelpIds} from '../../shared/help/help.ids';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {BaseSettings} from '../../lib/base.settings';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {AppHelper} from '../../lib/helper/app.helper';
import {DynamicFormModule} from '../../lib/dynamic-form/dynamic-form.module';
import {ContextMenuModule} from 'primeng/contextmenu';
import {AccordionModule} from 'primeng/accordion';
import {GenericConnectorDefDetailComponent} from './generic-connector-def-detail.component';
import {GenericConnectorEndpointPanelComponent} from './generic-connector-endpoint-panel.component';
import {GenericConnectorDefEditComponent} from './generic-connector-def-edit.component';
import {GenericConnectorEndpointEditComponent} from './generic-connector-endpoint-edit.component';
import {GenericConnectorHttpHeaderTableComponent} from './generic-connector-http-header-table.component';
import {GenericConnectorHttpHeader} from '../../entities/generic.connector.http.header';
import {GenericConnectorTestDialogComponent} from './generic-connector-test-dialog.component';
import {DialogService} from 'primeng/dynamicdialog';
import {DynamicDialogs} from '../../lib/dynamicdialog/component/dynamic.dialogs';
import {MailSendParam} from '../../lib/dynamicdialog/component/mail.send.dynamic.component';
import {AppSettings} from '../../shared/app.settings';

/**
 * Top-level component for managing generic connector definitions.
 * Displays a dropdown selector for connectors, connector detail fields, and an accordion
 * with endpoint panels containing field mapping tables.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">
      <h4>{{ 'GENERIC_CONNECTOR_DEF' | translate }}</h4>

      <dynamic-form [config]="config" [formConfig]="formConfig"
                    [translateService]="translateService" #form="dynamicForm">
      </dynamic-form>

      @if (isActivated() && contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems"></p-contextMenu>
      }

      @if (selectedEntity) {
        <generic-connector-def-detail [connectorDef]="selectedEntity">
        </generic-connector-def-detail>

        <generic-connector-http-header-table
          [httpHeaders]="selectedEntity.httpHeaders"
          [editable]="canEditConnectorDef()"
          (httpHeadersChange)="onHttpHeadersChange($event)">
        </generic-connector-http-header-table>

        <p-accordion [multiple]="true" [(value)]="expandedPanelValues">
          @for (endpoint of selectedEntity.endpoints; track endpoint.idEndpoint; let i = $index) {
            <div (click)="onEndpointPanelClick(endpoint, $event)"
                 [ngClass]="{'active-border': endpoint === selectedEndpoint,
                             'passiv-border': endpoint !== selectedEndpoint}">
              <p-accordion-panel [value]="'' + i">
                <p-accordion-header>
                  <h5>{{ endpoint.feedSupport  | translate }} &mdash; {{ endpoint.instrumentType  | translate }}</h5>
                </p-accordion-header>
                <p-accordion-content>
                  <generic-connector-endpoint-panel
                    [endpoint]="endpoint"
                    [editable]="canEditEndpoint(endpoint)"
                    (editEndpoint)="handleEditEndpoint(endpoint)"
                    (fieldMappingsChange)="onFieldMappingsChange($event, endpoint)">
                  </generic-connector-endpoint-panel>
                </p-accordion-content>
              </p-accordion-panel>
            </div>
          }
        </p-accordion>
      }
    </div>

    @if (visibleEditDialog) {
      <generic-connector-def-edit [visibleDialog]="visibleEditDialog"
        [callParam]="callParam" (closeDialog)="handleCloseEditDialog($event)">
      </generic-connector-def-edit>
    }
    @if (visibleEndpointDialog) {
      <generic-connector-endpoint-edit [visibleDialog]="visibleEndpointDialog"
        [endpoint]="selectedEndpoint" [connectorDef]="selectedEntity"
        (closeDialog)="handleCloseEndpointDialog($event)">
      </generic-connector-endpoint-edit>
    }
    @if (visibleTestDialog) {
      <generic-connector-test-dialog [visibleDialog]="visibleTestDialog"
        [endpoint]="selectedEndpoint" [connectorDef]="selectedEntity"
        (closeDialog)="handleCloseTestDialog($event)">
      </generic-connector-test-dialog>
    }
  `,
  standalone: true,
  imports: [
    CommonModule,
    TranslateModule,
    DynamicFormModule,
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
  implements OnInit, OnDestroy {

  visibleEndpointDialog = false;
  visibleTestDialog = false;
  selectedEndpoint: GenericConnectorEndpoint;
  private lastSelectedConnectorId: number = null;

  constructor(private genericConnectorDefService: GenericConnectorDefService,
              private dialogService: DialogService,
              gps: GlobalparameterService,
              confirmationService: ConfirmationService,
              messageToastService: MessageToastService,
              activePanelService: ActivePanelService,
              translateService: TranslateService) {
    super(gps, AppHelpIds.HELP_BASEDATA_GENERIC_CONNECTOR, 'idGenericConnector',
      AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF), genericConnectorDefService,
      confirmationService, messageToastService, activePanelService, translateService);

    this.formConfig = {labelColumns: 2, nonModal: true};
    this.config = [
      DynamicFieldHelper.createFieldSelectNumber('idGenericConnector', 'GENERIC_CONNECTOR_DEF', false,
        {usedLayoutColumns: 6})
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  expandedPanelValues: string[] = [];

  private initExpandedPanels(): void {
    if (!this.selectedEntity?.endpoints) {
      this.expandedPanelValues = [];
    } else {
      this.expandedPanelValues = this.selectedEntity.endpoints.map((_, i) => '' + i);
    }
  }

  ngOnInit(): void {
    this.readData();
  }

  readData(): void {
    this.genericConnectorDefService.getAllGenericConnectors().subscribe(connectors => {
      this.entityList = connectors;
      this.configObject.idGenericConnector.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idGenericConnector',
          'readableName', connectors, true);
      setTimeout(() => {
        this.valueChangedMainField();
        this.setFieldValues();
      });
    });
  }

  setChildData(selectedEntity: GenericConnectorDef): void {
    const newId = selectedEntity?.idGenericConnector ?? null;
    if (newId !== this.lastSelectedConnectorId) {
      this.selectedEndpoint = null;
      this.initExpandedPanels();
      this.lastSelectedConnectorId = newId;
    }
    this.childEntityList = selectedEntity?.endpoints || [];
    this.refreshMenus();
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
    return !this.selectedEntity.endpoints?.some(e => e.everUsedSuccessfully);
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
    const menuItems: MenuItem[] = this.getBaseEditMenu('GENERIC_CONNECTOR_DEF');

    // Override edit/delete disabled state for def-level based on usage permissions
    if (menuItems.length > 0) {
      const editItem = menuItems.find(m => m.label?.startsWith('EDIT_RECORD'));
      if (editItem) {
        editItem.disabled = !this.selectedEntity || !this.canEditConnectorDef();
      }
      const deleteItem = menuItems.find(m => m.label?.startsWith('DELETE_RECORD'));
      if (deleteItem) {
        deleteItem.disabled = !this.selectedEntity || !this.canEditConnectorDef()
          || (this.selectedEntity?.instrumentCount > 0);
      }
    }

    if (this.selectedEntity && !this.selectedEntity.activated && this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({separator: true});
      menuItems.push({
        label: 'ACTIVATE' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleActivate(this.selectedEntity)
      });
    }
    if (this.selectedEntity && this.selectedEntity.activated && this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      menuItems.push({separator: true});
      menuItems.push({
        label: 'DEACTIVATE' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleDeactivate(this.selectedEntity)
      });
    }
    if (this.selectedEntity) {
      const isAdmin = this.gps.hasRole(BaseSettings.ROLE_ADMIN);
      const isCreator = this.gps.isEntityCreatedByUser(this.selectedEntity);
      menuItems.push({separator: true});
      menuItems.push({
        label: 'CREATE|ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleCreateEndpoint(),
        disabled: this.selectedEntity.endpoints?.length >= 4 || (!isAdmin && !isCreator)
      });
      menuItems.push({
        label: 'EDIT_RECORD|ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleEditEndpoint(this.selectedEndpoint),
        disabled: !this.selectedEndpoint || !this.canEditEndpoint(this.selectedEndpoint)
      });
      menuItems.push({
        label: 'DELETE_RECORD|ENDPOINT',
        command: () => this.handleDeleteEndpoint(this.selectedEndpoint),
        disabled: !this.selectedEndpoint || !this.canEditEndpoint(this.selectedEndpoint)
      });
      menuItems.push({
        label: 'TEST_ENDPOINT' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.handleTestEndpoint(this.selectedEndpoint),
        disabled: !this.selectedEndpoint
      });
      menuItems.push({separator: true});
      menuItems.push({
        label: 'MAIL_TO_ADMIN' + BaseSettings.DIALOG_MENU_SUFFIX,
        command: () => this.mailToAdmin()
      });
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  protected prepareCallParam(entity: GenericConnectorDef): void {
    this.callParam = entity;
  }

  // --- HTTP Header handling ---

  onHttpHeadersChange(headers: GenericConnectorHttpHeader[]): void {
    this.selectedEntity.httpHeaders = headers;
    this.saveConnector();
  }

  // --- Endpoint handling ---

  onEndpointPanelClick(endpoint: GenericConnectorEndpoint, event: Event): void {
    this.selectedEndpoint = this.selectedEndpoint === endpoint ? null : endpoint;
    this.refreshMenus();
  }

  handleCreateEndpoint(): void {
    this.selectedEndpoint = new GenericConnectorEndpoint();
    this.selectedEndpoint.fieldMappings = [];
    this.visibleEndpointDialog = true;
  }

  handleEditEndpoint(endpoint: GenericConnectorEndpoint): void {
    this.selectedEndpoint = endpoint;
    this.visibleEndpointDialog = true;
  }

  handleDeleteEndpoint(endpoint: GenericConnectorEndpoint): void {
    AppHelper.confirmationDialog(this.translateService, this.confirmationService,
      'MSG_CONFIRM_DELETE_RECORD|ENDPOINT', () => {
        const idx = this.selectedEntity.endpoints.indexOf(endpoint);
        if (idx >= 0) {
          this.selectedEntity.endpoints.splice(idx, 1);
          this.selectedEndpoint = null;
          this.saveConnector();
        }
      });
  }

  private mailToAdmin(): void {
    const subject = this.selectedEntity.shortId + ' - ' + this.selectedEntity.readableName;
    DynamicDialogs.getOpenedMailSendComponent(this.translateService, this.dialogService,
      new MailSendParam(0, null, subject, BaseSettings.ROLE_ADMIN));
  }

  handleCloseEndpointDialog(processedActionData: ProcessedActionData): void {
    this.visibleEndpointDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      if (!this.selectedEntity.endpoints.includes(this.selectedEndpoint)) {
        this.selectedEntity.endpoints.push(this.selectedEndpoint);
      }
      this.saveConnector();
    }
  }

  handleTestEndpoint(endpoint: GenericConnectorEndpoint): void {
    this.selectedEndpoint = endpoint;
    this.visibleTestDialog = true;
  }

  handleCloseTestDialog(): void {
    this.visibleTestDialog = false;
  }

  // --- Field Mapping handling ---

  onFieldMappingsChange(mappings: GenericConnectorFieldMapping[], endpoint: GenericConnectorEndpoint): void {
    const ep = this.selectedEntity.endpoints.find(e => e.idEndpoint === endpoint.idEndpoint);
    if (ep) {
      ep.fieldMappings = mappings;
    }
    this.saveConnector();
  }

  // --- Admin actions ---

  private handleActivate(entity: GenericConnectorDef): void {
    this.genericConnectorDefService.activateConnector(entity.idGenericConnector).subscribe(() => {
      this.messageToastService.showMessageI18n(null, 'MSG_RECORD_SAVED',
        {i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)});
      this.readData();
    });
  }

  private handleDeactivate(entity: GenericConnectorDef): void {
    combineLatest([
      this.translateService.get('MSG_CONFIRM_DEACTIVATE_CONNECTOR', {count: entity.instrumentCount}),
      this.translateService.get('MSG_GENERAL_HEADER')
    ]).subscribe(([msg, header]) => {
      this.confirmationService.confirm({
        message: msg,
        header: header,
        accept: () => {
          this.genericConnectorDefService.deactivateConnector(entity.idGenericConnector).subscribe(() => {
            this.messageToastService.showMessageI18n(null, 'MSG_RECORD_SAVED',
              {i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)});
            this.readData();
          });
        }
      });
    });
  }

  private saveConnector(): void {
    this.genericConnectorDefService.update(this.selectedEntity).subscribe(updated => {
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'MSG_RECORD_SAVED',
        {i18nRecord: AppHelper.toUpperCaseWithUnderscore(AppSettings.GENERIC_CONNECTOR_DEF)});
      this.selectedEntity = updated;
      this.readData();
      this.genericConnectorDefService.reloadConnectors().subscribe();
    });
  }

  ngOnDestroy(): void {
    super.destroy();
  }
}
