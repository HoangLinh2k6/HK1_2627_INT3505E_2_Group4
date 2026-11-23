package group4.backend.soa.controller;

import group4.backend.soa.service.AddonService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import static group4.backend.soa.dto.AddonModels.*;

@RestController
@RequestMapping("/venues/{venueId}/services")
@RequiredArgsConstructor
public class VenueAddonController {
    private final AddonService addons;

    @GetMapping
    List<ServiceView> catalog(@PathVariable Long venueId) {
        return addons.catalog(venueId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ServiceView create(@PathVariable Long venueId, @Valid @RequestBody CreateService input) {
        return addons.createService(venueId, input);
    }

    @PutMapping("/{serviceId}")
    ServiceView update(@PathVariable Long venueId, @PathVariable Long serviceId, @Valid @RequestBody UpdateService input) {
        return addons.updateService(venueId, serviceId, input);
    }

    @GetMapping("/{serviceId}/assets")
    List<AssetView> assets(@PathVariable Long venueId, @PathVariable Long serviceId) {
        return addons.assets(venueId, serviceId);
    }

    @PostMapping("/{serviceId}/assets")
    @ResponseStatus(HttpStatus.CREATED)
    AssetView createAsset(@PathVariable Long venueId, @PathVariable Long serviceId, @Valid @RequestBody CreateAsset input) {
        return addons.createAsset(venueId, serviceId, input);
    }

    @PutMapping("/{serviceId}/assets/{assetId}")
    AssetView updateAsset(@PathVariable Long venueId, @PathVariable Long serviceId, @PathVariable Long assetId,
                          @Valid @RequestBody UpdateAsset input) {
        return addons.updateAsset(venueId, serviceId, assetId, input);
    }
}
