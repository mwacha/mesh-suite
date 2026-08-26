ALTER TABLE app_user ADD COLUMN permission_profile_id UUID REFERENCES permission_profile(id);
