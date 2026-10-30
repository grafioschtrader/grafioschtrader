import { Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationEnd, Router, RouterModule } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { filter } from 'rxjs';
import { Tab, TabList, Tabs } from '@openng/optimus-ui/tabs';
import { TabItem } from '../../types/tab.item';
import { TaskDataChangeService } from '../service/task.data.change.service';
import { MAIN_TASK_QUEUE, TaskDataChangeFormConstraints, TaskQueueInfo } from '../types/task.data.change';

/**
 * One tab of the task monitor per queue of background tasks. The main queue comes first and is the default, so the
 * monitor opened from the navigator looks as before: it shows only the tasks of the main queue, of which at most one
 * runs. A queue with workers of its own, such as the language model runs of an application, gets its own tab. With a
 * single queue no tab bar is shown.
 */
@Component({
  template: `
    <div class="card">
      @if (tabs().length > 1) {
        <p-tabs [value]="activeRoute()">
          <p-tablist>
            @for (tab of tabs(); track tab.route) {
              <p-tab [value]="tab.route" (click)="navigateTo(tab.route)">
                <span>{{ tab.label | translate }}</span>
              </p-tab>
            }
          </p-tablist>
        </p-tabs>
      }
      <router-outlet />
    </div>
  `,
  // The routed queue table gets the remaining height, so it scrolls inside its own viewport and its caption and column
  // headers stay visible instead of the whole view scrolling away.
  styles: [
    `
      :host {
        display: block;
        height: 100%;
      }
      .card {
        display: flex;
        flex-direction: column;
        height: 100%;
      }
      .card ::ng-deep > router-outlet + * {
        flex: 1;
        min-height: 0;
      }
    `
  ],
  imports: [TranslateModule, RouterModule, Tabs, TabList, Tab]
})
export class TaskDataChangeTabMenuComponent implements OnInit {
  private router = inject(Router);
  private activatedRoute = inject(ActivatedRoute);
  private taskDataChangeService = inject(TaskDataChangeService);

  /** Prefix of the child route of a queue, so the route of a queue cannot be mistaken for another part of the URL */
  static readonly QUEUE_ROUTE_PREFIX = 'queue-';
  readonly tabs = signal<TabItem[]>([]);
  readonly activeRoute = signal(TaskDataChangeTabMenuComponent.routeOf(MAIN_TASK_QUEUE));
  private readonly destroyRef = inject(DestroyRef);

  /** The child route of a queue. */
  static routeOf(queue: string): string {
    return TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX + queue;
  }

  /** The queue of a child route parameter; the main queue when it names none. */
  static queueOf(routeParam: string | undefined): string {
    return routeParam?.startsWith(TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX)
      ? routeParam.substring(TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX.length)
      : MAIN_TASK_QUEUE;
  }

  /** Builds the tabs from the queues of the server, then opens the queue of the URL or the main queue. */
  ngOnInit(): void {
    this.router.events
      .pipe(
        filter((event) => event instanceof NavigationEnd),
        takeUntilDestroyed(this.destroyRef)
      )
      .subscribe(() =>
        this.activeRoute.set(this.routeFromUrl() ?? TaskDataChangeTabMenuComponent.routeOf(MAIN_TASK_QUEUE))
      );
    this.taskDataChangeService
      .getFormConstraints()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((constraints: TaskDataChangeFormConstraints) => {
        const queues: TaskQueueInfo[] = constraints.queues?.length
          ? constraints.queues
          : [{ name: MAIN_TASK_QUEUE, parallel: 1, labelKey: 'TASK_DATA_MONITOR' }];
        this.tabs.set(
          queues.map((q) => ({
            label: q.labelKey,
            route: TaskDataChangeTabMenuComponent.routeOf(q.name),
            icon: ''
          }))
        );
        const route = this.routeFromUrl();
        if (route) this.activeRoute.set(route);
        else this.navigateTo(TaskDataChangeTabMenuComponent.routeOf(MAIN_TASK_QUEUE));
      });
  }

  navigateTo(route: string): void {
    this.activeRoute.set(route);
    this.router.navigate([route], { relativeTo: this.activatedRoute });
  }

  private routeFromUrl(): string | undefined {
    return this.tabs().find((tab) => this.router.url.split('/').includes(tab.route))?.route;
  }
}
