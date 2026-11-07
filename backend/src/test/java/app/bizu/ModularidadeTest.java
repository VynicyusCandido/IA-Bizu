package app.bizu;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularidadeTest {

    @Test
    void modulosRespeitamAsFronteiras() {
        ApplicationModules.of(BizuApplication.class).verify();
    }
}
