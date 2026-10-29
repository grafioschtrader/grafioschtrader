import {
  afterNextRender,
  Component,
  inject,
  Injector,
  OnDestroy,
  OnInit,
  viewChild,
  effect,
  input,
  numberAttribute,
  untracked
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MenuItem } from '@openng/optimus-ui/api';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { AppSettings } from '../../app.settings';
import { HelpIds } from '../../../lib/help/help.ids';
import { SingleRecordMasterViewBase } from '../../../lib/masterdetail/component/single.record.master.view.base';
import { InfoLevelType } from '../../../lib/message/info.leve.type';
import { DynamicFieldHelper } from '../../../lib/helper/dynamic.field.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../../lib/dynamic-form/dynamic-form.imports';
import { TranslateHelper } from '../../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../../lib/helper/select.options.helper';
import { ProcessedActionData } from '../../../lib/types/processed.action.data';
import { ProcessedAction } from '../../../lib/types/processed.action';
import { BaseSettings } from '../../../lib/base.settings';
import { GTNetSecurityImpHead } from '../model/gtnet-security-imp-head';
import { GTNetSecurityImpPos } from '../model/gtnet-security-imp-pos';
import { GTNetSecurityImpHeadService } from '../service/gtnet-security-imp-head.service';
import { GTNetSecurityImportTableComponent } from './gtnet-security-import-table.component';
import { GTNetSecurityImportEditHeadComponent } from './gtnet-security-import-edit-head.component';

/**
 * Main component for GTNet security import operations.
 * Allows users to create and manage import sets with security positions.
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
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />

      @if (contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" appendTo="body" />
      }
      <br />
      <gtnet-security-import-table [selectedHead]="selectedEntity" (positionChanged)="onPositionChanged()" />
    </div>

    @if (visibleEditDialog) {
      <gtnet-security-import-edit-head
        [visibleDialog]="visibleEditDialog"
        [entity]="callParam"
        (closeDialog)="handleCloseEditDialog($event)" />
    }
  `,
  imports: [
    ...DYNAMIC_FORM_IMPORTS,
    ContextMenuModule,
    GTNetSecurityImportTableComponent,
    GTNetSecurityImportEditHeadComponent
  ]
})
export class GTNetSecurityImportComponent
  extends SingleRecordMasterViewBase<GTNetSecurityImpHead, GTNetSecurityImpPos, GTNetSecurityImpHead>
  implements OnInit, OnDestroy
{
  readonly transactionHeadInput = input(undefined, { alias: 'idTransactionHead', transform: numberAttribute });

  private gtNetSecurityImpHeadService: GTNetSecurityImpHeadService = inject(GTNetSecurityImpHeadService);

  private static readonly MAIN_FIELD = 'idGtNetSecurityImpHead';
  private static readonly STORAGE_KEY_SELECTED_HEAD = 'selectedGtNetSecurityImpHead';

  readonly tableComponent = viewChild(GTNetSecurityImportTableComponent);

  /** Import transaction head ID passed from transaction import context */
  private idTransactionHead: number = null;
  private readonly injector = inject(Injector);

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY,
      GTNetSecurityImportComponent.MAIN_FIELD,
      'GTNET_SECURITY_IMP_HEAD',
      inject(GTNetSecurityImpHeadService)
    );

    this.formConfig = { labelColumns: 2, nonModal: true };

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(
        GTNetSecurityImportComponent.MAIN_FIELD,
        'GTNET_SECURITY_IMPORT_SET',
        false,
        { usedLayoutColumns: 6 }
      ),
      DynamicFieldHelper.createFieldTextareaInputStringHeqF('note', BaseSettings.FID_MAX_LETTERS, false, {
        usedLayoutColumns: 6,
        disabled: true
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);

    effect(() => {
      const id = this.transactionHeadInput();
      if (id)
        untracked(() => {
          this.idTransactionHead = id;
          this.reSetHelpId(HelpIds.HELP_PORTFOLIO_SECURITYACCOUNT_TRANSACTIONIMPORT_GTNET);
        });
    });
  }

  ngOnInit(): void {
    // Also check localStorage for idTransactionHead (set by import transaction component)
    const storedIdTransactionHead = localStorage.getItem(AppSettings.ID_TRANSACTION_HEAD);
    if (storedIdTransactionHead) {
      this.idTransactionHead = Number(storedIdTransactionHead);
      localStorage.removeItem(AppSettings.ID_TRANSACTION_HEAD); // Clear after reading
    }

    // dynamic-form creates the form controls while rendering
    afterNextRender(
      () => {
        this.valueChangedMainField();
        this.readData();
      },
      { injector: this.injector }
    );
  }

  readData(): void {
    this.gtNetSecurityImpHeadService
      .getAll()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((heads: GTNetSecurityImpHead[]) => {
        this.entityList = heads;
        this.configObject.idGtNetSecurityImpHead.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray('idGtNetSecurityImpHead', 'name', heads, true);

        // Restore selection from localStorage if no entity is selected
        if (!this.selectedEntity) {
          const savedHeadId = localStorage.getItem(GTNetSecurityImportComponent.STORAGE_KEY_SELECTED_HEAD);
          if (savedHeadId) {
            this.selectedEntity = this.entityList.find((h) => h.idGtNetSecurityImpHead === Number(savedHeadId));
          }
        }
        this.setFieldValues();
      });
  }

  protected override setFieldValues(): void {
    super.setFieldValues();
    // Save selection to localStorage
    if (this.selectedEntity?.idGtNetSecurityImpHead) {
      localStorage.setItem(
        GTNetSecurityImportComponent.STORAGE_KEY_SELECTED_HEAD,
        String(this.selectedEntity.idGtNetSecurityImpHead)
      );
    }
  }

  setChildData(selectedEntity: GTNetSecurityImpHead): void {
    const tableComponent = this.tableComponent();
    if (tableComponent) {
      tableComponent.loadPositions(selectedEntity);
    }
  }

  prepareEditMenu(): MenuItem[] {
    const menuItems: MenuItem[] = this.getBaseEditMenu('GTNET_SECURITY_IMP_HEAD');

    // Add import job menu item if a header is selected
    if (this.selectedEntity) {
      menuItems.push({ separator: true });
      menuItems.push({
        label: 'GTNET_IMPORT_SECURITIES_JOB',
        disabled: !this.hasUnmatchedPositions(),
        command: () => this.triggerImportJob()
      });
    }

    const tableComponent = this.tableComponent();
    if (tableComponent) {
      const tableMenuItems = tableComponent.prepareEditMenu();
      if (tableMenuItems.length > 0) {
        menuItems.push({ separator: true });
        menuItems.push(...tableMenuItems);
      }
    }

    return menuItems;
  }

  /**
   * Checks if there are positions without linked securities.
   */
  private hasUnmatchedPositions(): boolean {
    return (
      this.tableComponent()
        ?.positions()
        .some((p) => !p.security) ?? false
    );
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

    this.gtNetSecurityImpHeadService
      .queueImportJob(
        this.selectedEntity.idGtNetSecurityImpHead,
        this.idTransactionHead // Pass context if available
      )
      .subscribe({
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
