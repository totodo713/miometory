# Miometry - Manager Approval Guide

This guide helps managers efficiently review and approve team members' monthly time entries using Miometry.

## Table of Contents

1. [Overview](#overview)
2. [Accessing the Approval Dashboard](#accessing-the-approval-dashboard)
3. [Reviewing Submissions](#reviewing-submissions)
4. [Approving Entries](#approving-entries)
5. [Rejecting Entries](#rejecting-entries)
6. [Bulk Operations](#bulk-operations)
7. [Proxy Entry for Team Members](#proxy-entry-for-team-members)
8. [Reports and Analytics](#reports-and-analytics)
9. [Best Practices](#best-practices)

---

## Overview

As a manager, you're responsible for reviewing and approving your team members' monthly work log submissions. The approval workflow ensures accurate time tracking and compliance with organizational policies.

### Your Responsibilities

- Review submitted work logs by the 5th of each month
- Verify entries align with project assignments
- Approve accurate submissions promptly
- Provide clear feedback when rejecting entries
- Create proxy entries for team members when needed

### Approval Targets

| Metric | Target |
|--------|--------|
| Review completion | By 5th of following month |
| Time per team member | < 1 minute (target: 10 members in 10 minutes) |
| Approval accuracy | 95% on-time submissions team-wide |

---

## Accessing the Approval Dashboard

### Navigation

1. Log in to Miometry
2. Click "Approvals" in the main navigation
3. You'll see submissions pending your review

### Dashboard Overview

The approval dashboard shows:

- **Pending**: Submissions awaiting your review
- **Approved**: Recently approved submissions
- **Rejected**: Submissions returned for corrections
- **All**: Complete history of submissions

### Filtering Options

- **Month**: Filter by submission month
- **Status**: Draft, Pending, Approved, Rejected
- **Team Member**: Search by name
- **Department**: Filter by organizational unit

---

## Reviewing Submissions

### Quick Review Process

1. Click on a pending submission to open the detail view
2. Review the summary card:
   - Total working days
   - Total hours logged
   - Absence breakdown (vacation, sick, etc.)
   - Project allocation summary
3. Scan the calendar for anomalies:
   - Missing entries (highlighted in yellow)
   - Unusual hours (flagged with warning icon)
   - Weekend/holiday work (requires justification)

### Detailed Review

For a thorough review, click "View Details" to see:

- Day-by-day breakdown of hours
- Project allocation per day
- Notes and comments from the engineer
- Comparison with previous months

### Red Flags to Watch For

| Issue | Indicator |
|-------|-----------|
| Missing entries | Yellow highlighted days |
| Excessive hours | > 10 hours/day flagged |
| Weekend work | Blue days with entries |
| Holiday work | Orange days with entries |
| Project mismatch | Unassigned project codes |

---

## Approving Entries

### Single Approval

1. Review the submission
2. Click "Approve" button
3. Optionally add a note (e.g., "Looks good!")
4. Confirm the approval

### What Happens After Approval

- Entry is locked and cannot be edited
- Submission status changes to "Approved"
- Engineer receives email notification
- Entry is synced to payroll/billing systems

### Undoing an Approval

To unlock an approved entry for corrections:
1. Navigate to the approved submission
2. Click "Unlock for Editing"
3. Add a note explaining why
4. The status reverts to "Draft"
5. Engineer must re-submit after corrections

---

## Rejecting Entries

### When to Reject

- Missing or incomplete entries
- Hours don't match project expectations
- Incorrect absence coding
- Policy violations (e.g., unapproved overtime)

### Rejection Process

1. Click "Reject" on the submission
2. **Required**: Provide a clear rejection reason
3. Select affected days (optional but helpful)
4. Click "Confirm Rejection"

### Writing Effective Rejection Notes

**Good examples:**
- "Please add entries for Jan 15-16. Currently showing as blank."
- "Overtime on Jan 20 needs prior approval. Please attach the approved request."
- "Project XYZ-123 closed on Jan 10. Please reallocate hours after that date."

**Avoid:**
- "Please fix" (too vague)
- "Wrong" (unhelpful)
- "Check your entries" (doesn't specify the issue)

### After Rejection

- Engineer receives email with your feedback
- Status changes to "Rejected"
- Entry is unlocked for editing
- Engineer must re-submit after corrections

---

## Bulk Operations

### Bulk Approval

For approving multiple submissions at once:

1. Select submissions using checkboxes
2. Click "Bulk Actions" > "Approve Selected"
3. Review the list of selected submissions
4. Confirm the bulk approval

**When to use:** When submissions are clearly complete and accurate.

**When NOT to use:** For first-time reviews - always review individually first.

### CSV Export

Export submission data for reporting:

1. Filter the submissions you need
2. Click "Export" > "CSV"
3. Choose fields to include
4. Download the file

---

## Proxy Entry for Team Members

Create entries on behalf of team members when needed (e.g., extended absence, system access issues).

### Creating a Proxy Entry

1. Click "Proxy Entry" in the navigation
2. Select the team member from the dropdown
3. Choose the date(s) to enter
4. Fill in the time entry details
5. Add a note explaining the proxy entry
6. Save the entry

### Proxy Entry Audit Trail

All proxy entries are tracked:
- Original author (you as the manager)
- Timestamp of creation
- Reason/note for the proxy entry
- Cannot be edited to appear as the employee's own entry

### When to Use Proxy Entry

- Employee on extended medical leave
- System access issues preventing self-entry
- Retrospective corrections after employee departure
- Training new employees

---

## Reports and Analytics

### Available Reports

| Report | Description |
|--------|-------------|
| Team Summary | Overview of all team members' hours |
| Approval Status | Pending vs. approved by month |
| Overtime Report | Hours exceeding standard workweek |
| Absence Summary | Vacation, sick leave by employee |
| Project Allocation | Hours per project across team |

### Generating Reports

1. Navigate to "Reports" section
2. Select the report type
3. Choose date range and filters
4. Click "Generate"
5. Download as PDF or CSV

---

## Best Practices

### For Efficient Approvals

1. **Set a schedule**: Review submissions every Monday morning
2. **Use notifications**: Enable email alerts for new submissions
3. **Start with flagged items**: Address warnings first
4. **Use bulk approval wisely**: Only after initial individual review

### For Your Team

1. **Set expectations**: Communicate submission deadlines
2. **Provide feedback**: Specific rejection notes help engineers improve
3. **Be timely**: Approve within 48 hours to avoid bottlenecks
4. **Follow up**: Check on repeatedly rejected submissions

### For Compliance

1. **Document decisions**: Add notes when approving unusual entries
2. **Verify project codes**: Ensure allocations match assignments
3. **Flag anomalies**: Report suspected time fraud to HR
4. **Audit regularly**: Review approval patterns quarterly

---

## Quick Reference

### Approval Workflow States

```
Draft → Pending → Approved
                ↘ Rejected → (corrections) → Pending → ...
```

### Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `A` | Approve current submission |
| `R` | Reject current submission |
| `N` | Next submission |
| `P` | Previous submission |
| `Esc` | Close detail view |

### Status Icons

| Icon | Meaning |
|------|---------|
| Clock | Pending review |
| Green checkmark | Approved |
| Red X | Rejected |
| Warning triangle | Has issues to review |
| Lock | Locked (approved) |

---

## Getting Help

- **System Issues**: Contact IT Support
- **Policy Questions**: Contact HR
- **Feature Requests**: Submit via internal portal

---

*Last Updated: January 2026*
