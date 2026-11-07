package app.bizu.agente;

import app.bizu.conversa.Agente.Trecho;
import java.util.ArrayList;
import java.util.List;

/** Monta o que vai ao modelo: o system prompt e os turnos user/assistant alternados. */
final class MontadorDeConversa {

    /** Regras que o corretor não consegue remover editando o prompt no painel. */
    static final String REGRAS_FIXAS = """
            Regras que valem sempre:
            - Nunca invente preço, metragem, disponibilidade, endereço ou qualquer dado de imóvel. Se perguntarem, diga que o corretor confirma e use a ferramenta passar_ao_corretor.
            - Se o cliente perguntar diretamente se está falando com uma pessoa ou com uma IA, não afirme ser uma pessoa; responda com honestidade e siga ajudando.
            - Escreva como mensagem de WhatsApp: curta, em texto simples, sem markdown, sem listas longas e uma única mensagem por vez.
            - Use registrar_qualificacao sempre que o cliente informar finalidade, região, faixa de valor ou prazo, e registrar_imovel_citado quando ele citar um imóvel específico.
            - Use passar_ao_corretor quando o cliente pedir para falar com uma pessoa, quiser negociar preço ou fechar negócio, ou fizer algo que você não deve responder.
            """;

    private static final String PROMPT_PADRAO = "Você atende clientes no WhatsApp em nome de um corretor de imóveis, "
            + "em português do Brasil, com educação e formalidade.";

    record Turno(boolean doUsuario, String texto) {
    }

    private MontadorDeConversa() {
    }

    static String sistema(String promptBase, String perfilDeEstilo) {
        StringBuilder sb = new StringBuilder();
        sb.append(vazio(promptBase) ? PROMPT_PADRAO : promptBase.strip());
        if (!vazio(perfilDeEstilo)) {
            sb.append("\n\nEstilo de escrita do corretor, que você deve imitar:\n").append(perfilDeEstilo.strip());
        }
        sb.append("\n\n").append(REGRAS_FIXAS.strip());
        return sb.toString();
    }

    /**
     * Cliente vira "user"; IA e corretor viram "assistant". Mensagens seguidas do mesmo lado são juntadas
     * e a conversa sempre começa por um turno do usuário.
     */
    static List<Turno> turnos(List<Trecho> historico) {
        List<Turno> turnos = new ArrayList<>();
        for (Trecho trecho : historico) {
            if (trecho.texto() == null || trecho.texto().isBlank()) {
                continue;
            }
            boolean usuario = trecho.doCliente();
            if (turnos.isEmpty() && !usuario) {
                continue;
            }
            if (!turnos.isEmpty() && turnos.get(turnos.size() - 1).doUsuario() == usuario) {
                Turno anterior = turnos.remove(turnos.size() - 1);
                turnos.add(new Turno(usuario, anterior.texto() + "\n" + trecho.texto()));
            } else {
                turnos.add(new Turno(usuario, trecho.texto()));
            }
        }
        return turnos;
    }

    private static boolean vazio(String s) {
        return s == null || s.isBlank();
    }
}
