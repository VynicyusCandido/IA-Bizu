package app.bizu.aviso;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.bizu.corretor.ContaCriada;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class AvisoPorEmailTest {

    private static final ContaCriada CONTA = new ContaCriada("id-1", "ana@exemplo.com", "Ana", Instant.now());

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> com(JavaMailSender envio) {
        ObjectProvider<JavaMailSender> provedor = mock(ObjectProvider.class);
        when(provedor.getIfAvailable()).thenReturn(envio);
        return provedor;
    }

    @Test
    void enviaAoDonoComOComandoParaLiberar() {
        JavaMailSender envio = mock(JavaMailSender.class);

        new AvisoPorEmail(com(envio), new EmailProperties("dono@exemplo.com", "bizu@exemplo.com")).contaCriada(CONTA);

        ArgumentCaptor<SimpleMailMessage> enviada = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(envio).send(enviada.capture());
        assertThat(enviada.getValue().getTo()).containsExactly("dono@exemplo.com");
        assertThat(enviada.getValue().getFrom()).isEqualTo("bizu@exemplo.com");
        assertThat(enviada.getValue().getText()).contains("ana@exemplo.com").contains("id-1").contains("ativo = true");
    }

    @Test
    void semDestinatarioSoRegistraNoLog() {
        JavaMailSender envio = mock(JavaMailSender.class);

        new AvisoPorEmail(com(envio), new EmailProperties("", "")).contaCriada(CONTA);

        verify(envio, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void semServidorDeEmailConfiguradoNaoQuebra() {
        new AvisoPorEmail(com(null), new EmailProperties("dono@exemplo.com", "")).contaCriada(CONTA);
    }

    @Test
    void falhaNoEnvioNaoAtrapalhaOLoginDeQuemEntrou() {
        JavaMailSender envio = mock(JavaMailSender.class);
        doThrow(new org.springframework.mail.MailSendException("SMTP fora do ar")).when(envio).send(any(SimpleMailMessage.class));

        new AvisoPorEmail(com(envio), new EmailProperties("dono@exemplo.com", "")).contaCriada(CONTA);

        verify(envio).send(any(SimpleMailMessage.class));
    }
}
