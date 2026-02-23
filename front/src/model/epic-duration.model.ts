export interface EpicDurationEntry {
  epicKey: string;
  epicSummary: string;
  status: string;
  durationDays: number;
}

export interface SprintVersionEpicDuration {
  sprintId: number;
  sprintName: string;
  versionName: string;
  epicCount: number;
  totalDurationDays: number;
  averageDurationDays: number;
  epics: EpicDurationEntry[];
}
