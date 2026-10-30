import { inject, Service, signal } from '@angular/core';
import { concat, EMPTY, Observable, Subscription } from 'rxjs';
import { catchError, finalize, switchMap, tap } from 'rxjs/operators';
import { DashboardDocument, DashboardResult, DashboardWidget } from '../../../app/lib/dashboard/dashboard.types';
import { GlobalSessionNames } from '../../../app/lib/global.session.names';
import { mergeMobileResults, MobileResults } from '../widget/mobile.dashboard.state';
import { MobileDashboardService } from './mobile.dashboard.service';

/**
 * The dashboard shown by the mobile application, shared by the list and the full-screen view of one widget so that
 * opening a widget needs no second read.
 */
@Service()
export class MobileDashboardStore {
  private readonly dashboardService = inject(MobileDashboardService);

  private readonly widgetsState = signal<DashboardWidget[] | null>(null);
  private readonly resultsState = signal<MobileResults>({});
  private readonly loadingState = signal(false);
  private readonly loadFailedState = signal(false);
  private readonly loadedAtState = signal<string | null>(null);
  private loadSubscription: Subscription | null = null;
  /** Token of the session the shown dashboard was read in. */
  private loadedForToken: string | null = null;

  /** The layout in reading order, null until it has been read. */
  readonly widgets = this.widgetsState.asReadonly();
  readonly results = this.resultsState.asReadonly();
  /** True while the layout or one of its load groups is being read. */
  readonly loading = this.loadingState.asReadonly();
  /** The layout itself could not be read. */
  readonly loadFailed = this.loadFailedState.asReadonly();
  /** When the server generated the layout last read, as ISO timestamp. */
  readonly loadedAt = this.loadedAtState.asReadonly();

  /**
   * Whether the shown dashboard belongs to the current session. After the server ended a session and another user
   * signed in, the dashboard of the previous one must not be shown.
   */
  hasCurrentDashboard(): boolean {
    return this.widgetsState() !== null && this.loadedForToken === sessionStorage.getItem(GlobalSessionNames.JWT);
  }

  /**
   * Reads the layout with the results of its fastest load group, then the slower groups one after another, so the
   * fast cards never wait for the slow ones. A new call cancels a read still running, so a pull-to-refresh never mixes
   * two generations of results.
   *
   * @returns Resolves when every group has been read or the read failed
   */
  reload(): Promise<void> {
    this.loadSubscription?.unsubscribe();
    this.loadedForToken = sessionStorage.getItem(GlobalSessionNames.JWT);
    this.loadingState.set(true);
    this.loadFailedState.set(false);
    return new Promise<void>((resolve) => {
      this.loadSubscription = this.dashboardService
        .load()
        .pipe(
          tap((document) => this.showDocument(document)),
          switchMap((document) => this.readGroups(document.pendingGroups ?? [])),
          catchError(() => {
            this.loadFailedState.set(true);
            return EMPTY;
          }),
          finalize(() => {
            this.loadingState.set(false);
            resolve();
          })
        )
        .subscribe();
    });
  }

  /** Reads one widget again and keeps its earlier figures when the read fails. */
  refreshWidget(instanceId: string): Observable<DashboardResult> {
    return this.dashboardService
      .refresh(instanceId)
      .pipe(tap((result) => this.resultsState.update((results) => mergeMobileResults(results, [result]))));
  }

  /** Forgets the dashboard of a user who signed out. */
  clear(): void {
    this.loadSubscription?.unsubscribe();
    this.widgetsState.set(null);
    this.resultsState.set({});
    this.loadedAtState.set(null);
    this.loadedForToken = null;
  }

  private showDocument(document: DashboardDocument): void {
    this.widgetsState.set(document.widgets);
    this.resultsState.set(mergeMobileResults({}, document.results));
    this.loadedAtState.set(document.generatedAt);
  }

  /** One request per group, in ascending order; a failed group leaves its cards in their loading-failed state. */
  private readGroups(groups: number[]): Observable<DashboardResult[]> {
    return concat(
      ...groups.map((group) =>
        this.dashboardService.group(group).pipe(
          tap((results) => this.resultsState.update((current) => mergeMobileResults(current, results))),
          catchError(() => EMPTY)
        )
      )
    );
  }
}
