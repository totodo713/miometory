"use client"
import React, { useState } from 'react'

export default function SignupPage() {
  const [email, setEmail] = useState('')
  const [name, setName] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)

  function validatePassword(p: string) {
    return /(?=.*[0-9])(?=.*[A-Z]).{8,}/.test(p)
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    if (!email || !name || !password) {
      setError('All fields are required')
      return
    }
    if (!validatePassword(password)) {
      setError('Password must be 8+ chars, include a digit and an uppercase letter')
      return
    }
    // In real app: call API
    setError(null)
  }

  return (
    <form onSubmit={handleSubmit}>
      <label>
        Email
        <input aria-label="email" value={email} onChange={(e) => setEmail(e.target.value)} />
      </label>
      <label>
        Name
        <input aria-label="name" value={name} onChange={(e) => setName(e.target.value)} />
      </label>
      <label>
        Password
        <input aria-label="password" value={password} onChange={(e) => setPassword(e.target.value)} />
      </label>
      {error && <div role="alert">{error}</div>}
      <button type="submit">Sign up</button>
    </form>
  )
}
