import { ChangeDetectionStrategy, Component } from '@angular/core';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Tab, TabList, Tabs } from '@openng/optimus-ui/tabs';
import { BaseTabMenuComponent } from '../../tabmenu/component/base.tab.menu.component';
import { TabItem } from '../../types/tab.item';
import { TranslateHelper } from '../../helper/translate.helper';
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
      @if (tabs.length > 1) {
        <p-tabs [value]="activeRoute">
          <p-tablist>
            @for (tab of tabs; track tab.route) {
              <p-tab [value]="tab.route" (click)="navigateTo(tab.route)">
                <span>{{ tab.label | translate }}</span>
              </p-tab>
            }
          </p-tablist>
        </p-tabs>
      }
      <router-outlet></router-outlet>
    </div>
  `,
  standalone: true,
  changeDetection: ChangeDetectionStrategy.Eager,
  imports: [TranslateModule, RouterModule, Tabs, TabList, Tab]
})
export class TaskDataChangeTabMenuComponent extends BaseTabMenuComponent {
  /** Prefix of the child route of a queue, so the route of a queue cannot be mistaken for another part of the URL */
  static readonly QUEUE_ROUTE_PREFIX = 'queue-';

  constructor(
    router: Router,
    activatedRoute: ActivatedRoute,
    translateService: TranslateService,
    private taskDataChangeService: TaskDataChangeService
  ) {
    super(router, activatedRoute, translateService);
  }

  /** The child route of a queue. */
  static routeOf(queue: string): string {
    return TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX + queue;
  }

  /** The queue of a child route parameter; the main queue when it names none. */
  static queueOf(routeParam: string): string {
    return routeParam?.startsWith(TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX)
      ? routeParam.substring(TaskDataChangeTabMenuComponent.QUEUE_ROUTE_PREFIX.length)
      : MAIN_TASK_QUEUE;
  }

  /** The queues are known only from the server, see {@link navigateToDefault}. */
  protected initializeTabs(): TabItem[] {
    return [];
  }

  protected getDefaultRoute(): string {
    return TaskDataChangeTabMenuComponent.routeOf(MAIN_TASK_QUEUE);
  }

  /** Builds the tabs from the queues of the server, then opens the queue of the URL or the main queue. */
  protected override navigateToDefault(): void {
    this.taskDataChangeService.getFormConstraints().subscribe((constraints: TaskDataChangeFormConstraints) => {
      const queues: TaskQueueInfo[] = constraints.queues?.length
        ? constraints.queues
        : [{ name: MAIN_TASK_QUEUE, parallel: 1, labelKey: 'TASK_DATA_MONITOR' }];
      this.tabs = queues.map((q) => ({
        label: q.labelKey,
        route: TaskDataChangeTabMenuComponent.routeOf(q.name),
        icon: ''
      }));
      TranslateHelper.translateMenuItems(this.tabs, this.translateService);
      const routeFromUrl = this.tabs.find((tab) => this.router.url.split('/').includes(tab.route))?.route;
      this.navigateTo(routeFromUrl ?? this.getDefaultRoute());
    });
  }
}
