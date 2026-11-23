package group4.backend.soa.service;

import group4.backend.soa.dto.AddonModels;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@TestConfiguration(proxyBeanMethods = false)
public class CloudinaryTestConfiguration {
    @Bean
    @Primary
    CloudinaryRentalPhotos fakeCloudinary() {
        var files = new ConcurrentHashMap<String, byte[]>();
        var client = mock(CloudinaryRentalPhotos.class);
        doAnswer(invocation -> {
            AddonModels.PhotoContent photo = invocation.getArgument(1);
            files.putIfAbsent(invocation.getArgument(0), photo.bytes());
            return null;
        }).when(client).upload(anyString(), any(AddonModels.PhotoContent.class));
        when(client.download(anyString(), anyString())).thenAnswer(invocation -> files.get(invocation.getArgument(0)));
        return client;
    }
}
