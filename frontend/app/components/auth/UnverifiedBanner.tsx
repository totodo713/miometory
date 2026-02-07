import React from 'react'
import type { AuthUser } from '@/hooks/useAuth'

export type UnverifiedBannerProps = {
  user: AuthUser | null
  isVerified?: boolean
}

export function UnverifiedBanner({ user, isVerified }: UnverifiedBannerProps) {
  if (!user) return null
  if (isVerified) return null

  return (
    <div role="alert" aria-live="assertive" className="bg-yellow-100 border-l-4 border-yellow-500 text-yellow-700 p-4">
      <p>Your email address is not verified. Please check your inbox for a verification link.</p>
    </div>
  )
}

export default UnverifiedBanner
