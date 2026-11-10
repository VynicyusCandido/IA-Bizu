package app.bizu.corretor;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code limiteMensalRespostas}: teto de respostas da IA por corretor por mês, quando ele não tem um próprio. */
@ConfigurationProperties("bizu.corretor")
record CorretorProperties(int limiteMensalRespostas) {
}
