import { signal, Component, OnInit, viewChild, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { YamlEditorComponent } from './yaml-editor.component';
import { TranslateModule } from '@ngx-translate/core';
import moment from 'moment';
import { finalize } from 'rxjs/operators';
import { DYNAMIC_FORM_IMPORTS } from '../../lib/dynamic-form/dynamic-form.imports';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { SimpleDynamicEditBase } from '../../lib/edit/simple.dynamic.edit.base';
import { AppHelper } from '../../lib/helper/app.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { HelpIds } from '../../lib/help/help.ids';
import { DataChangedService } from '../../lib/maintree/service/data.changed.service';
import { ProcessedAction } from '../../lib/types/processed.action';
import { ProcessedActionData } from '../../lib/types/processed.action.data';
import { CallParam } from '../../shared/maintree/types/dialog.visible';
import { SimulationRunResult } from '../model/simulation.run';
import { SimulationTenantInfo } from '../model/simulation.tenant';
import { AlgoSimulationRunService } from '../service/algo-simulation-run.service';

/**
 * Dialog that collects the inputs of a historical replay and starts it.
 *
 * It is opened from the context menu of a simulation environment in the main tree and from the edit menu of the replay
 * panel. Only the end date and the two optional estimates are asked for: the replay begins at the immutable opening
 * date of the environment, so that a recorded result keeps saying what it was calculated from. The dialog closes as
 * soon as the server has accepted the run; watching its progress and reading its figures is the job of the replay
 * panel, which is told about the start through the data changed service regardless of where the dialog was opened.
 *
 * The form definition of the request is fetched by the opener and passed as `callParam.parentObject.formDefinition`,
 * because the form must be built synchronously when the dialog renders.
 */
@Component({
  template: `
    <p>{{ 'SIMULATION_TAX_COUPON_LIMITATIONS' | translate }}</p>
    <p>{{ 'CUSTODY_OPENING_HELP' | translate }}</p>
    <yaml-editor
      #yamlEditor
      format="CUSTODY"
      [(value)]="custodyYaml"
      [schema]="custodySchema()"
      height="250px"
      (syntaxValidChange)="configObject.submit.disabled = !$event || yamlEditor.validating"
      (validationPendingChange)="configObject.submit.disabled = $event || !yamlEditor.syntaxValid" />
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
  `,
  styles: [':host { display: block; max-height: calc(100vh - 140px); overflow-y: auto; }'],
  imports: [...DYNAMIC_FORM_IMPORTS, TranslateModule, YamlEditorComponent]
})
export class AlgoSimulationRunStartDynamicDialogComponent
  extends SimpleDynamicEditBase<SimulationRunResult>
  implements OnInit
{
  private http: HttpClient = inject(HttpClient);
  private runService: AlgoSimulationRunService = inject(AlgoSimulationRunService);
  private dataChangedService: DataChangedService = inject(DataChangedService);

  /** Read by MainTreeDynamicDialogs; the limitation text above the form needs more than the default 400px. */
  static readonly DIALOG_WIDTH = 800;
  readonly yamlEditor = viewChild(YamlEditorComponent);
  readonly custodyYaml = signal('');
  readonly custodySchema = signal<object>(undefined);

  private simulation: SimulationTenantInfo;

  constructor() {
    // No entity service: submit is overridden, because a run is started rather than saved.
    super(HelpIds.HELP_ALGO_HISTORICAL_RUN, null);
  }

  ngOnInit(): void {
    const callParam: CallParam = this.dynamicDialogConfig.data.callParam;
    this.simulation = callParam.thisObject as any as SimulationTenantInfo;
    // The locale of the form makes the calendar read in the user's date format.
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
      this.translateService,
      (callParam.parentObject as any).formDefinition,
      '',
      false
    ) as FieldConfig[];
    this.config = this.config.filter((field) => field.field !== 'custodyOpeningYaml');
    this.http
      .get('assets/schemas/custody-opening-schema.json')
      .pipe(this.untilDialogClosed())
      .subscribe((schema) => this.custodySchema.set(schema));
    this.config.push(DynamicFieldHelper.createSubmitButton('SIMULATION_RUN_START'));
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
    this.limitEndDate();
  }

  /**
   * The calendar of the form holds a native Date; the endpoint expects the date alone. The conversion is left to the
   * form itself, so that the day sent is the day picked - serializing the Date would send an instant in UTC, which is
   * the previous day for every user east of Greenwich.
   */
  override async submit(_value: { [name: string]: any }): Promise<void> {
    if (!(await this.yamlEditor().validateForSubmit())) {
      this.configObject.submit.disabled = !this.yamlEditor().syntaxValid;
      return;
    }
    if (this.destroyRef.destroyed) return;
    const request = {
      endDate: null as string,
      applyTaxModels: false,
      generateBondCoupons: false,
      custodyOpeningYaml: null as string
    };
    this.form().cleanMaskAndTransferValuesToBusinessObject(request);
    request.custodyOpeningYaml = this.custodyYaml();
    this.configObject.submit.disabled = true;
    this.runService
      .start(
        this.simulation.idTenant,
        request.endDate,
        request.applyTaxModels,
        request.generateBondCoupons,
        request.custodyOpeningYaml
      )
      .pipe(finalize(() => (this.configObject.submit.disabled = false)))
      .pipe(this.untilDialogClosed())
      .subscribe((run) => {
        const processedActionData = new ProcessedActionData(ProcessedAction.CREATED, run);
        this.dataChangedService.dataHasChanged(processedActionData);
        this.dynamicDialogRef.close(processedActionData);
      });
  }

  protected getNewOrExistingInstanceBeforeSave(_value: { [name: string]: any }): SimulationRunResult {
    return undefined;
  }

  /**
   * An unfinished day has no closing price to decide against, and the opening date itself is not a day a decision may
   * be taken on. The server enforces both; bounding the calendar only spares the user a refused request.
   */
  private limitEndDate(): void {
    const endDate = this.configObject.endDate;
    if (!endDate) {
      return;
    }
    const yesterday = new Date();
    yesterday.setDate(yesterday.getDate() - 1);
    const calendarConfig = { ...endDate.calendarConfig, maxDate: yesterday };
    if (this.simulation.simulationStartDate) {
      calendarConfig.minDate = moment(this.simulation.simulationStartDate).add(1, 'day').toDate();
    }
    endDate.calendarConfig = calendarConfig;
  }
}
