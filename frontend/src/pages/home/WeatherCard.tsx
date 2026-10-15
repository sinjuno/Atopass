import { getCurrentWeather } from '@/apis/weather';
import { useAsync } from '@/hooks/useAsync';

/** WMO 날씨 코드 → 표시 문구·아이콘 */
function describeWeather(code: number): { label: string; icon: string } {
  if (code === 0) return { label: '맑음', icon: '☀️' };
  if (code <= 2) return { label: '구름 조금', icon: '🌤️' };
  if (code === 3) return { label: '흐림', icon: '☁️' };
  if (code === 45 || code === 48) return { label: '안개', icon: '🌫️' };
  if (code >= 51 && code <= 57) return { label: '이슬비', icon: '🌦️' };
  if ((code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return { label: '비', icon: '🌧️' };
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return { label: '눈', icon: '🌨️' };
  if (code >= 95) return { label: '뇌우', icon: '⛈️' };
  return { label: '알 수 없음', icon: '🌡️' };
}

/** 홈 — 오늘 날씨, 기온, 습도 */
export default function WeatherCard() {
  const weather = useAsync(getCurrentWeather, []);

  return (
    <section className="rounded-2xl border border-gray-200 p-4">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="font-bold text-gray-700">오늘 날씨</h2>
        {weather.data && <span className="text-sm text-gray-400">{weather.data.locationLabel} 기준</span>}
      </div>

      {weather.loading ? (
        <p className="py-2 text-gray-400">날씨를 불러오는 중…</p>
      ) : weather.error || !weather.data ? (
        <div className="flex items-center justify-between gap-3">
          <p className="text-gray-500">날씨를 불러오지 못했어요.</p>
          <button type="button" onClick={weather.reload} className="rounded-lg border px-3 py-1 text-sm">
            다시 시도
          </button>
        </div>
      ) : (
        <WeatherSummary {...weather.data} />
      )}
    </section>
  );
}

function WeatherSummary({ weatherCode, temperature, humidity }: { weatherCode: number; temperature: number; humidity: number }) {
  const { label, icon } = describeWeather(weatherCode);
  return (
    <dl className="grid grid-cols-3 text-center">
      <div>
        <dt className="text-sm text-gray-500">날씨</dt>
        <dd className="mt-1 text-lg font-bold">
          <span aria-hidden="true">{icon} </span>
          {label}
        </dd>
      </div>
      <div>
        <dt className="text-sm text-gray-500">온도</dt>
        <dd className="mt-1 text-lg font-bold">{Math.round(temperature)}°C</dd>
      </div>
      <div>
        <dt className="text-sm text-gray-500">습도</dt>
        <dd className="mt-1 text-lg font-bold">{Math.round(humidity)}%</dd>
      </div>
    </dl>
  );
}
