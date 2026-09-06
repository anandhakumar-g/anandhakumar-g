import { ArrowRight, LogIn } from "lucide-react";
import { useState } from "react";
import { useAuth } from "../auth";
import { Button, Card, Field } from "../ui";

export function Login() {
  const { requestOtp, verifyOtp } = useAuth();
  const [phone, setPhone] = useState("");
  const [code, setCode] = useState("");
  const [sent, setSent] = useState(false);
  const [dev, setDev] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function send() {
    setErr(null);
    setBusy(true);
    try {
      setDev(await requestOtp(phone.trim()));
      setSent(true);
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  async function verify() {
    setErr(null);
    setBusy(true);
    try {
      await verifyOtp(phone.trim(), code.trim());
    } catch (e) {
      setErr((e as Error).message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <Card title="Console sign-in">
          <div className="stack">
            <p className="muted" style={{ marginTop: 0 }}>Platform Super Admins only.</p>
            <Field
              label="Phone"
              value={phone}
              onChange={(e) => setPhone(e.target.value)}
              placeholder="+9190000000000"
              disabled={sent}
            />
            {sent && (
              <>
                {dev && <p className="ok">Dev code: {dev}</p>}
                <Field label="6-digit code" value={code} onChange={(e) => setCode(e.target.value)} maxLength={6} />
              </>
            )}
            {err && <p className="error">{err}</p>}
            {sent ? (
              <Button onClick={verify} loading={busy} disabled={code.trim().length < 4} icon={LogIn}>Verify</Button>
            ) : (
              <Button onClick={send} loading={busy} disabled={phone.trim().length < 8} icon={ArrowRight}>Send code</Button>
            )}
          </div>
        </Card>
      </div>
    </div>
  );
}
