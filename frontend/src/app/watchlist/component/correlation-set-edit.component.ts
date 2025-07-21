import {Component, Input, OnInit} from '@angular/core';
import {SimpleEntityEditBase} from '../../lib/edit/simple.entity.edit.base';
import {TranslateService} from '@ngx-translate/core';
import {GlobalparameterService} from '../../shared/service/globalparameter.service';
import {MessageToastService} from '../../lib/message/message.toast.service';
import {HelpIds} from '../../shared/help/help.ids';
import {CorrelationSetService} from '../service/correlation.set.service';
import {CorrelationLimit, CorrelationSet, SamplingPeriodType} from '../../entities/correlation.set';
import {AppHelper} from '../../lib/helper/app.helper';
import {TranslateHelper} from '../../lib/helper/translate.helper';
import {SelectOptionsHelper} from '../../lib/helper/select.options.helper';
import {CallParam} from '../../shared/maintree/types/dialog.visible';
import {CorrelationEditingSupport} from './correlation.editing.support';

/**
 * Dialog component for creating and editing correlation sets with form validation and dynamic field configuration.
 * Provides a modal dialog interface for correlation set management with sampling period and rolling configuration.
 */
@Component({
    selector: 'correlation-set-edit',
    template: `
    <p-dialog header="{{'CORRELATION_SET' | translate}}" [(visible)]="visibleDialog"
              [style]="{width: '400px'}"
              (onShow)="onShow($event)" (onHide)="onHide($event)" [modal]="true">

      <dynamic-form [config]="config" [formConfig]="formConfig" [translateService]="translateService"
                    #form="dynamicForm"
                    (submitBt)="submit($event)">
      </dynamic-form>
    </p-dialog>`,
    standalone: false
})
export class CorrelationSetEditComponent extends SimpleEntityEditBase<CorrelationSet> implements OnInit {

  /** Dialog configuration parameters containing entity data and operation context */
  @Input() callParam: CallParam;

  /** Correlation set limits and configuration constraints for validation */
  @Input() correlationLimit: CorrelationLimit;

  /** Helper service for managing correlation-specific form fields and validation */
  private correlationEditingSupport: CorrelationEditingSupport = new CorrelationEditingSupport();

  /**
   * Creates correlation set edit dialog with required services.
   * @param translateService Service for internationalization and text translation
   * @param gps Global parameter service for application settings and locale
   * @param messageToastService Service for displaying user notifications and messages
   * @param correlationSetService Service for correlation set CRUD operations
   */
  constructor(translateService: TranslateService,
              gps: GlobalparameterService,
              messageToastService: MessageToastService,
              correlationSetService: CorrelationSetService) {
    super(HelpIds.HELP_WATCHLIST_CORRELATION, 'CORRELATION_SET', translateService, gps,
      messageToastService, correlationSetService);
  }

  /**
   * Initializes component with form configuration and field definitions.
   * Sets up dynamic form fields for correlation set properties including sampling period and rolling configuration.
   */
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps,
      5, this.helpLink.bind(this));
    this.config = this.correlationEditingSupport.getCorrelationFieldDefinition(null, 12, 'SAVE_AND_CALC');
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }

  /**
   * Sets up form validation, dropdown options, and initial values.
   * Configures sampling period options, establishes field dependencies, and loads existing entity data if available.
   */
  protected override initialize(): void {
    this.configObject.samplingPeriod.valueKeyHtmlOptions = SelectOptionsHelper.createHtmlOptionsFromEnum(this.translateService,
      SamplingPeriodType);
    this.correlationEditingSupport.setUpValueChange(this.configObject, this.correlationLimit);
    if (this.callParam.thisObject != null) {
      this.form.transferBusinessObjectToForm(this.callParam.thisObject);
    }
    setTimeout(() => this.configObject.name.elementRef.nativeElement.focus());
  }

  /**
   * Prepares correlation set entity for save operation.
   * Creates new or updates existing correlation set instance with form data, excluding runtime-only properties.
   * @param value Form values containing user input data
   * @returns Prepared correlation set entity ready for persistence
   */
  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): CorrelationSet {
    const newCorrelationSet = this.copyFormToPrivateBusinessObject(new CorrelationSet(),
      <CorrelationSet>this.callParam.thisObject);
    delete newCorrelationSet.securitycurrencyList;
    return newCorrelationSet;
  }

  /**
   * Handles dialog close event with cleanup operations.
   * Destroys correlation editing support resources and calls parent cleanup logic.
   * @param event Dialog hide event data
   */
  override onHide(event): void {
    this.correlationEditingSupport.destroy();
    super.onHide(event);
  }
}
