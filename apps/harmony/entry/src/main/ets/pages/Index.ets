import { StudentAppShell } from '../view/StudentAppShell';

@Entry
@Component
struct Index {
  build() {
    Column() {
      StudentAppShell()
    }
    .width('100%')
    .height('100%')
  }
}
