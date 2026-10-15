import axios from 'axios';

/** 위치 권한이 없거나 위치를 못 받으면 서울 기준으로 보여 준다 */
const DEFAULT_LOCATION = { latitude: 37.5665, longitude: 126.978, label: '서울' };
const GEOLOCATION_TIMEOUT_MS = 5000;

export interface CurrentWeather {
  /** 섭씨 */
  temperature: number;
  /** % */
  humidity: number;
  /** WMO 날씨 코드 */
  weatherCode: number;
  /** '현재 위치' 또는 기본 위치 이름 */
  locationLabel: string;
}

interface OpenMeteoResponse {
  current: {
    temperature_2m: number;
    relative_humidity_2m: number;
    weather_code: number;
  };
}

function getLocation(): Promise<{ latitude: number; longitude: number; label: string }> {
  return new Promise((resolve) => {
    if (!navigator.geolocation) {
      resolve(DEFAULT_LOCATION);
      return;
    }
    // 권한 팝업에 응답하지 않으면 getCurrentPosition의 timeout이 시작되지 않으므로 따로 기다림을 끊는다
    const fallback = setTimeout(() => resolve(DEFAULT_LOCATION), GEOLOCATION_TIMEOUT_MS);
    navigator.geolocation.getCurrentPosition(
      ({ coords }) => {
        clearTimeout(fallback);
        // 날씨에는 이 정도 정밀도면 충분하다. 외부 서비스에 정확한 좌표를 보내지 않는다.
        resolve({
          latitude: Math.round(coords.latitude * 100) / 100,
          longitude: Math.round(coords.longitude * 100) / 100,
          label: '현재 위치',
        });
      },
      () => {
        clearTimeout(fallback);
        resolve(DEFAULT_LOCATION);
      },
      { timeout: GEOLOCATION_TIMEOUT_MS, maximumAge: 10 * 60 * 1000 },
    );
  });
}

/**
 * 현재 날씨 — Open-Meteo (API 키 없음, 백엔드를 거치지 않고 바로 호출).
 * 인증 헤더가 붙지 않도록 공용 client가 아닌 axios를 그대로 쓴다.
 */
export async function getCurrentWeather(): Promise<CurrentWeather> {
  const location = await getLocation();
  const { data } = await axios.get<OpenMeteoResponse>('https://api.open-meteo.com/v1/forecast', {
    params: {
      latitude: location.latitude,
      longitude: location.longitude,
      current: 'temperature_2m,relative_humidity_2m,weather_code',
      timezone: 'Asia/Seoul',
    },
  });
  return {
    temperature: data.current.temperature_2m,
    humidity: data.current.relative_humidity_2m,
    weatherCode: data.current.weather_code,
    locationLabel: location.label,
  };
}
