ALTER TABLE app_user ADD COLUMN permission_profile_id UUID REFERENCES permission_profile(id);
CREATE INDEX idx_app_user_permission_profile_id ON app_user(permission_profile_id);
