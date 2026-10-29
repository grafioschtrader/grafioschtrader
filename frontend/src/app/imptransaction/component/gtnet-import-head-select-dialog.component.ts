import { requireFormControl } from '../../lib/dynamic-form/models/form-control-access';
import { Component, OnInit, input, inject } from '@angular/core';
import { Validators } from '@angular/forms';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { DialogModule } from '@openng/optimus-ui/dialog';

import { SimpleFormEditBase } from '../../lib/edit/simple.form.edit.base';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { InfoLevelType } from '../../lib/message/info.leve.type';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { SelectOptionsHelper } from '../../lib/helper/select.options.helper';
import { AppHelper } from '../../lib/helper/app.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ValueKeyHtmlSelectOptions } from '../../lib/dynamic-form/models/value.key.html.select.options';

import { GTNetSecurityImpHead } from '../../shared/gtnet/model/gtnet-security-imp-head';
import { GTNetSecurityImpHeadService } from '../../shared/gtnet/service/gtnet-security-imp-head.service';
import { GTNetSecurityImpPosService } from '../../shared/gtnet/service/gtnet-security-imp-pos.service';

/**
 * Dialog component for selecting or creating a GTNet security import header
 * and creating positions from missing import transaction securities.
 * The backend reads ImportTransactionPos entries directly.
 */
@Component({
  selector: 'gtnet-import-head-select-dialog',
  template: `
    <p-dialog
      header="{{ 'SELECT_OR_CREATE_GTNET_IMPORT_HEAD' | translate }}"
      [visible]="visibleDialog()"
      [style]="{ width: '500px' }"
      (onShow)="onShow($event)"
      (onHide)="onHide($event)"
      [modal]="true">
      <dynamic-form
        [config]="config"
        [formConfig]="formConfig"
        [translateService]="translateService"
        #form="dynamicForm"
        (submitBt)="submit($event)" />
    </p-dialog>
  `,
  imports: [DialogModule, ...DYNAMIC_FORM_IMPORTS, TranslateModule]
})
export class GTNetImportHeadSelectDialogComponent extends SimpleFormEditBase implements OnInit {
  public translateService: TranslateService = inject(TranslateService);
  private messageToastService: MessageToastService = inject(MessageToastService);
  private gtNetSecurityImpHeadService: GTNetSecurityImpHeadService = inject(GTNetSecurityImpHeadService);
  private gtNetSecurityImpPosService: GTNetSecurityImpPosService = inject(GTNetSecurityImpPosService);

  /** Suggested name for new header (from ImportTransactionHead.name) */
  readonly suggestedHeadName = input('');

  /** The import transaction head ID to create positions from */
  readonly idTransactionHead = input<number>(undefined);

  existingHeads: GTNetSecurityImpHead[] = [];
  private readonly MODE_NEW = 'new';
  private readonly MODE_EXISTING = 'existing';

  constructor() {
    super(HelpIds.HELP_BASEDATA_GT_NET_IMPORT_SECURITY);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('mode', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idGtNetSecurityImpHead', false),
      DynamicFieldHelper.createFieldInputStringHeqF('headName', 64, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.setupModeOptions();
    this.setupValueChangeHandlers();
    this.initializeFields();
    this.loadExistingHeads();
  }

  submit(value: { [name: string]: any }): void {
    const mode = requireFormControl(this.configObject.mode).value;
    const idGtNetSecurityImpHead =
      mode === this.MODE_EXISTING ? requireFormControl(this.configObject.idGtNetSecurityImpHead).value : undefined;
    const headName = mode === this.MODE_NEW ? requireFormControl(this.configObject.headName).value : undefined;

    this.configObject.submit.disabled = true;

    this.gtNetSecurityImpPosService
      .createFromImportTransactionHead(this.idTransactionHead(), idGtNetSecurityImpHead, headName)
      .pipe(this.untilDialogClosed())
      .subscribe({
        next: (head: GTNetSecurityImpHead) => {
          this.messageToastService.showMessageI18n(InfoLevelType.SUCCESS, 'GTNET_IMPORT_POSITIONS_CREATED');
          // Save to localStorage so the GTNet tab can pre-select it
          localStorage.setItem('selectedGtNetSecurityImpHead', String(head.idGtNetSecurityImpHead));
          this.closeDialog.emit(new ProcessedActionData(ProcessedAction.CREATED, head));
        },
        error: () => {
          this.configObject.submit.disabled = false;
        }
      });
  }

  private loadExistingHeads(): void {
    this.gtNetSecurityImpHeadService
      .getAll()
      .pipe(this.untilDialogClosed())
      .subscribe((heads: GTNetSecurityImpHead[]) => {
        this.existingHeads = heads;
        this.setupHeadOptions();
        const suggestedHeadName = this.suggestedHeadName();
        if (requireFormControl(this.configObject.headName).value === suggestedHeadName) {
          requireFormControl(this.configObject.headName).setValue(this.suggestUniqueName(suggestedHeadName));
        }
      });
  }

  private setupModeOptions(): void {
    const modeOptions = [
      new ValueKeyHtmlSelectOptions(this.MODE_NEW, this.translateService.instant('CREATE_NEW_HEAD')),
      new ValueKeyHtmlSelectOptions(this.MODE_EXISTING, this.translateService.instant('USE_EXISTING_HEAD'))
    ];
    this.configObject.mode.valueKeyHtmlOptions = modeOptions;
  }

  private setupHeadOptions(): void {
    this.configObject.idGtNetSecurityImpHead.valueKeyHtmlOptions =
      SelectOptionsHelper.createValueKeyHtmlSelectOptionsFromArray(
        'idGtNetSecurityImpHead',
        'name',
        this.existingHeads,
        true
      );
  }

  private setupValueChangeHandlers(): void {
    // Handle mode changes to show/hide appropriate fields
    requireFormControl(this.configObject.mode)
      .valueChanges.pipe(this.untilDialogClosed())
      .subscribe((mode: string) => {
        this.updateFieldVisibility(mode);
      });
  }

  private updateFieldVisibility(mode: string): void {
    const isNewMode = mode === this.MODE_NEW;

    // Update visibility
    this.configObject.headName.invisible = !isNewMode;
    this.configObject.idGtNetSecurityImpHead.invisible = isNewMode;

    // Update required validators
    if (isNewMode) {
      requireFormControl(this.configObject.headName).setValidators([Validators.required]);
      requireFormControl(this.configObject.idGtNetSecurityImpHead).setValidators([]);
      requireFormControl(this.configObject.idGtNetSecurityImpHead).setValue(null);
    } else {
      requireFormControl(this.configObject.headName).setValidators([]);
      requireFormControl(this.configObject.idGtNetSecurityImpHead).setValidators([Validators.required]);
      requireFormControl(this.configObject.headName).setValue('');
    }

    // Update validity after changing validators
    requireFormControl(this.configObject.headName).updateValueAndValidity();
    requireFormControl(this.configObject.idGtNetSecurityImpHead).updateValueAndValidity();
  }

  private initializeFields(): void {
    const initialMode = this.MODE_NEW;
    requireFormControl(this.configObject.mode).setValue(initialMode);

    // Set suggested name with uniqueness check
    const suggestedName = this.suggestUniqueName(this.suggestedHeadName());
    requireFormControl(this.configObject.headName).setValue(suggestedName);

    // Update visibility based on initial mode
    this.updateFieldVisibility(initialMode);
  }

  /**
   * Generates a unique name by appending a suffix if the name already exists.
   */
  private suggestUniqueName(baseName: string): string {
    const existingNames = new Set(this.existingHeads.map((h) => h.name));
    let suggestedName = baseName;
    let counter = 2;

    while (existingNames.has(suggestedName)) {
      suggestedName = `${baseName} (${counter})`;
      counter++;
    }
    return suggestedName;
  }
}
