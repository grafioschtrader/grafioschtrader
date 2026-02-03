import {Component, Input, OnInit} from '@angular/core';
import {Validators} from '@angular/forms';
import {TranslateService} from '@ngx-translate/core';

import {SimpleEditBase} from '../../lib/edit/simple.edit.base';
import {GlobalparameterService} from '../../lib/services/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {InfoLevelType} from '../../lib/message/info.leve.type';
import {DynamicFieldHelper} from '../../lib/helper/dynamic.field.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {AppHelper} from '../../lib/helper/app.helper';
import {HelpIds} from '../../lib/help/help.ids';
import {ProcessedActionData} from '../../lib/types/processed.action.data';
import {ProcessedAction} from '../../lib/types/processed.action';
import {ValueKeyHtmlSelectOptions} from '../../lib/dynamic-form/models/value.key.html.select.options';

import {GTNetSecurityImpHead} from '../../shared/gtnet/model/gtnet-security-imp-head';
import {GTNetSecurityImpHeadService} from '../../shared/gtnet/service/gtnet-security-imp-head.service';
import {GTNetSecurityImpPosService} from '../../shared/gtnet/service/gtnet-security-imp-pos.service';

/**
 * Dialog component for selecting or creating a GTNet security import header
 * and creating positions from missing import transaction securities.
 * The backend reads ImportTransactionPos entries directly.
 */
@Component({
  selector: 'gtnet-import-head-select-dialog',
  template: `
    <p-dialog header="{{'SELECT_OR_CREATE_GTNET_IMPORT_HEAD' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '500px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>
  `,
  standalone: false
})
export class GTNetImportHeadSelectDialogComponent extends SimpleEditBase implements OnInit {

  /** Suggested name for new header (from ImportTransactionHead.name) */
  @Input() suggestedHeadName = '';

  /** The import transaction head ID to create positions from */
  @Input() idTransactionHead: number;

  existingHeads: GTNetSecurityImpHead[] = [];
  private readonly MODE_NEW = 'new';
  private readonly MODE_EXISTING = 'existing';

  constructor(
    public translateService: TranslateService,
    gps: GlobalparameterService,
    private messageToastService: MessageToastService,
    private gtNetSecurityImpHeadService: GTNetSecurityImpHeadService,
    private gtNetSecurityImpPosService: GTNetSecurityImpPosService
  ) {
    super(HelpIds.HELP_BASEDATA_GTNET, gps);
  }

  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(
      this.gps,
      5,
      this.helpLink.bind(this)
    );

    this.config = [
      DynamicFieldHelper.createFieldSelectStringHeqF('mode', true),
      DynamicFieldHelper.createFieldSelectNumberHeqF('idGtNetSecurityImpHead', false),
      DynamicFieldHelper.createFieldInputStringHeqF('headName', 64, false),
      DynamicFieldHelper.createSubmitButton()
    ];
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  protected override initialize(): void {
    this.loadExistingHeads();
  }

  submit(value: { [name: string]: any }): void {
    const mode = this.configObject.mode.formControl.value;
    const idGtNetSecurityImpHead = mode === this.MODE_EXISTING
      ? this.configObject.idGtNetSecurityImpHead.formControl.value
      : undefined;
    const headName = mode === this.MODE_NEW
      ? this.configObject.headName.formControl.value
      : undefined;

    this.configObject.submit.disabled = true;

    this.gtNetSecurityImpPosService.createFromImportTransactionHead(
      this.idTransactionHead, idGtNetSecurityImpHead, headName
    ).subscribe({
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
    this.gtNetSecurityImpHeadService.getAll().subscribe((heads: GTNetSecurityImpHead[]) => {
      this.existingHeads = heads;
      this.setupModeOptions();
      this.setupHeadOptions();
      this.setupValueChangeHandlers();
      this.initializeFields();
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
    this.configObject.mode.formControl.valueChanges.subscribe((mode: string) => {
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
      this.configObject.headName.formControl.setValidators([Validators.required]);
      this.configObject.idGtNetSecurityImpHead.formControl.setValidators([]);
      this.configObject.idGtNetSecurityImpHead.formControl.setValue(null);
    } else {
      this.configObject.headName.formControl.setValidators([]);
      this.configObject.idGtNetSecurityImpHead.formControl.setValidators([Validators.required]);
      this.configObject.headName.formControl.setValue('');
    }

    // Update validity after changing validators
    this.configObject.headName.formControl.updateValueAndValidity();
    this.configObject.idGtNetSecurityImpHead.formControl.updateValueAndValidity();
  }

  private initializeFields(): void {
    const initialMode = this.MODE_NEW;
    this.configObject.mode.formControl.setValue(initialMode);

    // Set suggested name with uniqueness check
    const suggestedName = this.suggestUniqueName(this.suggestedHeadName);
    this.configObject.headName.formControl.setValue(suggestedName);

    // Update visibility based on initial mode
    this.updateFieldVisibility(initialMode);
  }

  /**
   * Generates a unique name by appending a suffix if the name already exists.
   */
  private suggestUniqueName(baseName: string): string {
    const existingNames = new Set(this.existingHeads.map(h => h.name));
    let suggestedName = baseName;
    let counter = 2;

    while (existingNames.has(suggestedName)) {
      suggestedName = `${baseName} (${counter})`;
      counter++;
    }
    return suggestedName;
  }
}
