import {Component, OnDestroy, OnInit, ViewChild} from '@angular/core';
import {CommonModule} from '@angular/common';
import {ActivatedRoute, Params} from '@angular/router';
import {TranslateService} from '@ngx-translate/core';
import {ConfirmationService, MenuItem} from 'primeng/api';
import {ContextMenuModule} from 'primeng/contextmenu';

import {ActivePanelService} from '../../../lib/mainmenubar/service/active.panel.service';
import {AppSettings} from '../../app.settings';
import {HelpIds} from '../../../lib/help/help.ids';
import {SingleRecordMasterViewBase} from '../../../lib/masterdetail/component/single.record.master.view.base';
import {MessageToastService} from '../../../lib/message/message.toast.service';
import {InfoLevelType} from '../../../lib/message/info.leve.type';
import {GlobalparameterService} from '../../../lib/services/globalparameter.service';
import {DynamicFieldHelper} from '../../../lib/helper/dynamic.field.helper';
import {DynamicFormModule} from '../../../lib/dynamic-form/dynamic-form.module';
import {TranslateHelper} from '../../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../../lib/helper/select.options.helper';
import {ProcessedActionData} from '../../../lib/types/processed.action.data';
import {ProcessedAction} from '../../../lib/types/processed.action';
import {BaseSettings} from '../../../lib/base.settings';

import {GTNetSecurityImpHead} from '../model/gtnet-security-imp-head';
import {GTNetSecurityImpPos} from '../model/gtnet-security-imp-pos';
import {GTNetSecurityImpHeadService} from '../service/gtnet-security-imp-head.service';
import {GTNetSecurityImportTableComponent} from './gtnet-security-import-table.component';
import {GTNetSecurityImportEditHeadComponent} from './gtnet-security-import-edit-head.component';

/**
 * Main component for GTNet security import operations.
 * Allows users to create and manage import sets with security positions.
 */
@Component({
  template: `
    <div class="data-container" (click)="onComponentClick($event)" #cmDiv
         [ngClass]="{'active-border': isActivated(), 'passiv-border': !isActivated()}">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm">
      </dynamic-form>

      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" appendTo="body"></p-contextMenu>
      }
      <br/>
      <gtnet-security-import-table
        [selectedHead]="selectedEntity"
        (positionChanged)="onPositionChanged()">
      </gtnet-security-import-table>
    </div>

    @if (visibleEditDialog) {
      <gtnet-security-import-edit-head
        [visibleDialog]="visibleEditDialog"
        [entity]="callParam"
        (closeDialog)="handleCloseEditDialog($event)">
      </gtnet-security-import-edit-head>
    }
  `,
  standalone: true,
  imports: [
    CommonModule,
    DynamicFormModule,
    ContextMenuModule,
    GTNetSecurityImportTableComponent,
    GTNetSecurityImportEditHeadComponent
  ]
})
export class GTNetSecurityImportComponent
  extends SingleRecordMasterViewBase<GTNetSecurityImpHead, GTNetSecurityImpPos, GTNetSecurityImpHead>
  implements OnInit, OnDestroy {

  private static readonly MAIN_FIELD = 'idGtNetSecurityImpHead';
  private static readonly STORAGE_KEY_SELECTED_HEAD = 'selectedGtNetSecurityImpHead';

  @ViewChild(GTNetSecurityImportTableComponent) tableComponent: GTNetSecurityImportTableComponent;

  /** Import transaction head ID passed from transaction import context */
  private idTransactionHead: number = null;

  constructor(
    private activatedRoute: ActivatedRoute,
    private gtNetSecurityImpHeadService: GTNetSecurityImpHeadService,
    gps: GlobalparameterService,
    confirmationService: ConfirmationService,
    messageToastService: MessageToastService,
    activePanelService: ActivePanelService,
    translateService: TranslateService
  ) {
    super(
      gps,
      HelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY,
      GTNetSecurityImportComponent.MAIN_FIELD,
      'GTNET_SECURITY_IMP_HEAD',
      gtNetSecurityImpHeadService,
      confirmationService,
      messageToastService,
      activePanelService,
      translateService
    );

    this.formConfig = {labelColumns: 2, nonModal: true};

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(
        GTNetSecurityImportComponent.MAIN_FIELD,
        'GTNET_SECURITY_IMPORT_SET',
        false,
        {usedLayoutColumns: 6}
      ),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF(
        'note',
        BaseSettings.FID_MAX_LETTERS,
        false,
        {usedLayoutColumns: 6, disabled: true}
      )
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    // Subscribe to route params to read idTransactionHead (passed from transaction import context)
    this.activatedRoute.params.subscribe((params: Params) => {
      if (params[AppSettings.ID_TRANSACTION_HEAD]) {
        this.idTransactionHead = Number(params[AppSettings.ID_TRANSACTION_HEAD]);
        this.reSetHelpId(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT_GTNET);
      }
    });

    // Also check localStorage for idTransactionHead (set by import transaction component)
    const storedIdTransactionHead = localStorage.getItem(AppSettings.ID_TRANSACTION_HEAD);
    if (storedIdTransactionHead) {
      this.idTransactionHead = Number(storedIdTransactionHead);
      localStorage.removeItem(AppSettings.ID_TRANSACTION_HEAD);  // Clear after reading
    }

    // Use setTimeout to ensure dynamic-form has created the form controls
    setTimeout(() => {
      this.valueChangedMainField();
      this.readData();
    });
  }

  readData(): void {
    this.gtNetSecurityImpHeadService.getAll().subscribe((heads: GTNetSecurityImpHead[]) => {
      this.entityList = heads;
      this.configObject.idGtNetSecurityImpHead.valueKeyHtmlOptions =
        SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
          'idGtNetSecurityImpHead',
          'name',
          heads,
          true
        );

      // Restore selection from localStorage if no entity is selected
      if (!this.selectedEntity) {
        const savedHeadId = localStorage.getItem(GTNetSecurityImportComponent.STORAGE_KEY_SELECTED_HEAD);
        if (savedHeadId) {
          this.selectedEntity = this.entityList.find(h => h.idGtNetSecurityImpHead === Number(savedHeadId));
        }
      }
      this.setFieldValues();
    });
  }

  protected override setFieldValues(): void {
    super.setFieldValues();
    // Save selection to localStorage
    if (this.selectedEntity?.idGtNetSecurityImpHead) {
      localStorage.setItem(GTNetSecurityImportComponent.STORAGE_KEY_SELECTED_HEAD,
        String(this.selectedEntity.idGtNetSecurityImpHead));
    }
  }

  setChildData(selectedEntity: GTNetSecurityImpHead): void {
    if (this.tableComponent) {
      this.tableComponent.loadPositions(selectedEntity);
    }
  }

  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = this.getBaseEditMenu('GTNET_SECURITY_IMP_HEAD');

    // Add import job menu item if a header is selected
    if (this.selectedEntity) {
      menuItems.push({separator: true});
      menuItems.push({
        label: 'GTNET_IMPORT_SECURITIES_JOB',
        disabled: !this.hasUnmatchedPositions(),
        command: () => this.triggerImportJob()
      });
    }

    if (this.tableComponent) {
      const tableMenuItems = this.tableComponent.prepareEditMenu();
      if (tableMenuItems.length > 0) {
        menuItems.push({separator: true});
        menuItems.push(...tableMenuItems);
      }
    }

    TranslateHelper.translateMenuItems(menuItems, this.translateService);
    return menuItems;
  }

  /**
   * Checks if there are positions without linked securities.
   */
  private hasUnmatchedPositions(): boolean {
    return this.tableComponent?.positions?.some(p => !p.security) ?? false;
  }

  /**
   * Triggers the background import job for the selected header.
   * If idTransactionHead is available (from import context), passes it to auto-assign securities
   * to matching ImportTransactionPos entries after successful import.
   */
  private triggerImportJob(): void {
    if (!this.selectedEntity) {
      return;
    }

    this.gtNetSecurityImpHeadService.queueImportJob(
      this.selectedEntity.idGtNetSecurityImpHead,
      this.idTransactionHead  // Pass context if available
    ).subscribe({
      next: (result) => {
        const msgKey = result.queued ? 'GTNET_IMPORT_JOB_QUEUED' : 'GTNET_IMPORT_JOB_ALREADY_PENDING';
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, msgKey);
      }
    });
  }

  onPositionChanged(): void {
    this.refreshMenus();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  protected prepareCallParam(entity: GTNetSecurityImpHead): void {
    this.callParam = entity;
  }

  override handleCloseEditDialog(processedActionData: ProcessedActionData): void {
    this.visibleEditDialog = false;
    if (processedActionData.action !== ProcessedAction.NO_CHANGE) {
      this.selectedEntity = processedActionData.data;
      this.readData();
    }
  }
}
