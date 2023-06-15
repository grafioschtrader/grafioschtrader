import {Pipe, PipeTransform} from '@angular/core';

@Pipe({
  name: 'filterOut'
})
export class FilterOutPipe implements PipeTransform {
  constructor() {
  }

  transform(inputStr: string, filterOutValue: string): any {
    return inputStr === filterOutValue ? null : inputStr;
  }
}
