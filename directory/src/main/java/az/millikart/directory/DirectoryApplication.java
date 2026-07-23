package az.millikart.directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "az.millikart")
public class DirectoryApplication {

    public static void main(String[] args) {
        SpringApplication.run(DirectoryApplication.class, args);
    }
}
