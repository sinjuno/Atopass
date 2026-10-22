import { useState, type FormEvent } from 'react';
import PageHeader from '@/components/common/PageHeader';
import { ErrorMessage, LoadingMessage } from '@/components/common/StatusMessage';
import TextField from '@/components/common/TextField';
import { useAsync } from '@/hooks/useAsync';
import { memberApi } from '@/apis/member';
import { getErrorMessage, getFieldErrors } from '@/utils/errorMessage';

const PASSWORD_MIN_LENGTH = 8;

/** 마이페이지 — /mypage. 내 정보(이메일·닉네임)를 보고, 닉네임과 비밀번호를 바꿀 수 있다. */
export default function MyPage() {
  const me = useAsync(memberApi.getMe, []);

  return (
    <>
      <PageHeader title="마이페이지" />
      <div className="flex flex-col gap-4 px-5 pt-4 pb-6">
        {me.loading ? (
          <LoadingMessage />
        ) : me.error ? (
          <ErrorMessage message={getErrorMessage(me.error)} onRetry={me.reload} />
        ) : (
          me.data && (
            <>
              <section className="rounded-3xl bg-white p-5 shadow-card">
                <p className="text-sm text-gray-500">이메일</p>
                <p className="mt-1 text-lg font-semibold">{me.data.email}</p>
              </section>

              <NicknameSection nickname={me.data.nickname} onSaved={me.reload} />
              <PasswordSection />
            </>
          )
        )}
      </div>
    </>
  );
}

function NicknameSection({ nickname, onSaved }: { nickname: string; onSaved: () => void }) {
  const [value, setValue] = useState(nickname);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    const trimmed = value.trim();
    if (!trimmed || saving) return;
    setSaving(true);
    setMessage(null);
    setError(null);
    try {
      await memberApi.updateMe({ nickname: trimmed });
      setMessage('닉네임이 바뀌었어요.');
      onSaved();
    } catch (e) {
      setError(Object.values(getFieldErrors(e))[0] ?? getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="rounded-3xl bg-white p-5 shadow-card">
      <h2 className="mb-3 font-bold text-gray-700">닉네임</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <TextField label="닉네임" value={value} onChange={(e) => setValue(e.target.value)} />
        {message && <p className="text-sm text-brand-700">{message}</p>}
        {error && <p className="text-sm text-red-600">{error}</p>}
        <button
          type="submit"
          disabled={saving || !value.trim() || value.trim() === nickname}
          className="rounded-2xl bg-brand-600 py-3 font-bold text-white disabled:opacity-40"
        >
          {saving ? '저장 중…' : '닉네임 저장'}
        </button>
      </form>
    </section>
  );
}

function PasswordSection() {
  const [password, setPassword] = useState('');
  const [passwordConfirm, setPasswordConfirm] = useState('');
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | null>(null);

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setMessage(null);
    setFieldError(null);
    if (password.length < PASSWORD_MIN_LENGTH) {
      setFieldError(`비밀번호는 ${PASSWORD_MIN_LENGTH}자 이상이어야 해요.`);
      return;
    }
    if (password !== passwordConfirm) {
      setFieldError('비밀번호가 일치하지 않아요.');
      return;
    }
    setSaving(true);
    try {
      await memberApi.updateMe({ password });
      setPassword('');
      setPasswordConfirm('');
      setMessage('비밀번호가 바뀌었어요.');
    } catch (e) {
      setFieldError(Object.values(getFieldErrors(e))[0] ?? getErrorMessage(e));
    } finally {
      setSaving(false);
    }
  };

  return (
    <section className="rounded-3xl bg-white p-5 shadow-card">
      <h2 className="mb-3 font-bold text-gray-700">비밀번호 변경</h2>
      <form onSubmit={handleSubmit} className="flex flex-col gap-3">
        <TextField
          label="새 비밀번호"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          hint={`${PASSWORD_MIN_LENGTH}자 이상 입력해 주세요.`}
        />
        <TextField
          label="새 비밀번호 확인"
          type="password"
          autoComplete="new-password"
          value={passwordConfirm}
          onChange={(e) => setPasswordConfirm(e.target.value)}
        />
        {message && <p className="text-sm text-brand-700">{message}</p>}
        {fieldError && <p className="text-sm text-red-600">{fieldError}</p>}
        <button
          type="submit"
          disabled={saving || !password || !passwordConfirm}
          className="rounded-2xl bg-brand-600 py-3 font-bold text-white disabled:opacity-40"
        >
          {saving ? '저장 중…' : '비밀번호 저장'}
        </button>
      </form>
    </section>
  );
}
