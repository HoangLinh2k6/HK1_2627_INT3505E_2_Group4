ALTER TABLE courts
    ADD COLUMN base_price_per_hour DECIMAL(12, 2) NULL;

ALTER TABLE bookings
    ADD COLUMN request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ADD CONSTRAINT uk_booking_user_request UNIQUE (user_id, request_key);
