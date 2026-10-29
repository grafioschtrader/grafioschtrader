import { Component, ElementRef, OnInit, viewChild, inject } from '@angular/core';
import { ChangedViewSizeType, ViewSizeChangedService } from '../service/view.size.changed.service';
import { RouterModule } from '@angular/router';

import { MenubarComponent } from '../../mainmenubar/component/menubar.component';
import { MainTreeComponent } from '../../maintree/component/main-tree.component';
import { MainDialogComponent } from '../../mainmenubar/component/main-dialog.component';

declare const Split: typeof import('split.js');

/**
 * This component defines the global split layout of GT. There is a division into navigation, main and additional area.
 * The size of these areas can be adjusted.
 */
@Component({
  selector: 'split-layout',
  template: `
    <menubar />
    <div class="fullheight" #split (window:resize)="onResize($event)">
      <div id="tree" class="split split-horizontal">
        <main-tree />
      </div>
      <div id="mainpanel" class="split split-horizontal">
        <div id="maintop" class="split split-vetrical">
          <router-outlet />
        </div>
        <div id="mainbottom" class="split split-vetrical">
          <router-outlet name="mainbottom" />
        </div>
      </div>
    </div>

    <main-dialog />
  `,
  imports: [RouterModule, MenubarComponent, MainTreeComponent, MainDialogComponent]
})
export class SplitLayoutComponent implements OnInit {
  private viewSizeChangedService = inject(ViewSizeChangedService);

  readonly splitElementRef = viewChild<ElementRef>('split');

  ngOnInit() {
    const splitTreeMain = Split(['#tree', '#mainpanel'], {
      sizes: [20, 80],
      minSize: 0,
      gutterSize: 8,
      direction: 'horizontal',
      onDragEnd: () => this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.ALL_VIEWS)
    });

    const splitTopBottom = Split(['#maintop', '#mainbottom'], {
      sizes: [80, 20],
      minSize: 100,
      gutterSize: 8,
      direction: 'vertical',
      onDragEnd: () => this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.DATA_VIEWS)
    });
    this.viewSizeChangedService.setMainTreeSplit(splitTreeMain);
  }

  onResize(event: unknown): void {
    this.viewSizeChangedService.viewPanelChanged(ChangedViewSizeType.WINDOW);
  }
}
