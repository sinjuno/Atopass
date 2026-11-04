import type { ApiSuccess } from '@/types/api';
import type {
  ExtractedMedication,
  Medication,
  MedicationHistoryItem,
  MedicationInput,
  MedicationSource,
  MedicationStatus,
  OnboardingStep,
} from '@/types/medication';
import { client, unwrap } from './client';
import { mockMedicationApi } from './mockMedication';

// VITE_USE_MOCK=true 이면 백엔드 없이 mock 으로 동작한다. 기본은 실제 API.
const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

const realApi = {
  list: (status: MedicationStatus) =>
    client.get<ApiSuccess<Medication[]>>('/medications', { params: { status } }).then(unwrap),

  create: (item: MedicationInput, source: MedicationSource = 'mypage') =>
    client.post<ApiSuccess<Medication>>('/medications', { ...item, source }).then(unwrap),

  bulkCreate: (items: MedicationInput[], source: MedicationSource) =>
    client.post<ApiSuccess<Medication[]>>('/medications/bulk', { items, source }).then(unwrap),

  update: (id: string, patch: Partial<MedicationInput>) =>
    client.patch<ApiSuccess<Medication>>(`/medications/${id}`, patch).then(unwrap),

  stop: (id: string, endDate: string) =>
    client.post<ApiSuccess<Medication>>(`/medications/${id}/stop`, { endDate }).then(unwrap),

  history: (id: string) =>
    client.get<ApiSuccess<MedicationHistoryItem[]>>(`/medications/${id}/history`).then(unwrap),

  /** 사진 → 약 목록. 저장하지 않는다. 업로드 필드명은 'files'(백엔드와 확인 필요). */
  extract: (files: File[]) => {
    const form = new FormData();
    files.forEach((f) => form.append('files', f));
    return client
      .post<ApiSuccess<{ items: ExtractedMedication[] }>>('/medications/extract', form)
      .then(unwrap)
      .then((d) => d.items);
  },

  getOnboarding: () =>
    client
      .get<ApiSuccess<{ medicationStep: OnboardingStep }>>('/me/onboarding')
      .then(unwrap)
      .then((d) => d.medicationStep),

  setOnboarding: (step: 'done' | 'skipped') =>
    client
      .patch<ApiSuccess<{ medicationStep: OnboardingStep }>>('/me/onboarding', { medicationStep: step })
      .then(unwrap)
      .then((d) => d.medicationStep),
};

export const medicationApi: typeof realApi = USE_MOCK ? mockMedicationApi : realApi;
