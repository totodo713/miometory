import React from 'react'
import { render, screen } from '@testing-library/react'
import VerifyEmailPage from '../page'

// Because Next's useSearchParams doesn't work in this test environment easily,
// just render the component without token to exercise the missing token branch.
describe('Verify Email page', () => {
  test('shows missing token alert when token not provided', () => {
    render(<VerifyEmailPage />)
    expect(screen.getByRole('alert')).toHaveTextContent(/missing token/i)
  })
})
