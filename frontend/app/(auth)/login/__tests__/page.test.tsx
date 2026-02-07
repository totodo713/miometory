import React from 'react'
import { render, screen, fireEvent } from '@testing-library/react'
import LoginPage from '../page'

describe('Login page', () => {
  test('shows error when fields missing', () => {
    render(<LoginPage />)
    fireEvent.click(screen.getByText(/log in/i))
    expect(screen.getByRole('alert')).toHaveTextContent(/email and password are required/i)
  })

  test('remember me checkbox toggles', () => {
    render(<LoginPage />)
    const cb = screen.getByLabelText('remember-me') as HTMLInputElement
    expect(cb.checked).toBe(false)
    fireEvent.click(cb)
    expect(cb.checked).toBe(true)
  })
})
