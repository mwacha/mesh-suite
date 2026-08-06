ALTER TABLE user_permission DROP CONSTRAINT user_permission_module_check;

ALTER TABLE user_permission ADD CONSTRAINT user_permission_module_check
    CHECK (module IN ('CUSTOMER','PRODUCT','ORDER','USER','PURCHASE','STOCK','PAYABLE'));
