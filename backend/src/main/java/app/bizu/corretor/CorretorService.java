package app.bizu.corretor;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Contas dos corretores: criação no primeiro login com Google, consulta, instância da Evolution e o uso
 * mensal da IA (teto de respostas por corretor).
 */
@Service
public class CorretorService {

    private static final DateTimeFormatter MES = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);

    private final CorretorRepository corretores;
    private final CorretorProperties props;
    private final ApplicationEventPublisher eventos;

    CorretorService(CorretorRepository corretores, CorretorProperties props, ApplicationEventPublisher eventos) {
        this.corretores = corretores;
        this.props = props;
        this.eventos = eventos;
    }

    /**
     * Acha a conta pelo identificador do Google. Se não há, usa a conta antiga em que o sub era o próprio auth_id
     * (e passa a guardá-lo em google_sub). Se não há nenhuma, cria uma inativa, com auth_id novo, e avisa.
     */
    @Transactional
    public Corretor entrarComGoogle(String googleSub, String email, String nome, Instant agora) {
        Optional<Corretor> existente = corretores.buscarPorGoogleSub(googleSub)
                .or(() -> corretores.buscarPorAuthIdSemGoogleSub(googleSub));
        if (existente.isPresent()) {
            String authId = existente.get().authId();
            corretores.registrarLogin(authId, googleSub, email, nome, agora);
            return corretores.buscar(authId).orElseThrow();
        }
        boolean criada = corretores.criar(UUID.randomUUID().toString(), googleSub, email, nome, agora);
        Corretor corretor = corretores.buscarPorGoogleSub(googleSub).orElseThrow();
        if (criada) {
            eventos.publishEvent(new ContaCriada(corretor.authId(), email, nome, agora));
        }
        return corretor;
    }

    @Transactional(readOnly = true)
    public Optional<Corretor> buscar(String authId) {
        return corretores.buscar(authId);
    }

    @Transactional(readOnly = true)
    public Optional<Corretor> porInstancia(String instancia) {
        return corretores.buscarPorInstancia(instancia);
    }

    @Transactional
    public void definirInstancia(String authId, String instancia) {
        corretores.definirInstancia(authId, instancia);
    }

    /** Vazio quando a IA pode responder; senão, o motivo da pausa (conta, assinatura ou teto do mês). */
    @Transactional(readOnly = true)
    public Optional<MotivoDaPausa> motivoDaPausa(String authId, Instant agora) {
        Optional<Corretor> corretor = corretores.buscar(authId);
        if (corretor.isEmpty() || !corretor.get().ativo()) {
            return Optional.of(MotivoDaPausa.CONTA_INATIVA);
        }
        if (!corretor.get().iaLiberada(agora)) {
            return Optional.of(MotivoDaPausa.ASSINATURA_VENCIDA);
        }
        if (respostasNoMes(authId, agora) >= limiteMensal(corretor.get())) {
            return Optional.of(MotivoDaPausa.LIMITE_MENSAL);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public int respostasNoMes(String authId, Instant agora) {
        return corretores.respostasNoMes(authId, MES.format(agora));
    }

    public int limiteMensal(Corretor corretor) {
        return corretor.limiteRespostasMes() != null ? corretor.limiteRespostasMes() : props.limiteMensalRespostas();
    }

    /** Conta uma resposta da IA no mês; chamado na mesma transação que grava a mensagem enviada. */
    @Transactional
    public void contarRespostaDaIa(String authId, Instant agora) {
        corretores.contarResposta(authId, MES.format(agora));
    }
}
