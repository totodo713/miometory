# Miometry Frontend

The frontend application for Miometry (ミオメトリー) - a time entry management system built with Next.js and React.

## Technology Stack

- **Framework**: [Next.js](https://nextjs.org) 16.1.1
- **UI Library**: React 19.2.3
- **Language**: TypeScript 5.x
- **Styling**: Tailwind CSS
- **Linter/Formatter**: Biome

## Getting Started

First, run the development server:

```bash
npm run dev
# or
yarn dev
# or
pnpm dev
```

Open [http://localhost:3000](http://localhost:3000) with your browser to see the result.

## Project Structure

```
frontend/
├── app/
│   ├── components/
│   │   ├── shared/      # Reusable UI components
│   │   └── worklog/     # Domain-specific components
│   ├── hooks/           # Custom React hooks
│   ├── lib/             # Utilities and helpers
│   ├── services/        # API client and state management
│   ├── types/           # TypeScript type definitions
│   └── worklog/         # Page routes
├── tests/
│   ├── e2e/             # Playwright end-to-end tests
│   └── unit/            # Component unit tests
└── public/              # Static assets
```

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run start` - Start production server
- `npm run lint` - Run Biome linter
- `npm run format` - Format code with Biome
- `npm run test` - Run unit tests
- `npm run test:e2e` - Run E2E tests with Playwright

## Key Features

- **Calendar View**: Monthly calendar with work log entries
- **Daily Entry Form**: Time entry with multi-project allocation
- **Absence Tracking**: Vacation, sick leave, and other absences
- **Auto-Save**: 3-second debounce with optimistic UI updates
- **CSV Import**: Drag-and-drop file upload with progress tracking
- **Approval Workflow**: Submit, view status, and manage approvals
- **Proxy Entry**: Managers can enter time for team members

## Learn More

- [Next.js Documentation](https://nextjs.org/docs)
- [React Documentation](https://react.dev)
- [Tailwind CSS](https://tailwindcss.com/docs)
- [Biome](https://biomejs.dev/docs/)

## Deployment

The application can be deployed on Vercel, Docker, or any Node.js hosting platform.

See the [main README](../README.md) for full deployment instructions.
