"use client"
import React, { useEffect, useState } from 'react'
import { useSearchParams } from 'next/navigation'

export default function VerifyEmailPage() {
  const params = useSearchParams()
  const token = params?.get('token') || ''
  const [status, setStatus] = useState<string | null>(null)

  useEffect(() => {
    if (!token) return
    // In real app: call API to verify token
    setStatus('verified')
  }, [token])

  if (!token) return <div role="alert">Missing token</div>
  return <div>{status === 'verified' ? 'Email verified' : 'Verifying...'}</div>
}
