import React from 'react'
import { render, screen } from '@testing-library/react'
import VerifyEmailPage from '@/app/(auth)/verify-email/page'

describe('Verify Email page', () => {
  test('shows missing token alert when token not provided', () => {
    render(<VerifyEmailPage />)
    expect(screen.getByRole('alert')).toHaveTextContent(/missing token/i)
  })
})
