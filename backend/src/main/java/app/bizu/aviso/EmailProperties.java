package app.bizu.aviso;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code dono}: quem recebe os avisos do sistema (vazio = só registra no log). {@code remetente}: o "de" dos e-mails. */
@ConfigurationProperties("bizu.email")
record EmailProperties(String dono, String remetente) {
}
