# Feature Specification: Login Page Design, Auth Integration & Logout

**Feature Branch**: `012-login-auth-ui`
**Created**: 2026-02-18
**Status**: Draft
**Input**: User description: "Apply simple design to login screen, make the app work with logged-in user, add simple logout functionality"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Login with Credentials (Priority: P1)

A user visits the application and sees a clean, professionally designed login page with the Miometry branding. They enter their email address and password, then click the login button. The system authenticates their credentials and redirects them to the worklog dashboard where all data corresponds to their identity.

**Why this priority**: Without working login, the application cannot identify who is using it. This is the foundational capability that all other features depend on.

**Independent Test**: Can be fully tested by navigating to the login page, entering valid credentials, and verifying that the user lands on the worklog dashboard with their identity reflected in API calls.

**Acceptance Scenarios**:

1. **Given** a user is not logged in, **When** they visit the login page, **Then** they see a styled login form with email and password fields, a "remember me" option, and a login button.
2. **Given** a user is on the login page, **When** they enter valid credentials and click login, **Then** they are redirected to the worklog dashboard.
3. **Given** a user is on the login page, **When** they enter invalid credentials, **Then** they see an error message in Japanese indicating the credentials are incorrect.
4. **Given** a user is on the login page, **When** they submit the form without filling in required fields, **Then** they see a validation error message.
5. **Given** a user is already logged in, **When** they visit the login page, **Then** they are automatically redirected to the worklog dashboard.

---

### User Story 2 - Logout (Priority: P1)

A logged-in user can see their display name in a global header bar at the top of every page. They click the "logout" button next to their name, which ends their session and returns them to the login page.

**Why this priority**: Logout is essential for security and for switching between user accounts. Paired with login, it completes the basic authentication lifecycle.

**Independent Test**: Can be tested by logging in, verifying the header shows the user's name, clicking logout, and confirming redirection to the login page with worklog pages no longer accessible.

**Acceptance Scenarios**:

1. **Given** a user is logged in, **When** they view any page, **Then** they see a header bar displaying their display name and a logout button.
2. **Given** a user is logged in, **When** they click the logout button, **Then** their session is ended and they are redirected to the login page.
3. **Given** a user has logged out, **When** they try to access the worklog pages directly, **Then** they are redirected to the login page.
4. **Given** a user is on the login page, **When** they view the page, **Then** no header bar with user information is visible.

---

### User Story 3 - Route Protection (Priority: P2)

Unauthenticated users are prevented from accessing worklog pages. When they attempt to visit any worklog URL directly, they are redirected to the login page. The root URL of the application also redirects appropriately based on authentication state.

**Why this priority**: Protects application pages from unauthorized access. Important but secondary to the login/logout core flow.

**Independent Test**: Can be tested by clearing session state and directly navigating to worklog URLs, verifying redirection to login occurs.

**Acceptance Scenarios**:

1. **Given** a user is not logged in, **When** they visit any worklog page URL, **Then** they are redirected to the login page.
2. **Given** a user is not logged in, **When** they visit the application root URL, **Then** they are redirected to the login page.
3. **Given** a user is logged in, **When** they visit the application root URL, **Then** they are redirected to the worklog dashboard.

---

### User Story 4 - Session Persistence Across Page Refresh (Priority: P2)

When a logged-in user refreshes the page or opens a new tab, their login state is preserved and they remain authenticated without needing to log in again.

**Why this priority**: Without session persistence, users would lose their login state on every page navigation, making the application unusable in practice.

**Independent Test**: Can be tested by logging in, refreshing the browser, and verifying the user remains on the worklog dashboard with their identity intact.

**Acceptance Scenarios**:

1. **Given** a user is logged in, **When** they refresh the page, **Then** they remain authenticated and see their worklog data.
2. **Given** a user is logged in, **When** the session timeout period elapses (30 minutes of inactivity), **Then** they are automatically logged out and redirected to the login page.

---

### Edge Cases

- What happens when the backend server is unreachable during login? The user sees a network error message.
- What happens when a logged-in user's session expires on the backend but the frontend still shows them as logged in? The next API call returns 401, and the user is redirected to the login page.
- What happens when the user submits the login form multiple times rapidly? The submit button is disabled during the request to prevent duplicate submissions.
- What happens when the login page is accessed on a mobile device? The form remains usable and properly laid out on smaller screens.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST display a styled login page with email and password fields, a "remember me" checkbox, and a login button.
- **FR-002**: System MUST authenticate users against stored credentials when the login form is submitted.
- **FR-003**: System MUST display error messages in Japanese when authentication fails (invalid credentials, server error, network error).
- **FR-004**: System MUST redirect authenticated users to the worklog dashboard after successful login.
- **FR-005**: System MUST display a global header bar showing the authenticated user's display name and a logout button on all pages except the login page.
- **FR-006**: System MUST end the user's session and redirect to the login page when the logout button is clicked.
- **FR-007**: System MUST prevent unauthenticated users from accessing worklog pages by redirecting them to the login page.
- **FR-008**: System MUST redirect the application root URL to the login page (if unauthenticated) or worklog dashboard (if authenticated).
- **FR-009**: System MUST persist the user's authentication state across page refreshes within the same browser session.
- **FR-010**: System MUST disable the login button and show a loading indicator while a login request is in progress.
- **FR-011**: System MUST provide development test user accounts that allow login functionality to be tested without manual user creation.
- **FR-012**: Session timeout behavior MUST properly log out the user and redirect to the login page (fixing the current broken redirect).

### Key Entities

- **User (Authentication)**: Represents a person who can log in. Key attributes: email address, display name, account status. Related to Member for worklog operations.
- **Session**: Represents an active login session. Key attributes: authenticated user identity, expiration time. Stored server-side with client-side persistence for page refresh survival.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can complete the login process (entering credentials and reaching the worklog dashboard) in under 10 seconds.
- **SC-002**: 100% of worklog page URLs redirect unauthenticated users to the login page.
- **SC-003**: Users can identify who they are logged in as on every page of the application.
- **SC-004**: Users can log out and confirm they are logged out within 3 seconds.
- **SC-005**: All 4 development test users can successfully log in and access their respective worklog data.
- **SC-006**: Login state survives page refresh without requiring re-authentication.

## Assumptions

- The backend authentication API (`POST /api/v1/auth/login` and `POST /api/v1/auth/logout`) is already implemented and functional.
- The development environment operates with session-based authentication (no OAuth or JWT needed at this stage).
- A shared development password (`Password1`) for all test users is acceptable for the development environment.
- The login page design follows the existing application's visual language (consistent colors, typography, and spacing).
- Mobile responsiveness is a secondary concern; the login page should be usable on mobile but pixel-perfect mobile design is not required.
- Error messages are displayed in Japanese to match the application's target user base.
