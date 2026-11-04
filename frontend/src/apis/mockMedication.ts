import { ApiError } from './client';
import type {
  ExtractedMedication,
  Medication,
  MedicationHistoryItem,
  MedicationInput,
  MedicationSource,
  MedicationStatus,
  OnboardingStep,
} from '@/types/medication';
import { today } from '@/utils/date';
import { MED_PHOTO_MAX_BYTES, MED_PHOTO_MAX_COUNT } from '@/utils/medication';

// 백엔드가 준비되기 전 화면 개발용 mock. localStorage 에 저장해 새로고침해도 유지된다.
const MEDS_KEY = 'atopass.mock.medications';
const HISTORY_KEY = 'atopass.mock.medicationHistory';
const ONBOARDING_KEY = 'atopass.mock.onboarding';

const read = <T>(key: string, fallback: T): T => {
  try {
    const raw = localStorage.getItem(key);
    return raw ? (JSON.parse(raw) as T) : fallback;
  } catch {
    return fallback;
  }
};
const write = (key: string, value: unknown) => localStorage.setItem(key, JSON.stringify(value));
const delay = (ms = 400) => new Promise((resolve) => setTimeout(resolve, ms));

const validate = (item: Partial<MedicationInput>) => {
  if (!item.name?.trim() || !item.type || !item.startDate) {
    throw new ApiError('필수값이 없어요.', 400, 'INVALID_INPUT');
  }
};

export const mockMedicationApi = {
  async list(status: MedicationStatus): Promise<Medication[]> {
    await delay(200);
    return read<Medication[]>(MEDS_KEY, []).filter((m) => (status === 'active' ? !m.endDate : !!m.endDate));
  },

  async create(item: MedicationInput, source: MedicationSource = 'mypage'): Promise<Medication> {
    await delay(200);
    validate(item);
    const created: Medication = { ...item, id: crypto.randomUUID(), source };
    write(MEDS_KEY, [...read<Medication[]>(MEDS_KEY, []), created]);
    return created;
  },

  async bulkCreate(items: MedicationInput[], source: MedicationSource): Promise<Medication[]> {
    await delay();
    items.forEach(validate);
    const created = items.map((item): Medication => ({ ...item, id: crypto.randomUUID(), source }));
    write(MEDS_KEY, [...read<Medication[]>(MEDS_KEY, []), ...created]);
    return created;
  },

  async update(id: string, patch: Partial<MedicationInput>): Promise<Medication> {
    await delay(200);
    const all = read<Medication[]>(MEDS_KEY, []);
    const target = all.find((m) => m.id === id);
    if (!target) throw new ApiError('약을 찾을 수 없어요.', 404);
    validate({ ...target, ...patch });
    const history = read<MedicationHistoryItem[]>(HISTORY_KEY, []);
    const changedAt = new Date().toISOString();
    (Object.keys(patch) as (keyof MedicationInput)[]).forEach((field) => {
      if (JSON.stringify(patch[field]) === JSON.stringify(target[field])) return;
      history.push({
        changedAt,
        field: `${id}:${field}`,
        before: JSON.stringify(target[field]),
        after: JSON.stringify(patch[field]),
      });
    });
    write(HISTORY_KEY, history);
    // 백엔드처럼 dose·timesText·memo 는 ''이면 비우고(null 로 저장), null 은 "안 바꿈"으로 본다.
    const next: Partial<MedicationInput> = { ...patch };
    (['dose', 'timesText', 'memo'] as const).forEach((key) => {
      if (next[key] === null) delete next[key];
      else if (next[key] === '') next[key] = null;
    });
    const updated = { ...target, ...next };
    write(MEDS_KEY, all.map((m) => (m.id === id ? updated : m)));
    return updated;
  },

  async stop(id: string, endDate: string): Promise<Medication> {
    return mockMedicationApi.update(id, { endDate });
  },

  async history(id: string): Promise<MedicationHistoryItem[]> {
    await delay(200);
    return read<MedicationHistoryItem[]>(HISTORY_KEY, [])
      .filter((h) => h.field.startsWith(`${id}:`))
      .map((h) => ({ ...h, field: h.field.slice(id.length + 1) }));
  },

  /** 저장하지 않는다. 파일이 1개면 시간대를 못 읽은 경우, 3개면 못 읽는 경우(422) 등을 흉내 낸다. */
  async extract(files: File[]): Promise<ExtractedMedication[]> {
    await delay(1500);
    if (files.length > MED_PHOTO_MAX_COUNT || files.some((f) => f.size > MED_PHOTO_MAX_BYTES)) {
      throw new ApiError('too large', 413);
    }
    const start = today();
    return [
      {
        name: '세티리진정',
        type: 'oral',
        dose: '1정',
        times: [],
        timesText: '1일 2회 식후 30분',
        startDate: start,
        endDate: null,
        memo: null,
        isOtherCondition: false,
      },
      {
        name: '프로토픽 연고',
        type: 'topical',
        dose: null,
        times: ['morning', 'bedtime'],
        timesText: null,
        startDate: start,
        endDate: null,
        memo: null,
        isOtherCondition: false,
      },
    ];
  },

  async getOnboarding(): Promise<OnboardingStep> {
    await delay(150);
    return read<OnboardingStep>(ONBOARDING_KEY, 'pending');
  },

  async setOnboarding(step: 'done' | 'skipped'): Promise<OnboardingStep> {
    await delay(150);
    write(ONBOARDING_KEY, step);
    return step;
  },
};
