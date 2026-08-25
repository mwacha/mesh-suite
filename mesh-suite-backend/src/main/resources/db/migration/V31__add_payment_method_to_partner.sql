ALTER TABLE partner ADD COLUMN payment_method_id UUID REFERENCES payment_method(id);
