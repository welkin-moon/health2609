import sensor from '@ohos.sensor';
import hilog from '@ohos.hilog';
import { SchoolDayWindowDto, OutsideSchoolActivityRequest } from '../model/ApiModels';
import { HealthApi } from './HealthApi';

interface StepInterval {
  start: number;
  end: number;
  steps: number;
}

export class MotionService {
  private static instance: MotionService | null = null;
  private listening: boolean = false;
  private lastSteps: number = -1;
  private lastTimestamp: number = 0;
  private intervals: StepInterval[] = [];

  public static getInstance(): MotionService {
    if (!MotionService.instance) MotionService.instance = new MotionService();
    return MotionService.instance;
  }

  public startMotionListening(): void {
    if (this.listening) return;
    // The caller must obtain ACTIVITY_MOTION runtime permission first.
    sensor.on(sensor.SensorId.PEDOMETER, (data: sensor.PedometerResponse) => {
      const now = Date.now();
      const steps = Number(data.steps);
      if (!Number.isFinite(steps) || steps < 0) return;
      if (this.lastSteps >= 0 && steps >= this.lastSteps && now > this.lastTimestamp) {
        if (steps > this.lastSteps) this.intervals.push({ start: this.lastTimestamp, end: now, steps: steps - this.lastSteps });
      }
      this.lastSteps = steps;
      this.lastTimestamp = now;
      // Bound memory; only observed intervals from the latest 48 hours are useful.
      this.intervals = this.intervals.filter((item: StepInterval) => item.end >= now - 172800000);
    });
    this.listening = true;
  }

  public stopMotionListening(): void {
    if (!this.listening) return;
    sensor.off(sensor.SensorId.PEDOMETER);
    this.listening = false;
    this.lastSteps = -1;
    this.lastTimestamp = 0;
  }

  public isWithinSchoolWindows(time: string, windows: SchoolDayWindowDto[]): boolean {
    return windows.some((item: SchoolDayWindowDto) => time >= item.startTime && time < item.endTime);
  }

  public async aggregateOutsideSchoolMotion(date: string, windows: SchoolDayWindowDto[]): Promise<OutsideSchoolActivityRequest> {
    const start = new Date(date + 'T00:00:00').getTime();
    const endDate = new Date(start);
    endDate.setDate(endDate.getDate() + 1);
    const end = endDate.getTime();
    if (!Number.isFinite(start)) throw new Error('日期无效');
    let steps = 0;
    for (const interval of this.intervals) {
      if (interval.start < start || interval.end > end) continue;
      let overlapsSchool = false;
      for (const win of windows) {
        const schoolStart = new Date(date + 'T' + win.startTime + ':00').getTime();
        const schoolEnd = new Date(date + 'T' + win.endTime + ':00').getTime();
        if (!Number.isFinite(schoolStart) || !Number.isFinite(schoolEnd) || schoolEnd <= schoolStart) {
          throw new Error('在校时段无效');
        }
        if (interval.start < schoolEnd && interval.end > schoolStart) overlapsSchool = true;
      }
      // A delta straddling school time cannot be apportioned faithfully; omit it.
      if (!overlapsSchool) steps += interval.steps;
    }
    hilog.info(0x2609, 'MotionService', 'Observed outside-school steps: %{public}d', steps);
    // A pedometer is not an exercise-duration or active-energy measurement.
    return { date: date, exerciseMinutes: 0, steps: steps };
  }

  public async syncAndSaveOutsideSchoolMotion(date: string): Promise<OutsideSchoolActivityRequest> {
    const api = new HealthApi();
    const windows = await api.getSchoolDayWindows(date);
    const activity = await this.aggregateOutsideSchoolMotion(date, windows.windows);
    await api.saveOutsideSchoolActivity(activity);
    return activity;
  }
}
