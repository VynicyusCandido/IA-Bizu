package app.bizu.conversa;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class RelogioConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }
}
