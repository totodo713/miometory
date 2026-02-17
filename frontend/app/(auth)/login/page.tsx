"use client";
import Link from "next/link";
import type React from "react";
import { useState } from "react";

export default function LoginPage() {
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState<string | null>(null);

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setError(null);
    if (!email || !password) {
      setError("Email and password are required");
      return;
    }
    // In real app: call API and set cookie for remember
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Email
        <input aria-label="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Password
        <input aria-label="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      <label>
        Remember me
        <input
          aria-label="remember-me"
          type="checkbox"
          checked={remember}
          onChange={(e) => setRemember(e.target.checked)}
        />
      </label>
      {error && <div role="alert">{error}</div>}
      <button type="submit">Log in</button>
      <div className="mt-4 text-center">
        <Link href="/password-reset/request" className="text-sm text-blue-600 hover:underline">
          パスワードをお忘れですか？
        </Link>
      </div>
    </form>
  );
}
