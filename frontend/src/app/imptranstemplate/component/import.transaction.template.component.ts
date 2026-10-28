import { afterNextRender, Component, inject, Injector, OnDestroy, OnInit, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';

import { TranslateModule } from '@ngx-translate/core';
import { ActivatedRoute } from '@angular/router';
import { SingleRecordMasterViewBase } from '../../lib/masterdetail/component/single.record.master.view.base';
import { HelpIds } from '../../lib/help/help.ids';
import { ImportTransactionPlatformService } from '../service/import.transaction.platform.service';
import { IPlatformTransactionImport } from '../../portfolio/component/iplatform.transaction.import';
import { ImportTransactionPlatform } from '../../entities/import.transaction.platform';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';
import { ImportTransactionTemplate } from '../../entities/import.transaction.template';
import { ImportTransactionTemplateTableComponent } from './import-transaction-template-table.component';
import { ParentChildRowSelection } from '../../lib/datashowbase/parent.child.row.selection';
import { MenuItem } from '@openng/optimus-ui/api';
import { GlobalparameterGTService } from '../../gtservice/globalparameter.gt.service';
import { plainToInstance } from 'class-transformer';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import {
  ImportTransactionTemplateService,
  SuccessFailedImportTransactionTemplate
} from '../service/import.transaction.template.service';
import { saveAs } from '../../lib/filesaver/filesaver';
import { NgxFileDropEntry, NgxFileDropModule } from 'ngx-file-drop';
import { AppHelper } from '../../lib/helper/app.helper';
import { BaseSettings } from '../../lib/base.settings';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { ContextMenuModule } from '@openng/optimus-ui/contextmenu';
import { ImportTransactionEditPlatformComponent } from './import-transaction-edit-platform.component';
import { TransformPdfToTxtDialogComponent } from './transform-pdf-to-txt-dialog.component';
import { TemplateFormCheckDialogComponent } from './template-form-check-dialog.component';

/**
 * Main component of import transaction template. It combines other components like a table.
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
      <div class="flex-two-columns">
        <h4 class="ui-widget-header singleRowTableHeader">
          {{ 'IMPORT_TRANSACTION_PLATFORM' | translate }}
        </h4>
        @if (selectedEntity) {
          <div class="right-half drop-import-template">
            <ngx-file-drop
              dropZoneLabel="{{ 'DROP_TEMPLATE_HERE' | translate }}"
              (onFileDrop)="dropped($event)"
              dropZoneClassName="drop-zone-trans-long"
              contentClassName="content-trans" />
          </div>
        }
      </div>
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm" />
      @if (isActivated() && contextMenuItems) {
        <p-contextMenu [target]="cmDiv" [model]="contextMenuItems" />
      }
      <br />
      <import-transaction-template-table />
    </div>
    @if (visibleEditDialog) {
      <import-transaction-edit-platform
        [visibleDialog]="visibleEditDialog"
        [callParam]="callParam"
        [platformTransactionImportHtmlOptions]="platformTransactionImportHtmlOptions()"
        (closeDialog)="handleCloseEditDialog($event)" />
    }
    @if (visibleTransformPDFToTxtDialog()) {
      <transform-pdf-to-txt-dialog
        [visibleDialog]="visibleTransformPDFToTxtDialog()"
        (closeDialog)="handleCloseTransformPDFToTxtDialog()" />
    }
    @if (visibleTemplateFormCheckDialog()) {
      <template-form-check-dialog
        [visibleDialog]="visibleTemplateFormCheckDialog()"
        [importTransactionPlatform]="selectedEntity"
        (closeDialog)="handleCloseTemplateFormCheckDialog()" />
    }
  `,
  imports: [
    TranslateModule,
    NgxFileDropModule,
    ...DYNAMIC_FORM_IMPORTS,
    ContextMenuModule,
    ImportTransactionTemplateTableComponent,
    ImportTransactionEditPlatformComponent,
    TransformPdfToTxtDialogComponent,
    TemplateFormCheckDialogComponent
  ]
})
export class ImportTransactionTemplateComponent
  extends SingleRecordMasterViewBase<ImportTransactionPlatform, ImportTransactionTemplate, CallParam>
  implements OnInit, OnDestroy, ParentChildRowSelection<ImportTransactionTemplate>
{
  private activatedRoute: ActivatedRoute = inject(ActivatedRoute);
  private importTransactionPlatformService: ImportTransactionPlatformService = inject(ImportTransactionPlatformService);
  private importTransactionTemplateService: ImportTransactionTemplateService = inject(ImportTransactionTemplateService);
  private gpsGT: GlobalparameterGTService = inject(GlobalparameterGTService);

  private static readonly MAIN_FIELD = 'idTransactionImportPlatform';

  // Access child components
  readonly ittdc = viewChild(ImportTransactionTemplateTableComponent);

  private readonly injector = inject(Injector);
  private readSubscription?: Subscription;
  readonly visibleTransformPDFToTxtDialog = signal(false);
  readonly visibleTemplateFormCheckDialog = signal(false);
  readonly platformTransactionImportHtmlOptions = signal<ValueKeyHtmlSelectOptions[]>([]);
  /** The platform of this instance holding the Grafioschtrader authored import templates, null when unset. */
  gtImportPlatformId: number | null;

  constructor() {
    super(
      HelpIds.HELP_BASEDATA_IMPORT_TRANSACTION_TEMPLATE_GROUP,
      ImportTransactionTemplateComponent.MAIN_FIELD,
      'IMPORT_TRANSACTION_PLATFORM',
      inject(ImportTransactionPlatformService)
    );

    this.formConfig = { labelColumns: 2, nonModal: true };

    this.config = [
      DynamicFieldHelper.createFieldSelectNumber(ImportTransactionTemplateComponent.MAIN_FIELD, 'NAME', false, {
        usedLayoutColumns: 6
      }),
      DynamicFieldHelper.createFieldInputString('idCsvImportImplementation', 'TRANSACTION_IMPLEMENTATION', 32, false, {
        disabled: true,
        usedLayoutColumns: 6
      })
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  ngOnInit(): void {
    this.gtImportPlatformId = this.gpsGT.getGtImportPlatformId();
    this.importTransactionPlatformService
      .getPlatformTransactionImport()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((platformTransactionImports: IPlatformTransactionImport[]) => {
        this.platformTransactionImportHtmlOptions.set(
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'id',
            'readableName',
            platformTransactionImports,
            true
          )
        );
        this.configObject.idCsvImportImplementation.valueKeyHtmlOptions = this.platformTransactionImportHtmlOptions();
        afterNextRender(
          () => {
            this.valueChangedMainField();
            this.readData();
          },
          { injector: this.injector }
        );
      });
  }

  readData(): void {
    this.readSubscription?.unsubscribe();
    this.readSubscription = this.importTransactionPlatformService
      .getAllImportTransactionPlatforms()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((importTransactionPlatforms: ImportTransactionPlatform[]) => {
        this.entityList = plainToInstance(ImportTransactionPlatform, importTransactionPlatforms);
        this.configObject.idTransactionImportPlatform.valueKeyHtmlOptions =
          SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
            'idTransactionImportPlatform',
            'name',
            importTransactionPlatforms,
            true
          );
        afterNextRender(() => this.setFieldValues(), { injector: this.injector });
      });
  }

  setChildData(selectedEntity: ImportTransactionPlatform): void {
    this.ittdc().parentSelectionChanged(selectedEntity, this);
  }

  prepareEditMenu(): MenuItem[] {
    let menuItems: MenuItem[] = [];
    menuItems.push({
      label: 'TRANSFORM_PDF_TO_TXT' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.transformPDFToTxtDialog()
    });
    menuItems.push({ separator: true });
    menuItems = menuItems.concat(this.getBaseEditMenu('IMPORT_TRANSACTION_PLATFORM'));
    const ittdc = this.ittdc();
    menuItems.push({
      label: 'EXPORT_ALL_IMPORTTEMPLATES',
      command: () => this.exportAllTemplates(this.selectedEntity),
      disabled: ittdc.isEmpty()
    });
    if (this.gps.hasRole(BaseSettings.ROLE_ADMIN)) {
      // Which platform carries the Grafioschtrader templates is instance wide, so only an administrator sets it.
      menuItems.push({
        label: 'SET_AS_GT_IMPORT_PLATFORM',
        command: () => this.setAsGtImportPlatform(this.selectedEntity),
        disabled: !this.selectedEntity || this.selectedEntity.idTransactionImportPlatform === this.gtImportPlatformId
      });
    }

    menuItems.push({ separator: true });
    menuItems.push({
      label: 'CHECK_TEMPLATE_FORM' + BaseSettings.DIALOG_MENU_SUFFIX,
      command: () => this.testTemplateDialog(),
      disabled: !this.selectedEntity || ittdc.isEmpty()
    });

    menuItems.splice(menuItems.length - 2, 0, ...ittdc.prepareEditMenu());

    return menuItems;
  }

  transformPDFToTxtDialog(): void {
    this.visibleTransformPDFToTxtDialog.set(true);
  }

  testTemplateDialog(): void {
    this.visibleTemplateFormCheckDialog.set(true);
  }

  handleCloseTransformPDFToTxtDialog(): void {
    this.visibleTransformPDFToTxtDialog.set(false);
  }

  handleCloseTemplateFormCheckDialog() {
    this.visibleTemplateFormCheckDialog.set(false);
  }

  rowSelectionChanged(childEntityList: ImportTransactionTemplate[], childSelectedEntity: ImportTransactionTemplate) {
    this.childEntityList = childEntityList;
    this.refreshMenus();
  }

  ngOnDestroy(): void {
    super.destroy();
  }

  public dropped(files: NgxFileDropEntry[]) {
    AppHelper.processDroppedFiles(files, this.messageToastService, 'tmpl', this.uploadTemplateFiles.bind(this));
  }

  /*
    protected beforeDelete(entity: ImportTransactionPlatform): ImportTransactionPlatform {
      const importTransactionPlatform = new ImportTransactionPlatform();
      return Object.assign(importTransactionPlatform, entity);
    }
  */
  protected prepareCallParam(entity: ImportTransactionPlatform): void {
    this.callParam = new CallParam(null, entity);
  }

  private setAsGtImportPlatform(itp: ImportTransactionPlatform): void {
    this.importTransactionPlatformService
      .setGtImportPlatform(itp.idTransactionImportPlatform)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((idTransactionImportPlatform: number | null) => {
        this.gtImportPlatformId = idTransactionImportPlatform;
        this.gpsGT.setGtImportPlatformId(idTransactionImportPlatform);
        this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'SET_AS_GT_IMPORT_PLATFORM_SUCCESS', {
          name: itp.name
        });
        this.refreshMenus();
      });
  }

  private async exportAllTemplates(itp: ImportTransactionPlatform): Promise<void> {
    const blob = await this.importTransactionTemplateService
      .getTemplatesByPlatformPlanAsZip(this.selectedEntity.idTransactionImportPlatform)
      .catch((error) => this.messageToastService.showMessageI18n(InfoLevelType.ERROR, 'DOWNLOAD_TEMPLATE_DATA_FAILED'));
    if (blob) {
      saveAs(blob, itp.name + '.zip');
      this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'DOWNLOAD_TEMPLATE_DATA_SUCCESS');
    }
  }

  private uploadTemplateFiles(formData: FormData): void {
    this.importTransactionTemplateService
      .uploadImportTemplateFiles(this.selectedEntity.idTransactionImportPlatform, formData)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((sitt: SuccessFailedImportTransactionTemplate) => {
        this.messageToastService.showMessageI18nEnableHtml(InfoLevelType.INFO, 'UPLOAD_TEMPLATES_SUCCESS', {
          successNew: sitt.successNew,
          successUpdated: sitt.successUpdated,
          notOwner: sitt.notOwner,
          fileNameError: sitt.fileNameError,
          contentError: sitt.contentError
        });
        this.setChildData(this.selectedEntity);
      });
  }
}
