package group4.backend.soa.service;

import group4.backend.soa.repository.AddonStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RentalPhotoMigration {
    private final AddonStore store;
    private final RentalPhotoService photos;

    public long migrate() {
        long lastId = 0;
        long count = 0;
        while (true) {
            var ids = store.legacyPhotoIds(lastId);
            if (ids.isEmpty()) {
                return count;
            }
            for (Long id : ids) {
                if (photos.migrateLegacyPhoto(id)) {
                    count++;
                }
                lastId = id;
            }
        }
    }
}
