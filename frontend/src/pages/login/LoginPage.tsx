import { useState, type ChangeEvent, type FormEvent } from 'react';
import { useLocation, useNavigate } from 'react-router';
import { ApiError } from '@/apis/client';
import { authApi } from '@/apis/auth';
import TextField from '@/components/common/TextField';
import { getErrorMessage, getFieldErrors } from '@/utils/errorMessage';

type Mode = 'login' | 'signup';

interface FormValues {
  email: string;
  password: string;
  passwordConfirm: string;
  nickname: string;
}

type FieldErrors = Partial<Record<keyof FormValues, string>>;

const INITIAL_VALUES: FormValues = { email: '', password: '', passwordConfirm: '', nickname: '' };
const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
const PASSWORD_MIN_LENGTH = 8;

function validate(mode: Mode, values: FormValues): FieldErrors {
  const errors: FieldErrors = {};
  if (!values.email.trim()) errors.email = '이메일을 입력해 주세요.';
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = '이메일 형식이 올바르지 않아요.';

  if (!values.password) errors.password = '비밀번호를 입력해 주세요.';

  if (mode === 'signup') {
    if (!values.nickname.trim()) errors.nickname = '닉네임을 입력해 주세요.';
    if (values.password && values.password.length < PASSWORD_MIN_LENGTH) {
      errors.password = `비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 해요.`;
    }
    if (values.password !== values.passwordConfirm) errors.passwordConfirm = '비밀번호가 일치하지 않아요.';
  }
  return errors;
}

export default function LoginPage() {
  const navigate = useNavigate();
  const from = (useLocation().state as { from?: string } | null)?.from ?? '/';

  const [mode, setMode] = useState<Mode>('login');
  const [values, setValues] = useState<FormValues>(INITIAL_VALUES);
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isSignup = mode === 'signup';

  const switchMode = (next: Mode) => {
    setMode(next);
    setFieldErrors({});
    setFormError(null);
    setNotice(null);
    setValues((prev) => ({ ...INITIAL_VALUES, email: prev.email }));
  };

  const updateField = (field: keyof FormValues) => (event: ChangeEvent<HTMLInputElement>) => {
    const { value } = event.target;
    setValues((prev) => ({ ...prev, [field]: value }));
    setFieldErrors((prev) => ({ ...prev, [field]: undefined }));
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    if (submitting) return;

    const errors = validate(mode, values);
    setFieldErrors(errors);
    setFormError(null);
    setNotice(null);
    if (Object.keys(errors).length > 0) return;

    const email = values.email.trim();
    let signedUp = false;
    setSubmitting(true);
    try {
      if (isSignup) {
        await authApi.signup({ email, password: values.password, nickname: values.nickname.trim() });
        signedUp = true;
      }
      // 가입 후에는 바로 로그인한다. 액세스 토큰은 authApi.login 이 메모리에 보관한다.
      await authApi.login({ email, password: values.password });
      navigate(from, { replace: true });
    } catch (error) {
      if (signedUp) {
        // 가입은 됐는데 자동 로그인만 실패한 경우: 다시 가입하지 않도록 로그인 탭으로 보낸다.
        switchMode('login');
        setNotice('가입이 완료됐어요. 로그인해 주세요.');
      } else if (error instanceof ApiError && error.code === 'EMAIL_DUPLICATED') {
        setFieldErrors({ email: '이미 가입된 이메일이에요.' });
      } else {
        const serverFieldErrors = getFieldErrors(error) as FieldErrors;
        setFieldErrors(serverFieldErrors);
        if (Object.keys(serverFieldErrors).length === 0) setFormError(getErrorMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto flex min-h-dvh max-w-md flex-col justify-center p-6">
      <div className="mb-8 text-center">
        <p className="text-3xl font-extrabold text-emerald-600">Atopass</p>
        <p className="mt-2 text-gray-500">매일의 피부 기록을 쉽고 꾸준하게</p>
      </div>

      <div role="tablist" aria-label="로그인 또는 회원가입" className="mb-6 grid grid-cols-2 rounded-xl bg-gray-100 p-1">
        {(['login', 'signup'] as const).map((tab) => (
          <button
            key={tab}
            type="button"
            role="tab"
            aria-selected={mode === tab}
            onClick={() => switchMode(tab)}
            className={`rounded-lg py-2.5 font-bold ${mode === tab ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500'}`}
          >
            {tab === 'login' ? '로그인' : '회원가입'}
          </button>
        ))}
      </div>

      <form noValidate onSubmit={handleSubmit} className="flex flex-col gap-4">
        {isSignup && (
          <TextField
            label="닉네임"
            autoComplete="nickname"
            value={values.nickname}
            onChange={updateField('nickname')}
            error={fieldErrors.nickname}
          />
        )}
        <TextField
          label="이메일"
          type="email"
          inputMode="email"
          autoComplete="email"
          value={values.email}
          onChange={updateField('email')}
          error={fieldErrors.email}
        />
        <TextField
          label="비밀번호"
          type="password"
          autoComplete={isSignup ? 'new-password' : 'current-password'}
          value={values.password}
          onChange={updateField('password')}
          error={fieldErrors.password}
          hint={isSignup ? `${PASSWORD_MIN_LENGTH}자 이상 입력해 주세요.` : undefined}
        />
        {isSignup && (
          <TextField
            label="비밀번호 확인"
            type="password"
            autoComplete="new-password"
            value={values.passwordConfirm}
            onChange={updateField('passwordConfirm')}
            error={fieldErrors.passwordConfirm}
          />
        )}

        {notice && (
          <p role="status" className="rounded-xl bg-emerald-50 px-4 py-3 text-emerald-700">
            {notice}
          </p>
        )}
        {formError && (
          <p role="alert" className="rounded-xl bg-red-50 px-4 py-3 text-red-700">
            {formError}
          </p>
        )}

        <button
          type="submit"
          disabled={submitting}
          className="mt-2 rounded-xl bg-emerald-600 py-3 text-lg font-bold text-white disabled:opacity-60"
        >
          {submitting ? '처리 중…' : isSignup ? '가입하고 시작하기' : '로그인'}
        </button>
      </form>
    </div>
  );
}
