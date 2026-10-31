import { Service } from '@angular/core';
import { Subject } from 'rxjs';

/**
 * Service that informs about the possible resizing of a view.
 */
@Service()
export class ViewSizeChangedService {
  private splitTreeMain?: import('split.js').Instance;
  /** Sizes restored by the collapse entry; the initial split also covers a tree collapsed by dragging the gutter */
  private lastSplitSize: number[] = [20, 80];

  private viewSizeChanged = new Subject<ChangedViewSizeType>();
  viewSizeChanged$ = this.viewSizeChanged.asObservable();

  viewPanelChanged(changedViewSizeType: ChangedViewSizeType) {
    this.viewSizeChanged.next(changedViewSizeType);
  }

  setMainTreeSplit(splitTreeMain: import('split.js').Instance): void {
    this.splitTreeMain = splitTreeMain;
  }

  toggleMainTree(): void {
    const split = this.splitTreeMain;
    if (!split) return;
    if (this.isMainTreeVisible()) {
      this.lastSplitSize = split.getSizes();
      split.collapse(0);
    } else {
      split.setSizes(this.lastSplitSize);
    }
    this.viewPanelChanged(ChangedViewSizeType.ALL_VIEWS);
  }

  isMainTreeVisible(): boolean {
    return (this.splitTreeMain?.getSizes()[0] ?? 0) > 1;
  }
}

export enum ChangedViewSizeType {
  /**
   * Size of the Browser Window has changed
   */
  WINDOW,
  /**
   * The size of all three content panel has changed
   */
  ALL_VIEWS,
  /**
   * The horizontal gutter has been moved.
   */
  DATA_VIEWS
}
