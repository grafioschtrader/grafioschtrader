import { Observable, of, Subject, Subscription } from 'rxjs';
import { catchError, debounceTime, map } from 'rxjs/operators';
import { TranslateService } from '@ngx-translate/core';
import { ConfirmationService } from '@openng/optimus-ui/api';
import { UserChartShapeService } from '../service/user.chart.shape.service';

declare let Plotly: any;

/**
 * Owns the freehand drawings a user places on a time series chart: lines, paths, circles and rectangles
 * are Plotly shapes that live in the chart layout and are stored per security or currency pair.
 *
 * Three concerns are bundled here because they are inseparable in practice:
 *
 * <ul>
 * <li><b>Persistence</b> - every change is written back through {@link UserChartShapeService}, debounced
 *     so that dragging a shape does not produce a request per mouse move.</li>
 * <li><b>Undo and redo</b> - a history of shape snapshots with a cursor. Applying a snapshot triggers a
 *     relayout event of its own, which is why the next event has to be recognized as self inflicted and
 *     must not push another history entry.</li>
 * <li><b>Mode bar buttons</b> - undo, redo and delete all, which Plotly only accepts as part of the
 *     chart configuration.</li>
 * </ul>
 *
 * Shapes belong to exactly one instrument, so they are disabled while the chart compares several
 * instruments. The controller is then told a null id and quietly ignores every change.
 */
export class ChartShapeController {
  /** Snapshots of the shape array, oldest first. Always holds at least the state the chart was loaded with. */
  private shapeHistory: any[][] = [];
  private shapeHistoryIndex = -1;
  /** True while a relayout caused by undo or redo is still expected, so it is not recorded as a user change. */
  private isProgrammaticRelayout = false;
  private shapeSave$ = new Subject<{ idSecuritycurrency: number; shapes: any[] }>();
  private shapeSaveSubscription: Subscription;
  private chartNativeElement: any;
  /** Instrument the shapes belong to, null while more than one instrument is charted. */
  private idSecuritycurrency: number = null;

  /**
   * @param userChartShapeService REST access to the stored shapes of the logged in user
   * @param confirmationService used to confirm before all shapes of an instrument are discarded
   * @param translateService supplies the texts of that confirmation dialog
   */
  constructor(
    private userChartShapeService: UserChartShapeService,
    private confirmationService: ConfirmationService,
    private translateService: TranslateService
  ) {}

  /**
   * Starts the debounced save pipeline. Must be called once, from the ngOnInit of the chart component.
   */
  public init(): void {
    this.shapeSaveSubscription = this.shapeSave$.pipe(debounceTime(500)).subscribe((data) => {
      if (data.shapes.length > 0) {
        this.userChartShapeService.saveShapes(data.idSecuritycurrency, data.shapes).subscribe();
      } else {
        this.userChartShapeService.deleteShapes(data.idSecuritycurrency).subscribe();
      }
    });
  }

  /**
   * Releases the save subscription. Must be called from the ngOnDestroy of the chart component.
   */
  public destroy(): void {
    this.shapeSaveSubscription && this.shapeSaveSubscription.unsubscribe();
  }

  /**
   * Hands over the plot container. Only available once the view exists, so it is called from ngOnInit
   * rather than from the constructor.
   *
   * @param chartNativeElement native element Plotly draws into
   */
  public setChartElement(chartNativeElement: any): void {
    this.chartNativeElement = chartNativeElement;
  }

  /**
   * Selects the instrument whose shapes are edited and persisted. Passing null disables drawing
   * persistence, which is what the chart does while it compares several instruments.
   *
   * @param idSecuritycurrency id of the single charted security or currency pair, or null
   */
  public setSecuritycurrency(idSecuritycurrency: number): void {
    this.idSecuritycurrency = idSecuritycurrency;
  }

  /**
   * Loads the stored shapes of an instrument. A user without stored shapes and a failing request are
   * both reported as an empty array, so the caller can plot unconditionally.
   *
   * @param idSecuritycurrency id of the security or currency pair
   * @returns observable emitting exactly one shape array, never an error
   */
  public loadShapes(idSecuritycurrency: number): Observable<any[]> {
    return this.userChartShapeService.getShapes(idSecuritycurrency).pipe(
      map((response) => response?.shapeData || []),
      catchError(() => of([]))
    );
  }

  /**
   * Discards the undo history and starts a new one with the given shapes as its only entry.
   *
   * @param shapes shapes the chart is about to be plotted with
   */
  public initHistory(shapes: any[]): void {
    this.shapeHistory = [JSON.parse(JSON.stringify(shapes || []))];
    this.shapeHistoryIndex = 0;
  }

  /**
   * The shapes at the current history position, to be passed into the layout of the next plot. This keeps
   * drawings on screen when the chart is replotted for an unrelated reason, such as a changed date range.
   *
   * @returns the current shape snapshot, or an empty array before the history was initialized
   */
  public getCurrentShapes(): any[] {
    return this.shapeHistory.length > 0 && this.shapeHistoryIndex >= 0 ? this.shapeHistory[this.shapeHistoryIndex] : [];
  }

  /**
   * Handles a Plotly relayout event: records the new shape state in the history and schedules a save.
   * A relayout that this controller itself caused through undo or redo is saved but not recorded again,
   * otherwise stepping back through the history would immediately append the state stepped back to.
   */
  public onRelayout(): void {
    if (this.idSecuritycurrency == null) {
      return;
    }
    const shapes = this.chartNativeElement?.layout?.shapes;
    if (shapes === undefined) {
      return;
    }
    if (this.isProgrammaticRelayout) {
      this.isProgrammaticRelayout = false;
    } else {
      this.shapeHistory = this.shapeHistory.slice(0, this.shapeHistoryIndex + 1);
      this.shapeHistory.push(JSON.parse(JSON.stringify(shapes)));
      this.shapeHistoryIndex = this.shapeHistory.length - 1;
    }
    this.persistShapes(shapes);
  }

  /**
   * The undo, redo and delete all buttons the chart adds to the Plotly mode bar. Plotly only accepts
   * these as part of the plot configuration, which is why they are built here instead of bound in a
   * template.
   *
   * @returns mode bar button definitions, ready to be appended to modeBarButtonsToAdd
   */
  public getModeBarButtons(): any[] {
    return [
      {
        name: 'undo',
        title: 'Undo',
        icon: Plotly.Icons.undo,
        click: () => this.undoShapes()
      },
      {
        name: 'redo',
        title: 'Redo',
        icon: {
          width: 857.1,
          height: 1000,
          path:
            'm857 350q0-87-34-166t-91-137-137-92-166-34q-96 0-183 41t-147 114q-4 6-4 13t5 11l76 77q6 5 14 5 9-1 13-7 ' +
            '41-53 100-82t126-29q58 0 110 23t92 61 61 91 22 111-22 111-61 91-92 61-110 23q-55 0-105-20t-90-57l77-77q17-16 ' +
            '8-38-10-23-33-23h-250q-15 0-25 11t-11 25v250q0 24 22 33 22 10 39-8l72-72q60 57 137 88t159 31q87 0 166-34t137-91 ' +
            '91-137 34-166z',
          transform: 'matrix(-1 0 0 1 857 0)'
        },
        click: () => this.redoShapes()
      },
      {
        name: 'deleteAllShapes',
        title: 'Delete all shapes',
        icon: {
          width: 448,
          height: 512,
          path:
            'M432 32H312l-9.4-18.7A24 24 0 0 0 281.1 0H166.8a23.72 23.72 0 0 0-21.4 13.3L136 32H16A16 16 0 0 0 0 ' +
            '48v32a16 16 0 0 0 16 16h416a16 16 0 0 0 16-16V48a16 16 0 0 0-16-16zM53.2 467a48 48 0 0 0 47.9 45h245.8a48 48 ' +
            '0 0 0 47.9-45L416 128H32z'
        },
        click: () => this.deleteAllShapes()
      }
    ];
  }

  /**
   * Schedules the shapes for the debounced write back. Nothing is stored while no single instrument is
   * selected.
   *
   * @param shapes shapes to persist, an empty array deletes the stored entry
   */
  private persistShapes(shapes: any[]): void {
    if (this.idSecuritycurrency != null) {
      this.shapeSave$.next({ idSecuritycurrency: this.idSecuritycurrency, shapes: shapes || [] });
    }
  }

  /**
   * Steps one entry back in the shape history, doing nothing at the oldest entry.
   */
  private undoShapes(): void {
    if (this.shapeHistoryIndex > 0) {
      this.shapeHistoryIndex--;
      this.applyShapesFromHistory();
    }
  }

  /**
   * Steps one entry forward in the shape history, doing nothing at the newest entry.
   */
  private redoShapes(): void {
    if (this.shapeHistoryIndex < this.shapeHistory.length - 1) {
      this.shapeHistoryIndex++;
      this.applyShapesFromHistory();
    }
  }

  /**
   * Draws the snapshot at the current history position. The relayout this causes is flagged in advance so
   * that the resulting event is not recorded as a new user change.
   */
  private applyShapesFromHistory(): void {
    this.isProgrammaticRelayout = true;
    const shapes = JSON.parse(JSON.stringify(this.shapeHistory[this.shapeHistoryIndex]));
    Plotly.relayout(this.chartNativeElement, { shapes });
  }

  /**
   * Removes every shape after the user confirmed. The removal goes through a normal relayout, so it is
   * recorded in the history and can be undone.
   */
  private deleteAllShapes(): void {
    const currentShapes = this.chartNativeElement?.layout?.shapes || [];
    if (currentShapes.length === 0) {
      return;
    }
    this.translateService.get(['DELETE_ALL_SHAPES', 'DELETE_ALL_SHAPES_CONFIRM']).subscribe((translations) => {
      this.confirmationService.confirm({
        header: translations['DELETE_ALL_SHAPES'],
        message: translations['DELETE_ALL_SHAPES_CONFIRM'],
        accept: () => {
          Plotly.relayout(this.chartNativeElement, { shapes: [] });
        }
      });
    });
  }
}
