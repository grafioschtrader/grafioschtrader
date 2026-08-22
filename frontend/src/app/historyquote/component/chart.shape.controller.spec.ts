import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ChartShapeController } from './chart.shape.controller';

/**
 * Stand-in for the plot container. Plotly keeps the drawn shapes in the layout of the element, and a
 * relayout replaces them, which is all the controller ever touches.
 */
function chartElement(shapes: any[] = []): any {
  return { layout: { shapes } };
}

function shape(id: string): any {
  return { type: 'line', name: id };
}

/**
 * Builds a controller wired to spies, with the global Plotly object replaced by a stub whose relayout
 * writes into the element the way the real one does and then reports the event back.
 */
function setup(storedShapes: any[] = [], failLoad = false) {
  const element = chartElement([]);
  const userChartShapeService: any = {
    getShapes: vi.fn(() => (failLoad ? throwError(() => new Error('boom')) : of({ shapeData: storedShapes }))),
    saveShapes: vi.fn(() => of({})),
    deleteShapes: vi.fn(() => of({}))
  };
  const confirmationService: any = { confirm: vi.fn() };
  const translateService: any = { get: vi.fn((keys: string[]) => of(Object.fromEntries(keys.map((k) => [k, k])))) };

  const controller = new ChartShapeController(userChartShapeService, confirmationService, translateService);
  controller.setChartElement(element);
  controller.setSecuritycurrency(42);

  // The real Plotly applies the shapes and emits plotly_relayout, which the chart forwards to onRelayout.
  (globalThis as any).Plotly = {
    Icons: { undo: 'undo-icon' },
    relayout: vi.fn((el: any, update: any) => {
      el.layout.shapes = update.shapes;
      controller.onRelayout();
    })
  };

  return { controller, element, userChartShapeService, confirmationService, translateService };
}

/** Draws a shape the way a user would: Plotly appends it to the layout and fires a relayout. */
function draw(controller: ChartShapeController, element: any, drawn: any): void {
  element.layout.shapes = [...element.layout.shapes, drawn];
  controller.onRelayout();
}

describe('ChartShapeController', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  describe('loadShapes', () => {
    it('yields the stored shapes', async () => {
      const { controller } = setup([shape('a')]);
      await expect(new Promise((resolve) => controller.loadShapes(42).subscribe(resolve))).resolves.toEqual([
        shape('a')
      ]);
    });

    it('yields an empty array when the user has no stored shapes', async () => {
      const { controller, userChartShapeService } = setup();
      userChartShapeService.getShapes.mockReturnValue(of(null));
      await expect(new Promise((resolve) => controller.loadShapes(42).subscribe(resolve))).resolves.toEqual([]);
    });

    it('yields an empty array instead of failing when the request errors', async () => {
      const { controller } = setup([], true);
      await expect(new Promise((resolve) => controller.loadShapes(42).subscribe(resolve))).resolves.toEqual([]);
    });
  });

  describe('history', () => {
    it('reports no shapes before the history was initialized', () => {
      const { controller } = setup();
      expect(controller.getCurrentShapes()).toEqual([]);
    });

    it('starts the history from the shapes the chart was loaded with', () => {
      const { controller } = setup();
      controller.initHistory([shape('a')]);
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('snapshots the history, so a later change does not alter the recorded entry', () => {
      const { controller } = setup();
      const loaded = [shape('a')];
      controller.initHistory(loaded);
      loaded.push(shape('b'));
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('records a drawn shape', () => {
      const { controller, element } = setup();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('steps back and forward through the history', () => {
      const { controller, element } = setup();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      draw(controller, element, shape('b'));

      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([]);
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'redo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('stops at the oldest and at the newest entry', () => {
      const { controller, element } = setup();
      controller.initHistory([]);
      draw(controller, element, shape('a'));

      const undo = controller.getModeBarButtons().find((b) => b.name === 'undo');
      undo.click();
      undo.click();
      expect(controller.getCurrentShapes()).toEqual([]);

      const redo = controller.getModeBarButtons().find((b) => b.name === 'redo');
      redo.click();
      redo.click();
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('does not record the relayout that undo itself causes', () => {
      const { controller, element } = setup();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      draw(controller, element, shape('b'));

      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();
      // Were the undo recorded as a new change, redo would have nothing left to step forward to.
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'redo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([shape('a'), shape('b')]);
    });

    it('discards the redone entries once a new shape is drawn after an undo', () => {
      const { controller, element } = setup();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      draw(controller, element, shape('b'));
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();

      draw(controller, element, shape('c'));
      expect(controller.getCurrentShapes()).toEqual([shape('a'), shape('c')]);
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'redo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([shape('a'), shape('c')]);
    });
  });

  describe('persistence', () => {
    it('saves the shapes once the changes settle', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));

      expect(userChartShapeService.saveShapes).not.toHaveBeenCalled();
      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).toHaveBeenCalledWith(42, [shape('a')]);
    });

    it('writes only once for a burst of changes', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      draw(controller, element, shape('b'));
      draw(controller, element, shape('c'));

      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).toHaveBeenCalledTimes(1);
      expect(userChartShapeService.saveShapes).toHaveBeenCalledWith(42, [shape('a'), shape('b'), shape('c')]);
    });

    it('deletes the stored entry instead of saving an empty array', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([shape('a')]);
      element.layout.shapes = [];
      controller.onRelayout();

      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).not.toHaveBeenCalled();
      expect(userChartShapeService.deleteShapes).toHaveBeenCalledWith(42);
    });

    it('persists an undo, because the earlier state has to become the stored one', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      draw(controller, element, shape('b'));
      vi.advanceTimersByTime(500);
      userChartShapeService.saveShapes.mockClear();

      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();
      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).toHaveBeenCalledWith(42, [shape('a')]);
    });

    it('ignores changes while several instruments are charted', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([]);
      controller.setSecuritycurrency(null);
      draw(controller, element, shape('a'));

      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).not.toHaveBeenCalled();
      expect(userChartShapeService.deleteShapes).not.toHaveBeenCalled();
      expect(controller.getCurrentShapes()).toEqual([]);
    });

    it('stops saving after destroy', () => {
      const { controller, element, userChartShapeService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));
      controller.destroy();

      vi.advanceTimersByTime(500);
      expect(userChartShapeService.saveShapes).not.toHaveBeenCalled();
    });
  });

  describe('delete all shapes', () => {
    it('asks for confirmation and removes every shape when accepted', () => {
      const { controller, element, confirmationService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));

      controller
        .getModeBarButtons()
        .find((b) => b.name === 'deleteAllShapes')
        .click();
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      confirmationService.confirm.mock.calls[0][0].accept();
      expect(element.layout.shapes).toEqual([]);
      // The removal goes through a normal relayout, so it can be undone again.
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'undo')
        .click();
      expect(controller.getCurrentShapes()).toEqual([shape('a')]);
    });

    it('keeps the shapes when the confirmation is not accepted', () => {
      const { controller, element, confirmationService } = setup();
      controller.init();
      controller.initHistory([]);
      draw(controller, element, shape('a'));

      controller
        .getModeBarButtons()
        .find((b) => b.name === 'deleteAllShapes')
        .click();
      expect(confirmationService.confirm).toHaveBeenCalledTimes(1);
      expect(element.layout.shapes).toEqual([shape('a')]);
    });

    it('does not ask when there is nothing to delete', () => {
      const { controller, confirmationService } = setup();
      controller.initHistory([]);
      controller
        .getModeBarButtons()
        .find((b) => b.name === 'deleteAllShapes')
        .click();
      expect(confirmationService.confirm).not.toHaveBeenCalled();
    });
  });
});
