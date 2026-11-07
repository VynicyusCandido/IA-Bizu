package app.bizu.conversa;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Conversa com um cliente. Imutável: cada transição devolve uma nova instância. */
public record Conversa(
        UUID id,
        String telefone,
        Estado estado,
        int respostasIa,
        boolean ativa,
        Instant criadaEm,
        Instant ultimaAtividadeEm) {

    public static Conversa nova(String telefone, Instant agora) {
        return new Conversa(UUID.randomUUID(), telefone, Estado.BOT, 0, true, agora, agora);
    }

    public boolean podeResponder(int limite) {
        return estado == Estado.BOT && respostasIa < limite;
    }

    /** Conta uma resposta da IA; ao atingir o limite a conversa é encerrada e a IA silencia. */
    public Conversa aposRespostaIa(int limite, Instant agora) {
        int total = respostasIa + 1;
        Estado novo = total >= limite ? Estado.ENCERRADA : estado;
        return new Conversa(id, telefone, novo, total, ativa, criadaEm, agora);
    }

    public Conversa assumida(Instant agora) {
        return new Conversa(id, telefone, Estado.HUMANO, respostasIa, ativa, criadaEm, agora);
    }

    /** Devolver à IA não zera o contador: se o limite já foi atingido, a conversa continua encerrada. */
    public Conversa devolvida(int limite, Instant agora) {
        Estado novo = respostasIa >= limite ? Estado.ENCERRADA : Estado.BOT;
        return new Conversa(id, telefone, novo, respostasIa, ativa, criadaEm, agora);
    }

    public Conversa comAtividade(Instant agora) {
        return new Conversa(id, telefone, estado, respostasIa, ativa, criadaEm, agora);
    }

    public Conversa desativada() {
        return new Conversa(id, telefone, estado, respostasIa, false, criadaEm, ultimaAtividadeEm);
    }

    public boolean expirada(Instant agora, Duration reinicioApos) {
        return Duration.between(ultimaAtividadeEm, agora).compareTo(reinicioApos) >= 0;
    }
}
