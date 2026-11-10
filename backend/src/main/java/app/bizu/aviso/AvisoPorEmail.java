package app.bizu.aviso;

import app.bizu.corretor.ContaCriada;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Avisa o dono do sistema quando uma conta nova espera liberação. Sempre deixa uma linha no log; o e-mail só sai
 * se houver SMTP configurado (SPRING_MAIL_HOST) e um destinatário (BIZU_EMAIL_DONO). Falha de envio nunca
 * atrapalha o login de quem acabou de entrar.
 */
@Component
class AvisoPorEmail {

    private static final Logger log = LoggerFactory.getLogger(AvisoPorEmail.class);

    private final ObjectProvider<JavaMailSender> envio;
    private final EmailProperties props;

    AvisoPorEmail(ObjectProvider<JavaMailSender> envio, EmailProperties props) {
        this.envio = envio;
        this.props = props;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void contaCriada(ContaCriada conta) {
        log.warn("Conta nova aguardando liberação: {} ({}), auth_id {}", conta.email(), conta.nome(), conta.authId());
        JavaMailSender remetente = envio.getIfAvailable();
        if (remetente == null || props.dono() == null || props.dono().isBlank()) {
            return;
        }
        try {
            SimpleMailMessage mensagem = new SimpleMailMessage();
            mensagem.setTo(props.dono());
            if (props.remetente() != null && !props.remetente().isBlank()) {
                mensagem.setFrom(props.remetente());
            }
            mensagem.setSubject("Bizu: conta aguardando liberação");
            mensagem.setText("""
                    Um corretor entrou pela primeira vez e espera liberação.

                    Nome: %s
                    E-mail: %s

                    Para liberar (30 dias de assinatura):
                    update corretor set ativo = true, assinatura_ate = now() + interval '30 days' where auth_id = '%s';
                    """.formatted(conta.nome(), conta.email(), conta.authId()));
            remetente.send(mensagem);
        } catch (RuntimeException e) {
            log.error("Não consegui enviar o e-mail de conta nova ({}): {}", conta.email(), e.getMessage());
        }
    }
}
