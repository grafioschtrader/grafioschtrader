import { BaseInputComponent } from '../base.input.component';
import { Component } from '@angular/core';
import { FileRequiredValidator } from './file-input.validator';
import { ReactiveFormsModule } from '@angular/forms';

@Component({
  selector: 'form-file-upload',
  template: `
    @let config = this.config();
    @let formConfig = this.formConfig();
    @let group = this.group();
    @let rev = notifier?.revision();
    <ng-container [formGroup]="group">
      <input
        class="form-control-file-input"
        type="file"
        #input
        [id]="config.field"
        [multiple]="config.dataType === DataType.Files"
        [class.required-input]="isRequired && !config.readonly"
        [accept]="config.acceptFileUploadType"
        (change)="handleChangeFileInput($event.target.files)"
        [formControlName]="config.field" />
    </ng-container>
  `,
  imports: [ReactiveFormsModule]
})
export class FormFileUploadComponent extends BaseInputComponent {
  fileToUpload: File = null;

  handleChangeFileInput(files: FileList) {
    if (this.config().handleChangeFileInputFN) {
      this.config().handleChangeFileInputFN(files);
    }
  }

  override reEvaluateRequired(): void {
    this.isRequired = this.config().validation && this.config().validation.indexOf(FileRequiredValidator.validate) >= 0;
  }
}
