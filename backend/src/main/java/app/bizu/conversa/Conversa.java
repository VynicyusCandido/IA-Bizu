package app.bizu.conversa;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Conversa com um cliente. Imutável: cada transição devolve uma nova instância. */
public record Conversa(
        UUID id,
        String corretorId,
        String telefone,
        Estado estado,
        int respostasIa,
        boolean ativa,
        Instant criadaEm,
        Instant ultimaAtividadeEm) {

    public static Conversa nova(String corretorId, String telefone, Instant agora) {
        return nova(corretorId, telefone, Estado.BOT, agora);
    }

    /** Conversa nova já no estado certo (contato salvo nasce SEM_IA, quem o corretor já atende nasce HUMANO). */
    public static Conversa nova(String corretorId, String telefone, Estado estado, Instant agora) {
        return new Conversa(UUID.randomUUID(), corretorId, telefone, estado, 0, true, agora, agora);
    }

    /** Conversa que o histórico importado criou: nasce na data da primeira mensagem e vale até a última. */
    public static Conversa doHistorico(String corretorId, String telefone, Estado estado, Instant primeira, Instant ultima) {
        return new Conversa(UUID.randomUUID(), corretorId, telefone, estado, 0, true, primeira, ultima);
    }

    public boolean podeResponder(int limite) {
        return estado == Estado.BOT && respostasIa < limite;
    }

    /** Conta uma resposta da IA; ao atingir o limite a conversa é encerrada e a IA silencia. */
    public Conversa aposRespostaIa(int limite, Instant agora) {
        int total = respostasIa + 1;
        Estado novo = total >= limite ? Estado.ENCERRADA : estado;
        return new Conversa(id, corretorId, telefone, novo, total, ativa, criadaEm, agora);
    }

    public Conversa assumida(Instant agora) {
        return new Conversa(id, corretorId, telefone, Estado.HUMANO, respostasIa, ativa, criadaEm, agora);
    }

    /** Devolver à IA não zera o contador: se o limite já foi atingido, a conversa continua encerrada. */
    public Conversa devolvida(int limite, Instant agora) {
        Estado novo = respostasIa >= limite ? Estado.ENCERRADA : Estado.BOT;
        return new Conversa(id, corretorId, telefone, novo, respostasIa, ativa, criadaEm, agora);
    }

    public Conversa comAtividade(Instant agora) {
        return new Conversa(id, corretorId, telefone, estado, respostasIa, ativa, criadaEm, agora);
    }

    public Conversa desativada() {
        return new Conversa(id, corretorId, telefone, estado, respostasIa, false, criadaEm, ultimaAtividadeEm);
    }

    public boolean expirada(Instant agora, Duration reinicioApos) {
        return Duration.between(ultimaAtividadeEm, agora).compareTo(reinicioApos) >= 0;
    }
}
