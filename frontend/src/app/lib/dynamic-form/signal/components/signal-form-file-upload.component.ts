import { Component, computed } from '@angular/core';
import { FormField } from '@angular/forms/signals';
import { FileValueAccessorDirective } from '../../components/form-input-file/file-control-value-accessor';
import { FileRequiredValidator } from '../../components/form-input-file/file-input.validator';
import { SignalBaseInputComponent } from './signal-base-input.component';

/** File input of a signal dynamic form, bound through the existing file ControlValueAccessor. */
@Component({
  selector: 'signal-form-file-upload',
  template: `
    @let config = this.view();
    <input
      class="form-control-file-input"
      type="file"
      #input
      [formField]="field()"
      [id]="fieldName"
      [multiple]="config.dataType === DataType.Files"
      [class.required-input]="isFileRequired()"
      [accept]="config.acceptFileUploadType"
      (change)="handleChangeFileInput($any($event.target).files)" />
  `,
  imports: [FormField, FileValueAccessorDirective]
})
export class SignalFormFileUploadComponent extends SignalBaseInputComponent {
  /** The file validator is no `required` rule, so the mark also looks at the validators of the FieldConfig. */
  protected readonly isFileRequired = computed(
    () => this.isRequired() || (this.config().validation?.includes(FileRequiredValidator.validate) ?? false)
  );

  handleChangeFileInput(files: FileList | null) {
    if (!files) return;
    this.config().handleChangeFileInputFN?.(files);
  }
}
