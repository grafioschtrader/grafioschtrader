import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ButtonModule } from '@openng/optimus-ui/button';
import { ChangeDetectionStrategy, Component, Input, OnInit, DestroyRef, inject } from '@angular/core';
import { SimpleEntityEditBase } from '../../lib/edit/simple.entity.edit.base';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { GlobalparameterService } from '../../lib/services/globalparameter.service';
import { MessageToastService } from '../../lib/message/message.toast.service';
import { HelpIds } from '../../lib/help/help.ids';
import { AppHelper } from '../../lib/helper/app.helper';
import { TranslateHelper } from '../../lib/helper/translate.helper';
import { DynamicFieldModelHelper } from '../../lib/helper/dynamic.field.model.helper';
import { DynamicFieldHelper } from '../../lib/helper/dynamic.field.helper';
import { FieldConfig } from '../../lib/dynamic-form/models/field.config';
import { DataType } from '../../lib/dynamic-form/models/data.type';
import { DialogModule } from '@openng/optimus-ui/dialog';
import { DialogService } from '@openng/optimus-ui/dynamicdialog';
import { DynamicFormComponent } from '../../lib/dynamic-form/containers/dynamic-form/dynamic-form.component';
import { SecuritycurrencySearchAndSetDialogComponent } from '../../securitycurrency/component/securitycurrency-search-and-set-dialog.component';
import { SupplementCriteria } from '../../securitycurrency/model/supplement.criteria';
import { WatchlistIndicatorColumn } from '../model/watchlist-indicator';
import { WatchlistIndicatorService } from '../service/watchlist-indicator.service';
import { debounceTime, distinctUntilChanged, switchMap, catchError, of } from 'rxjs';

/** Entity-derived expression form; benchmark selection uses the normal instrument search. */
@Component({
  selector: 'watchlist-indicator-column-edit',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [ButtonModule, TranslateModule, DialogModule, DynamicFormComponent],
  template: `<p-dialog
    [header]="'WATCHLIST_INDICATOR_COLUMN' | translate"
    [visible]="visibleDialog"
    [modal]="true"
    [style]="{ width: '620px', maxHeight: '90vh' }"
    appendTo="body"
    (onShow)="onShow($event)"
    (onHide)="onHide($event)">
    <dynamic-form
      [config]="config"
      [formConfig]="formConfig"
      [translateService]="translateService"
      #form="dynamicForm"
      (submitBt)="submit($event)" />
    <p-button [label]="'WIC_CLEAR_BENCHMARK' | translate" (click)="clearBenchmark()" />
  </p-dialog>`
})
export class WatchlistIndicatorColumnEditComponent
  extends SimpleEntityEditBase<WatchlistIndicatorColumn>
  implements OnInit
{
  @Input() callParam: any;
  private benchmark: number;
  private readonly destroyRef = inject(DestroyRef);
  constructor(
    translate: TranslateService,
    gps: GlobalparameterService,
    messages: MessageToastService,
    private indicators: WatchlistIndicatorService,
    private dialogs: DialogService
  ) {
    super(HelpIds.HELP_WATCHLIST_INDICATOR, 'WATCHLIST_INDICATOR_COLUMN', translate, gps, messages, indicators);
  }
  ngOnInit(): void {
    this.formConfig = AppHelper.getDefaultFormConfig(this.gps, 5, this.helpLink.bind(this));
    this.config = <FieldConfig[]>(
      DynamicFieldModelHelper.createFieldsFromClassDescriptorInputAndShow(
        this.translateService,
        this.callParam.optParam.formDefinition,
        '',
        true
      )
    );
    // Persistence uses a byte, while the entity getter and REST contract use the enum name.
    this.config.find((f) => f.field === 'markColors').dataType = DataType.String;
    const index = this.config.findIndex((f) => f.field === 'idBenchmark');
    this.config.splice(
      index,
      1,
      DynamicFieldHelper.createFieldInputButton(
        DataType.String,
        'benchmarkName',
        'ID_BENCHMARK',
        () => this.searchBenchmark(),
        false
      )
    );
    this.configObject = TranslateHelper.prepareFieldsAndErrors(this.translateService, this.config);
  }
  protected override initialize(): void {
    this.configObject.idWatchlist.valueKeyHtmlOptions = [
      { key: null, value: this.translateService.instant('WIC_ALL_WATCHLISTS') },
      ...this.callParam.optParam.watchlists.map((w) => ({ key: w.idWatchlist, value: w.name }))
    ];
    this.configObject.markColors.valueKeyHtmlOptions = this.callParam.optParam.colors.map((c) => ({
      key: c.key,
      value: this.translateService.instant(c.value)
    }));
    this.configObject.expression.formControl.valueChanges
      .pipe(
        debounceTime(350),
        distinctUntilChanged(),
        switchMap((expression) =>
          expression?.trim() ? this.indicators.resultType(expression).pipe(catchError(() => of(null))) : of(null)
        ),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe((result) => {
        if (result) this.showNumeric(result.resultType === 'NUMBER');
      });
    const row = this.callParam.thisObject ?? new WatchlistIndicatorColumn();
    this.benchmark = row.idBenchmark;
    this.form.setDefaultValuesAndEnableSubmit();
    this.form.transferBusinessObjectToForm(row);
    if (this.benchmark)
      this.configObject.benchmarkName.formControl.setValue(
        this.callParam.optParam.benchmarks[this.benchmark] ?? this.translateService.instant('WIC_BENCHMARK_MISSING')
      );
    this.showNumeric(row.resultType !== 'BOOLEAN');
  }
  clearBenchmark(): void {
    this.benchmark = null;
    this.configObject.benchmarkName.formControl.setValue(null);
    this.configObject.benchmarkName.formControl.markAsDirty();
  }
  private showNumeric(numeric: boolean): void {
    ['decimals', 'lowerMark', 'upperMark', 'markColors'].forEach(
      (name) => (this.configObject[name].invisible = !numeric)
    );
  }
  private searchBenchmark(): void {
    const ref = this.dialogs.open(SecuritycurrencySearchAndSetDialogComponent, {
      header: this.translateService.instant('ID_BENCHMARK'),
      width: '720px',
      modal: true,
      closable: true,
      closeOnEscape: true,
      data: { supplementCriteria: new SupplementCriteria(false, false) }
    });
    ref.onClose.subscribe((instrument) => {
      if (instrument) {
        this.benchmark = instrument.idSecuritycurrency;
        this.configObject.benchmarkName.formControl.setValue(
          instrument.name ?? instrument.fromCurrency + '/' + instrument.toCurrency
        );
        this.configObject.benchmarkName.formControl.markAsDirty();
      }
    });
  }
  protected override getNewOrExistingInstanceBeforeSave(value: { [name: string]: any }): WatchlistIndicatorColumn {
    const row = this.copyFormToPrivateBusinessObject(new WatchlistIndicatorColumn(), this.callParam.thisObject);
    row.idBenchmark = this.benchmark;
    if (this.configObject.markColors.invisible) {
      row.lowerMark = null;
      row.upperMark = null;
      row.markColors = 'LOW_NEGATIVE_HIGH_POSITIVE';
      row.decimals = 0;
    }
    return row;
  }
}
