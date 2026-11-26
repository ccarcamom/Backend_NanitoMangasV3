package cl.nanitomangas.administracion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class AdministracionApplication {
    public static void main(String[] args) {
        SpringApplication.run(AdministracionApplication.class, args);
    }
}
