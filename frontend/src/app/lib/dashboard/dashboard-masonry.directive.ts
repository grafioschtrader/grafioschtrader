import { AfterViewInit, Directive, ElementRef, Input, NgZone, OnChanges, OnDestroy } from '@angular/core';

/**
 * Packs the dashboard cards column by column instead of row by row.
 *
 * <p>
 * A grid row is as tall as its tallest card, so a short card is followed by the empty remainder of that row rather than
 * by the next card. The directive turns the implicit rows into a fine raster and gives every card the number of rows
 * its own height needs, which lets the automatic placement start the next card directly below its predecessor in the
 * same column. The heights are measured rather than declared, because a card grows with the data it received and with
 * the branches the reader opens; a card that changes height therefore moves the cards below it at once.
 * </p>
 *
 * <p>
 * The vertical gap is part of the span instead of a row gap, because a gap would be inserted between every row of the
 * raster and not only between two cards.
 * </p>
 */
@Directive({
  selector: '[dashboardMasonry]',
  standalone: true,
  host: { '[class.dashboard-masonry]': 'dashboardMasonry' }
})
export class DashboardMasonryDirective implements AfterViewInit, OnChanges, OnDestroy {
  /** Height of one raster row in pixels; a card spans as many of them as it is tall. */
  private static readonly ROW_HEIGHT = 1;

  /**
   * Off while the layout is edited: the draft cards are uniform, and the plain rows are what the drag and drop of the
   * edit mode measures its drop positions against.
   */
  @Input() dashboardMasonry = true;

  private readonly grid: HTMLElement;
  private sizes: ResizeObserver;
  private cards: MutationObserver;
  private frame = 0;

  constructor(
    host: ElementRef<HTMLElement>,
    private zone: NgZone
  ) {
    this.grid = host.nativeElement;
  }

  ngAfterViewInit(): void {
    // Outside Angular because a card whose table has just been rendered would otherwise start a change detection run
    // for a change that is nothing but its own measurement.
    this.zone.runOutsideAngular(() => {
      this.sizes = new ResizeObserver(() => this.schedule());
      this.cards = new MutationObserver(() => this.observeCards());
      this.cards.observe(this.grid, { childList: true });
      this.observeCards();
    });
  }

  ngOnChanges(): void {
    this.schedule();
  }

  ngOnDestroy(): void {
    this.sizes?.disconnect();
    this.cards?.disconnect();
    cancelAnimationFrame(this.frame);
  }

  /** Re-subscribes to the cards after the grid gained or lost one; the observer holds no stale elements. */
  private observeCards(): void {
    this.sizes.disconnect();
    for (const card of this.cardElements()) {
      this.sizes.observe(card);
    }
    this.schedule();
  }

  private cardElements(): HTMLElement[] {
    return Array.from(this.grid.children).filter(
      (child): child is HTMLElement => child instanceof HTMLElement && child.classList.contains('dashboard-card')
    );
  }

  /** One pass per frame, so a card reporting several size changes of one render is laid out once. */
  private schedule(): void {
    cancelAnimationFrame(this.frame);
    this.frame = requestAnimationFrame(() => this.apply());
  }

  private apply(): void {
    const cards = this.cardElements();
    if (!this.dashboardMasonry) {
      cards.forEach((card) => card.style.removeProperty('grid-row-end'));
      return;
    }
    // The column gap is the intended distance between two cards; the row gap is zero because the raster needs it to be.
    const gap = parseFloat(getComputedStyle(this.grid).columnGap) || 0;
    for (const card of cards) {
      const rows = Math.ceil((card.getBoundingClientRect().height + gap) / DashboardMasonryDirective.ROW_HEIGHT);
      const span = `span ${Math.max(1, rows)}`;
      if (card.style.gridRowEnd !== span) {
        card.style.gridRowEnd = span;
      }
    }
  }
}
