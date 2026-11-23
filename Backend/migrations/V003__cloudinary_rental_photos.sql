ALTER TABLE rental_photos
    MODIFY COLUMN image MEDIUMBLOB NULL,
    ADD COLUMN cloudinary_public_id VARCHAR(255) NULL,
    ADD COLUMN content_sha256 VARCHAR(64) NULL;
