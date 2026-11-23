ALTER TABLE bookings
    ADD COLUMN checked_in_at DATETIME(6) NULL,
    ADD COLUMN checked_out_at DATETIME(6) NULL;

CREATE TABLE venue_addons (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    stock_on_hand INT NULL,
    active BOOLEAN NOT NULL,
    INDEX idx_addon_venue (venue_id),
    CONSTRAINT fk_addon_venue FOREIGN KEY (venue_id) REFERENCES venues (id)
);

CREATE TABLE equipment_assets (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    venue_id BIGINT NOT NULL,
    addon_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    state VARCHAR(20) NOT NULL,
    INDEX idx_asset_addon_state (addon_id, state),
    CONSTRAINT uk_asset_venue_code UNIQUE (venue_id, code),
    CONSTRAINT fk_asset_venue FOREIGN KEY (venue_id) REFERENCES venues (id),
    CONSTRAINT fk_asset_addon FOREIGN KEY (addon_id) REFERENCES venue_addons (id)
);

CREATE TABLE addon_orders (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    booking_id BIGINT NOT NULL,
    addon_id BIGINT NOT NULL,
    request_key VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    item_name VARCHAR(120) NOT NULL,
    quantity INT NOT NULL,
    unit_price DECIMAL(12, 2) NOT NULL,
    ordered_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    delivered_at DATETIME(6) NULL,
    delivered_by BIGINT NULL,
    INDEX idx_addon_order_addon (addon_id),
    CONSTRAINT uk_addon_order_request UNIQUE (booking_id, request_key),
    CONSTRAINT fk_addon_order_booking FOREIGN KEY (booking_id) REFERENCES bookings (id),
    CONSTRAINT fk_addon_order_addon FOREIGN KEY (addon_id) REFERENCES venue_addons (id)
);

CREATE TABLE equipment_rentals (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    asset_id BIGINT NOT NULL,
    handed_out_at DATETIME(6) NULL,
    returned_at DATETIME(6) NULL,
    returned_by BIGINT NULL,
    INDEX idx_rental_asset_return (asset_id, returned_at),
    CONSTRAINT uk_rental_order_asset UNIQUE (order_id, asset_id),
    CONSTRAINT fk_rental_order FOREIGN KEY (order_id) REFERENCES addon_orders (id),
    CONSTRAINT fk_rental_asset FOREIGN KEY (asset_id) REFERENCES equipment_assets (id)
);

CREATE TABLE rental_photos (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    rental_id BIGINT NOT NULL,
    phase VARCHAR(20) NOT NULL,
    angle VARCHAR(20) NOT NULL,
    content_type VARCHAR(20) NOT NULL,
    image MEDIUMBLOB NOT NULL,
    uploaded_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_rental_photo_angle UNIQUE (rental_id, phase, angle),
    CONSTRAINT fk_photo_rental FOREIGN KEY (rental_id) REFERENCES equipment_rentals (id)
);
