import {Component} from '@angular/core';
import {HelpIds} from '../../shared/help/help.ids';
import {TableCrudSupportMenu} from '../../shared/datashowbase/table.crud.support.menu';
import {GTNetMessageAnswer} from '../model/gtnet.message.answer';


@Component({
  template: `
    Auto Answer Message
  `,
})
export class GTNetMessageAutoAnswerComponent extends TableCrudSupportMenu<GTNetMessageAnswer> {

  public override getHelpContextId(): HelpIds {
    return HelpIds.HELP_GTNET_SETUP;
  }

  override prepareCallParam(entity: GTNetMessageAnswer): void {

  }

  protected override readData(): void {

  }

}
