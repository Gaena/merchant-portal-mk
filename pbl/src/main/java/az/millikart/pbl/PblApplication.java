package az.millikart.pbl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;


@SpringBootApplication(scanBasePackages = "az.millikart")
@EnableScheduling
public class PblApplication {


    public static void main(String[] args) {
        SpringApplication.run(PblApplication.class, args);
    }

}
