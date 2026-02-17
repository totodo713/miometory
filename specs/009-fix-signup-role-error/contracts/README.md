# API Contracts: 009-fix-signup-role-error

No new API contracts for this bug fix. The existing signup endpoint contract is unchanged:

- **Endpoint**: `POST /api/v1/auth/signup`
- **Request**: `RegistrationRequest { email, name, password }`
- **Response**: `SignupResponse { id, email, name }` (200/201)
- **Error**: Appropriate HTTP error codes with descriptive messages

The fix is internal to the persistence layer and does not alter the API contract.
