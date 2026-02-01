# Miometry - Engineer User Manual

This guide helps software engineers use Miometry to record daily work hours, manage absences, and submit monthly time entries.

## Table of Contents

1. [Getting Started](#getting-started)
2. [Daily Time Entry](#daily-time-entry)
3. [Calendar View](#calendar-view)
4. [Managing Absences](#managing-absences)
5. [Multi-Project Time Allocation](#multi-project-time-allocation)
6. [Monthly Submission](#monthly-submission)
7. [Copy Previous Month](#copy-previous-month)
8. [Keyboard Shortcuts](#keyboard-shortcuts)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Logging In

1. Navigate to the Miometry application URL provided by your IT department
2. Click "Sign In" to authenticate via your organization's SSO provider
3. After successful authentication, you'll see your monthly calendar view

### First-Time Setup

No additional setup is required. Your employee profile is automatically synced from your organization's identity provider.

---

## Daily Time Entry

### Recording Work Hours

1. Click on any day in the calendar to open the daily entry form
2. For each project you worked on:
   - **Project**: Enter or select the project ID
   - **Hours**: Enter hours worked (in 0.25 increments, e.g., 7.5)
   - **Comment**: Optionally add a description of work performed
3. Click "+ Add Project" to add additional projects for the same day
4. Your total daily hours are calculated automatically from all project entries

### Auto-Save Feature

- All changes are automatically saved every 3 seconds
- Look for the "Saved" indicator in the form header
- If you see "Saving...", wait a moment for the save to complete
- You can safely close the browser; your data is preserved

### Editing Previous Entries

- Click on any past day to view and edit the entry
- Changes are auto-saved
- **Note**: You cannot edit entries for days in an already-approved month

---

## Calendar View

### Understanding the Calendar

| Indicator | Meaning |
|-----------|---------|
| Green checkmark | Entry complete with valid hours |
| Yellow outline | Entry exists but incomplete |
| Blue background | Weekend (Saturday/Sunday) |
| Orange background | Holiday (no entry required) |
| Gray background | Future dates (not yet editable) |
| Lock icon | Month submitted for approval |

### Navigation

- Use **< >** arrows to navigate between months
- Click "Today" to jump to the current month
- Click any day to open the entry form

---

## Managing Absences

### Recording an Absence

1. Click on the day you were absent
2. Select the **Absence Type** from the dropdown:
   - **Vacation**: Paid time off
   - **Sick Leave**: Illness-related absence
   - **Personal Day**: Personal errands
   - **Bereavement**: Family emergency leave
   - **Other**: Any other approved absence
3. Optionally add a note explaining the absence
4. The entry is auto-saved

### Partial Day Absences

For half-days or partial absences:
1. Enter the hours you actually worked
2. Select the absence type for the remaining time
3. Add a note like "Morning vacation, worked afternoon"

---

## Multi-Project Time Allocation

If you work on multiple projects, you can split your daily hours.

### Adding Project Allocations

1. Open a day's entry form
2. In the **Projects** section, click "Add Project"
3. Select the project from the dropdown
4. Enter the hours spent on that project
5. Repeat for additional projects

### Rules

- Total project hours must equal your working hours for the day
- You can allocate to a maximum of 10 projects per day
- If you don't use project allocation, hours default to your primary project

---

## Monthly Submission

### Submitting Your Month

1. Ensure all days in the month have valid entries
2. Click the "Submit for Approval" button
3. Review the summary:
   - Total working days
   - Total hours worked
   - Absences breakdown
4. Click "Confirm Submission"

### Submission Status

| Status | Meaning |
|--------|---------|
| Draft | Not yet submitted |
| Pending Approval | Submitted, awaiting manager review |
| Approved | Manager approved your entries |
| Rejected | Manager requested changes (see notes) |

### Handling Rejections

If your submission is rejected:
1. Check the rejection notes from your manager
2. Make the required corrections
3. Re-submit the month for approval

---

## Copy Previous Month

Speed up data entry by copying your project list from a previous month.

### How to Copy

1. Navigate to the month you want to fill
2. Click "Copy from Previous Month"
3. The system will copy your project assignments from last month
4. You will still need to enter hours and comments for each day

### What Gets Copied

- Project assignments (the list of projects you worked on)

### What Doesn't Get Copied

- Work hours (you must enter these manually)
- Comments and notes
- Absences (vacation, sick leave, etc.)
- Days that already have entries (won't be overwritten)

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `←` / `→` | Navigate between months |
| `Esc` | Close the current form |
| `Enter` | Save and close the form |
| `Tab` | Move to next field |
| `Shift + Tab` | Move to previous field |

---

## Troubleshooting

### "Session Expired" Message

Your session times out after 30 minutes of inactivity:
1. Click "Sign In Again"
2. Your unsaved changes from the last 3 seconds may be lost
3. Auto-save typically prevents significant data loss

### Entry Not Saving

If changes aren't being saved:
1. Check your internet connection
2. Look for error messages in red
3. Try refreshing the page
4. Contact IT if the issue persists

### Cannot Edit Past Month

Once a month is approved, entries are locked:
1. Contact your manager to request unlocking
2. Manager can reject the month to allow edits
3. After edits, re-submit for approval

### Missing Project in Dropdown

If your project isn't listed:
1. Check with your project manager that you're assigned
2. Project assignments sync hourly from the HR system
3. Contact IT to manually add the assignment if urgent

### Calendar Shows Wrong Holidays

Holiday calendars are configured by your organization:
1. Verify your location setting in your profile
2. Contact HR to update your regional settings
3. Holiday changes sync overnight

---

## Getting Help

- **Technical Issues**: Contact IT Support at it-support@company.com
- **Process Questions**: Contact your manager or HR
- **Feature Requests**: Submit via the internal feedback portal

---

*Last Updated: January 2026*
