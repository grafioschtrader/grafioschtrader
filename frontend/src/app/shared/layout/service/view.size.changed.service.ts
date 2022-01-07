import {Injectable} from '@angular/core';
import {Subject} from 'rxjs';

/**
 * Service that informs about the possible resizing of a view.
 */
@Injectable()
export class ViewSizeChangedService {

  private splitTreeMain;
  private lastSplitSize: number[];

  private viewSizeChanged = new Subject<ChangedViewSizeType>();
  viewSizeChanged$ = this.viewSizeChanged.asObservable();

  viewPanelChanged(changedViewSizeType: ChangedViewSizeType) {
    this.viewSizeChanged.next(changedViewSizeType);
  }

  setMainTreeSplit(splitTreeMain): void {
    this.splitTreeMain = splitTreeMain;
  }

  toggleMainTree(): void {
    if (this.isMainTreeVisible()) {
      this.lastSplitSize = this.splitTreeMain.getSizes();
      this.splitTreeMain.collapse(0);
    } else {
      this.splitTreeMain.setSizes(this.lastSplitSize);
    }
    this.viewPanelChanged(ChangedViewSizeType.ALL_VIEWS);
  }

  isMainTreeVisible(): boolean {
    return this.splitTreeMain.getSizes()[0] > 1;
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
