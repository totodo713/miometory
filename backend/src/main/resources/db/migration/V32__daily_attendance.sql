CREATE TABLE IF NOT EXISTS daily_attendance (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant(id),
    member_id UUID NOT NULL REFERENCES members(id),
    attendance_date DATE NOT NULL,
    start_time TIME,
    end_time TIME,
    remarks TEXT,
    version INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_daily_attendance_member_date UNIQUE (member_id, attendance_date)
);

CREATE INDEX idx_daily_attendance_member_date ON daily_attendance(member_id, attendance_date);
CREATE INDEX idx_daily_attendance_tenant ON daily_attendance(tenant_id);
