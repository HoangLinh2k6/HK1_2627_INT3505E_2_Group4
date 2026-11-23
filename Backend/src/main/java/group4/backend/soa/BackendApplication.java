package group4.backend.soa;

import group4.backend.soa.auth.AccountProvisioning;
import group4.backend.soa.service.RentalPhotoMigration;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		if (args.length == 1 && args[0].equals("--provision-auth-account")) {
			try (var context = new SpringApplicationBuilder(BackendApplication.class)
					.web(WebApplicationType.NONE).run()) {
				context.getBean(AccountProvisioning.class).provision(context.getEnvironment());
				System.out.println("Account credentials saved.");
			}
			return;
		}
		if (args.length == 1 && args[0].equals("--migrate-rental-photos")) {
			try (var context = new SpringApplicationBuilder(BackendApplication.class)
					.web(WebApplicationType.NONE).run()) {
				long count = context.getBean(RentalPhotoMigration.class).migrate();
				System.out.printf("Migrated %d rental photos.%n", count);
			}
			return;
		}
		SpringApplication.run(BackendApplication.class, args);
	}

}
