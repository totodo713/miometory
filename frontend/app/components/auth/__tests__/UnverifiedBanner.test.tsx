import React from 'react'
import { render, screen } from '@testing-library/react'
import UnverifiedBanner from '../UnverifiedBanner'

describe('UnverifiedBanner', () => {
  test('does not render when no user', () => {
    const { container } = render(<UnverifiedBanner user={null} isVerified={false} />)
    expect(container).toBeEmptyDOMElement()
  })

  test('does not render when user is verified', () => {
    const user = { id: '1', email: 'a@example.com', displayName: 'A' }
    const { container } = render(<UnverifiedBanner user={user} isVerified={true} />)
    expect(container).toBeEmptyDOMElement()
  })

  test('renders when user exists and not verified', () => {
    const user = { id: '1', email: 'a@example.com', displayName: 'A' }
    render(<UnverifiedBanner user={user} isVerified={false} />)
    expect(screen.getByRole('alert')).toBeInTheDocument()
    expect(screen.getByText(/not verified/i)).toBeInTheDocument()
  })
})
